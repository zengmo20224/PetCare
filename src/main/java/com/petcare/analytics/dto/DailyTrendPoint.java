package com.petcare.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 每日趋势点（服务+商品合并后）。区间内无数据的日期也会补零，
 * 保证前端图表 x 轴连续。
 */
public record DailyTrendPoint(
        LocalDate statDate,
        long bookingCount,
        BigDecimal bookingAmount,
        long productCount,
        BigDecimal productAmount
) {

    public BigDecimal totalAmount() {
        return bookingAmount.add(productAmount);
    }
}
