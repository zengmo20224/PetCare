package com.petcare.analytics.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 按天聚合的营收统计行（Mapper 原始行，单来源）。
 * 口径：仅统计 COMPLETED 单（与 SalesAnalyticsAggregator 既有口径一致）。
 */
@Getter
@Setter
public class DailyRevenueStat {

    private LocalDate statDate;

    private Long orderCount;

    private BigDecimal amount;
}
