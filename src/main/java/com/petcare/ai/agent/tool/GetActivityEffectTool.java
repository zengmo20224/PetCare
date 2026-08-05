package com.petcare.ai.agent.tool;

import com.petcare.ai.agent.AgentContext;
import com.petcare.ai.analytics.ActivityAnalyticsAggregator;
import com.petcare.ai.analytics.dto.ActivityAnalytics;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 经营分析只读 Tool：查询营销活动效果（对标 docs/09 §5.2）。
 * <p>
 * 包装 {@link ActivityAnalyticsAggregator}（V1 既有聚合器），不新增数据访问路径。
 * 活动数据天然弱（单门店 demo），明确标注 dataSufficient，避免 LLM 编造 ROI。
 * <p>
 * <b>边界（B1/B4/B6）</b>：只读、不依赖 Mapper、Agent 不直连 DB。
 */
public class GetActivityEffectTool implements AgentTool {

    public static final String NAME = "getActivityEffect";
    private static final String ARG_START_DATE = "startDate";
    private static final String ARG_END_DATE = "endDate";

    private final ActivityAnalyticsAggregator activityAggregator;

    public GetActivityEffectTool(ActivityAnalyticsAggregator activityAggregator) {
        this.activityAggregator = activityAggregator;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "查询指定时间窗的营销活动概况（活动数、进行中活动数、关联商品/服务数）。"
                + "可选参数：startDate（YYYY-MM-DD）、endDate（YYYY-MM-DD），不传默认最近 7 天。"
                + "当用户想了解活动数量、商品/服务关联覆盖度时调用。";
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
            ActivityAnalytics data = activityAggregator.aggregate(startDate, endDate);
            String dataNote = data.dataSufficient()
                    ? "数据充分，可作分析参考"
                    : "数据不足（demo 场景活动量较小），不要编造 ROI 或转化率";
            String summary = String.format(
                    "营销活动概况（%s 至 %s）：活动总数 %d 个，进行中 %d 个，"
                            + "关联商品 %s 个，关联服务 %s 个。%s。",
                    startDate,
                    endDate,
                    data.totalActivities(),
                    data.activeActivities(),
                    data.associatedProductCount() == null ? "0" : data.associatedProductCount().toString(),
                    data.associatedServiceCount() == null ? "0" : data.associatedServiceCount().toString(),
                    dataNote);
            return AgentToolResult.ok(summary);
        } catch (Exception e) {
            return AgentToolResult.fail("活动数据查询失败");
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
