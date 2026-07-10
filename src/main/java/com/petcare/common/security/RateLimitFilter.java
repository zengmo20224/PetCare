package com.petcare.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcare.common.api.ApiResponse;
import com.petcare.common.exception.ErrorCode;
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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存滑动窗口限流过滤器（H2 安全修复）。
 *
 * <p>仅作用于以下易遭暴力破解的 permitAll 端点：
 * <ul>
 *   <li>{@code POST /api/v1/auth/login}             用户登录</li>
 *   <li>{@code POST /api/v1/admin/auth/login}        管理员登录</li>
 *   <li>{@code POST /api/v1/auth/register}           注册</li>
 *   <li>{@code POST /api/v1/auth/forgot-password/**} 找回密码</li>
 * </ul>
 *
 * <p>按 {@code 客户端 IP + 端点} 维度做滑动窗口计数。超出阈值返回 HTTP 429 +
 * 统一 {@link ApiResponse} 错误体（code = {@code rate_limit_exceeded}）。
 *
 * <p>实现说明：
 * <ul>
 *   <li>纯进程内 {@link ConcurrentHashMap}，零外部依赖（V1 单门店单体边界）。</li>
 *   <li>窗口过期条目惰性清理，避免后台线程。</li>
 *   <li>线程安全：每个桶用 {@link AtomicLong} 计数。</li>
 * </ul>
 *
 * <p>注意：单机限流对 V1 单实例部署足够；多实例需替换为 Redis 后端。
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /** 受限端点前缀列表（仅 POST，匹配 requestURI）。 */
    private static final List<String> PROTECTED_PREFIXES = List.of(
            "/api/v1/auth/login",
            "/api/v1/admin/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/forgot-password/"
    );

    /** 默认窗口（秒）：60 秒。 */
    @Value("${petcare.security.rate-limit.window-seconds:60}")
    private int windowSeconds;

    /** 默认窗口内最大请求数：10。 */
    @Value("${petcare.security.rate-limit.max-requests:10}")
    private long maxRequests;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 桶：key = "ip|endpoint"，value = 滑动窗口计数器。 */
    private final Map<String, SlidingWindow> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String endpoint = matchProtectedEndpoint(request);
        if (endpoint == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        String bucketKey = clientIp + "|" + endpoint;

        SlidingWindow window = buckets.computeIfPresent(bucketKey, (k, w) -> {
            w.evictIfExpired(windowSeconds);
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
        if (count > maxRequests) {
            log.warn("Rate limit exceeded: ip={}, endpoint={}, count={}, max={}",
                    clientIp, endpoint, count, maxRequests);
            writeRateLimited(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 判断当前请求是否命中受限端点。返回规范化端点 key（用于桶分组），未命中返回 null。
     */
    private String matchProtectedEndpoint(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        String uri = request.getRequestURI();
        for (String prefix : PROTECTED_PREFIXES) {
            if (uri.equals(prefix) || uri.startsWith(prefix)) {
                // forgot-password 子路径归一化为统一端点，避免被路径参数绕过
                if (prefix.equals("/api/v1/auth/forgot-password/")) {
                    return "/api/v1/auth/forgot-password/**";
                }
                return uri;
            }
        }
        return null;
    }

    /**
     * 解析客户端真实 IP。优先信任反代头（V1 单门店 nginx 部署），
     * 取 X-Forwarded-For 第一个；无则用 remoteAddr。
     */
    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            String first = comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * 写入 429 响应，保持与 GlobalExceptionHandler 一致的 ApiResponse 结构。
     */
    private void writeRateLimited(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> body = ApiResponse.error(
                ErrorCode.RATE_LIMIT_EXCEEDED,
                "请求过于频繁，请稍后再试");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    /**
     * 滑动窗口计数器：记录窗口起点与累计计数。
     * 过期后通过 evictIfExpired 重置（惰性清理）。
     */
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

        void evictIfExpired(int windowSeconds) {
            long now = System.currentTimeMillis() / 1000;
            if (now - windowStartSeconds >= windowSeconds) {
                windowStartSeconds = now;
                count.set(0);
            }
        }
    }
}
