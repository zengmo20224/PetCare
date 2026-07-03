package com.petcare.booking.mapper;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.petcare.booking.dto.BookingCreateRequest;
import com.petcare.booking.dto.BookingResponse;
import com.petcare.booking.entity.BookingStatusLog;
import com.petcare.booking.entity.ServiceBooking;
import com.petcare.booking.entity.StaffBookingLock;
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
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * MySQL 8 integration tests for booking lock and concurrency (RM-B01).
 *
 * These tests verify:
 * 1. Upsert SQL executes on MySQL 8 (no MERGE INTO error)
 * 2. Repeated upserts preserve existing primary key
 * 3. Same-staff overlapping concurrent bookings: exactly 1 succeeds
 * 4. Same-staff adjacent time slots: both succeed
 *
 * Run with the Testcontainers MySQL gate:
 *   mvn clean test -Ptc-mysql
 */
@Tag("tc-mysql")
class BookingConcurrencyMySqlIT extends AbstractTcMySqlIT {

    @Autowired private StaffBookingLockMapper staffBookingLockMapper;
    @Autowired private BookingApplicationService bookingApplicationService;
    @Autowired private StoreService storeService;
    @Autowired private StoreConfigService storeConfigService;
    @Autowired private ServiceCategoryService serviceCategoryService;
    @Autowired private ServiceItemService serviceItemService;
    @Autowired private StaffService staffService;
    @Autowired private StaffSkillService staffSkillService;
    @Autowired private StaffScheduleService staffScheduleService;
    @Autowired private ServiceBookingService serviceBookingService;
    @Autowired private BookingStatusLogService bookingStatusLogService;

    private Long storeId;
    private Long serviceItemId;
    private Long staffId;
    private Long categoryId;
    private LocalDate futureDate;

    @BeforeEach
    void setUp() {
        futureDate = LocalDate.now().plusDays(3);

        Store store = new Store();
        store.setStoreName("MySQL锁测试门店");
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
        cat.setName("MySQL锁测试分类");
        cat.setSort(1);
        cat.setStatus("ACTIVE");
        serviceCategoryService.save(cat);
        categoryId = cat.getId();

        ServiceItem item = new ServiceItem();
        item.setCategoryId(cat.getId());
        item.setName("MySQL锁测试服务");
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

        // Create ONLY ONE staff member so auto-assignment targets the same staff
        staffId = createStaffWithSkill("MySQL锁测试员工", storeId, cat.getId());
        createSchedule(staffId, storeId, futureDate);
    }

    // --- Upsert SQL Tests ---

    @Test
    @DisplayName("upsertStaffBookingLock executes on MySQL 8 without MERGE INTO error")
    void upsertStaffBookingLock_executesOnMysql8() {
        long lockId = IdWorker.getId();
        assertThatCode(() ->
                staffBookingLockMapper.upsertStaffBookingLock(lockId, staffId, futureDate)
        ).doesNotThrowAnyException();

        StaffBookingLock lock = staffBookingLockMapper.selectStaffBookingLockForUpdate(staffId, futureDate);
        assertThat(lock).isNotNull();
        assertThat(lock.getStaffId()).isEqualTo(staffId);
        assertThat(lock.getBookingDate()).isEqualTo(futureDate);
    }

    @Test
    @DisplayName("Repeated upsert preserves existing primary key")
    void upsertStaffBookingLock_preservesExistingPrimaryKey() {
        long lockId1 = IdWorker.getId();
        staffBookingLockMapper.upsertStaffBookingLock(lockId1, staffId, futureDate);

        StaffBookingLock afterFirst = staffBookingLockMapper.selectStaffBookingLockForUpdate(staffId, futureDate);
        assertThat(afterFirst).isNotNull();
        Long originalId = afterFirst.getId();

        // Upsert again with a different proposed ID
        long lockId2 = IdWorker.getId();
        staffBookingLockMapper.upsertStaffBookingLock(lockId2, staffId, futureDate);

        StaffBookingLock afterSecond = staffBookingLockMapper.selectStaffBookingLockForUpdate(staffId, futureDate);
        assertThat(afterSecond).isNotNull();
        assertThat(afterSecond.getId())
                .as("Repeated upsert must preserve the original primary key")
                .isEqualTo(originalId);

        // Only one lock row for this staff+date
        long count = countLockRows(staffId, futureDate);
        assertThat(count).isEqualTo(1);
    }

