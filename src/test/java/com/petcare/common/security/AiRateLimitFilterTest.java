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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AiRateLimitFilter}（A5 限流 + 上线额度加固）。
 * No Spring context — fully isolated counter logic（与 {@link RateLimitFilterTest} 同范式）。
 *
 * 覆盖上线前审计缺陷与新增额度规则：
 * 1. post-assistant 计费端点纳入限流（此前完全裸奔，可刷爆 DeepSeek 账单）
 * 2. conversations 前缀仍限流；管理端 AI 端点同样受限
 * 3. GET / 非保护路径放行
 * 4. 每用户每日额度：超限 429，不同主体独立计数
 * 5. 全站每日额度：跨主体累计兜底（防多账号刷量）
 * 6. 发帖（/api/v1/posts）额度联动 Provider 开关：关闭时不计额度不影响正常发帖
 * 7. 发帖与 AI 开关无关地受分钟级频率保护（防灌水）
 */
class AiRateLimitFilterTest {

    private AiRateLimitFilter filter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new AiRateLimitFilter();
        // 默认配置：分钟放宽（隔离额度逻辑），额度关闭，Provider 关闭——各测试自行覆盖
        ReflectionTestUtils.setField(filter, "maxRequestsPerMinute", 1000L);
        ReflectionTestUtils.setField(filter, "dailyLimitPerUser", 0L);
        ReflectionTestUtils.setField(filter, "dailyLimitGlobal", 0L);
        ReflectionTestUtils.setField(filter, "providerEnabled", false);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("post-assistant 计费端点已纳入限流（上线前审计缺陷）")
    void postAssistantBillingEndpoint_rateLimited() throws Exception {
        ReflectionTestUtils.setField(filter, "maxRequestsPerMinute", 2L);

        for (int i = 0; i < 2; i++) {
            filter.doFilter(mockRequest("POST", "/api/v1/ai/post-assistant/generate"), mock(HttpServletResponse.class), mockChain());
        }

        HttpServletRequest req = mockRequest("POST", "/api/v1/ai/post-assistant/generate");
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
    @DisplayName("conversations 发消息端点仍受限流（A5 原保护不回退）")
    void conversationsEndpoint_stillRateLimited() throws Exception {
        ReflectionTestUtils.setField(filter, "maxRequestsPerMinute", 1L);

        filter.doFilter(mockRequest("POST", "/api/v1/ai/conversations/1/messages"), mock(HttpServletResponse.class), mockChain());

        HttpServletRequest req = mockRequest("POST", "/api/v1/ai/conversations/1/messages/stream");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);
        verify(resp).setStatus(429);
    }

    @Test
    @DisplayName("管理端 AI 端点（经营分析）同样受限流")
    void adminAiEndpoint_rateLimited() throws Exception {
        ReflectionTestUtils.setField(filter, "maxRequestsPerMinute", 1L);

        filter.doFilter(mockRequest("POST", "/api/v1/admin/ai/analysis-reports"), mock(HttpServletResponse.class), mockChain());

        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(mockRequest("POST", "/api/v1/admin/ai/analysis-reports"), resp, chain);
        verify(resp).setStatus(429);
    }

    @Test
    @DisplayName("GET 请求与未保护路径直接放行")
    void getRequestAndUnprotectedPath_passThrough() throws Exception {
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(mockRequest("GET", "/api/v1/ai/conversations/1/messages"), resp, chain);
        verify(chain).doFilter(any(), any());
        verify(resp, never()).setStatus(anyInt());

        FilterChain chain2 = mock(FilterChain.class);
        filter.doFilter(mockRequest("POST", "/api/v1/products"), mock(HttpServletResponse.class), chain2);
        verify(chain2).doFilter(any(), any());
    }

    @Test
    @DisplayName("每用户每日额度：超限 429，另一主体不受影响")
    void dailyUserQuota_exceededIndependently() throws Exception {
        ReflectionTestUtils.setField(filter, "dailyLimitPerUser", 2L);
        loginAs(1L);

        // user:1 用满 2 次
        for (int i = 0; i < 2; i++) {
            filter.doFilter(mockRequest("POST", "/api/v1/ai/post-assistant/generate"), mock(HttpServletResponse.class), mockChain());
        }

        // user:1 第 3 次：额度拒绝
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(sw));
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(mockRequest("POST", "/api/v1/ai/conversations/1/messages"), resp, chain);
        verify(chain, never()).doFilter(any(), any());
        verify(resp).setStatus(429);
        assertThat(objectMapper.readTree(sw.toString()).get("error").get("code").asText())
                .isEqualTo("rate_limit_exceeded");

