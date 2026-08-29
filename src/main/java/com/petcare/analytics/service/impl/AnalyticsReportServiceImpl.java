package com.petcare.analytics.service.impl;

import com.petcare.analytics.dto.DailyRevenueStat;
import com.petcare.analytics.dto.DailyTrendPoint;
import com.petcare.analytics.dto.OverviewCountRow;
import com.petcare.analytics.dto.OverviewReport;
import com.petcare.analytics.dto.TopItemStat;
import com.petcare.analytics.dto.TopItemsReport;
import com.petcare.analytics.mapper.AnalyticsMapper;
import com.petcare.analytics.service.AnalyticsReportService;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 运营统计报表实现。聚合口径见 {@link AnalyticsMapper}，
 * 有效订单统一为 COMPLETED；时间轴商品按下单日、预约按服务日。
 */
@Service
public class AnalyticsReportServiceImpl implements AnalyticsReportService {

    private static final int MAX_RANGE_DAYS = 92;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;

    private final AnalyticsMapper analyticsMapper;

    public AnalyticsReportServiceImpl(AnalyticsMapper analyticsMapper) {
        this.analyticsMapper = analyticsMapper;
    }

    @Override
    public OverviewReport overview(LocalDate startDate, LocalDate endDate) {
        validateRange(startDate, endDate);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime endExclusive = endDate.plusDays(1).atStartOfDay();

        OverviewCountRow booking = analyticsMapper.summarizeBookings(startDate, endDate);
        OverviewCountRow product = analyticsMapper.summarizeProductOrders(start, endExclusive);

        BigDecimal bookingRevenue = booking.getCompletedAmount();
        BigDecimal productRevenue = product.getCompletedAmount();
        return new OverviewReport(
                startDate, endDate,
                bookingRevenue.add(productRevenue),
                bookingRevenue, productRevenue,
                booking.getCompletedCount() + product.getCompletedCount(),
                booking.getCompletedCount(), product.getCompletedCount(),
                booking.getTotalCount(), completionRate(booking),
                product.getTotalCount(), completionRate(product));
    }

    @Override
    public List<DailyTrendPoint> dailyTrend(LocalDate startDate, LocalDate endDate) {
        validateRange(startDate, endDate);
        Map<LocalDate, DailyRevenueStat> bookingByDay = byDate(
                analyticsMapper.sumBookingRevenueByDay(startDate, endDate));
        Map<LocalDate, DailyRevenueStat> productByDay = byDate(
                analyticsMapper.sumProductRevenueByDay(startDate.atStartOfDay(),
                        endDate.plusDays(1).atStartOfDay()));

        List<DailyTrendPoint> points = new ArrayList<>();
        for (LocalDate day = startDate; !day.isAfter(endDate); day = day.plusDays(1)) {
            DailyRevenueStat booking = bookingByDay.getOrDefault(day, emptyStat(day));
            DailyRevenueStat product = productByDay.getOrDefault(day, emptyStat(day));
            points.add(new DailyTrendPoint(day,
                    booking.getOrderCount(), booking.getAmount(),
                    product.getOrderCount(), product.getAmount()));
        }
        return points;
    }

    @Override
    public TopItemsReport topItems(LocalDate startDate, LocalDate endDate, int limit) {
        validateRange(startDate, endDate);
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        List<TopItemStat> services =
                analyticsMapper.topSellingServices(startDate, endDate, safeLimit);
        List<TopItemStat> products =
                analyticsMapper.topSellingProducts(startDate.atStartOfDay(),
                        endDate.plusDays(1).atStartOfDay(), safeLimit);
        return new TopItemsReport(services, products);
    }

