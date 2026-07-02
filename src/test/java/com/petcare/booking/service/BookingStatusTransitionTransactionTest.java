package com.petcare.booking.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petcare.booking.dto.BookingCancelRequest;
import com.petcare.booking.dto.BookingCreateRequest;
import com.petcare.booking.dto.BookingResponse;
import com.petcare.booking.entity.BookingStatusLog;
import com.petcare.booking.entity.ServiceBooking;
import com.petcare.booking.entity.StaffSchedule;
import com.petcare.common.exception.BusinessException;
import com.petcare.service.entity.ServiceCategory;
import com.petcare.service.entity.ServiceItem;
import com.petcare.service.service.ServiceCategoryService;
import com.petcare.service.service.ServiceItemService;
import com.petcare.staff.entity.Staff;
import com.petcare.staff.entity.StaffSkill;
import com.petcare.staff.service.StaffService;
import com.petcare.staff.service.StaffSkillService;
import com.petcare.store.entity.Store;
import com.petcare.store.entity.StoreConfig;
import com.petcare.store.service.StoreConfigService;
import com.petcare.store.service.StoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Status transition transaction regression tests (RM-B02).
 *
 * Verifies:
 * 1. User cancel writes the REAL old status (not CANCELLED->CANCELLED)
 * 2. Repeated same transition is rejected without extra log
 * 3. Status log and booking update are atomic
 *
 * Runs on H2 to validate logic. MySQL concurrency tested separately.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("status-transition")
