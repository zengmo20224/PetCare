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
            "/api/v1/auth/forgot-password/",
            // C3 修复：微信登录同样可被脚本化批量打（mock 模式下还能无限造号），纳入限流
            "/api/v1/auth/wechat-login"
    );

    /** 默认窗口（秒）：60 秒。 */
    @Value("${petcare.security.rate-limit.window-seconds:60}")
    private int windowSeconds;

    /** 默认窗口内最大请求数：10。 */
    @Value("${petcare.security.rate-limit.max-requests:10}")
    private long maxRequests;

    /**
     * 管理员登录端点独立阈值（默认 5，2026-08-23 审计 M5）。
     * 管理后台是爆破首要目标，IP 维度阈值应严于普通端点，
     * 与 {@link LoginAttemptService} 的账号级锁定形成双层防护。
     */
    @Value("${petcare.security.rate-limit.admin-max-requests:5}")
    private long adminMaxRequests = 5;

    /**
     * 桶容量上限（默认 50000，2026-08-23 审计 M1）。
     * <p>达到上限时先清扫闲置桶；清扫后仍满则该请求放行但不计数（fail-open），
     * 防止海量伪造 IP 以新桶撑爆堆内存（慢性 OOM DoS）。字段初始化保证非
     * Spring 构造（单测）下同样有界。</p>
     */
    @Value("${petcare.security.rate-limit.max-buckets:50000}")
    private int maxBuckets = 50_000;

    /** 上次全量清扫时间戳（秒）。节流清扫频率，避免洪峰期每请求 O(n) 扫描。 */
    private volatile long lastSweepSeconds;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 多段 XFF 告警节流（60 秒一条），避免攻击者刷头打爆日志。 */
    private volatile long lastMultiXffWarnSeconds;

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

        if (!tryReserveBucketSlot()) {
            log.warn("Rate-limit bucket capacity exhausted ({}), serving without counting: ip={}, endpoint={}",
                    maxBuckets, clientIp, endpoint);
            filterChain.doFilter(request, response);
            return;
        }

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
        long limit = "/api/v1/admin/auth/login".equals(endpoint) ? adminMaxRequests : maxRequests;
        if (count > limit) {
            log.warn("Rate limit exceeded: ip={}, endpoint={}, count={}, max={}",
                    clientIp, endpoint, count, limit);
            writeRateLimited(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 为新桶预留容量（2026-08-23 审计 M1）。
     * <p>仅在需要创建新桶时调用：容量未满直接放行；已满则先清扫
     * 闲置超过一个窗口的桶（清扫按窗口节流），清扫后仍有空位才算预留成功。
     * 返回 false 表示容量耗尽，调用方应放行请求但不计数（fail-open，
     * 避免 IP 洪泛演变为全站登录不可用）。既有桶的命中不受容量限制。</p>
     */
    private boolean tryReserveBucketSlot() {
        if (buckets.size() < maxBuckets) {
            return true;
        }
        long now = System.currentTimeMillis() / 1000;
        if (now - lastSweepSeconds >= windowSeconds) {
            lastSweepSeconds = now;
            long idleCutoff = now - windowSeconds;
            buckets.values().removeIf(w -> w.lastAccessSeconds <= idleCutoff);
        }
        return buckets.size() < maxBuckets;
    }

    /** 桶数量观测（测试/运维用，包内可见）。 */
    int bucketCount() {
        return buckets.size();
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
     * 解析客户端真实 IP（C1 安全修复）。
     * <p>取 X-Forwarded-For <b>最后一段</b>：本项目 nginx 反代用
     * {@code $proxy_add_x_forwarded_for}（追加而非覆盖），XFF 尾部是可信 nginx
     * 追加的真实连接地址；首段攻击者可控（伪造 {@code XFF: 1.2.3.x} 即可每请求
     * 换桶绕过限流），绝不可用作限流键。无 XFF 时用 remoteAddr。</p>
     */
    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // B1/M4（2026-08-23）：生产链路（Caddy 覆盖写 + nginx 透传）应收到单值；
            // 多段意味着反代配置偏离预期（追加式传递或多余代理层），限流键可能退化，告警提示排查。
            if (xff.contains(",") && shouldWarnMultiSegmentXff()) {
                log.warn("X-Forwarded-For has multiple segments ({}); expected single value "
                        + "from the trusted reverse proxy. Check nginx 'proxy_set_header "
                        + "X-Forwarded-For' config (must pass through, not append).", xff);
            }
            int comma = xff.lastIndexOf(',');
            String last = comma > 0 ? xff.substring(comma + 1).trim() : xff.trim();
            if (!last.isEmpty()) {
                return last;
            }
        }
        return request.getRemoteAddr();
    }

    private boolean shouldWarnMultiSegmentXff() {
        long now = System.currentTimeMillis() / 1000;
        if (now - lastMultiXffWarnSeconds < 60) {
            return false;
        }
        lastMultiXffWarnSeconds = now;
        return true;
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
     * lastAccessSeconds 供桶清扫判定闲置（M1 容量防护）。
     */
    private static final class SlidingWindow {
        private volatile long windowStartSeconds;
        private volatile long lastAccessSeconds;
        private final AtomicLong count;

        SlidingWindow() {
            long now = System.currentTimeMillis() / 1000;
            this.windowStartSeconds = now;
            this.lastAccessSeconds = now;
            this.count = new AtomicLong(0);
        }

        long incrementAndGet() {
            lastAccessSeconds = System.currentTimeMillis() / 1000;
            return count.incrementAndGet();
        }

        void evictIfExpired(int windowSeconds) {
            long now = System.currentTimeMillis() / 1000;
            if (now - windowStartSeconds >= windowSeconds) {
                windowStartSeconds = now;
                count.set(0);
            }
            lastAccessSeconds = now;
        }
    }
}
