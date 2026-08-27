package com.petcare.common.security;

import com.petcare.admin.security.AdminUserDetailsService;
import com.petcare.user.security.UserAuthLoadingService;
import com.petcare.user.security.UserPrincipal;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JwtAuthenticationFilter} 的 HttpOnly cookie 双轨通道
 * （2026-08-15 迁移改造）。
 *
 * 覆盖：
 * 1. 无 header 时用户 cookie 认证成功（SecurityContext 填充 UserPrincipal）
 * 2. /api/v1/admin/** 路径选 ADMIN_TOKEN cookie，用户路径选 USER_TOKEN
 * 3. Authorization Bearer header 优先于 cookie（小程序通道不受影响）
 * 4. cookie 存在但无效 → 401（不降级匿名，与 header 语义一致）
 * 5. 无任何 token → 匿名放行（permitAll 公共读不受影响）
 */
class JwtAuthenticationFilterCookieTest {

    private JwtTokenService jwtTokenService;
    private AdminUserDetailsService adminUserDetailsService;
    private UserAuthLoadingService userAuthLoadingService;
    private RestAuthenticationEntryPoint entryPoint;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jwtTokenService = mock(JwtTokenService.class);
        adminUserDetailsService = mock(AdminUserDetailsService.class);
        userAuthLoadingService = mock(UserAuthLoadingService.class);
        entryPoint = mock(RestAuthenticationEntryPoint.class);
        filter = new JwtAuthenticationFilter(jwtTokenService, adminUserDetailsService,
                userAuthLoadingService, entryPoint, new JwtRevocationRegistry());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("无 header 时 USER_TOKEN cookie 认证成功")
    void userCookie_authenticates() throws Exception {
        when(jwtTokenService.parseTokenForFilter("user-tok"))
                .thenReturn(new JwtTokenService.TokenParseResult("USER", 42L, System.currentTimeMillis() / 1000));
        when(userAuthLoadingService.loadActiveUserById(42L)).thenReturn(new UserPrincipal(42L));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
        request.setCookies(new Cookie(AuthCookieService.USER_COOKIE_NAME, "user-tok"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        org.assertj.core.api.Assertions.assertThat(principal).isInstanceOf(UserPrincipal.class);
    }

    @Test
    @DisplayName("admin 路径读 ADMIN_TOKEN：用户 cookie 在 admin 路径不生效（按路径选名不互踩）")
    void adminPath_selectsAdminCookie() throws Exception {
        when(jwtTokenService.parseTokenForFilter("admin-tok"))
                .thenReturn(new JwtTokenService.TokenParseResult("ADMIN", 7L, System.currentTimeMillis() / 1000));
        UserDetails adminDetails = User.withUsername("admin").password("x").roles("ADMIN").build();
        when(adminUserDetailsService.loadUserByAdminId(7L)).thenReturn(adminDetails);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/auth/me");
        request.setCookies(new Cookie(AuthCookieService.ADMIN_COOKIE_NAME, "admin-tok"),
                new Cookie(AuthCookieService.USER_COOKIE_NAME, "user-tok"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        org.assertj.core.api.Assertions.assertThat(
                        SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isSameAs(adminDetails);
    }

    @Test
    @DisplayName("共享端点回退：非 admin 路径仅有 ADMIN cookie 也能认证（管理端传商品图走 /api/v1/upload）")
    void adminCookie_onUserPath_fallsBack() throws Exception {
        when(jwtTokenService.parseTokenForFilter("admin-tok"))
                .thenReturn(new JwtTokenService.TokenParseResult("ADMIN", 7L, System.currentTimeMillis() / 1000));
        UserDetails adminDetails = User.withUsername("admin").password("x").roles("ADMIN").build();
        when(adminUserDetailsService.loadUserByAdminId(7L)).thenReturn(adminDetails);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/upload");
        request.setCookies(new Cookie(AuthCookieService.ADMIN_COOKIE_NAME, "admin-tok"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        org.assertj.core.api.Assertions.assertThat(
                        SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isSameAs(adminDetails);
    }

    @Test
    @DisplayName("Bearer header 优先于 cookie（小程序/存量客户端通道不受影响）")
    void headerTakesPrecedence_overCookie() throws Exception {
        when(jwtTokenService.parseTokenForFilter("header-tok"))
                .thenReturn(new JwtTokenService.TokenParseResult("USER", 1L, System.currentTimeMillis() / 1000));
        when(userAuthLoadingService.loadActiveUserById(1L)).thenReturn(new UserPrincipal(1L));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
        request.addHeader("Authorization", "Bearer header-tok");
        request.setCookies(new Cookie(AuthCookieService.USER_COOKIE_NAME, "cookie-tok"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(jwtTokenService).parseTokenForFilter("header-tok");
        verify(jwtTokenService, never()).parseTokenForFilter("cookie-tok");
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("cookie 无效 → 401 拒绝，不降级匿名（与 header 语义一致）")
    void invalidCookie_rejected401() throws Exception {
        when(jwtTokenService.parseTokenForFilter("bad-tok")).thenThrow(new JwtException("expired"));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
        request.setCookies(new Cookie(AuthCookieService.USER_COOKIE_NAME, "bad-tok"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        verify(entryPoint).commence(any(), any(), any());
    }

    @Test
    @DisplayName("无 header 无 cookie → 匿名放行")
    void noToken_anonymousPass() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(jwtTokenService, never()).parseTokenForFilter(anyString());
        org.assertj.core.api.Assertions.assertThat(
                SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
