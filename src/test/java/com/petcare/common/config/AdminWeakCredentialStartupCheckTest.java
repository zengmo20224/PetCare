package com.petcare.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * H-2 安全修复：prod 弱口令超管启动自检的单元测试。
 */
class AdminWeakCredentialStartupCheckTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    @DisplayName("超管使用种子弱口令 admin123456 → 拒绝启动")
    void weakSeedPassword_refusesStartup() {
        // data-dev.sql 中公开的种子哈希对应 admin123456
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(
                Map.of("username", "admin", "password", encoder.encode("admin123456"))
        ));

        AdminWeakCredentialStartupCheck check = new AdminWeakCredentialStartupCheck(jdbcTemplate, encoder);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> check.run(null));
        assertTrue(ex.getMessage().contains("admin"),
                "异常信息应指出弱口令账号名，便于运维定位");
    }

    @Test
    @DisplayName("全部账号使用强口令 → 正常启动")
    void strongPasswords_startNormally() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(
                Map.of("username", "admin", "password", encoder.encode("Xk9#complex-Secret-2026!")),
                Map.of("username", "ops", "password", encoder.encode("An0ther!Strong-1"))
        ));

        AdminWeakCredentialStartupCheck check = new AdminWeakCredentialStartupCheck(jdbcTemplate, encoder);

        assertDoesNotThrow(() -> check.run(null));
    }

    @Test
    @DisplayName("多个账号中只要有一个弱口令 → 拒绝启动（不因账号顺序漏检）")
    void mixedAccounts_anyWeak_refusesStartup() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(
                Map.of("username", "ops", "password", encoder.encode("Xk9#complex-Secret-2026!")),
                Map.of("username", "staff01", "password", encoder.encode("user123456"))
        ));

        AdminWeakCredentialStartupCheck check = new AdminWeakCredentialStartupCheck(jdbcTemplate, encoder);

        assertThrows(IllegalStateException.class, () -> check.run(null));
    }

    @Test
    @DisplayName("无管理员账号（空表）→ 正常启动")
    void emptyAdminTable_startNormally() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());

        AdminWeakCredentialStartupCheck check = new AdminWeakCredentialStartupCheck(jdbcTemplate, encoder);

        assertDoesNotThrow(() -> check.run(null));
    }
}
