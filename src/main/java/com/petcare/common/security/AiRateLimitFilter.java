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
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 计费端点限流 + 每日额度（A5 安全修复 + 上线额度加固，docs/09 §7.6）。
 *
 * <p>背景：AI 相关端点每次触发真实 DeepSeek 计费。A5 修复只覆盖了
 * {@code POST /api/v1/ai/conversations/{id}/messages[/stream]}；上线前审计发现
 * {@code /api/v1/ai/post-assistant/generate}、管理端 AI 端点、社区发帖触发的文本审核
 * 同样计费却不受限流——本 Filter 统一保护，并增加每日额度（用户要求：AI 上线启用
 * 且必须可设额度上限）。</p>
 *
 * <p>保护范围（均为 POST）：{@code /api/v1/ai/conversations/}（发消息/SSE）、
 * {@code /api/v1/ai/post-assistant/}（帖子文案助手）、{@code /api/v1/admin/ai/}
 * （经营分析/知识重建等管理端 AI）、{@code /api/v1/posts}（发帖触发文本审核计费）。</p>
 *
 * <p>三层限制，维度均为<b>登录主体</b>（优先 userId/adminId，未认证回退直连 IP，
 * 不信任 XFF——AI 端点全部需要登录，按主体分桶不受 XFF 伪造影响）：
 * <ol>
 *   <li>每分钟滑动窗口：{@code petcare.ai.agent.rate-limit-per-min}（默认 20）；</li>
 *   <li>每用户每日额度：{@code petcare.ai.agent.daily-limit-per-user}（默认 50，0=关闭）；
 *       发帖仅在 AI Provider 启用（真实计费）时计入，Provider 关闭不影响正常发帖；</li>
 *   <li>全站每日额度兜底：{@code petcare.ai.agent.daily-limit-global}（默认 0=关闭），
 *       跨主体累计，防多账号脚本刷量。</li>
 * </ol></p>
 *
 * <p>实现与 {@link RateLimitFilter} 同范式：进程内 ConcurrentHashMap、惰性重置、
 * 单机足够（V1 单门店单体边界），多实例需换 Redis。额度计数为拦截层近似
 * （重启清零、分钟级拒绝不重复计入），精确对账以 ai_usage_log 为准。</p>
 */
