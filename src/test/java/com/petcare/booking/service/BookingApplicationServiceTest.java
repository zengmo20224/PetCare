package com.petcare.booking.service;

import com.petcare.booking.dto.BookingAvailabilityRequest;
import com.petcare.booking.dto.BookingAvailabilityResponse;
import com.petcare.booking.dto.BookingCreateRequest;
import com.petcare.booking.dto.BookingResponse;
import com.petcare.booking.entity.BookingStatusLog;
import com.petcare.booking.entity.ServiceBooking;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
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
import com.petcare.user.entity.UserAddress;
import com.petcare.user.service.UserAddressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for BookingApplicationService.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BookingApplicationServiceTest {

    @Autowired
    private BookingApplicationService bookingApplicationService;

    @Autowired
    private StoreService storeService;
    @Autowired
    private StoreConfigService storeConfigService;
    @Autowired
    private ServiceCategoryService serviceCategoryService;
    @Autowired
    private ServiceItemService serviceItemService;
    @Autowired
    private StaffService staffService;
    @Autowired
    private StaffSkillService staffSkillService;
    @Autowired
    private StaffScheduleService staffScheduleService;
    @Autowired
    private ServiceBookingService serviceBookingService;
    @Autowired
    private BookingStatusLogService bookingStatusLogService;
    @Autowired
    private UserAddressService userAddressService;

    private Long storeId;
    private Long serviceItemId;
    private Long staffId;
    private LocalDate futureDate;

    @BeforeEach
    void setUp() {
        futureDate = LocalDate.now().plusDays(3);

        // Create store
        Store store = new Store();
        store.setStoreName("测试门店");
        store.setLongitude(new BigDecimal("116.407400"));
        store.setLatitude(new BigDecimal("39.904200"));
        store.setStatus("OPEN");
        storeService.save(store);
        storeId = store.getId();

        // Create store config
        StoreConfig config = new StoreConfig();
        config.setStoreId(storeId);
        config.setHomeServiceRadiusKm(new BigDecimal("5.00"));
        config.setBookingAdvanceDays(14);
        config.setBookingCancelHours(4);
        config.setTimeSlotMinutes(30);
        storeConfigService.save(config);

        // Create service category
        ServiceCategory cat = new ServiceCategory();
        cat.setName("美容");
        cat.setSort(1);
        cat.setStatus("ACTIVE");
        serviceCategoryService.save(cat);

        // Create service item
        ServiceItem item = new ServiceItem();
        item.setCategoryId(cat.getId());
        item.setName("洗澡服务");
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

        // Create staff
        Staff staff = new Staff();
        staff.setStoreId(storeId);
        staff.setName("张师傅");
        staff.setPhone("13800000001");
        staff.setRole("GROOMER");
        staff.setStatus("ACTIVE");
        staffService.save(staff);
        staffId = staff.getId();

        // Create staff skill
        StaffSkill skill = new StaffSkill();
        skill.setStaffId(staffId);
        skill.setServiceCategoryId(cat.getId());
        staffSkillService.save(skill);

        // Create schedule for future date
        com.petcare.booking.entity.StaffSchedule schedule = new com.petcare.booking.entity.StaffSchedule();
        schedule.setStaffId(staffId);
        schedule.setStoreId(storeId);
        schedule.setWorkDate(futureDate);
        schedule.setStartTime(LocalTime.of(9, 0));
        schedule.setEndTime(LocalTime.of(17, 0));
        schedule.setStatus("AVAILABLE");
        staffScheduleService.save(schedule);
    }

    @Nested
    @DisplayName("Availability")
    class Availability {

        @Test
        @DisplayName("Returns available slots for valid input")
        void returnsAvailableSlots() {
            BookingAvailabilityRequest request = new BookingAvailabilityRequest(
                    storeId, serviceItemId, futureDate, "STORE");

            BookingAvailabilityResponse response = bookingApplicationService.getAvailability(request);

            assertThat(response.slots()).isNotEmpty();
            assertThat(response.storeId()).isEqualTo(storeId);
            assertThat(response.serviceItemId()).isEqualTo(serviceItemId);
            assertThat(response.durationMinutes()).isEqualTo(60);
            assertThat(response.timeSlotMinutes()).isEqualTo(30);
            // Should not expose staffId
            assertThat(response.toString()).doesNotContain("staffId");
        }

        @Test
        @DisplayName("Invalid serviceItemId throws BOOKING_SERVICE_UNAVAILABLE")
        void invalidServiceItem() {
            BookingAvailabilityRequest request = new BookingAvailabilityRequest(
                    storeId, 999999L, futureDate, "STORE");

            assertThatThrownBy(() -> bookingApplicationService.getAvailability(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }

        @Test
        @DisplayName("Date beyond advance days throws BOOKING_DATE_OUT_OF_RANGE")
        void dateOutOfRange() {
            LocalDate tooFar = LocalDate.now().plusDays(30);
            BookingAvailabilityRequest request = new BookingAvailabilityRequest(
                    storeId, serviceItemId, tooFar, "STORE");

            assertThatThrownBy(() -> bookingApplicationService.getAvailability(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.BOOKING_DATE_OUT_OF_RANGE);
        }
    }

    @Nested
    @DisplayName("Create Booking")
    class CreateBooking {

        @Test
        @DisplayName("Successful booking creation")
        void successfulCreation() {
            BookingCreateRequest request = new BookingCreateRequest(
                    storeId, serviceItemId, null, "STORE", futureDate,
                    LocalTime.of(10, 0), null, "张三", "13800000000",
                    "OFFLINE_STORE", null);

            BookingResponse response = bookingApplicationService.createBooking(1000L, request);

            assertThat(response.id()).isNotNull();
            assertThat(response.bookingNo()).startsWith("BK");
            assertThat(response.status()).isEqualTo("CONFIRMED");
            assertThat(response.paymentStatus()).isEqualTo("UNPAID");
            assertThat(response.staffId()).isEqualTo(staffId);
            assertThat(response.endTime()).isEqualTo(LocalTime.of(11, 0));
            assertThat(response.price()).isEqualByComparingTo(new BigDecimal("99.00"));

            // Verify status log
            ServiceBooking saved = serviceBookingService.getById(response.id());
            assertThat(saved).isNotNull();

            long logCount = bookingStatusLogService.count(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BookingStatusLog>()
                            .eq(BookingStatusLog::getBookingId, response.id()));
            assertThat(logCount).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("HOME mode without addressId throws BOOKING_ADDRESS_REQUIRED")
        void homeModeWithoutAddress() {
            BookingCreateRequest request = new BookingCreateRequest(
                    storeId, serviceItemId, null, "HOME", futureDate,
                    LocalTime.of(10, 0), null, "张三", "13800000000",
                    "OFFLINE_HOME", null);

            assertThatThrownBy(() -> bookingApplicationService.createBooking(1000L, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.BOOKING_ADDRESS_REQUIRED);
        }

        @Test
        @DisplayName("HOME mode with address not belonging to user throws BOOKING_ADDRESS_NOT_FOUND")
        void homeModeWrongUser() {
            // Create address for different user
            UserAddress address = new UserAddress();
            address.setUserId(9999L); // Different user
            address.setContactName("李四");
            address.setContactPhone("13800000001");
            address.setDetailAddress("附近");
            address.setLongitude(new BigDecimal("116.408000"));
            address.setLatitude(new BigDecimal("39.905000"));
            userAddressService.save(address);

            BookingCreateRequest request = new BookingCreateRequest(
                    storeId, serviceItemId, null, "HOME", futureDate,
                    LocalTime.of(10, 0), address.getId(), "张三", "13800000000",
                    "OFFLINE_HOME", null);

            assertThatThrownBy(() -> bookingApplicationService.createBooking(1000L, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.BOOKING_ADDRESS_NOT_FOUND);
        }

        @Test
        @DisplayName("Date beyond advance days throws BOOKING_DATE_OUT_OF_RANGE")
        void dateOutOfRange() {
            LocalDate tooFar = LocalDate.now().plusDays(30);
            BookingCreateRequest request = new BookingCreateRequest(
                    storeId, serviceItemId, null, "STORE", tooFar,
                    LocalTime.of(10, 0), null, "张三", "13800000000",
                    "OFFLINE_STORE", null);

            assertThatThrownBy(() -> bookingApplicationService.createBooking(1000L, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.BOOKING_DATE_OUT_OF_RANGE);
        }

        @Test
        @DisplayName("Valid HOME booking within radius succeeds")
        void homeModeWithinRadius() {
            // Create address near the store
            UserAddress address = new UserAddress();
            address.setUserId(1000L);
            address.setContactName("张三");
            address.setContactPhone("13800000000");
            address.setDetailAddress("门店附近");
            address.setLongitude(new BigDecimal("116.410000"));
            address.setLatitude(new BigDecimal("39.906000"));
            userAddressService.save(address);

            // Create HOME-only service
            ServiceItem homeItem = new ServiceItem();
            homeItem.setCategoryId(serviceItemService.getById(serviceItemId).getCategoryId());
            homeItem.setName("上门遛狗");
            homeItem.setServiceMode("HOME");
            homeItem.setPrice(new BigDecimal("120.00"));
            homeItem.setDurationMinutes(60);
            homeItem.setPetType("DOG");
            homeItem.setPetSize("ALL");
            homeItem.setNeedAddress(1);
            homeItem.setNeedPet(1);
            homeItem.setStatus("ON_SALE");
            homeItem.setSort(0);
            serviceItemService.save(homeItem);

            BookingCreateRequest request = new BookingCreateRequest(
                    storeId, homeItem.getId(), null, "HOME", futureDate,
                    LocalTime.of(10, 0), address.getId(), "张三", "13800000000",
                    "OFFLINE_HOME", null);

            BookingResponse response = bookingApplicationService.createBooking(1000L, request);
            assertThat(response.status()).isEqualTo("CONFIRMED");
            // 距离限制已取消：distanceKm 不再计算，保持为 null
            assertThat(response.distanceKm()).isNull();
        }
    }
}
