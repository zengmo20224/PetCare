package com.petcare.ai.agent.tool;

import com.petcare.ai.agent.AgentContext;
import com.petcare.ai.analytics.CommunityAnalyticsAggregator;
import com.petcare.ai.analytics.dto.CommunityAnalytics;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 经营分析只读 Tool：查询社区运营指标（对标 docs/09 §5.2）。
 * <p>
 * 包装 {@link CommunityAnalyticsAggregator}（V1 既有聚合器），不新增数据访问路径。
 * <p>
 * <b>边界（B1/B4/B6）</b>：只读、不依赖 Mapper、Agent 不直连 DB。
 */
public class GetCommunityMetricsTool implements AgentTool {

    public static final String NAME = "getCommunityMetrics";
    private static final String ARG_START_DATE = "startDate";
    private static final String ARG_END_DATE = "endDate";

    private final CommunityAnalyticsAggregator communityAggregator;

    public GetCommunityMetricsTool(CommunityAnalyticsAggregator communityAggregator) {
        this.communityAggregator = communityAggregator;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "查询指定时间窗的社区运营指标（发帖数、评论数、举报数、待审核数、热门话题）。"
                + "可选参数：startDate（YYYY-MM-DD）、endDate（YYYY-MM-DD），不传默认最近 7 天。"
                + "当用户想了解社区活跃度、举报量或热门内容时调用。";
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
            CommunityAnalytics data = communityAggregator.aggregate(startDate, endDate);
            String summary = String.format(
                    "社区指标（%s 至 %s）：发帖 %d 条，评论 %d 条，举报 %d 条，"
                            + "待审核 %d 条，已拒绝 %d 条，热门话题「%s」（相关帖 %d 条）。",
                    startDate,
                    endDate,
                    data.totalPosts(),
                    data.totalComments(),
                    data.totalReports(),
                    data.pendingReviewCount(),
                    data.rejectedCount(),
                    data.topTopicName() == null ? "无" : data.topTopicName(),
                    data.topTopicPostCount());
            return AgentToolResult.ok(summary);
        } catch (Exception e) {
            return AgentToolResult.fail("社区数据查询失败");
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