@Component
public class AiRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AiRateLimitFilter.class);

    /** AI 消息端点前缀（发消息 + SSE，A5 原覆盖范围）。 */
    private static final String CONVERSATIONS_PREFIX = "/api/v1/ai/conversations/";

    /** 帖子文案助手前缀（上线前审计补充：同走 DeepSeek 计费）。 */
    private static final String POST_ASSISTANT_PREFIX = "/api/v1/ai/post-assistant/";

    /** 管理端 AI 端点前缀（经营分析 / 知识重建等）。 */
    private static final String ADMIN_AI_PREFIX = "/api/v1/admin/ai/";

    /** 社区发帖精确路径（触发文本审核 Agent 计费）。 */
    private static final String POSTS_PATH = "/api/v1/posts";

    /** 窗口（秒）：固定 60，与配置项 "-per-min" 语义一致。 */
    private static final int WINDOW_SECONDS = 60;

    /** docs/09 §11：单用户每分钟 AI 调用上限，默认 20。 */
    @Value("${petcare.ai.agent.rate-limit-per-min:20}")
    private long maxRequestsPerMinute;

    /** 上线额度加固：单主体每日 AI 调用上限，0 = 关闭。 */
    @Value("${petcare.ai.agent.daily-limit-per-user:50}")
    private long dailyLimitPerUser;

    /** 上线额度加固：全站每日 AI 调用上限（多账号刷量兜底），0 = 关闭。 */
    @Value("${petcare.ai.agent.daily-limit-global:0}")
    private long dailyLimitGlobal;

    /**
     * AI Provider 是否启用（DeepSeek 真实计费开关）。仅影响 {@code /api/v1/posts}
     * 是否计入每日额度：Provider 关闭时发帖不产生计费，不应占用 AI 额度。
     */
    @Value("${petcare.ai.provider-enabled:false}")
    private boolean providerEnabled;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 分钟桶：key = "ai|principal"。 */
    private final Map<String, SlidingWindow> buckets = new ConcurrentHashMap<>();

    /** 日额度桶：key = "ai-daily|principal"。 */
    private final Map<String, DailyCounter> dailyBuckets = new ConcurrentHashMap<>();

    /** 全站日额度计数器（单实例，无需 Map）。 */
    private final DailyCounter globalDaily = new DailyCounter();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (!"POST".equalsIgnoreCase(request.getMethod()) || !isProtected(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        String principal = resolvePrincipal(request);

        // 1) 每分钟滑动窗口（A5 原有行为）
        SlidingWindow window = windowFor(principal);
        long minuteCount = window.incrementAndGet();
        if (minuteCount > maxRequestsPerMinute) {
            log.warn("AI rate limit exceeded: principal={}, count={}, max={}",
                    principal, minuteCount, maxRequestsPerMinute);
            reject(response, "AI 请求过于频繁，请稍后再试");
            return;
        }

        // 2) 每日额度（上线加固；发帖仅在 Provider 计费时计入）
        if (countsTowardDailyQuota(uri)) {
            if (dailyLimitPerUser > 0 && counterFor(principal).incrementAndGet() > dailyLimitPerUser) {
                log.warn("AI daily user quota exceeded: principal={}, limit={}", principal, dailyLimitPerUser);
                reject(response, "今日 AI 用量已达上限，请明日再试");
                return;
            }
            if (dailyLimitGlobal > 0 && globalDaily.incrementAndGet() > dailyLimitGlobal) {
                log.warn("AI daily global quota exceeded: principal={}, limit={}", principal, dailyLimitGlobal);
                reject(response, "今日全站 AI 用量已达上限，请稍后再试");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isProtected(String uri) {
        return uri.startsWith(CONVERSATIONS_PREFIX)
                || uri.startsWith(POST_ASSISTANT_PREFIX)
                || uri.startsWith(ADMIN_AI_PREFIX)
                || POSTS_PATH.equals(uri);
    }

    /** 发帖只有触发文本审核（Provider 启用）才产生 AI 计费，关闭时不占额度。 */
    private boolean countsTowardDailyQuota(String uri) {
        return !POSTS_PATH.equals(uri) || providerEnabled;
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> body = ApiResponse.error(ErrorCode.RATE_LIMIT_EXCEEDED, message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private SlidingWindow windowFor(String principal) {
        String key = "ai|" + principal;
        SlidingWindow window = buckets.computeIfPresent(key, (k, w) -> {
            w.evictIfExpired();
            return w;
        });
        if (window == null) {
            window = new SlidingWindow();
            SlidingWindow existing = buckets.putIfAbsent(key, window);
            if (existing != null) {
                window = existing;
            }
        }
        return window;
    }

    private DailyCounter counterFor(String principal) {
        String key = "ai-daily|" + principal;
        DailyCounter counter = dailyBuckets.computeIfPresent(key, (k, c) -> {
            c.evictIfExpired();
            return c;
        });
        if (counter == null) {
            counter = new DailyCounter();
            DailyCounter existing = dailyBuckets.putIfAbsent(key, counter);
            if (existing != null) {
                counter = existing;
            }
        }
        return counter;
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

    /** 按自然日重置的计数器（日额度）。 */
    private static final class DailyCounter {
        private volatile long dayEpoch;
        private final AtomicLong count;

        DailyCounter() {
            this.dayEpoch = LocalDate.now().toEpochDay();
            this.count = new AtomicLong(0);
        }

        long incrementAndGet() {
            return count.incrementAndGet();
        }

        void evictIfExpired() {
            long today = LocalDate.now().toEpochDay();
            if (today != dayEpoch) {
                dayEpoch = today;
                count.set(0);
            }
        }
    }
}
