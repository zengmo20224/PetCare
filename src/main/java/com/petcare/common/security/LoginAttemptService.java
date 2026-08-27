package com.petcare.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 账号级登录失败锁定（2026-08-23 审计 M5）。
 *
 * <p>按 <b>通道 + 账号标识</b> 维度做固定窗口失败计数：窗口内连续失败达到阈值即锁定，
 * 锁定期间登录请求在密码比对前直接拒绝；窗口过期自动解锁并重新计数，成功登录清零。</p>
 *
 * <ul>
 *   <li>USER 通道：默认 5 次失败 / 10 分钟窗口</li>
 *   <li>ADMIN 通道更严：默认 3 次失败 / 15 分钟窗口</li>
 *   <li>锁定错误复用 {@code rate_limit_exceeded}（HTTP 429），文案不透露账号是否存在，
 *       避免被用于账号枚举</li>
 * </ul>
 *
 * <p>实现与 {@link RateLimitFilter} 同范式：进程内 ConcurrentHashMap、惰性过期重置、
 * 容量上限 + 闲置清扫防内存耗尽（伪造账号名枚举洪泛场景）。多实例部署需换 Redis。</p>
 */
@Component
public class LoginAttemptService {

    public enum Channel { USER, ADMIN }

    @Value("${petcare.security.lockout.user-max-failures:5}")
    private int userMaxFailures = 5;

    @Value("${petcare.security.lockout.user-window-seconds:600}")
    private int userWindowSeconds = 600;

    @Value("${petcare.security.lockout.admin-max-failures:3}")
    private int adminMaxFailures = 3;

    @Value("${petcare.security.lockout.admin-window-seconds:900}")
    private int adminWindowSeconds = 900;

    /** 记录容量上限：超过即触发闲置清扫，防止海量伪造账号名撑爆堆内存。 */
    @Value("${petcare.security.lockout.max-keys:50000}")
    private int maxKeys = 50_000;

    private static final int SWEEP_THROTTLE_SECONDS = 60;

    private final ConcurrentHashMap<String, FailureWindow> failures = new ConcurrentHashMap<>();
    private volatile long lastSweepSeconds;

    /**
     * 判断账号是否处于锁定状态。
     * 标识为空视为不可判定，返回 false（由上层凭证校验兜底）。
     */
    public boolean isLocked(Channel channel, String principal) {
        if (principal == null || principal.isBlank()) {
            return false;
        }
        FailureWindow window = failures.get(key(channel, principal));
        if (window == null) {
            return false;
        }
        window.evictIfExpired(windowOf(channel));
        return window.count() >= maxFailures(channel);
    }

    /** 记录一次失败。容量打满且无可清扫条目时放弃记录新 key（保护内存，已跟踪账号不受影响）。 */
    public void recordFailure(Channel channel, String principal) {
        if (principal == null || principal.isBlank()) {
            return;
        }
        String k = key(channel, principal);
        // 仅新 key 需要预留容量；已跟踪账号在容量打满时仍正常计数
        if (!failures.containsKey(k) && !tryReserveSlot()) {
            return;
        }
        FailureWindow window = failures.computeIfAbsent(k, x -> new FailureWindow());
        window.record(windowOf(channel));
    }

    /** 登录成功后清零该账号的失败计数。 */
    public void onSuccess(Channel channel, String principal) {
        if (principal == null || principal.isBlank()) {
            return;
        }
        failures.remove(key(channel, principal));
    }

    /** 已跟踪 key 数量观测（测试/运维用，包内可见）。 */
    int trackedKeyCount() {
        return failures.size();
    }

    private boolean tryReserveSlot() {
        if (failures.size() < maxKeys) {
            return true;
        }
        long now = System.currentTimeMillis() / 1000;
        if (now - lastSweepSeconds >= SWEEP_THROTTLE_SECONDS) {
            lastSweepSeconds = now;
            long idleCutoff = now - 2L * Math.max(userWindowSeconds, adminWindowSeconds);
            failures.values().removeIf(w -> w.lastAccessSeconds <= idleCutoff);
        }
        return failures.size() < maxKeys;
    }

    private int maxFailures(Channel channel) {
        return channel == Channel.ADMIN ? adminMaxFailures : userMaxFailures;
    }

    private int windowOf(Channel channel) {
        return channel == Channel.ADMIN ? adminWindowSeconds : userWindowSeconds;
    }

    private static String key(Channel channel, String principal) {
        return channel.name() + ":" + principal;
    }

    /**
     * 固定窗口失败计数器：首个失败开启窗口，窗口内累计；过期后惰性重置。
     */
    private static final class FailureWindow {
        private volatile long windowStartSeconds;
        private volatile long lastAccessSeconds;
        private final AtomicInteger count = new AtomicInteger(0);

        FailureWindow() {
            long now = System.currentTimeMillis() / 1000;
            this.windowStartSeconds = now;
            this.lastAccessSeconds = now;
        }

        int count() {
            return count.get();
        }

        void record(int windowSeconds) {
            evictIfExpired(windowSeconds);
            lastAccessSeconds = System.currentTimeMillis() / 1000;
            count.incrementAndGet();
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
