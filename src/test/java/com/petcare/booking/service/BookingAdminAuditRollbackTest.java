package com.petcare.booking.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petcare.admin.entity.AdminOperationLog;
import com.petcare.admin.service.AdminOperationLogService;
import com.petcare.booking.dto.BookingCreateRequest;
import com.petcare.booking.dto.BookingResponse;
import com.petcare.booking.entity.BookingStatusLog;
import com.petcare.booking.entity.ServiceBooking;
import com.petcare.booking.entity.StaffSchedule;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

/**
 * Spring integration test for booking admin audit transaction behavior.
 *
 * Verifies that SUCCESS audit save failure rolls back the booking status
 * transition and booking status log, proving they share the same transaction.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("audit-rollback")
class BookingAdminAuditRollbackTest {

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

    @MockBean private AdminOperationLogService operationLogService;

    private Long storeId;
    private Long serviceItemId;
    private Long staffId;
    private LocalDate futureDate;

    @BeforeEach
    void setUp() {
        futureDate = LocalDate.now().plusDays(3);

        Store store = new Store();
        store.setStoreName("审计回滚测试门店");
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
        cat.setName("审计回滚测试分类");
        cat.setSort(1);
        cat.setStatus("ACTIVE");
        serviceCategoryService.save(cat);

        ServiceItem item = new ServiceItem();
        item.setCategoryId(cat.getId());
        item.setName("审计回滚测试服务");
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

        Staff staff = new Staff();
        staff.setStoreId(storeId);
        staff.setName("审计回滚测试员工");
        staff.setRole("GROOMER");
        staff.setStatus("ACTIVE");
        staffService.save(staff);
        staffId = staff.getId();

        StaffSkill skill = new StaffSkill();
        skill.setStaffId(staffId);
        skill.setServiceCategoryId(cat.getId());
        staffSkillService.save(skill);

        StaffSchedule schedule = new StaffSchedule();
        schedule.setStaffId(staffId);
        schedule.setStoreId(storeId);
        schedule.setWorkDate(futureDate);
        schedule.setStartTime(LocalTime.of(9, 0));
        schedule.setEndTime(LocalTime.of(17, 0));
        schedule.setStatus("AVAILABLE");
        staffScheduleService.save(schedule);
    }

    @Test
    @DisplayName("SUCCESS audit save failure rolls back booking status and status log")
    void successAuditFailure_rollsBackBookingTransition() {
        // Create a booking（新规则：创建即 CONFIRMED）
        BookingResponse booking = createTestBooking();
        Long bookingId = booking.id();

        // Verify initial state
        ServiceBooking beforeStart = serviceBookingService.getById(bookingId);
        assertThat(beforeStart.getStatus()).isEqualTo("CONFIRMED");

        long statusLogCountBefore = bookingStatusLogService.count(
                new LambdaQueryWrapper<BookingStatusLog>()
                        .eq(BookingStatusLog::getBookingId, bookingId)
                        .eq(BookingStatusLog::getNewStatus, "IN_SERVICE"));

        // Mock operationLogService.save() to return false (audit failure)
        when(operationLogService.save(any())).thenReturn(false);

        // start 应因审计保存失败而回滚（CONFIRMED → IN_SERVICE 失败）
        assertThatThrownBy(() ->
                bookingApplicationService.startBooking(bookingId, 9999L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("admin operation log");

        // Verify booking status was rolled back to CONFIRMED
        ServiceBooking afterStart = serviceBookingService.getById(bookingId);
        assertThat(afterStart.getStatus())
                .as("Booking status must be rolled back to CONFIRMED after audit failure")
                .isEqualTo("CONFIRMED");

        // Verify no new IN_SERVICE status log was created
        long statusLogCountAfter = bookingStatusLogService.count(
                new LambdaQueryWrapper<BookingStatusLog>()
                        .eq(BookingStatusLog::getBookingId, bookingId)
                        .eq(BookingStatusLog::getNewStatus, "IN_SERVICE"));

        assertThat(statusLogCountAfter)
                .as("No IN_SERVICE status log should exist after audit-triggered rollback")
                .isEqualTo(statusLogCountBefore);
    }

    @Test
    @DisplayName("Business failure writes FAIL audit that survives transaction rollback")
    @Transactional
    void businessFailure_writesFailAuditSurvivingRollback() {
        BookingResponse booking = createTestBooking();
        Long bookingId = booking.id();

        // 对 CONFIRMED 的预约调 complete 是非法流转（需先 start）
        assertThatThrownBy(() ->
                bookingApplicationService.completeBooking(bookingId, 9999L))
                .isInstanceOf(com.petcare.common.exception.BusinessException.class);

        // Verify FAIL audit was written despite business transaction rollback
        ArgumentCaptor<AdminOperationLog> captor = ArgumentCaptor.forClass(AdminOperationLog.class);
        org.mockito.Mockito.verify(operationLogService).saveFailLog(captor.capture());

        AdminOperationLog failLog = captor.getValue();
        assertThat(failLog.getResult()).isEqualTo("FAIL");
        assertThat(failLog.getOperation()).isEqualTo("complete-booking");
        assertThat(failLog.getAdminId()).isEqualTo(9999L);

        // Verify booking status was not changed by the failed operation
        ServiceBooking unchanged = serviceBookingService.getById(bookingId);
        assertThat(unchanged.getStatus()).isEqualTo("CONFIRMED");
    }

    private BookingResponse createTestBooking() {
        BookingCreateRequest req = new BookingCreateRequest(
                storeId, serviceItemId, null, "STORE", futureDate,
                LocalTime.of(14, 0), null, "审计测试用户", "13800900099",
                "OFFLINE_STORE", null);
        return bookingApplicationService.createBooking(70001L, req);
    }
}
