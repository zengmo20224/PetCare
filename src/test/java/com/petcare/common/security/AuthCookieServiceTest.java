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
    @DisplayName("写用户 cookie：HttpOnly+Secure+SameSite=Strict+Path=/api+Max-Age；并配对签发非 HttpOnly XSRF")
    void writeUserCookie_fullAttributes() {
        HttpServletResponse response = mock(HttpServletResponse.class);

        service.writeUserCookie(response, "token-abc");

        var cookies = captureAllSetCookies(response);
        assertThat(cookies.get(0)).contains("USER_TOKEN=token-abc");
        assertThat(cookies.get(0)).contains("HttpOnly");
        assertThat(cookies.get(0)).contains("Secure");
        assertThat(cookies.get(0)).contains("SameSite=Strict");
        assertThat(cookies.get(0)).contains("Path=/api");
        assertThat(cookies.get(0)).contains("Max-Age=7200");
        // M7 CSRF 双提交：登录配对签发 XSRF-TOKEN（JS 可读，非 HttpOnly）
        assertThat(cookies.get(1)).startsWith("XSRF-TOKEN=");
        assertThat(cookies.get(1)).doesNotContain("HttpOnly");
    }

    @Test
    @DisplayName("管理员 cookie 使用独立名字（ADMIN/USER 隔离，过滤器按 tokenType 路由不互踩）")
    void writeAdminCookie_distinctName() {
        HttpServletResponse response = mock(HttpServletResponse.class);

        service.writeAdminCookie(response, "admin-token");

        var cookies = captureAllSetCookies(response);
        assertThat(cookies.get(0)).contains("ADMIN_TOKEN=admin-token");
        assertThat(cookies.get(1)).startsWith("XSRF-TOKEN=");
    }

    @Test
    @DisplayName("清 cookie：Max-Age=0（登出必须服务端清除，含 XSRF 配对清除）")
    void clearCookie_expiresImmediately() {
        HttpServletResponse response = mock(HttpServletResponse.class);

        service.clearUserCookie(response);

        var cookies = captureAllSetCookies(response);
        assertThat(cookies.get(0)).contains("USER_TOKEN=");
        assertThat(cookies.get(0)).contains("Max-Age=0");
        assertThat(cookies.get(1)).startsWith("XSRF-TOKEN=");
        assertThat(cookies.get(1)).contains("Max-Age=0");
    }

    private java.util.List<String> captureAllSetCookies(HttpServletResponse response) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(response, org.mockito.Mockito.times(2))
                .addHeader(eq(HttpHeaders.SET_COOKIE), captor.capture());
        return captor.getAllValues();
    }
}
