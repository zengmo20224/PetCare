package com.petcare.analytics.service;

import com.petcare.analytics.dto.DailyTrendPoint;
import com.petcare.analytics.dto.OverviewReport;
import com.petcare.analytics.dto.TopItemsReport;

import java.time.LocalDate;
import java.util.List;

/**
 * 管理端运营统计报表（概览 / 每日趋势 / 销量 Top / Excel 导出）。
 */
public interface AnalyticsReportService {

    /** 窗口运营概览：营业额（服务+商品）、有效订单数、完成率。 */
    OverviewReport overview(LocalDate startDate, LocalDate endDate);

    /** 按天趋势（含无数据日期补零），日期升序。 */
    List<DailyTrendPoint> dailyTrend(LocalDate startDate, LocalDate endDate);

    /** 服务与商品销量 Top N。 */
    TopItemsReport topItems(LocalDate startDate, LocalDate endDate, int limit);

    /**
     * 导出运营数据 Excel（xlsx）。单进程内存生成，窗口上限 92 天，
     * 数据量为单门店量级，无需流式写。
     */
    byte[] exportWorkbook(LocalDate startDate, LocalDate endDate);
}
