package com.petcare.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link JwtRevocationRegistry} 单测（2026-08-23 审计 P2：改密后旧 JWT 撤销）。
 */
class JwtRevocationRegistryTest {

    private JwtRevocationRegistry registry;
    private long nowSeconds;

    @BeforeEach
    void setUp() {
        registry = new JwtRevocationRegistry();
        nowSeconds = System.currentTimeMillis() / 1000;
        ReflectionTestUtils.setField(registry, "jwtExpirationMinutes", 120);
    }

    @Test
    @DisplayName("撤销后：早于撤销时刻签发的 token 被判 revoked，之后签发的放行")
    void revoke_invalidatesTokensIssuedBefore() {
        long issuedBefore = nowSeconds - 600;
        long issuedAfter = nowSeconds + 1;

        assertThat(registry.isRevoked("USER", 42L, issuedBefore)).isFalse();

        registry.revokeUser(42L);

        assertThat(registry.isRevoked("USER", 42L, issuedBefore)).isTrue();
        assertThat(registry.isRevoked("USER", 42L, nowSeconds)).isTrue();
        assertThat(registry.isRevoked("USER", 42L, issuedAfter)).isFalse();
    }

    @Test
    @DisplayName("未撤销主体不受影响；USER/ADMIN 同 ID 通道互不干扰")
    void channelsAndSubjectsIndependent() {
        registry.revokeUser(7L);

        assertThat(registry.isRevoked("USER", 8L, nowSeconds - 100)).isFalse();
        assertThat(registry.isRevoked("ADMIN", 7L, nowSeconds - 100)).isFalse();
        assertThat(registry.isRevoked("USER", 7L, nowSeconds - 100)).isTrue();
    }

    @Test
    @DisplayName("无 iat 的异常 token（按 0 处理）在撤销后必判 revoked")
    void missingIssuedAt_treatedAsZero() {
        registry.revokeAdmin(9L);
        assertThat(registry.isRevoked("ADMIN", 9L, 0L)).isTrue();
    }

    @Test
    @DisplayName("容量触发的惰性清扫回收过期条目：超过 TTL 后 Map 不保留陈旧撤销")
    void staleEntries_swept() throws Exception {
        ReflectionTestUtils.setField(registry, "jwtExpirationMinutes", 0);
        ReflectionTestUtils.setField(registry, "sweepThrottleSeconds", 0);
        ReflectionTestUtils.setField(registry, "maxKeys", 1);
        registry.revokeUser(100L);
        assertThat(registry.trackedCount()).isEqualTo(1);

        Thread.sleep(1100);

        // 容量已满触发清扫：旧条目（threshold 已超 TTL）被回收，新条目正常写入
        registry.revokeUser(101L);
        assertThat(registry.trackedCount()).isEqualTo(1);
        assertThat(registry.isRevoked("USER", 101L, nowSeconds)).isTrue();
    }
}
