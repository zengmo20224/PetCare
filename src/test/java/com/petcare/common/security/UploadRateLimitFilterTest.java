package com.petcare.common.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcare.user.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UploadRateLimitFilter}（上传接口分钟级限流，2026-08-15 审计加固）。
 *
 * 覆盖：
 * 1. 仅 POST /api/v1/upload 受限，其他方法/路径放行
 * 2. 超阈值 429 + 统一错误体
 * 3. 不同登录主体独立计数
 */
class UploadRateLimitFilterTest {

    private UploadRateLimitFilter filter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new UploadRateLimitFilter();
        ReflectionTestUtils.setField(filter, "maxRequestsPerMinute", 3L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("仅 POST /api/v1/upload 受限：GET 与其他路径放行")
    void onlyUploadPostIsLimited() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request("GET", "/api/v1/upload"), mock(HttpServletResponse.class), chain);
        verify(chain).doFilter(any(), any());

        FilterChain chain2 = mock(FilterChain.class);
        filter.doFilter(request("POST", "/api/v1/products"), mock(HttpServletResponse.class), chain2);
        verify(chain2).doFilter(any(), any());
    }

    @Test
    @DisplayName("超阈值第 4 次返回 429 + rate_limit_exceeded")
    void overThreshold_returns429() throws Exception {
        loginAs(1L);
        for (int i = 0; i < 3; i++) {
            filter.doFilter(request("POST", "/api/v1/upload"), mock(HttpServletResponse.class), mock(FilterChain.class));
        }

        HttpServletRequest req = request("POST", "/api/v1/upload");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(sw));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        verify(chain, never()).doFilter(any(), any());
        verify(resp).setStatus(429);
        JsonNode body = objectMapper.readTree(sw.toString());
        assertThat(body.get("error").get("code").asText()).isEqualTo("rate_limit_exceeded");
    }

    @Test
    @DisplayName("不同登录主体独立计数：A 超限不影响 B")
    void independentPrincipals() throws Exception {
        loginAs(1L);
        for (int i = 0; i < 3; i++) {
            filter.doFilter(request("POST", "/api/v1/upload"), mock(HttpServletResponse.class), mock(FilterChain.class));
        }
        loginAs(2L);
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request("POST", "/api/v1/upload"), mock(HttpServletResponse.class), chain);
        verify(chain).doFilter(any(), any());
    }

    private void loginAs(Long userId) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(new UserPrincipal(userId), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("P2：桶容量上限触发清扫，Map 有界（fail-open 放行不计数）")
    void buckets_boundedUnderFlood() throws Exception {
        ReflectionTestUtils.setField(filter, "maxKeys", 10);
        for (int i = 0; i < 50; i++) {
            HttpServletRequest req = mock(HttpServletRequest.class);
            when(req.getMethod()).thenReturn("POST");
            when(req.getRequestURI()).thenReturn("/api/v1/upload");
            when(req.getRemoteAddr()).thenReturn("10.8." + (i / 256) + "." + (i % 256));
            HttpServletResponse resp = mock(HttpServletResponse.class);
            when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(req, resp, chain);
        }
        assertThat(filter.trackedKeyCount()).isLessThanOrEqualTo(10);
    }
    private HttpServletRequest request(String method, String uri) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn(method);
        when(req.getRequestURI()).thenReturn(uri);
        when(req.getRemoteAddr()).thenReturn("127.0.0.1");
        return req;
    }
}
