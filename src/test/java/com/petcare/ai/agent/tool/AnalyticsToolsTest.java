package com.petcare.ai.agent.tool;

import com.petcare.ai.agent.AgentContext;
import com.petcare.ai.agent.AgentType;
import com.petcare.ai.analytics.ActivityAnalyticsAggregator;
import com.petcare.ai.analytics.BusinessAnalyticsAggregator;
import com.petcare.ai.analytics.CommunityAnalyticsAggregator;
import com.petcare.ai.analytics.SalesAnalyticsAggregator;
import com.petcare.ai.analytics.dto.ActivityAnalytics;
import com.petcare.ai.analytics.dto.BusinessAnalytics;
import com.petcare.ai.analytics.dto.CommunityAnalytics;
import com.petcare.ai.analytics.dto.SalesAnalytics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * M8.2 经营分析 4 个只读 Tool 的单元测试。
 * <p>
 * 验证：入参解析、缺省日期（最近 7 天）、非法日期格式容错、日期顺序校验、
 * Aggregator 异常降级为脱敏失败、返回摘要格式正确。
 * <p>
 * 越权校验（requireUser）由 Registry 负责，不在此测试（见 AnalyticsAgentFlowTest）。
 */
class AnalyticsToolsTest {

    private static final AgentContext CTX = new AgentContext(1L, AgentType.ANALYSIS, null);

    // ==================== GetSalesTrendTool ====================

    @Test
    @DisplayName("GetSalesTrendTool：显式日期参数，返回格式化摘要")
    void salesTrend_explicitDates() {
        SalesAnalyticsAggregator agg = mock(SalesAnalyticsAggregator.class);
        when(agg.aggregate(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)))
                .thenReturn(new SalesAnalytics(120, 100, new BigDecimal("15600.00"),
                        new BigDecimal("130.00"), "猫粮", 40));

        GetSalesTrendTool tool = new GetSalesTrendTool(agg);
        AgentToolResult result = tool.invoke(args(Map.of(
                "startDate", "2026-07-01",
                "endDate", "2026-07-31")), CTX);