    @Override
    public byte[] exportWorkbook(LocalDate startDate, LocalDate endDate) {
        OverviewReport overview = overview(startDate, endDate);
        List<DailyTrendPoint> trend = dailyTrend(startDate, endDate);
        TopItemsReport top = topItems(startDate, endDate, 10);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            writeOverviewSheet(workbook, headerStyle, overview);
            writeDailySheet(workbook, headerStyle, trend);
            writeTopSheet(workbook, headerStyle, "服务销量Top", top.services());
            writeTopSheet(workbook, headerStyle, "商品销量Top", top.products());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "报表导出失败，请重试");
        }
    }

    // ==================== sheets ====================

    private void writeOverviewSheet(XSSFWorkbook workbook, CellStyle headerStyle,
                                    OverviewReport overview) {
        var sheet = workbook.createSheet("概览");
        sheet.setColumnWidth(0, 20 * 256);
        sheet.setColumnWidth(1, 28 * 256);

        Row title = sheet.createRow(0);
        title.createCell(0).setCellValue("运营数据报表");
        title.createCell(1).setCellValue(overview.startDate() + " ~ " + overview.endDate());
        title.getCell(0).setCellStyle(headerStyle);

        String[][] rows = {
                {"总营业额（元）", overview.totalRevenue().toPlainString()},
                {"服务营业额（元）", overview.bookingRevenue().toPlainString()},
                {"商品营业额（元）", overview.productRevenue().toPlainString()},
                {"有效订单数（完成）", String.valueOf(overview.totalValidOrders())},
                {"其中：服务预约完成", String.valueOf(overview.validBookingOrders())},
                {"其中：商品订单完成", String.valueOf(overview.validProductOrders())},
                {"预约完成率", overview.bookingCompletionRate() + "%"},
                {"商品订单完成率", overview.productCompletionRate() + "%"},
        };
        for (int i = 0; i < rows.length; i++) {
            Row row = sheet.createRow(i + 1);
            Cell label = row.createCell(0);
            label.setCellValue(rows[i][0]);
            label.setCellStyle(headerStyle);
            row.createCell(1).setCellValue(rows[i][1]);
        }
    }

    private void writeDailySheet(XSSFWorkbook workbook, CellStyle headerStyle,
                                 List<DailyTrendPoint> trend) {
        var sheet = workbook.createSheet("每日明细");
        String[] headers = {"日期", "预约单数", "预约营业额（元）", "商品单数", "商品营业额（元）", "当日合计（元）"};
        for (int i = 0; i < headers.length; i++) {
            sheet.setColumnWidth(i, 18 * 256);
        }
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        for (int i = 0; i < trend.size(); i++) {
            DailyTrendPoint point = trend.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(point.statDate().format(DAY));
            row.createCell(1).setCellValue(point.bookingCount());
            row.createCell(2).setCellValue(point.bookingAmount().toPlainString());
            row.createCell(3).setCellValue(point.productCount());
            row.createCell(4).setCellValue(point.productAmount().toPlainString());
            row.createCell(5).setCellValue(point.totalAmount().toPlainString());
        }
    }

    private void writeTopSheet(XSSFWorkbook workbook, CellStyle headerStyle,
                               String sheetName, List<TopItemStat> items) {
        var sheet = workbook.createSheet(sheetName);
        String[] headers = {"排名", "ID", "名称", "销量", "销售额（元）"};
        for (int i = 0; i < headers.length; i++) {
            sheet.setColumnWidth(i, 20 * 256);
        }
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        if (items.isEmpty()) {
            sheet.createRow(1).createCell(0).setCellValue("窗口内暂无完成订单");
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            TopItemStat item = items.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(i + 1);
            row.createCell(1).setCellValue(item.getItemId() == null ? "" : String.valueOf(item.getItemId()));
            row.createCell(2).setCellValue(item.getItemName() == null ? "（已删除服务）" : item.getItemName());
            row.createCell(3).setCellValue(item.getQuantity());
            row.createCell(4).setCellValue(item.getAmount().toPlainString());
        }
    }

    // ==================== helpers ====================

    private void validateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "开始与结束日期必填");
        }
        if (startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "开始日期不能晚于结束日期");
        }
        if (startDate.plusDays(MAX_RANGE_DAYS - 1).isBefore(endDate)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "日期跨度不能超过 " + MAX_RANGE_DAYS + " 天");
        }
    }

    private BigDecimal completionRate(OverviewCountRow row) {
        if (row.getTotalCount() == null || row.getTotalCount() == 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(row.getCompletedCount() * 100L)
                .divide(BigDecimal.valueOf(row.getTotalCount()), 1, RoundingMode.HALF_UP);
    }

    private Map<LocalDate, DailyRevenueStat> byDate(List<DailyRevenueStat> rows) {
        Map<LocalDate, DailyRevenueStat> map = new HashMap<>();
        for (DailyRevenueStat row : rows) {
            map.put(row.getStatDate(), row);
        }
        return map;
    }

    private DailyRevenueStat emptyStat(LocalDate day) {
        DailyRevenueStat stat = new DailyRevenueStat();
        stat.setStatDate(day);
        stat.setOrderCount(0L);
        stat.setAmount(BigDecimal.ZERO);
        return stat;
    }
}
