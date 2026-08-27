package com.petcare.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * JWT 服务端撤销登记（2026-08-23 审计 P2：改密后旧 token 立即失效）。
 *
 * <p>记录"主体 → 撤销生效时刻"，早于该时刻签发的同通道 token 在
 * {@link JwtAuthenticationFilter} 中一律拒绝。触发点：改密、密保找回重置。</p>
 *
 * <p><b>实现边界（与限流过滤器一致，V1 单实例可接受）</b>：
 * 进程内 ConcurrentHashMap；重启清零后旧 token 最长恢复至剩余 TTL（≤120 分钟），
 * 与未引入本机制前的常态一致——即本机制只改善不回退。多实例需换 Redis。
 * 条目在超过 max(TTL) 后由惰性清扫回收，Map 有界。</p>
 */
@Component
public class JwtRevocationRegistry {

    private static final String TYPE_USER = "USER";
    private static final String TYPE_ADMIN = "ADMIN";
    /** 撤销记录容量上限（默认 50000，可配）：达到后触发惰性清扫（与限流过滤器同款防护）。 */
    @Value("${petcare.security.revocation.max-keys:50000}")
    private int maxKeys = 50_000;
    private static final int SWEEP_THROTTLE_SECONDS = 60;

    @Value("${petcare.security.jwt-expiration-minutes:120}")
    private int jwtExpirationMinutes = 120;

    /** 测试可注入的清扫节流秒数。 */
    @Value("${petcare.security.revocation.sweep-throttle-seconds:60}")
    private int sweepThrottleSeconds = SWEEP_THROTTLE_SECONDS;

    /** key = "TYPE|subjectId" → 撤销生效 epoch 秒；早于该值签发的 token 无效。 */
    private final ConcurrentHashMap<String, Long> revokedBeforeBySubject = new ConcurrentHashMap<>();
    private volatile long lastSweepSeconds;

    public void revokeUser(Long userId) {
        revoke(TYPE_USER, userId);
    }

    public void revokeAdmin(Long adminId) {
        revoke(TYPE_ADMIN, adminId);
    }

    /**
     * 判断指定主体的 token 是否已被撤销。
     * 规则：{@code issuedAt <= revokeThreshold} 即撤销——撤销秒内已存在的旧 token
     * 必须失效（JWT iat 仅秒级精度，无法区分秒内先后，宁严勿漏）；
     * 撤销后新登录签发的 token 若落在同一秒会被误拒一次，属可接受的边界代价
     * （用户重试即成功）。无 iat 的异常 token 按 0 处理，必然命中撤销判定。
     */
    public boolean isRevoked(String tokenType, Long subjectId, long issuedAtEpochSeconds) {
        if (subjectId == null || tokenType == null) {
            return false;
        }
        Long threshold = revokedBeforeBySubject.get(tokenType + "|" + subjectId);
        return threshold != null && issuedAtEpochSeconds <= threshold;
    }

    /** 已跟踪条目数观测（测试/运维用，包内可见）。 */
    int trackedCount() {
        return revokedBeforeBySubject.size();
    }

    private void revoke(String type, Long subjectId) {
        if (subjectId == null) {
            return;
        }
        sweepIfNeeded();
        revokedBeforeBySubject.put(type + "|" + subjectId, System.currentTimeMillis() / 1000);
    }

    /**
     * 条目存活超过 TTL+宽限后即可回收（此时其覆盖的所有 token 已自然过期）。
     */
    private void sweepIfNeeded() {
        if (revokedBeforeBySubject.size() < maxKeys) {
            return;
        }
        long now = System.currentTimeMillis() / 1000;
        if (now - lastSweepSeconds < sweepThrottleSeconds) {
            return;
        }
        lastSweepSeconds = now;
        long expireCutoff = now - (jwtExpirationMinutes * 60L);
        // 严格小于：为 TTL 边界保留 1 秒时钟偏移余量
        revokedBeforeBySubject.values().removeIf(threshold -> threshold < expireCutoff);
    }
}
