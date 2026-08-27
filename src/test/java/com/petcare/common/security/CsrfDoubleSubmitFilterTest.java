package com.petcare.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CsrfDoubleSubmitFilter} 单测（2026-08-23 审计 M7 守卫）。
 */
class CsrfDoubleSubmitFilterTest {

    private CsrfDoubleSubmitFilter filter;
    private AuthCookieService authCookieService;
    private String currentXsrf;

    @BeforeEach
    void setUp() {
        authCookieService = new AuthCookieService();
        ReflectionTestUtils.setField(authCookieService, "jwtExpirationMinutes", 120);
        ReflectionTestUtils.setField(authCookieService, "secure", false);
        filter = new CsrfDoubleSubmitFilter(authCookieService);
        currentXsrf = authCookieService.newXsrfToken();
    }

    @Test
    @DisplayName("GET + 认证 cookie 但缺 XSRF cookie：放行并惰性补发 XSRF-TOKEN（存量会话迁移）")
    void getWithoutXsrf_issuesTokenAndPasses() throws Exception {
        HttpServletRequest req = request("GET", "/api/v1/user/wallet",
                cookie(AuthCookieService.USER_COOKIE_NAME, "jwt-value"));
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mockChain();

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(resp).addHeader(org.mockito.ArgumentMatchers.eq("Set-Cookie"), captor.capture());
        assertThat(captor.getValue()).startsWith("XSRF-TOKEN=");
    }

    @Test
    @DisplayName("GET 且 XSRF cookie 已存在：不重复签发")
    void getWithXsrf_noReissue() throws Exception {
        HttpServletRequest req = request("GET", "/api/v1/posts",
                cookie(AuthCookieService.USER_COOKIE_NAME, "jwt"),
                cookie(AuthCookieService.XSRF_COOKIE_NAME, currentXsrf));
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mockChain();

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        verify(resp, never()).addHeader(any(), any());
    }

    @Test
    @DisplayName("Path 修复迁移：旧 /api 版与新 / 版 XSRF cookie 共存 + header 带新值 → 任一匹配放行并作废旧证")
    void postWithLegacyAndNewXsrf_gracePassAndClearsLegacy() throws Exception {
        String legacyXsrf = authCookieService.newXsrfToken();
        String freshXsrf = authCookieService.newXsrfToken();
        // 浏览器 Cookie 头按 Path 长度排序：旧 /api 版在前——服务端 first-match 会读到旧值
        HttpServletRequest req = request("POST", "/api/v1/ai/conversations",
                cookie(AuthCookieService.USER_COOKIE_NAME, "jwt"),
                cookie(AuthCookieService.XSRF_COOKIE_NAME, legacyXsrf),
                cookie(AuthCookieService.XSRF_COOKIE_NAME, freshXsrf));
        when(req.getHeader(AuthCookieService.XSRF_HEADER_NAME)).thenReturn(freshXsrf);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mockChain();

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        verify(resp).addHeader(org.mockito.ArgumentMatchers.eq("Set-Cookie"),
                org.mockito.ArgumentMatchers.contains("Path=/api"));
    }

    @Test
    @DisplayName("共存时 header 与两张都不匹配：仍 403（宽限不等于免检）")
    void postWithLegacyXsrf_headerMatchesNone_rejected() throws Exception {
        HttpServletRequest req = request("POST", "/api/v1/ai/conversations",
                cookie(AuthCookieService.USER_COOKIE_NAME, "jwt"),
                cookie(AuthCookieService.XSRF_COOKIE_NAME, authCookieService.newXsrfToken()),
                cookie(AuthCookieService.XSRF_COOKIE_NAME, authCookieService.newXsrfToken()));
        when(req.getHeader(AuthCookieService.XSRF_HEADER_NAME)).thenReturn("forged-value");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        FilterChain chain = mockChain();

        filter.doFilter(req, resp, chain);

        verify(resp).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(req, resp);
    }

