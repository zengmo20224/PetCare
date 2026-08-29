package com.petcare.analytics.service;

import com.petcare.analytics.dto.DailyTrendPoint;
import com.petcare.analytics.dto.OverviewReport;
import com.petcare.analytics.dto.TopItemsReport;
import com.petcare.booking.entity.ServiceBooking;
import com.petcare.booking.mapper.ServiceBookingMapper;
import com.petcare.product.entity.ProductOrder;
import com.petcare.product.entity.ProductOrderItem;
import com.petcare.product.mapper.ProductOrderItemMapper;
import com.petcare.product.mapper.ProductOrderMapper;
import com.petcare.service.entity.ServiceItem;
import com.petcare.service.service.ServiceItemService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 运营统计报表（H2 集成）：概览口径、每日趋势合并与补零、Top N、Excel 内容。
 * 统一时钟窗口：day1=2026-08-27、day2=2026-08-28、day3=2026-08-29（无数据，应补零）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AnalyticsReportServiceTest {

    private static final LocalDate DAY1 = LocalDate.of(2026, 8, 27);
    private static final LocalDate DAY2 = LocalDate.of(2026, 8, 28);
    private static final LocalDate DAY3 = LocalDate.of(2026, 8, 29);

    @Autowired
    private AnalyticsReportService analyticsReportService;
    @Autowired
    private ServiceBookingMapper serviceBookingMapper;
    @Autowired
    private ProductOrderMapper productOrderMapper;
    @Autowired
    private ProductOrderItemMapper productOrderItemMapper;
    @Autowired
    private ServiceItemService serviceItemService;

    @BeforeEach
    void setUp() {
        insertServiceItem(101L, "宠物洗护");
        insertServiceItem(102L, "宠物寄养");

        insertBooking("COMPLETED", new BigDecimal("100.00"), DAY1, 101L);
        insertBooking("COMPLETED", new BigDecimal("50.00"), DAY1, 101L);
        insertBooking("COMPLETED", new BigDecimal("200.00"), DAY2, 102L);
        insertBooking("CANCELLED", new BigDecimal("999.00"), DAY1, 101L);

        insertOrder("COMPLETED", new BigDecimal("80.00"), DAY1.atTime(10, 0));
        insertOrder("COMPLETED", new BigDecimal("40.00"), DAY2.atTime(10, 0));
        insertOrder("PENDING_CONFIRM", new BigDecimal("500.00"), DAY2.atTime(11, 0));
    }

    @Test
    @DisplayName("概览：营业额只计完成单，完成率=完成/窗口全部，跨服务与商品两源合并")
    void overviewAggregatesBothSources() {
        OverviewReport overview = analyticsReportService.overview(DAY1, DAY3);

        assertThat(overview.totalRevenue()).isEqualByComparingTo("470");
        assertThat(overview.bookingRevenue()).isEqualByComparingTo("350");
        assertThat(overview.productRevenue()).isEqualByComparingTo("120");
        assertThat(overview.totalValidOrders()).isEqualTo(5);
        assertThat(overview.validBookingOrders()).isEqualTo(3);
        assertThat(overview.validProductOrders()).isEqualTo(2);
        assertThat(overview.bookingTotal()).isEqualTo(4);
        assertThat(overview.bookingCompletionRate()).isEqualByComparingTo("75.0");
        assertThat(overview.productOrderTotal()).isEqualTo(3);
        assertThat(overview.productCompletionRate()).isEqualByComparingTo("66.7");
    }

    @Test
    @DisplayName("每日趋势：同日两源各自计数，无数据日期补零，日期升序")
    void dailyTrendMergesAndZeroFills() {
        List<DailyTrendPoint> trend = analyticsReportService.dailyTrend(DAY1, DAY3);

        assertThat(trend).hasSize(3);
        assertThat(trend.get(0).statDate()).isEqualTo(DAY1);
        assertThat(trend.get(0).bookingCount()).isEqualTo(2);
        assertThat(trend.get(0).bookingAmount()).isEqualByComparingTo("150");
        assertThat(trend.get(0).productCount()).isEqualTo(1);
        assertThat(trend.get(0).productAmount()).isEqualByComparingTo("80");
        assertThat(trend.get(0).totalAmount()).isEqualByComparingTo("230");
        assertThat(trend.get(1).statDate()).isEqualTo(DAY2);
        assertThat(trend.get(1).bookingAmount()).isEqualByComparingTo("200");
        assertThat(trend.get(1).productAmount()).isEqualByComparingTo("40");
        assertThat(trend.get(2).statDate()).isEqualTo(DAY3);
        assertThat(trend.get(2).totalAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("销量 Top：按销量降序，limit 生效，服务名取自 service_item 联查")
    void topItemsOrdersAndLimits() {
        TopItemsReport report = analyticsReportService.topItems(DAY1, DAY3, 1);

        assertThat(report.services()).hasSize(1);
        assertThat(report.services().get(0).getItemId()).isEqualTo(101L);
        assertThat(report.services().get(0).getItemName()).isEqualTo("宠物洗护");
        assertThat(report.services().get(0).getQuantity()).isEqualTo(2L);
        assertThat(report.products()).hasSize(1);
        assertThat(report.products().get(0).getQuantity()).isEqualTo(3L);
    }

    @Test
    @DisplayName("Excel 导出：4 个 sheet，概览与每日明细数值正确")
    void exportWorkbookContainsAggregatedData() throws Exception {
        byte[] bytes = analyticsReportService.exportWorkbook(DAY1, DAY3);

        assertThat(bytes).isNotEmpty();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(4);
            assertThat(workbook.getSheetName(0)).isEqualTo("概览");
            assertThat(workbook.getSheetName(1)).isEqualTo("每日明细");
            assertThat(workbook.getSheetName(2)).isEqualTo("服务销量Top");
            assertThat(workbook.getSheetName(3)).isEqualTo("商品销量Top");

            Sheet overview = workbook.getSheet("概览");
            assertThat(overview.getRow(1).getCell(0).getStringCellValue()).isEqualTo("总营业额（元）");
            assertThat(overview.getRow(1).getCell(1).getStringCellValue()).isEqualTo("470.00");

            Sheet daily = workbook.getSheet("每日明细");
            assertThat(daily.getLastRowNum()).isEqualTo(3);
            Row day1Row = daily.getRow(1);
            assertThat(day1Row.getCell(0).getStringCellValue()).isEqualTo("2026-08-27");
            assertThat(day1Row.getCell(5).getStringCellValue()).isEqualTo("230.00");
        }
    }

    // ==================== fixtures ====================

    private void insertServiceItem(long id, String name) {
        ServiceItem item = new ServiceItem();
        item.setId(id);
        item.setCategoryId(1L);
        item.setName(name);
        item.setServiceMode("BOTH");
        item.setPrice(new BigDecimal("100.00"));
        item.setDurationMinutes(60);
        item.setPetType("ALL");
        item.setPetSize("ALL");
        item.setNeedAddress(0);
        item.setNeedPet(1);
        item.setStatus("ON_SALE");
        item.setSort(0);
        serviceItemService.save(item);
    }

    private void insertBooking(String status, BigDecimal price, LocalDate date, long serviceItemId) {
        ServiceBooking booking = new ServiceBooking();
        booking.setBookingNo("TB-REPORT-" + System.nanoTime());
        booking.setUserId(9003L);
        booking.setStoreId(1L);
        booking.setServiceItemId(serviceItemId);
        booking.setServiceMode("STORE");
        booking.setBookingDate(date);
        booking.setStartTime(java.time.LocalTime.of(10, 0));
        booking.setEndTime(java.time.LocalTime.of(11, 0));
        booking.setPrice(price);
        booking.setPaymentMethod("OFFLINE_STORE");
        booking.setPaymentStatus("UNPAID");
        booking.setStatus(status);
        serviceBookingMapper.insert(booking);
    }

    private void insertOrder(String status, BigDecimal totalAmount, LocalDateTime createTime) {
        ProductOrder order = new ProductOrder();
        order.setOrderNo("PO-REPORT-" + System.nanoTime());
        order.setUserId(9003L);
        order.setStoreId(1L);
        order.setTotalAmount(totalAmount);
        order.setDeliveryMethod("PICKUP");
        order.setPaymentMethod("OFFLINE_STORE");
        order.setPaymentStatus("UNPAID");
        order.setPickupStatus("WAIT_PREPARE");
        order.setStatus(status);
        order.setContactName("报表测试用户");
        order.setContactPhone("13800000001");
        order.setCreateTime(createTime);
        productOrderMapper.insert(order);

        ProductOrderItem item = new ProductOrderItem();
        item.setOrderId(order.getId());
        // 商品ID按订单金额简单区分：80 元订单对应 201，40 元订单对应 202
        item.setProductId(totalAmount.compareTo(new BigDecimal("80")) == 0 ? 201L : 202L);
        item.setProductName(totalAmount.compareTo(new BigDecimal("80")) == 0 ? "磨牙棒" : "洁齿骨");
        item.setPrice(totalAmount);
        item.setQuantity(totalAmount.compareTo(new BigDecimal("80")) == 0 ? 2 : 3);
        item.setTotalAmount(totalAmount);
        productOrderItemMapper.insert(item);
    }
}
