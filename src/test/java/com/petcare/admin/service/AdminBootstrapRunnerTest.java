package com.petcare.admin.service;

import com.petcare.admin.entity.AdminUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AdminBootstrapRunner} 单测（上线缺口方案 A 守卫）。
 */
class AdminBootstrapRunnerTest {

    private AdminUserService adminUserService;
    private Map<String, String> env;
    private AdminBootstrapRunner runner;

    @BeforeEach
    void setUp() {
        adminUserService = mock(AdminUserService.class);
        env = new HashMap<>(Map.of(
                "BOOTSTRAP_ADMIN_USERNAME", "opsadmin",
                "BOOTSTRAP_ADMIN_PASSWORD", "Str0ngPass!2026"));
        // 测试桩：env 来源可替换，不依赖进程真实环境变量
        runner = new AdminBootstrapRunner(adminUserService, new BCryptPasswordEncoder()) {
            @Override
            String requiredEnv(String name) {
                String value = env.get(name);
                if (value == null || value.isBlank()) {
                    throw new IllegalStateException("缺少环境变量 " + name
                            + "（bootstrap 凭据必须经环境变量注入，避免 ps/shell 历史泄露）");
                }
                return value.trim();
            }
        };
    }

    @Test
    @DisplayName("无 --bootstrap-admin 标志：零副作用直接返回")
    void noFlag_noOp() {
        runner.run(new DefaultApplicationArguments());

        verify(adminUserService, never()).save(any());
    }

    @Test
    @DisplayName("合法输入：创建 SUPER_ADMIN/ACTIVE 账号，密码为 BCrypt 哈希")
    void createsSuperAdminWithHashedPassword() {
        when(adminUserService.count(any())).thenReturn(0L);

        runner.run(new DefaultApplicationArguments("--bootstrap-admin"));

        ArgumentCaptor<AdminUser> captor = ArgumentCaptor.forClass(AdminUser.class);
        verify(adminUserService).save(captor.capture());
        AdminUser saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("opsadmin");
        assertThat(saved.getRole()).isEqualTo("SUPER_ADMIN");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getPassword()).startsWith("$2");
        assertThat(saved.getPassword()).isNotEqualTo("Str0ngPass!2026");
    }

    @Test
    @DisplayName("同名账号已存在：拒绝且不写库")
    void duplicateUsername_rejected() {
        when(adminUserService.count(any())).thenReturn(1L);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments("--bootstrap-admin")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已存在");
        verify(adminUserService, never()).save(any());
    }

    @Test
    @DisplayName("弱口令/种子口令/纯字母均拒绝")
    void weakPasswords_rejected() {
        when(adminUserService.count(any())).thenReturn(0L);
        for (String bad : new String[]{"admin123456", "user123456", "short1a", "onlylettersnoDigits"}) {
            env.put("BOOTSTRAP_ADMIN_PASSWORD", bad);
            assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments("--bootstrap-admin")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("PASSWORD");
        }
        verify(adminUserService, never()).save(any());
    }

    @Test
    @DisplayName("用户名非法字符拒绝")
    void invalidUsername_rejected() {
        when(adminUserService.count(any())).thenReturn(0L);
        env.put("BOOTSTRAP_ADMIN_USERNAME", "bad name!");
        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments("--bootstrap-admin")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(adminUserService, never()).save(any());
    }

    @Test
    @DisplayName("缺少凭据环境变量：明确报错")
    void missingEnv_throws() {
        env.remove("BOOTSTRAP_ADMIN_PASSWORD");
        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments("--bootstrap-admin")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_ADMIN_PASSWORD");
    }
}