        assertTrue(result.success());
        assertTrue(result.summary().contains("120 单"));
        assertTrue(result.summary().contains("15600"));
        assertTrue(result.summary().contains("猫粮"));
    }

    @Test
    @DisplayName("GetSalesTrendTool：不传日期，默认最近 7 天（endDate=今天，startDate=6天前）")
    void salesTrend_defaultDates() {
        SalesAnalyticsAggregator agg = mock(SalesAnalyticsAggregator.class);
        LocalDate today = LocalDate.now();
        LocalDate expectedStart = today.minusDays(6);
        when(agg.aggregate(expectedStart, today))
                .thenReturn(new SalesAnalytics(10, 8, new BigDecimal("1000.00"),
                        new BigDecimal("100.00"), null, 0));

        GetSalesTrendTool tool = new GetSalesTrendTool(agg);
        AgentToolResult result = tool.invoke(args(Map.of()), CTX);

        assertTrue(result.success());
        // 验证 Aggregator 被以默认日期调用（验证 ArgumentCaptor 等价：mock 已按精确日期 stub，
        // 若 Tool 传了别的日期，stub 不会命中会返回 null，上面 success 断言即间接验证）
        assertTrue(result.summary().contains("10 单"));
    }

    @Test
    @DisplayName("GetSalesTrendTool：非法日期格式，降级为默认值（不报错）")
    void salesTrend_invalidDateFormatFallsBackToDefault() {
        SalesAnalyticsAggregator agg = mock(SalesAnalyticsAggregator.class);
        when(agg.aggregate(any(), any()))
                .thenReturn(new SalesAnalytics(5, 5, new BigDecimal("500.00"),
                        new BigDecimal("100.00"), "狗粮", 5));

        GetSalesTrendTool tool = new GetSalesTrendTool(agg);
        AgentToolResult result = tool.invoke(args(Map.of(
                "startDate", "not-a-date",
                "endDate", "2026/07/31")), CTX);

        assertTrue(result.success(), "非法日期应降级为默认值，不返回失败");
    }

    @Test
    @DisplayName("GetSalesTrendTool：开始日期晚于结束日期 → 返回失败")
    void salesTrend_startAfterEndRejected() {
        SalesAnalyticsAggregator agg = mock(SalesAnalyticsAggregator.class);
        GetSalesTrendTool tool = new GetSalesTrendTool(agg);

        AgentToolResult result = tool.invoke(args(Map.of(
                "startDate", "2026-07-31",
                "endDate", "2026-07-01")), CTX);

        assertFalse(result.success());
        assertNotNull(result.errorMessage());
    }

    @Test
    @DisplayName("GetSalesTrendTool：Aggregator 抛异常 → 降级为脱敏失败")
    void salesTrend_aggregatorThrowsDegrades() {
        SalesAnalyticsAggregator agg = mock(SalesAnalyticsAggregator.class);
        when(agg.aggregate(any(), any())).thenThrow(new RuntimeException("DB down"));

        GetSalesTrendTool tool = new GetSalesTrendTool(agg);
        AgentToolResult result = tool.invoke(args(Map.of()), CTX);

        assertFalse(result.success());
        assertEquals("销售数据查询失败", result.errorMessage());
    }

    // ==================== GetBookingFunnelTool ====================

    @Test
    @DisplayName("GetBookingFunnelTool：返回漏斗各环节计数与转化率")
    void bookingFunnel_formatsRates() {
        BusinessAnalyticsAggregator agg = mock(BusinessAnalyticsAggregator.class);
        when(agg.aggregate(any(), any()))
                .thenReturn(new BusinessAnalytics(100, 60, 20, 20, null, null, 0.0));

        GetBookingFunnelTool tool = new GetBookingFunnelTool(agg);
        AgentToolResult result = tool.invoke(args(Map.of()), CTX);

        assertTrue(result.success());
        assertTrue(result.summary().contains("100 次"));
        assertTrue(result.summary().contains("完成率 60%"));
        assertTrue(result.summary().contains("取消率 20%"));
    }

    @Test
    @DisplayName("GetBookingFunnelTool：零预约时不报除零错误")
    void bookingFunnel_zeroBookingsNoDivisionError() {
        BusinessAnalyticsAggregator agg = mock(BusinessAnalyticsAggregator.class);
        when(agg.aggregate(any(), any()))
                .thenReturn(new BusinessAnalytics(0, 0, 0, 0, null, null, 0.0));

        GetBookingFunnelTool tool = new GetBookingFunnelTool(agg);
        AgentToolResult result = tool.invoke(args(Map.of()), CTX);

        assertTrue(result.success());
        assertTrue(result.summary().contains("完成率 0%"));
    }

    // ==================== GetCommunityMetricsTool ====================

    @Test
    @DisplayName("GetCommunityMetricsTool：返回社区各指标")
    void communityMetrics_formatsCorrectly() {
        CommunityAnalyticsAggregator agg = mock(CommunityAnalyticsAggregator.class);
        when(agg.aggregate(any(), any()))
                .thenReturn(new CommunityAnalytics(50, 120, 3, 1, 0, "养猫日常", 15));

        GetCommunityMetricsTool tool = new GetCommunityMetricsTool(agg);
        AgentToolResult result = tool.invoke(args(Map.of()), CTX);

        assertTrue(result.success());
        assertTrue(result.summary().contains("发帖 50 条"));
        assertTrue(result.summary().contains("养猫日常"));
    }

    @Test
    @DisplayName("GetCommunityMetricsTool：无热门话题时不显示 null")
    void communityMetrics_nullTopicHandled() {
        CommunityAnalyticsAggregator agg = mock(CommunityAnalyticsAggregator.class);
        when(agg.aggregate(any(), any()))
                .thenReturn(new CommunityAnalytics(0, 0, 0, 0, 0, null, 0));

        GetCommunityMetricsTool tool = new GetCommunityMetricsTool(agg);
        AgentToolResult result = tool.invoke(args(Map.of()), CTX);

        assertTrue(result.success());
        assertTrue(result.summary().contains("无"));
    }

    // ==================== GetActivityEffectTool ====================

    @Test
    @DisplayName("GetActivityEffectTool：数据充分时正常返回")
    void activityEffect_dataSufficient() {
        ActivityAnalyticsAggregator agg = mock(ActivityAnalyticsAggregator.class);
        when(agg.aggregate(any(), any()))
                .thenReturn(new ActivityAnalytics(5, 3, 10L, 4L, true));

        GetActivityEffectTool tool = new GetActivityEffectTool(agg);
        AgentToolResult result = tool.invoke(args(Map.of()), CTX);

        assertTrue(result.success());
        assertTrue(result.summary().contains("数据充分"));
    }

    @Test
    @DisplayName("GetActivityEffectTool：数据不足时明确提示，防 LLM 编造 ROI")
    void activityEffect_dataInsufficientWarnsLLM() {
        ActivityAnalyticsAggregator agg = mock(ActivityAnalyticsAggregator.class);
        when(agg.aggregate(any(), any()))
                .thenReturn(new ActivityAnalytics(0, 0, null, null, false));

        GetActivityEffectTool tool = new GetActivityEffectTool(agg);
        AgentToolResult result = tool.invoke(args(Map.of()), CTX);

        assertTrue(result.success());
        assertTrue(result.summary().contains("数据不足"));
        assertTrue(result.summary().contains("不要编造 ROI"));
    }

    // ==================== 共用工具 ====================

    /** 构造一个简单的 AgentToolArgs 实现（从 Map 取值，raw 返回不可变副本）。 */
    private static AgentToolArgs args(Map<String, String> map) {
        Map<String, String> readonly = Map.copyOf(map);
        return new AgentToolArgs() {
            @Override
            public String get(String key) {
                return readonly.get(key);
            }

            @Override
            public Map<String, String> raw() {
                return readonly;
            }
        };
    }
}
