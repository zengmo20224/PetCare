package com.petcare.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcare.admin.security.AdminPrincipal;
import com.petcare.common.api.ApiResponse;
import com.petcare.common.exception.ErrorCode;
import com.petcare.user.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 消息端点限流（A5 安全修复，docs/09 §7.6）。
 *
 * <p>背景：{@code POST /api/v1/ai/conversations/{id}/messages[/stream]} 每次触发真实
 * DeepSeek 计费调用，此前无任何限流——登录用户可无限调用造成成本滥用。</p>
 *
 * <p>维度：<b>登录主体</b>（优先 userId/adminId，未认证回退直连 IP）而非纯 IP——
 * AI 端点全部需要登录，按主体分桶不受 XFF 伪造影响。阈值消费 docs/09 §11 登记的
 * {@code petcare.ai.agent.rate-limit-per-min}（默认 20 次/分钟/用户），
 * 作用于全部 AI 消息端点（V1 同步 + V2 Agent/SSE），超出返回 429。</p>
 *
 * <p>实现与 {@link RateLimitFilter} 同范式：进程内 ConcurrentHashMap 滑动窗口、
 * 惰性清理、单机足够（V1 单门店单体边界），多实例需换 Redis。</p>
 */
@Component
public class AiRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AiRateLimitFilter.class);

    /** AI 消息端点前缀（均为 POST，需登录）。 */
    private static final String PROTECTED_PREFIX = "/api/v1/ai/conversations/";

    /** 窗口（秒）：固定 60，与配置项 "-per-min" 语义一致。 */
    private static final int WINDOW_SECONDS = 60;

    /** docs/09 §11：单用户每分钟 AI 调用上限，默认 20。 */
    @Value("${petcare.ai.agent.rate-limit-per-min:20}")
    private long maxRequestsPerMinute;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 桶：key = "ai|principal"，value = 滑动窗口计数器。 */
    private final Map<String, SlidingWindow> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod())
                || !request.getRequestURI().startsWith(PROTECTED_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String principal = resolvePrincipal(request);
        String bucketKey = "ai|" + principal;

        SlidingWindow window = buckets.computeIfPresent(bucketKey, (k, w) -> {
            w.evictIfExpired();
            return w;
        });
        if (window == null) {
            window = new SlidingWindow();
            SlidingWindow existing = buckets.putIfAbsent(bucketKey, window);
            if (existing != null) {
                window = existing;
            }
        }

        long count = window.incrementAndGet();
        if (count > maxRequestsPerMinute) {
            log.warn("AI rate limit exceeded: principal={}, count={}, max={}",
                    principal, count, maxRequestsPerMinute);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            ApiResponse<Void> body = ApiResponse.error(
                    ErrorCode.RATE_LIMIT_EXCEEDED, "AI 请求过于频繁，请稍后再试");
            response.getWriter().write(objectMapper.writeValueAsString(body));
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 解析限流主体：登录用户 ID > 管理员 ID > 直连 IP（兜底）。
     * 本 Filter 注册顺序在 Spring Security 之后，SecurityContext 已填充；
     * 兜底 IP 用 remoteAddr（不信任 XFF，避免伪造绕过）。
     */
    private String resolvePrincipal(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null) {
            if (auth.getPrincipal() instanceof UserPrincipal user) {
                return "user:" + user.getUserId();
            }
            if (auth.getPrincipal() instanceof AdminPrincipal admin) {
                return "admin:" + admin.getAdminId();
            }
        }
        return "ip:" + request.getRemoteAddr();
    }

    /** 滑动窗口计数器（与 {@link RateLimitFilter} 同范式）。 */
    private static final class SlidingWindow {
        private volatile long windowStartSeconds;
        private final AtomicLong count;

        SlidingWindow() {
            this.windowStartSeconds = System.currentTimeMillis() / 1000;
            this.count = new AtomicLong(0);
        }

        long incrementAndGet() {
            return count.incrementAndGet();
        }

        void evictIfExpired() {
            long now = System.currentTimeMillis() / 1000;
            if (now - windowStartSeconds >= WINDOW_SECONDS) {
                windowStartSeconds = now;
                count.set(0);
            }
        }
    }
}
