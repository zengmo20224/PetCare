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
 * 上传接口分钟级限流（上线加固，2026-08-15 安全审计项）。
 *
 * <p>背景：{@code POST /api/v1/upload} 是全站唯一文件上传口（头像/证件照/帖子配图/
 * 商品图），单文件 10MB 上限但<b>无任何频率限制</b>——登录用户可脚本化无限次上传，
 * 永久落盘写满 api-uploads 卷拖垮服务，且 /uploads/** 匿名可读可被当免费图床。</p>
 *
 * <p>与 {@link AiRateLimitFilter} 同范式：仅 POST、维度为登录主体
 * （userId/adminId，未认证回退直连 IP，不信任 XFF）、进程内滑动窗口、惰性重置，
 * 单机足够（V1 单门店单体边界），多实例需换 Redis。</p>
 *
 * <p>阈值：{@code petcare.upload.rate-limit-per-min}（默认 20 次/分钟/主体）。</p>
 */
@Component
public class UploadRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(UploadRateLimitFilter.class);

    /** 全站唯一上传端点（FileUploadController）。 */
    private static final String UPLOAD_PATH = "/api/v1/upload";

    /** 窗口（秒）：固定 60，与配置项 "-per-min" 语义一致。 */
    private static final int WINDOW_SECONDS = 60;

    @Value("${petcare.upload.rate-limit-per-min:20}")
    private long maxRequestsPerMinute;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 桶：key = "upload|principal"。 */
    private final Map<String, SlidingWindow> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod())
                || !UPLOAD_PATH.equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String principal = resolvePrincipal(request);
        String key = "upload|" + principal;

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

        long count = window.incrementAndGet();
        if (count > maxRequestsPerMinute) {
            log.warn("Upload rate limit exceeded: principal={}, count={}, max={}",
                    principal, count, maxRequestsPerMinute);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            ApiResponse<Void> body = ApiResponse.error(
                    ErrorCode.RATE_LIMIT_EXCEEDED, "上传过于频繁，请稍后再试");
            response.getWriter().write(objectMapper.writeValueAsString(body));
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** 与 {@link AiRateLimitFilter#resolvePrincipal} 同语义：登录主体 > 直连 IP。 */
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
