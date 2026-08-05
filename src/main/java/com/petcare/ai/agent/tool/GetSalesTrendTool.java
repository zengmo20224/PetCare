package com.petcare.ai.agent.tool;

import com.petcare.ai.agent.AgentContext;
import com.petcare.ai.analytics.SalesAnalyticsAggregator;
import com.petcare.ai.analytics.dto.SalesAnalytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 经营分析只读 Tool：查询销售趋势（对标 docs/09 §5.2）。
 * <p>
 * 包装 {@link SalesAnalyticsAggregator}（V1 既有聚合器），不新增数据访问路径。
 * 入参 startDate/endDate 可选，缺省取最近 7 天。返回脱敏后的文本摘要喂回 LLM。
 * <p>
 * <b>边界（B1/B4/B6）</b>：只读、不依赖 Mapper、Agent 不直连 DB。
 */
public class GetSalesTrendTool implements AgentTool {

    public static final String NAME = "getSalesTrend";
    private static final String ARG_START_DATE = "startDate";
    private static final String ARG_END_DATE = "endDate";

    private final SalesAnalyticsAggregator salesAggregator;

    public GetSalesTrendTool(SalesAnalyticsAggregator salesAggregator) {
        this.salesAggregator = salesAggregator;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "查询指定时间窗的商品销售趋势（订单量、营收、客单价、热销商品）。"
                + "可选参数：startDate（YYYY-MM-DD）、endDate（YYYY-MM-DD），不传默认最近 7 天。"
                + "当用户想了解销售概况、热销商品或营收下钻时调用。";
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
            SalesAnalytics data = salesAggregator.aggregate(startDate, endDate);
            String summary = String.format(
                    "销售趋势（%s 至 %s）：总订单 %d 单，已完成 %d 单，总营收 %s 元，客单价 %s 元，热销商品「%s」（销量 %d）。",
                    startDate,
                    endDate,
                    data.totalOrders(),
                    data.completedOrders(),
                    formatAmount(data.totalRevenue()),
                    formatAmount(data.avgOrderAmount()),
                    data.topProductName() == null ? "无" : data.topProductName(),
                    data.topProductSalesQty());
            return AgentToolResult.ok(summary);
        } catch (Exception e) {
            return AgentToolResult.fail("销售数据查询失败");
        }
    }

    private static String formatAmount(BigDecimal amount) {
        return amount == null ? "未知" : amount.toPlainString();
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
