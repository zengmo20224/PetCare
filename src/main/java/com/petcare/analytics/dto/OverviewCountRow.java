package com.petcare.analytics.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 单来源的窗口聚合行：总单数 / 完成单数 / 完成单金额（Mapper 原始行）。
 */
@Getter
@Setter
public class OverviewCountRow {

    private Long totalCount;

    private Long completedCount;

    private BigDecimal completedAmount;
}