    // --- Concurrency Tests ---

    @Test
    @DisplayName("Same staff, same overlapping time: exactly 1 booking succeeds")
    void sameStaffSameOverlappingTime_onlyOneBookingSucceeds() throws Exception {
        BookingCreateRequest req1 = new BookingCreateRequest(storeId, serviceItemId, null, "STORE",
                futureDate, LocalTime.of(10, 0), null, "用户1", "13800000001", "OFFLINE_STORE", null);
        BookingCreateRequest req2 = new BookingCreateRequest(storeId, serviceItemId, null, "STORE",
                futureDate, LocalTime.of(10, 0), null, "用户2", "13800000002", "OFFLINE_STORE", null);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Runnable task1 = () -> {
            ready.countDown();
            try { gate.await(); } catch (InterruptedException e) { return; }
            try {
                bookingApplicationService.createBooking(10001L, req1);
                successCount.incrementAndGet();
            } catch (BusinessException e) {
                if ("booking_time_conflict".equals(e.getCode())) {
                    conflictCount.incrementAndGet();
                } else {
                    errors.add(e);
                }
            } finally {
                done.countDown();
            }
        };

        Runnable task2 = () -> {
            ready.countDown();
            try { gate.await(); } catch (InterruptedException e) { return; }
            try {
                bookingApplicationService.createBooking(10002L, req2);
                successCount.incrementAndGet();
            } catch (BusinessException e) {
                if ("booking_time_conflict".equals(e.getCode())) {
                    conflictCount.incrementAndGet();
                } else {
                    errors.add(e);
                }
            } finally {
                done.countDown();
            }
        };

        executor.submit(task1);
        executor.submit(task2);

        // Wait for both threads to be ready, then open gate simultaneously
        ready.await(10, TimeUnit.SECONDS);
        gate.countDown();

        done.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Exact assertion: exactly 1 success, exactly 1 conflict
        assertThat(errors).as("No unexpected errors").isEmpty();
        assertThat(successCount.get())
                .as("Exactly 1 booking should succeed for same staff, same overlapping time")
                .isEqualTo(1);
        assertThat(conflictCount.get())
                .as("Exactly 1 booking should fail with booking_time_conflict")
                .isEqualTo(1);

        // Verify database state: exactly 1 active booking for this staff+date+time
        long bookingCount = serviceBookingService.count(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ServiceBooking>()
                        .eq(ServiceBooking::getStaffId, staffId)
                        .eq(ServiceBooking::getBookingDate, futureDate)
                        .eq(ServiceBooking::getStartTime, LocalTime.of(10, 0))
                        .in(ServiceBooking::getStatus, "PENDING_CONFIRM", "CONFIRMED", "IN_SERVICE"));
        assertThat(bookingCount)
                .as("Database should have exactly 1 active booking")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Same staff, adjacent time slots: both bookings succeed")
    void sameStaffAdjacentTime_bothBookingsSucceed() {
        BookingCreateRequest req1 = new BookingCreateRequest(storeId, serviceItemId, null, "STORE",
                futureDate, LocalTime.of(10, 0), null, "用户A", "13800000001", "OFFLINE_STORE", null);
        BookingCreateRequest req2 = new BookingCreateRequest(storeId, serviceItemId, null, "STORE",
                futureDate, LocalTime.of(11, 0), null, "用户B", "13800000002", "OFFLINE_STORE", null);

        BookingResponse r1 = bookingApplicationService.createBooking(20001L, req1);
        BookingResponse r2 = bookingApplicationService.createBooking(20002L, req2);

        // 自 a5a8810 起，预约创建即自动确认（CONFIRMED），不再有 PENDING_CONFIRM 中间态
        assertThat(r1.status()).isEqualTo("CONFIRMED");
        assertThat(r2.status()).isEqualTo("CONFIRMED");
    }

    // --- Helper methods ---

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

    private long countLockRows(Long staffId, LocalDate bookingDate) {
        return staffBookingLockMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StaffBookingLock>()
                        .eq(StaffBookingLock::getStaffId, staffId)
                        .eq(StaffBookingLock::getBookingDate, bookingDate)
        ).size();
    }
}
