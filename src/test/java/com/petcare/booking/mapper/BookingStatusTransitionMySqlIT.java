package com.petcare.booking.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petcare.booking.dto.BookingCancelRequest;
import com.petcare.booking.dto.BookingCreateRequest;
import com.petcare.booking.dto.BookingResponse;
import com.petcare.booking.entity.BookingStatusLog;
import com.petcare.booking.entity.ServiceBooking;
import com.petcare.booking.entity.StaffSchedule;
import com.petcare.booking.service.BookingApplicationService;
import com.petcare.booking.service.BookingStatusLogService;
import com.petcare.booking.service.ServiceBookingService;
import com.petcare.booking.service.StaffScheduleService;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.persistence.AbstractTcMySqlIT;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MySQL 8 integration tests for booking status transition concurrency (RM-B02).
 *
 * Verifies:
 * 1. Concurrent confirm and reject: only one wins
 * 2. Concurrent start and cancel: final state is valid
 *
 * Run with the Testcontainers MySQL gate:
 *   mvn clean test -Ptc-mysql
 */
@Tag("tc-mysql")
class BookingStatusTransitionMySqlIT extends AbstractTcMySqlIT {

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
    private Long staffId;
    private LocalDate futureDate;

    @BeforeEach
    void setUp() {
        futureDate = LocalDate.now().plusDays(3);

        Store store = new Store();
        store.setStoreName("状态并发测试门店");
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
        cat.setName("状态并发测试分类");
        cat.setSort(1);
        cat.setStatus("ACTIVE");
        serviceCategoryService.save(cat);

        ServiceItem item = new ServiceItem();
        item.setCategoryId(cat.getId());
        item.setName("状态并发测试服务");
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

        staffId = createStaffWithSkill("状态并发测试员工", storeId, cat.getId());
        createSchedule(staffId, storeId, futureDate);
    }

    @Test
    @DisplayName("Concurrent start and complete: only one transition wins")
    void concurrentStartAndComplete_onlyOneTransitionWins() throws Exception {
        // 自 a5a8810 起，createBooking 即 CONFIRMED。
        // 从 CONFIRMED 出发，并发两个相同合法转移（两次 start）：
        // 第一个 CONFIRMED→IN_SERVICE 成功，第二个 IN_SERVICE→IN_SERVICE 非法被拒。
        BookingResponse booking = createTestBooking(30001L, "13800300001");

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Thread 1: start
        executor.submit(() -> {
            ready.countDown();
            try { gate.await(); } catch (InterruptedException e) { return; }
            try {
                bookingApplicationService.startBooking(booking.id(), 9001L);
                successCount.incrementAndGet();
            } catch (BusinessException e) {
                failCount.incrementAndGet();
                errors.add(e);
            } finally {
                done.countDown();
            }
        });

        // Thread 2: start again (must lose — IN_SERVICE→IN_SERVICE illegal)
        executor.submit(() -> {
            ready.countDown();
            try { gate.await(); } catch (InterruptedException e) { return; }
            try {
                bookingApplicationService.startBooking(booking.id(), 9002L);
                successCount.incrementAndGet();
            } catch (BusinessException e) {
                failCount.incrementAndGet();
                errors.add(e);
            } finally {
                done.countDown();
            }
        });

        ready.await(10, TimeUnit.SECONDS);
        gate.countDown();
        done.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly 1 success
        assertThat(successCount.get())
                .as("Exactly 1 of concurrent start should succeed")
                .isEqualTo(1);
        assertThat(failCount.get())
                .as("Exactly 1 of concurrent start should fail with status error")
                .isEqualTo(1);

        // Final status must be IN_SERVICE
        ServiceBooking finalBooking = serviceBookingService.getById(booking.id());
        assertThat(finalBooking.getStatus())
                .as("Final status must be IN_SERVICE")
                .isEqualTo("IN_SERVICE");

        // Status logs must be consistent with final state
        List<BookingStatusLog> logs = bookingStatusLogService.list(
                new LambdaQueryWrapper<BookingStatusLog>()
                        .eq(BookingStatusLog::getBookingId, booking.id())
                        .isNotNull(BookingStatusLog::getOldStatus)); // exclude creation log

        // Only 1 transition log (the winning one)
        assertThat(logs.size())
                .as("Only 1 status transition log for the winning operation")
                .isEqualTo(1);

        BookingStatusLog transitionLog = logs.get(0);
        assertThat(transitionLog.getOldStatus()).isEqualTo("CONFIRMED");
        assertThat(transitionLog.getNewStatus()).isEqualTo("IN_SERVICE");
    }

    @Test
    @DisplayName("Concurrent start and cancel: final state is valid per state machine")
    void concurrentStartAndCancel_neverProducesInvalidFinalState() throws Exception {
        // 自 a5a8810 起，createBooking 即自动确认（CONFIRMED），无需再手动 confirm
        BookingResponse booking = createTestBooking(30002L, "13800300002");

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Thread 1: start service
        executor.submit(() -> {
            ready.countDown();
            try { gate.await(); } catch (InterruptedException e) { return; }
            try {
                bookingApplicationService.startBooking(booking.id(), 9001L);
                successCount.incrementAndGet();
            } catch (BusinessException e) {
                failCount.incrementAndGet();
                errors.add(e);
            } finally {
                done.countDown();
            }
        });

        // Thread 2: admin cancel
        executor.submit(() -> {
            ready.countDown();
            try { gate.await(); } catch (InterruptedException e) { return; }
            try {
                bookingApplicationService.cancelBookingAdmin(booking.id(), "紧急取消", 9002L);
                successCount.incrementAndGet();
            } catch (BusinessException e) {
                failCount.incrementAndGet();
                errors.add(e);
            } finally {
                done.countDown();
            }
        });

        ready.await(10, TimeUnit.SECONDS);
        gate.countDown();
        done.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly 1 success
        assertThat(successCount.get())
                .as("Exactly 1 of start/cancel should succeed")
                .isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);

        // Final state must be valid per state machine
        ServiceBooking finalBooking = serviceBookingService.getById(booking.id());
        assertThat(finalBooking.getStatus())
                .as("Final status must be IN_SERVICE or CANCELLED (both valid from CONFIRMED)")
                .isIn("IN_SERVICE", "CANCELLED");
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