    @Test
    @DisplayName("POST + Authorization Bearer 头：Bearer 通道免疫 CSRF，直接放行")
    void postWithBearer_passes() throws Exception {
        HttpServletRequest req = request("POST", "/api/v1/posts",
                cookie(AuthCookieService.USER_COOKIE_NAME, "jwt"));
        when(req.getHeader("Authorization")).thenReturn("Bearer some-jwt");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mockChain();

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        verify(resp, never()).setStatus(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("匿名写请求（无认证 cookie）：放行交给下游 401")
    void anonymousPost_passes() throws Exception {
        HttpServletRequest req = request("POST", "/api/v1/posts");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mockChain();

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        verify(resp, never()).setStatus(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("Cookie 会话写请求 + 匹配的 X-XSRF-TOKEN 头：通过")
    void postWithMatchingToken_passes() throws Exception {
        HttpServletRequest req = request("POST", "/api/v1/posts",
                cookie(AuthCookieService.USER_COOKIE_NAME, "jwt"),
                cookie(AuthCookieService.XSRF_COOKIE_NAME, currentXsrf));
        when(req.getHeader(AuthCookieService.XSRF_HEADER_NAME)).thenReturn(currentXsrf);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mockChain();

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
    }

    @Test
    @DisplayName("Cookie 会话写请求缺 X-XSRF-TOKEN 头：403 csrf_check_failed")
    void postWithoutHeader_rejected403() throws Exception {
        HttpServletRequest req = request("POST", "/api/v1/posts",
                cookie(AuthCookieService.USER_COOKIE_NAME, "jwt"));
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(sw));
        FilterChain chain = mockChain();

        filter.doFilter(req, resp, chain);

        verify(chain, never()).doFilter(any(), any());
        verify(resp).setStatus(403);
        assertThat(sw.toString()).contains("csrf_check_failed");
    }

    @Test
    @DisplayName("头部与 cookie 不匹配：403")
    void postWithMismatchedToken_rejected403() throws Exception {
        HttpServletRequest req = request("PUT", "/api/v1/user/profile",
                cookie(AuthCookieService.USER_COOKIE_NAME, "jwt"),
                cookie(AuthCookieService.XSRF_COOKIE_NAME, currentXsrf));
        when(req.getHeader(AuthCookieService.XSRF_HEADER_NAME))
                .thenReturn(authCookieService.newXsrfToken());
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        FilterChain chain = mockChain();

        filter.doFilter(req, resp, chain);

        verify(chain, never()).doFilter(any(), any());
        verify(resp).setStatus(403);
    }

    @Test
    @DisplayName("豁免端点：登录/登出/找回密码即使持认证 cookie 也无需 CSRF 头")
    void exemptAuthEndpoints_pass() throws Exception {
        for (String uri : new String[]{
                "/api/v1/auth/login", "/api/v1/admin/auth/login",
                "/api/v1/auth/logout", "/api/v1/auth/forgot-password/reset"}) {
            HttpServletRequest req = request("POST", uri,
                    cookie(AuthCookieService.USER_COOKIE_NAME, "jwt"));
            HttpServletResponse resp = mock(HttpServletResponse.class);
            FilterChain chain = mockChain();

            filter.doFilter(req, resp, chain);

            verify(chain, org.mockito.Mockito.description(uri)).doFilter(req, resp);
        }
    }

    @Test
    @DisplayName("tokensMatch：null/空白/不一致均拒绝，一致才通过")
    void tokensMatch_cases() {
        String token = authCookieService.newXsrfToken();
        assertThat(CsrfDoubleSubmitFilter.tokensMatch(null, token)).isFalse();
        assertThat(CsrfDoubleSubmitFilter.tokensMatch(token, null)).isFalse();
        assertThat(CsrfDoubleSubmitFilter.tokensMatch("", token)).isFalse();
        assertThat(CsrfDoubleSubmitFilter.tokensMatch(token, "  ")).isFalse();
        assertThat(CsrfDoubleSubmitFilter.tokensMatch(token, authCookieService.newXsrfToken())).isFalse();
        assertThat(CsrfDoubleSubmitFilter.tokensMatch(token, token)).isTrue();
    }

    // ==================== helpers ====================

    private HttpServletRequest request(String method, String uri, Cookie... cookies) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn(method);
        when(req.getRequestURI()).thenReturn(uri);
        when(req.getCookies()).thenReturn(cookies.length > 0 ? cookies : null);
        return req;
    }

    private Cookie cookie(String name, String value) {
        return new Cookie(name, value);
    }

    private FilterChain mockChain() {
        return mock(FilterChain.class);
    }
}
