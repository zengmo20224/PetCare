package com.petcare.booking.controller;

import com.petcare.admin.entity.AdminPermission;
import com.petcare.admin.entity.AdminRole;
import com.petcare.admin.entity.AdminRolePermission;
import com.petcare.admin.entity.AdminUser;
import com.petcare.admin.service.AdminPermissionService;
import com.petcare.admin.service.AdminRolePermissionService;
import com.petcare.admin.service.AdminRoleService;
import com.petcare.admin.service.AdminUserService;
import com.petcare.booking.dto.BookingCreateRequest;
import com.petcare.booking.dto.BookingResponse;
import com.petcare.booking.entity.BookingStatusLog;
import com.petcare.booking.entity.StaffSchedule;
import com.petcare.booking.service.BookingApplicationService;
import com.petcare.booking.service.BookingStatusLogService;
import com.petcare.booking.service.StaffScheduleService;
import com.petcare.common.security.JwtTokenService;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for AdminBookingController.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminBookingControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private StoreService storeService;
    @Autowired private StoreConfigService storeConfigService;
    @Autowired private ServiceCategoryService serviceCategoryService;
    @Autowired private ServiceItemService serviceItemService;
    @Autowired private StaffService staffService;
    @Autowired private StaffSkillService staffSkillService;
    @Autowired private StaffScheduleService staffScheduleService;
    @Autowired private BookingStatusLogService bookingStatusLogService;
    @Autowired private BookingApplicationService bookingApplicationService;
    @Autowired private AdminUserService adminUserService;
    @Autowired private AdminRoleService adminRoleService;
    @Autowired private AdminPermissionService adminPermissionService;
    @Autowired private AdminRolePermissionService adminRolePermissionService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenService jwtTokenService;

    private String adminToken;
    private Long bookingId;
    private Long storeId;

    @BeforeEach
    void setUp() {
        // Setup admin with booking permissions
        Long adminId = createTestAdmin("bookingadmin", "password123456", "MANAGER");
        setupPermissions();
        adminToken = jwtTokenService.signAdminToken(adminId, "bookingadmin", "MANAGER");

        // Setup test data
        Store store = new Store();
        store.setStoreName("测试门店");
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
        cat.setName("美容");
        cat.setSort(1);
        cat.setStatus("ACTIVE");
        serviceCategoryService.save(cat);

        ServiceItem item = new ServiceItem();
        item.setCategoryId(cat.getId());
        item.setName("洗澡");
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

        Staff staff = new Staff();
        staff.setStoreId(storeId);
        staff.setName("张师傅");
        staff.setRole("GROOMER");
        staff.setStatus("ACTIVE");
        staffService.save(staff);

        StaffSkill skill = new StaffSkill();
        skill.setStaffId(staff.getId());
        skill.setServiceCategoryId(cat.getId());
        staffSkillService.save(skill);

        LocalDate futureDate = LocalDate.now().plusDays(3);
        StaffSchedule schedule = new StaffSchedule();
        schedule.setStaffId(staff.getId());
        schedule.setStoreId(storeId);
        schedule.setWorkDate(futureDate);
        schedule.setStartTime(LocalTime.of(9, 0));
        schedule.setEndTime(LocalTime.of(17, 0));
        schedule.setStatus("AVAILABLE");
        staffScheduleService.save(schedule);

        // Create a booking via service
        BookingCreateRequest createReq = new BookingCreateRequest(
                storeId, item.getId(), null, "STORE", futureDate,
                LocalTime.of(10, 0), null, "张三", "13800000000",
                "OFFLINE_STORE", null);
        BookingResponse booking = bookingApplicationService.createBooking(1000L, createReq);
        bookingId = booking.id();
    }

    @Nested
    @DisplayName("Admin booking list")
    class ListBookings {

        @Test
        @DisplayName("GET /admin/bookings returns paginated list")
        void listBookings() throws Exception {
            mockMvc.perform(get("/api/v1/admin/bookings?size=100")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.items").isArray())
                    .andExpect(jsonPath("$.data.items[*].id").value(hasItem(bookingId.toString())));
        }

        @Test
        @DisplayName("GET /admin/bookings/{id} returns booking detail")
        void getBookingDetail() throws Exception {
            mockMvc.perform(get("/api/v1/admin/bookings/" + bookingId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(bookingId))
                    .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        }
    }

    @Nested
    @DisplayName("State transitions")
    class StateTransitions {

        @Test
        @DisplayName("创建即 CONFIRMED，重复 confirm 返回 409")
        void confirmBooking() throws Exception {
            // 新规则：创建预约即 CONFIRMED，再 confirm 应被拒（CONFIRMED -> CONFIRMED 非法）
            mockMvc.perform(post("/api/v1/admin/bookings/" + bookingId + "/confirm")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("booking_status_invalid"));
        }

        @Test
        @DisplayName("Reject on CONFIRMED booking returns 409（创建即确认，无法拒绝）")
        void rejectBooking() throws Exception {
            // 新规则：创建即 CONFIRMED，REJECT 仅允许从 PENDING_CONFIRM，故返回 409
            mockMvc.perform(post("/api/v1/admin/bookings/" + bookingId + "/reject")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"时间冲突\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("booking_status_invalid"));
        }

        @Test
        @DisplayName("Cancel: CONFIRMED -> CANCELLED with reason (admin)")
        void cancelBooking() throws Exception {
            mockMvc.perform(post("/api/v1/admin/bookings/" + bookingId + "/cancel")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"用户要求取消\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        }

        @Test
        @DisplayName("Cancel without reason returns 400（管理端取消必须填写原因）")
        void cancelWithoutReasonRejected() throws Exception {
            mockMvc.perform(post("/api/v1/admin/bookings/" + bookingId + "/cancel")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Cancel with blank reason returns 400")
        void cancelWithBlankReasonRejected() throws Exception {
            mockMvc.perform(post("/api/v1/admin/bookings/" + bookingId + "/cancel")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Full lifecycle: 创建即CONFIRMED -> start -> complete")
        void fullLifecycle() throws Exception {
            // 创建即 CONFIRMED，无需单独 confirm
            // Start
            mockMvc.perform(post("/api/v1/admin/bookings/" + bookingId + "/start")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("IN_SERVICE"));

            // Complete
            mockMvc.perform(post("/api/v1/admin/bookings/" + bookingId + "/complete")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        }

        @Test
        @DisplayName("Invalid transition: COMPLETED -> start returns 409")
        void invalidTransition() throws Exception {
            // 先走完完整流程到 COMPLETED
            mockMvc.perform(post("/api/v1/admin/bookings/" + bookingId + "/start")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/v1/admin/bookings/" + bookingId + "/complete")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());

            // COMPLETED 是终态，再 start 应返回 409
            mockMvc.perform(post("/api/v1/admin/bookings/" + bookingId + "/start")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("booking_status_invalid"));
        }
    }

    @Nested
    @DisplayName("Authentication and authorization")
    class Auth {

        @Test
        @DisplayName("Unauthenticated request returns 401")
        void unauthenticatedReturns401() throws Exception {
            mockMvc.perform(get("/api/v1/admin/bookings"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("STAFF role without booking:booking:read permission returns 403")
        void staffWithoutPermissionReturns403() throws Exception {
            Long staffAdminId = createTestAdmin("staffuser", "password123456", "STAFF");
            // No permissions granted for STAFF
            String staffToken = jwtTokenService.signAdminToken(staffAdminId, "staffuser", "STAFF");

            mockMvc.perform(get("/api/v1/admin/bookings")
                            .header("Authorization", "Bearer " + staffToken))
                    .andExpect(status().isForbidden());
        }
    }

    // --- helper methods ---

    private Long createTestAdmin(String username, String rawPassword, String role) {
        AdminUser admin = new AdminUser();
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode(rawPassword));
        admin.setRole(role);
        admin.setStatus("ACTIVE");
        adminUserService.save(admin);
        return admin.getId();
    }

    private void setupPermissions() {
        AdminRole role = new AdminRole();
        role.setRoleCode("MANAGER");
        role.setRoleName("MANAGER");
        role.setStatus("ACTIVE");
        adminRoleService.save(role);

        String[] permCodes = {
                "booking:booking:read", "booking:booking:confirm", "booking:booking:reject",
                "booking:booking:start", "booking:booking:complete", "booking:booking:cancel",
                "booking:booking:reassign"
        };
        for (String code : permCodes) {
            AdminPermission perm = new AdminPermission();
            perm.setPermissionCode(code);
            perm.setPermissionName(code);
            perm.setModule("booking");
            perm.setStatus("ACTIVE");
            adminPermissionService.save(perm);

            AdminRolePermission rp = new AdminRolePermission();
            rp.setRoleId(role.getId());
            rp.setPermissionId(perm.getId());
            adminRolePermissionService.save(rp);
        }
    }
}
