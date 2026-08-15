package com.petcare.common.security;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link AuthCookieService}（HttpOnly 双轨改造）。
 *
 * 覆盖安全属性回归：
 * 1. 写 cookie：HttpOnly + Secure + SameSite=Strict + Path=/api + Max-Age=过期分钟
 * 2. ADMIN/USER 双 cookie 名隔离
 * 3. 清 cookie：Max-Age=0 立即过期（HttpOnly 前端删不掉，只能服务端清）
 */
class AuthCookieServiceTest {

    private AuthCookieService service;

    @BeforeEach
    void setUp() {
        service = new AuthCookieService();
        ReflectionTestUtils.setField(service, "jwtExpirationMinutes", 120);
        ReflectionTestUtils.setField(service, "secure", true);
    }

    @Test
    @DisplayName("写用户 cookie：HttpOnly+Secure+SameSite=Strict+Path=/api+Max-Age")
    void writeUserCookie_fullAttributes() {
        HttpServletResponse response = mock(HttpServletResponse.class);

        service.writeUserCookie(response, "token-abc");

        String cookie = captureSetCookie(response);
        assertThat(cookie).contains("USER_TOKEN=token-abc");
        assertThat(cookie).contains("HttpOnly");
        assertThat(cookie).contains("Secure");
        assertThat(cookie).contains("SameSite=Strict");
        assertThat(cookie).contains("Path=/api");
        assertThat(cookie).contains("Max-Age=7200");
    }

    @Test
    @DisplayName("管理员 cookie 使用独立名字（ADMIN/USER 隔离，过滤器按 tokenType 路由不互踩）")
    void writeAdminCookie_distinctName() {
        HttpServletResponse response = mock(HttpServletResponse.class);

        service.writeAdminCookie(response, "admin-token");

        assertThat(captureSetCookie(response)).contains("ADMIN_TOKEN=admin-token");
    }

    @Test
    @DisplayName("清 cookie：Max-Age=0（登出必须服务端清除）")
    void clearCookie_expiresImmediately() {
        HttpServletResponse response = mock(HttpServletResponse.class);

        service.clearUserCookie(response);

        String cookie = captureSetCookie(response);
        assertThat(cookie).contains("USER_TOKEN=");
        assertThat(cookie).contains("Max-Age=0");
    }

    private String captureSetCookie(HttpServletResponse response) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(eq(HttpHeaders.SET_COOKIE), captor.capture());
        return captor.getValue();
    }
}
