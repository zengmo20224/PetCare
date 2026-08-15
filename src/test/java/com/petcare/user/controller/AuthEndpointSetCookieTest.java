package com.petcare.user.controller;

import com.petcare.admin.controller.AdminAuthController;
import com.petcare.admin.dto.AdminLoginRequest;
import com.petcare.admin.dto.AdminLoginResponse;
import com.petcare.admin.service.AdminAuthService;
import com.petcare.common.security.AuthCookieService;
import com.petcare.user.auth.UserAuthService;
import com.petcare.user.auth.WechatLoginApplicationService;
import com.petcare.user.dto.PasswordLoginRequest;
import com.petcare.user.dto.PasswordLoginResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登录/登出端点 Set-Cookie 断言（HttpOnly 双轨改造，standalone MockMvc——不挂
 * 安全链，只验证控制器层的 cookie 写出；过滤器双轨行为见
 * {@code JwtAuthenticationFilterCookieTest}）。
 *
 * 回归点：任何一处漏掉 writeXxxCookie 调用都会让对应前端的 cookie 认证静默失效。
 */
class AuthEndpointSetCookieTest {

    private AuthCookieService cookieService;
    private UserAuthService userAuthService;
    private AdminAuthService adminAuthService;

    @BeforeEach
    void setUp() {
        cookieService = new AuthCookieService();
        ReflectionTestUtils.setField(cookieService, "jwtExpirationMinutes", 120);
        ReflectionTestUtils.setField(cookieService, "secure", true);
        userAuthService = mock(UserAuthService.class);
        adminAuthService = mock(AdminAuthService.class);
    }

    @Test
    @DisplayName("用户登录：Set-Cookie USER_TOKEN（HttpOnly+SameSite=Strict+Path=/api）")
    void userLogin_setsUserCookie() throws Exception {
        when(userAuthService.login(any(PasswordLoginRequest.class)))
                .thenReturn(new PasswordLoginResponse("Bearer", "user-tok", 7200,
                        new PasswordLoginResponse.UserInfo("1", "nick")));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new UserAuthController(mock(WechatLoginApplicationService.class), userAuthService, cookieService))
                .build();

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800138001\",\"password\":\"user123456\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString("USER_TOKEN=user-tok"),
                        containsString("HttpOnly"),
                        containsString("SameSite=Strict"),
                        containsString("Path=/api"))));
    }

    @Test
    @DisplayName("管理员登录：Set-Cookie ADMIN_TOKEN（与用户 cookie 名隔离）")
    void adminLogin_setsAdminCookie() throws Exception {
        when(adminAuthService.login(any(AdminLoginRequest.class)))
                .thenReturn(new AdminLoginResponse("Bearer", "admin-tok", 7200,
                        new AdminLoginResponse.AdminSummary(1L, "admin", "管理员", "SUPER_ADMIN")));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AdminAuthController(adminAuthService, cookieService))
                .build();

        mvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123456\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString("ADMIN_TOKEN=admin-tok"),
                        containsString("HttpOnly"),
                        containsString("SameSite=Strict"))));
    }

    @Test
    @DisplayName("登出：用户与管理员各自清 cookie（Max-Age=0）")
    void logout_clearsCookies() throws Exception {
        MockMvc userMvc = MockMvcBuilders.standaloneSetup(
                new UserAuthController(mock(WechatLoginApplicationService.class), userAuthService, cookieService))
                .build();
        userMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        org.hamcrest.Matchers.containsString("USER_TOKEN="),
                        org.hamcrest.Matchers.containsString("Max-Age=0"))));

        MockMvc adminMvc = MockMvcBuilders.standaloneSetup(new AdminAuthController(adminAuthService, cookieService))
                .build();
        adminMvc.perform(post("/api/v1/admin/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        org.hamcrest.Matchers.containsString("ADMIN_TOKEN="),
                        org.hamcrest.Matchers.containsString("Max-Age=0"))));
    }
}
