package com.petcare.ai.agent.tool;

import com.petcare.ai.agent.AgentContext;
import com.petcare.ai.analytics.BusinessAnalyticsAggregator;
import com.petcare.ai.analytics.dto.BusinessAnalytics;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 经营分析只读 Tool：查询预约漏斗（对标 docs/09 §5.2）。
 * <p>
 * 包装 {@link BusinessAnalyticsAggregator}（V1 既有聚合器），不新增数据访问路径。
 * 返回预约各状态计数（创建/完成/取消/待确认）作为漏斗各环节快照。
 * <p>
 * <b>边界（B1/B4/B6）</b>：只读、不依赖 Mapper、Agent 不直连 DB。
 */
public class GetBookingFunnelTool implements AgentTool {

    public static final String NAME = "getBookingFunnel";
    private static final String ARG_START_DATE = "startDate";
    private static final String ARG_END_DATE = "endDate";

    private final BusinessAnalyticsAggregator businessAggregator;

    public GetBookingFunnelTool(BusinessAnalyticsAggregator businessAggregator) {
        this.businessAggregator = businessAggregator;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "查询指定时间窗的预约漏斗（总预约数、已完成、已取消、待确认）。"
                + "可选参数：startDate（YYYY-MM-DD）、endDate（YYYY-MM-DD），不传默认最近 7 天。"
                + "当用户想了解预约转化、取消率或履约情况时调用。";
    }

    @Override
    public boolean readOnly() {
        return true;
    }

    @Override
    public AgentToolResult invoke(AgentToolArgs input, AgentContext ctx) {
        LocalDate endDate = parseDate(input.get(ARG_END_DATE));
        LocalDate startDate = parseDate(input.get(ARG_START_DATE));
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        if (startDate == null) {
            startDate = endDate.minusDays(6);
        }
        if (startDate.isAfter(endDate)) {
            return AgentToolResult.fail("开始日期不能晚于结束日期");
        }
        try {
            BusinessAnalytics data = businessAggregator.aggregate(startDate, endDate);
            long completionRate = data.totalBookings() == 0
                    ? 0
                    : Math.round(data.completedBookings() * 100.0 / data.totalBookings());
            long cancelRate = data.totalBookings() == 0
                    ? 0
                    : Math.round(data.cancelledBookings() * 100.0 / data.totalBookings());
            String summary = String.format(
                    "预约漏斗（%s 至 %s）：总预约 %d 次，已完成 %d 次（完成率 %d%%），"
                            + "已取消 %d 次（取消率 %d%%），待确认 %d 次。",
                    startDate,
                    endDate,
                    data.totalBookings(),
                    data.completedBookings(),
                    completionRate,
                    data.cancelledBookings(),
                    cancelRate,
                    data.pendingBookings());
            return AgentToolResult.ok(summary);
        } catch (Exception e) {
            return AgentToolResult.fail("预约数据查询失败");
        }
    }

    /** LLM 输出不可信，统一容错解析 YYYY-MM-DD。 */
    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
