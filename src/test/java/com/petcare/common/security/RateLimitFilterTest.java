package com.petcare.common.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RateLimitFilter} using Mockito mocks.
 * No Spring context — fully isolated sliding-window logic.
 *
 * 覆盖 H2 限流安全修复的关键路径：
 * 1. 非受限端点直接放行
 * 2. 阈值内请求放行
 * 3. 超阈值请求返回 429 + 统一错误体
 * 4. 不同 IP 独立计数
 * 5. 仅 POST 触发（GET 不限流）
 */
class RateLimitFilterTest {

    private RateLimitFilter filter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int WINDOW_SECONDS = 60;
    private static final long MAX_REQUESTS = 3;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
        // 注入配置（绕过 Spring @Value）
        ReflectionTestUtils.setField(filter, "windowSeconds", WINDOW_SECONDS);
        ReflectionTestUtils.setField(filter, "maxRequests", MAX_REQUESTS);
    }

    @Test
    @DisplayName("非受限端点直接放行，不触发限流")
    void nonProtectedEndpoint_passesThrough() throws Exception {
        HttpServletRequest req = mockRequest("GET", "/api/v1/products", null);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        verify(resp, never()).setStatus(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("GET 请求即使是受限路径也不限流（仅限 POST）")
    void getRequestOnProtectedPath_notRateLimited() throws Exception {
        HttpServletRequest req = mockRequest("GET", "/api/v1/auth/login", null);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        verify(resp, never()).setStatus(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("阈值内（3 次）的 POST /auth/login 全部放行")
    void withinThreshold_passes() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            HttpServletRequest req = mockRequest("POST", "/api/v1/auth/login", "1.2.3.4");
            HttpServletResponse resp = mock(HttpServletResponse.class);
            StringWriter sw = new StringWriter();
            when(resp.getWriter()).thenReturn(new PrintWriter(sw));
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(req, resp, chain);

            verify(chain).doFilter(req, resp);
        }
    }

    @Test
    @DisplayName("超阈值（第 4 次）的 POST /auth/login 返回 429 + rate_limit_exceeded")
    void overThreshold_returns429() throws Exception {
        // 先消耗 3 次配额
        for (int i = 0; i < MAX_REQUESTS; i++) {
            HttpServletRequest req = mockRequest("POST", "/api/v1/auth/login", "1.2.3.4");
            HttpServletResponse resp = mock(HttpServletResponse.class);
            when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(req, resp, chain);
        }

        // 第 4 次：应被拒
        HttpServletRequest req = mockRequest("POST", "/api/v1/auth/login", "1.2.3.4");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(sw));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        verify(chain, never()).doFilter(any(), any());
        verify(resp).setStatus(429);
        JsonNode body = objectMapper.readTree(sw.toString());
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("error").get("code").asText()).isEqualTo("rate_limit_exceeded");
    }

    @Test
    @DisplayName("不同 IP 独立计数：A 超限不影响 B")
    void differentIp_independentBuckets() throws Exception {
        // IP-A 耗尽配额
        for (int i = 0; i < MAX_REQUESTS; i++) {
            HttpServletRequest req = mockRequest("POST", "/api/v1/auth/login", "1.1.1.1");
            HttpServletResponse resp = mock(HttpServletResponse.class);
            when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(req, resp, chain);
        }

        // IP-B 首次请求：应放行
        HttpServletRequest reqB = mockRequest("POST", "/api/v1/auth/login", "2.2.2.2");
        HttpServletResponse respB = mock(HttpServletResponse.class);
        when(respB.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        FilterChain chainB = mock(FilterChain.class);

        filter.doFilter(reqB, respB, chainB);

        verify(chainB).doFilter(reqB, respB);
        verify(respB, never()).setStatus(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("不同端点独立计数：login 超限不影响 register")
    void differentEndpoint_independentBuckets() throws Exception {
        // login 耗尽
        for (int i = 0; i < MAX_REQUESTS; i++) {
            HttpServletRequest req = mockRequest("POST", "/api/v1/auth/login", "9.9.9.9");
            HttpServletResponse resp = mock(HttpServletResponse.class);
            when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(req, resp, chain);
        }

        // register 首次请求：应放行
        HttpServletRequest req = mockRequest("POST", "/api/v1/auth/register", "9.9.9.9");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        verify(resp, never()).setStatus(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("管理员登录端点 /admin/auth/login 同样受限")
    void adminLoginEndpoint_isRateLimited() throws Exception {
        // 耗尽配额
        for (int i = 0; i < MAX_REQUESTS; i++) {
            HttpServletRequest req = mockRequest("POST", "/api/v1/admin/auth/login", "5.5.5.5");
            HttpServletResponse resp = mock(HttpServletResponse.class);
            when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(req, resp, chain);
        }

        // 第 4 次：应被拒
        HttpServletRequest req = mockRequest("POST", "/api/v1/admin/auth/login", "5.5.5.5");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(sw));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        verify(chain, never()).doFilter(any(), any());
        verify(resp).setStatus(429);
    }

    @Test
    @DisplayName("X-Forwarded-For 头被解析为客户端真实 IP（反代场景）")
    void xForwardedFor_isUsedAsClientIp() throws Exception {
        // IP-X 耗尽
        for (int i = 0; i < MAX_REQUESTS; i++) {
            HttpServletRequest req = mockRequest("POST", "/api/v1/auth/login", "10.0.0.1", "203.0.113.5, 10.0.0.1");
            HttpServletResponse resp = mock(HttpServletResponse.class);
            when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(req, resp, chain);
        }

        // 第 4 次同 XFF：应被拒（同一个真实 IP）
        HttpServletRequest req = mockRequest("POST", "/api/v1/auth/login", "10.0.0.1", "203.0.113.5, 10.0.0.1");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(sw));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);
        verify(resp).setStatus(429);
    }

    @Test
    @DisplayName("forgot-password 子路径归一化限流，不被路径绕过")
    void forgotPasswordSubPaths_areRateLimitedTogether() throws Exception {
        // 用 /forgot-password/questions 耗尽配额
        for (int i = 0; i < MAX_REQUESTS; i++) {
            HttpServletRequest req = mockRequest("POST", "/api/v1/auth/forgot-password/questions", "7.7.7.7");
            HttpServletResponse resp = mock(HttpServletResponse.class);
            when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(req, resp, chain);
        }

        // 同 IP 调 /forgot-password/reset：应被拒（归一化为同一桶）
        HttpServletRequest req = mockRequest("POST", "/api/v1/auth/forgot-password/reset", "7.7.7.7");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(sw));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);
        verify(resp).setStatus(429);
    }

    // ==================== helpers ====================

    private HttpServletRequest mockRequest(String method, String uri, String remoteAddr) {
        return mockRequest(method, uri, remoteAddr, null);
    }

    private HttpServletRequest mockRequest(String method, String uri, String remoteAddr, String xff) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn(method);
        when(req.getRequestURI()).thenReturn(uri);
        when(req.getRemoteAddr()).thenReturn(remoteAddr != null ? remoteAddr : "127.0.0.1");
        if (xff != null) {
            when(req.getHeader("X-Forwarded-For")).thenReturn(xff);
        }
        return req;
    }
}
