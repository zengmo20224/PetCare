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
    private static final long ADMIN_MAX_REQUESTS = 2;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
        // 注入配置（绕过 Spring @Value）
        ReflectionTestUtils.setField(filter, "windowSeconds", WINDOW_SECONDS);
        ReflectionTestUtils.setField(filter, "maxRequests", MAX_REQUESTS);
        ReflectionTestUtils.setField(filter, "adminMaxRequests", ADMIN_MAX_REQUESTS);
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
    @DisplayName("管理员登录端点使用更严阈值（2026-08-23 审计 M5）：第 3 次即被拒")
    void adminLoginEndpoint_usesStricterThreshold() throws Exception {
        // 前两次放行
        for (int i = 0; i < ADMIN_MAX_REQUESTS; i++) {
            HttpServletRequest req = mockRequest("POST", "/api/v1/admin/auth/login", "5.5.5.5");
            HttpServletResponse resp = mock(HttpServletResponse.class);
            when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(req, resp, chain);
            verify(chain).doFilter(req, resp);
        }

        // 第 3 次：应被拒（严于普通端点的 MAX_REQUESTS=3）
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
    @DisplayName("更严的管理员阈值不影响同 IP 的用户登录阈值")
    void stricterAdminThreshold_doesNotAffectUserEndpoint() throws Exception {
        // 同 IP 已打满 admin 阈值
        for (int i = 0; i < ADMIN_MAX_REQUESTS; i++) {
            HttpServletRequest req = mockRequest("POST", "/api/v1/admin/auth/login", "6.6.6.6");
            HttpServletResponse resp = mock(HttpServletResponse.class);
            when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(req, resp, chain);
        }

        // 同 IP 用户登录：MAX_REQUESTS 次内全部放行
        for (int i = 0; i < MAX_REQUESTS; i++) {
            HttpServletRequest req = mockRequest("POST", "/api/v1/auth/login", "6.6.6.6");
            HttpServletResponse resp = mock(HttpServletResponse.class);
            when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(req, resp, chain);
            verify(chain).doFilter(req, resp);
        }
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

    // ==================== 桶容量防护（2026-08-23 审计 M1：防海量伪造 IP 撑爆堆内存） ====================

    @Test
    @DisplayName("桶容量上限：海量伪造 IP 打满后触发清扫，桶数量有界不再无限增长")
    void bucketCapacity_boundedUnderUniqueIpFlood() throws Exception {
        ReflectionTestUtils.setField(filter, "maxBuckets", 5);
        for (int i = 0; i < 50; i++) {
            HttpServletRequest req = mockRequest("POST", "/api/v1/auth/login",
                    "10.1." + (i / 256) + "." + (i % 256));
            HttpServletResponse resp = mock(HttpServletResponse.class);
            when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(req, resp, chain);
        }
        assertThat(filter.bucketCount()).isLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("过期桶被清扫回收：闲置超过窗口的条目被移除，容量得以复用")
    void idleBuckets_areEvictedBySweep() throws Exception {
        ReflectionTestUtils.setField(filter, "windowSeconds", 1);
        ReflectionTestUtils.setField(filter, "maxBuckets", 3);
        for (int i = 0; i < 3; i++) {
            HttpServletRequest req = mockRequest("POST", "/api/v1/auth/login", "10.2.0." + i);
            HttpServletResponse resp = mock(HttpServletResponse.class);
            when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(req, resp, chain);
        }
        assertThat(filter.bucketCount()).isEqualTo(3);

        Thread.sleep(1100);

        // 新 IP 到来触发清扫：旧闲置桶被移除，新桶可建立且请求放行
        HttpServletRequest req = mockRequest("POST", "/api/v1/auth/login", "10.2.9.9");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        assertThat(filter.bucketCount()).isLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("容量打满且无可清扫条目时 fail-open 放行，不误伤正常用户")
    void saturated_failsOpen_notLockedOut() throws Exception {
        ReflectionTestUtils.setField(filter, "maxBuckets", 2);
        for (int i = 0; i < 2; i++) {
            HttpServletRequest req = mockRequest("POST", "/api/v1/auth/login", "10.3.0." + i);
            HttpServletResponse resp = mock(HttpServletResponse.class);
            when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(req, resp, chain);
        }
        assertThat(filter.bucketCount()).isEqualTo(2);

        // 第 3 个新 IP：应放行（fail-open），既不是 429 也不抛异常
        HttpServletRequest req = mockRequest("POST", "/api/v1/auth/login", "10.3.9.9");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        verify(resp, never()).setStatus(org.mockito.ArgumentMatchers.anyInt());
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