        // user:2 不受影响
        loginAs(2L);
        FilterChain chain2 = mock(FilterChain.class);
        filter.doFilter(mockRequest("POST", "/api/v1/ai/post-assistant/generate"), mock(HttpServletResponse.class), chain2);
        verify(chain2).doFilter(any(), any());
    }

    @Test
    @DisplayName("全站每日额度：跨主体累计，防多账号刷量")
    void globalDailyQuota_accumulatesAcrossPrincipals() throws Exception {
        ReflectionTestUtils.setField(filter, "dailyLimitGlobal", 2L);

        loginAs(1L);
        filter.doFilter(mockRequest("POST", "/api/v1/ai/conversations/1/messages"), mock(HttpServletResponse.class), mockChain());
        filter.doFilter(mockRequest("POST", "/api/v1/ai/post-assistant/generate"), mock(HttpServletResponse.class), mockChain());

        // 换账号也绕不过全站上限
        loginAs(2L);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(mockRequest("POST", "/api/v1/ai/post-assistant/generate"), resp, chain);
        verify(chain, never()).doFilter(any(), any());
        verify(resp).setStatus(429);
    }

    @Test
    @DisplayName("Provider 关闭时发帖不计 AI 额度（正常发帖不受影响）")
    void posts_notCountedWhenProviderDisabled() throws Exception {
        ReflectionTestUtils.setField(filter, "dailyLimitPerUser", 2L);
        ReflectionTestUtils.setField(filter, "providerEnabled", false);
        loginAs(1L);

        for (int i = 0; i < 5; i++) {
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(mockRequest("POST", "/api/v1/posts"), mock(HttpServletResponse.class), chain);
            verify(chain).doFilter(any(), any());
        }
    }

    @Test
    @DisplayName("Provider 启用时发帖计入每日额度（发帖触发文本审核计费）")
    void posts_countedWhenProviderEnabled() throws Exception {
        ReflectionTestUtils.setField(filter, "dailyLimitPerUser", 2L);
        ReflectionTestUtils.setField(filter, "providerEnabled", true);
        loginAs(1L);

        for (int i = 0; i < 2; i++) {
            filter.doFilter(mockRequest("POST", "/api/v1/posts"), mock(HttpServletResponse.class), mockChain());
        }

        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(mockRequest("POST", "/api/v1/posts"), resp, chain);
        verify(chain, never()).doFilter(any(), any());
        verify(resp).setStatus(429);
    }

    @Test
    @DisplayName("发帖与 AI 开关无关地受分钟级频率保护（防灌水）")
    void posts_rateLimitedByMinute() throws Exception {
        ReflectionTestUtils.setField(filter, "maxRequestsPerMinute", 2L);

        for (int i = 0; i < 2; i++) {
            filter.doFilter(mockRequest("POST", "/api/v1/posts"), mock(HttpServletResponse.class), mockChain());
        }

        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(mockRequest("POST", "/api/v1/posts"), resp, chain);
        verify(resp).setStatus(429);
    }

    // ==================== helpers ====================

    private void loginAs(Long userId) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(new UserPrincipal(userId), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("P2：POST /api/v1/ai/conversations（创建会话）纳入分钟限流")
    void createConversationPath_isRateLimited() throws Exception {
        ReflectionTestUtils.setField(filter, "maxRequestsPerMinute", 1L);

        HttpServletResponse okResp = mock(HttpServletResponse.class);
        when(okResp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        FilterChain first = mockChain();
        filter.doFilter(mockRequest("POST", "/api/v1/ai/conversations"), okResp, first);
        verify(first).doFilter(any(), any());

        HttpServletResponse limited = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(limited.getWriter()).thenReturn(new PrintWriter(sw));
        FilterChain chain = mockChain();
        filter.doFilter(mockRequest("POST", "/api/v1/ai/conversations"), limited, chain);
        verify(chain, never()).doFilter(any(), any());
        verify(limited).setStatus(429);
    }

    @Test
    @DisplayName("P2：分钟桶容量上限触发清扫，Map 有界（fail-open 放行不计数）")
    void minuteBuckets_boundedUnderFlood() throws Exception {
        ReflectionTestUtils.setField(filter, "maxKeys", 10);
        for (int i = 0; i < 50; i++) {
            HttpServletRequest req = mock(HttpServletRequest.class);
            when(req.getMethod()).thenReturn("POST");
            when(req.getRequestURI()).thenReturn("/api/v1/ai/conversations/1/messages");
            when(req.getRemoteAddr()).thenReturn("10.9." + (i / 256) + "." + (i % 256));
            HttpServletResponse resp = mock(HttpServletResponse.class);
            when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            filter.doFilter(req, resp, mockChain());
        }
        org.assertj.core.api.Assertions.assertThat(filter.trackedKeyCount()).isLessThanOrEqualTo(10);
    }
    private FilterChain mockChain() {
        return mock(FilterChain.class);
    }

    private HttpServletRequest mockRequest(String method, String uri) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn(method);
        when(req.getRequestURI()).thenReturn(uri);
        when(req.getRemoteAddr()).thenReturn("127.0.0.1");
        return req;
    }
}