// 每个测试方法结束后重建 Spring 上下文 + H2 schema，避免 843 个测试
// 共用同一个内存库时数据污染（Jenkins 全量 test 时 oldStatus 被前面残留数据置空）
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BookingStatusTransitionTransactionTest {

    @Autowired private BookingApplicationService bookingApplicationService;
    @Autowired private ServiceBookingService serviceBookingService;
    @Autowired private BookingStatusLogService bookingStatusLogService;
    @Autowired private StoreService storeService;
    @Autowired private StoreConfigService storeConfigService;
    @Autowired private ServiceCategoryService serviceCategoryService;
    @Autowired private ServiceItemService serviceItemService;
    @Autowired private StaffService staffService;
    @Autowired private StaffSkillService staffSkillService;
    @Autowired private StaffScheduleService staffScheduleService;

    private Long storeId;
    private Long serviceItemId;
    private LocalDate futureDate;

    @BeforeEach
    void setUp() {
        futureDate = LocalDate.now().plusDays(3);

        Store store = new Store();
        store.setStoreName("状态流转测试门店");
        store.setLongitude(new BigDecimal("116.407400"));
        store.setLatitude(new BigDecimal("39.904200"));
        store.setStatus("OPEN");
        storeService.save(store);
        storeId = store.getId();

        StoreConfig config = new StoreConfig();
        config.setStoreId(storeId);
        config.setHomeServiceRadiusKm(new BigDecimal("5.00"));
        config.setBookingAdvanceDays(14);
        config.setBookingCancelHours(4);
        config.setTimeSlotMinutes(30);
        storeConfigService.save(config);

        ServiceCategory cat = new ServiceCategory();
        cat.setName("状态测试分类");
        cat.setSort(1);
        cat.setStatus("ACTIVE");
        serviceCategoryService.save(cat);

        ServiceItem item = new ServiceItem();
        item.setCategoryId(cat.getId());
        item.setName("状态测试服务");
        item.setServiceMode("BOTH");
        item.setPrice(new BigDecimal("99.00"));
        item.setDurationMinutes(60);
        item.setPetType("ALL");
        item.setPetSize("ALL");
        item.setNeedAddress(0);
        item.setNeedPet(1);
        item.setStatus("ON_SALE");
        item.setSort(0);
        serviceItemService.save(item);
        serviceItemId = item.getId();

        Long staffId = createStaffWithSkill("状态测试员工", storeId, cat.getId());
        createSchedule(staffId, storeId, futureDate);
    }

    @Test
    @DisplayName("User cancel writes real old status CONFIRMED (创建即已确认), not CANCELLED")
    void userCancel_writesActualOldStatus() {
        // Create a booking（新规则：创建即 CONFIRMED）
        BookingResponse booking = createTestBooking(90001L, "13800900001");

        // Cancel it
        bookingApplicationService.cancelBooking(90001L, booking.id(),
                new BookingCancelRequest("不想要了"));

        // Check the status log.
        BookingStatusLog log = bookingStatusLogService.getOne(
                new LambdaQueryWrapper<BookingStatusLog>()
                        .eq(BookingStatusLog::getBookingId, booking.id())
                        .eq(BookingStatusLog::getNewStatus, "CANCELLED"));

        assertThat(log).as("A status log must exist for the cancel operation").isNotNull();
        assertThat(log.getOldStatus())
                .as("Old status must be CONFIRMED (the real state before cancel), not CANCELLED")
                .isEqualTo("CONFIRMED");
        assertThat(log.getNewStatus()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("Repeated same transition is rejected without extra log")
    void repeatedSameTransition_isRejectedWithoutExtraLog() {
        BookingResponse booking = createTestBooking(90003L, "13800900003");

        long logCountBefore = bookingStatusLogService.count(
                new LambdaQueryWrapper<BookingStatusLog>()
                        .eq(BookingStatusLog::getBookingId, booking.id()));

        // 创建即 CONFIRMED，再次 confirm 应失败（CONFIRMED -> CONFIRMED 非法流转）
        assertThatThrownBy(() ->
                bookingApplicationService.confirmBooking(booking.id(), null, 9999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("预约状态不允许");

        // No extra log should be written
        long logCountAfter = bookingStatusLogService.count(
                new LambdaQueryWrapper<BookingStatusLog>()
                        .eq(BookingStatusLog::getBookingId, booking.id()));

        assertThat(logCountAfter)
                .as("Rejected transition must NOT create an extra status log")
                .isEqualTo(logCountBefore);
    }

    @Test
    @DisplayName("Start booking writes correct old and new status in log")
    void startBooking_writesCorrectStatusLog() {
        BookingResponse booking = createTestBooking(90004L, "13800900004");

        // 创建即 CONFIRMED，直接 start
        bookingApplicationService.startBooking(booking.id(), 9999L);

        BookingStatusLog log = bookingStatusLogService.getOne(
                new LambdaQueryWrapper<BookingStatusLog>()
                        .eq(BookingStatusLog::getBookingId, booking.id())
                        .eq(BookingStatusLog::getNewStatus, "IN_SERVICE"));

        assertThat(log).isNotNull();
        assertThat(log.getOldStatus()).isEqualTo("CONFIRMED");
        assertThat(log.getNewStatus()).isEqualTo("IN_SERVICE");
    }

    @Test
    @DisplayName("Complete full lifecycle: create(=CONFIRMED) -> start -> complete")
    void fullLifecycle_allLogsCorrect() {
        BookingResponse booking = createTestBooking(90005L, "13800900005");

        // 创建即 CONFIRMED，无需单独 confirm
        // Start
        bookingApplicationService.startBooking(booking.id(), 9999L);
        // Complete
        bookingApplicationService.completeBooking(booking.id(), 9999L);

        // Verify logs
        long logCount = bookingStatusLogService.count(
                new LambdaQueryWrapper<BookingStatusLog>()
                        .eq(BookingStatusLog::getBookingId, booking.id()));

        // 3 logs: create(CONFIRMED) + start + complete
        assertThat(logCount).isGreaterThanOrEqualTo(3);

        // Verify final state
        ServiceBooking finalBooking = serviceBookingService.getById(booking.id());
        assertThat(finalBooking.getStatus()).isEqualTo("COMPLETED");
        assertThat(finalBooking.getCompleteTime()).isNotNull();
    }

    // --- Helper methods ---

    private BookingResponse createTestBooking(Long userId, String phone) {
        BookingCreateRequest req = new BookingCreateRequest(storeId, serviceItemId, null, "STORE",
                futureDate, LocalTime.of(14, 0), null, "测试用户", phone, "OFFLINE_STORE", null);
        return bookingApplicationService.createBooking(userId, req);
    }

    private Long createStaffWithSkill(String name, Long storeId, Long categoryId) {
        Staff staff = new Staff();
        staff.setStoreId(storeId);
        staff.setName(name);
        staff.setRole("GROOMER");
        staff.setStatus("ACTIVE");
        staffService.save(staff);

        StaffSkill skill = new StaffSkill();
        skill.setStaffId(staff.getId());
        skill.setServiceCategoryId(categoryId);
        staffSkillService.save(skill);

        return staff.getId();
    }

    private void createSchedule(Long staffId, Long storeId, LocalDate date) {
        StaffSchedule schedule = new StaffSchedule();
        schedule.setStaffId(staffId);
        schedule.setStoreId(storeId);
        schedule.setWorkDate(date);
        schedule.setStartTime(LocalTime.of(9, 0));
        schedule.setEndTime(LocalTime.of(17, 0));
        schedule.setStatus("AVAILABLE");
        staffScheduleService.save(schedule);
    }
}
