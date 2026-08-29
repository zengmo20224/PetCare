package com.petcare.analytics.controller;

import com.petcare.analytics.dto.DailyTrendPoint;
import com.petcare.analytics.dto.OverviewReport;
import com.petcare.analytics.dto.TopItemsReport;
import com.petcare.analytics.service.AnalyticsReportService;
import com.petcare.common.api.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 管理端运营统计工作台。
 * 权限沿用 {@code analytics:dashboard:read}（与 AI 分析报告看板同一权限点）。
 */
@RestController
@RequestMapping("/api/v1/admin/analytics")
public class AdminAnalyticsController {

    private final AnalyticsReportService analyticsReportService;

    public AdminAnalyticsController(AnalyticsReportService analyticsReportService) {
        this.analyticsReportService = analyticsReportService;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('analytics:dashboard:read')")
    public ResponseEntity<ApiResponse<OverviewReport>> overview(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(ApiResponse.ok(analyticsReportService.overview(startDate, endDate)));
    }

    @GetMapping("/daily-trend")
    @PreAuthorize("hasAuthority('analytics:dashboard:read')")
    public ResponseEntity<ApiResponse<List<DailyTrendPoint>>> dailyTrend(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(ApiResponse.ok(analyticsReportService.dailyTrend(startDate, endDate)));
    }

    @GetMapping("/top-items")
    @PreAuthorize("hasAuthority('analytics:dashboard:read')")
    public ResponseEntity<ApiResponse<TopItemsReport>> topItems(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(
                ApiResponse.ok(analyticsReportService.topItems(startDate, endDate, limit)));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('analytics:dashboard:read')")
    public ResponseEntity<byte[]> export(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        byte[] workbook = analyticsReportService.exportWorkbook(startDate, endDate);
        String filename = "petcare-analytics-" + startDate + "_" + endDate + ".xlsx";
        return ResponseEntity.status(HttpStatus.OK)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(workbook);
    }
}
