package com.petcare.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LoginAttemptService} 单测（2026-08-23 审计 M5：账号级登录失败锁定）。
 * <p>
 * 覆盖：用户/管理双通道阈值（admin 更严）、成功清零、窗口过期解锁、
 * 容量有界（防伪造账号名枚举洪泛撑爆内存）。
 */
class LoginAttemptServiceTest {

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService();
        ReflectionTestUtils.setField(service, "userMaxFailures", 5);
        ReflectionTestUtils.setField(service, "userWindowSeconds", 60);
        ReflectionTestUtils.setField(service, "adminMaxFailures", 3);
        ReflectionTestUtils.setField(service, "adminWindowSeconds", 60);
        ReflectionTestUtils.setField(service, "maxKeys", 100);
    }

    @Test
    @DisplayName("用户通道：连续失败第 5 次锁定，第 4 次未锁")
    void userChannel_locksAtFifthFailure() {
        String phone = "13800000001";
        for (int i = 0; i < 4; i++) {
            service.recordFailure(LoginAttemptService.Channel.USER, phone);
            assertThat(service.isLocked(LoginAttemptService.Channel.USER, phone))
                    .as("第 %d 次失败后不应锁定", i + 1).isFalse();
        }
        service.recordFailure(LoginAttemptService.Channel.USER, phone);
        assertThat(service.isLocked(LoginAttemptService.Channel.USER, phone)).isTrue();
    }

    @Test
    @DisplayName("管理通道更严格：3 次失败即锁定")
    void adminChannel_locksAtThirdFailure() {
        String username = "admin";
        for (int i = 0; i < 2; i++) {
            service.recordFailure(LoginAttemptService.Channel.ADMIN, username);
            assertThat(service.isLocked(LoginAttemptService.Channel.ADMIN, username))
                    .as("第 %d 次失败后不应锁定", i + 1).isFalse();
        }
        service.recordFailure(LoginAttemptService.Channel.ADMIN, username);
        assertThat(service.isLocked(LoginAttemptService.Channel.ADMIN, username)).isTrue();
    }

    @Test
    @DisplayName("不同账号独立计数；USER/ADMIN 同名互不影响")
    void keysAreIndependent() {
        for (int i = 0; i < 4; i++) {
            service.recordFailure(LoginAttemptService.Channel.USER, "13800000002");
        }
        assertThat(service.isLocked(LoginAttemptService.Channel.USER, "13800000003")).isFalse();
        // USER:13800000002 已 4 次失败，但 ADMIN:13800000002 独立计数
        assertThat(service.isLocked(LoginAttemptService.Channel.ADMIN, "13800000002")).isFalse();
    }

    @Test
    @DisplayName("登录成功清零失败计数")
    void success_clearsCounter() {
        String phone = "13800000004";
        for (int i = 0; i < 4; i++) {
            service.recordFailure(LoginAttemptService.Channel.USER, phone);
        }
        service.onSuccess(LoginAttemptService.Channel.USER, phone);

        // 清零后再积累 4 次也不应锁定
        for (int i = 0; i < 4; i++) {
            service.recordFailure(LoginAttemptService.Channel.USER, phone);
        }
        assertThat(service.isLocked(LoginAttemptService.Channel.USER, phone)).isFalse();
    }

    @Test
    @DisplayName("窗口过期后自动解锁并重新计数")
    void windowExpiry_unlocks() throws Exception {
        ReflectionTestUtils.setField(service, "userWindowSeconds", 1);
        String phone = "13800000005";
        for (int i = 0; i < 5; i++) {
            service.recordFailure(LoginAttemptService.Channel.USER, phone);
        }
        assertThat(service.isLocked(LoginAttemptService.Channel.USER, phone)).isTrue();

        Thread.sleep(1100);

        assertThat(service.isLocked(LoginAttemptService.Channel.USER, phone)).isFalse();
    }

    @Test
    @DisplayName("容量上限：海量伪造账号名打满后 Map 有界，不再无限增长")
    void capacity_boundedUnderFlood() {
        ReflectionTestUtils.setField(service, "maxKeys", 10);
        for (int i = 0; i < 100; i++) {
            service.recordFailure(LoginAttemptService.Channel.USER, "ghost-" + i);
        }
        assertThat(service.trackedKeyCount()).isLessThanOrEqualTo(10);
    }
}
