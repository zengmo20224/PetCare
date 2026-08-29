package com.petcare.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 运营概览报表（对标苍穹外卖 WorkSpace/Report 的四件套口径）。
 * 营业额与"有效订单"只统计 COMPLETED 单；完成率 = 完成单数 / 窗口内全部单数
 * （含取消/拒单），单位为百分数值（如 75.0 表示 75%），保留 1 位小数。
 */
public record OverviewReport(
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal totalRevenue,
        BigDecimal bookingRevenue,
        BigDecimal productRevenue,
        long totalValidOrders,
        long validBookingOrders,
        long validProductOrders,
        long bookingTotal,
        BigDecimal bookingCompletionRate,
        long productOrderTotal,
        BigDecimal productCompletionRate
) {
}
