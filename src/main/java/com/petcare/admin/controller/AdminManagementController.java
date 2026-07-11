package com.petcare.admin.controller;

import com.petcare.admin.dto.AdminManagementDtos.OperationLogView;
import com.petcare.admin.dto.AdminManagementDtos.ProductCarouselImageView;
import com.petcare.admin.dto.AdminManagementDtos.ProductCarouselImagesUpdateRequest;
import com.petcare.admin.dto.AdminManagementDtos.ProductRequest;
import com.petcare.admin.dto.AdminManagementDtos.ProductStockUpdateRequest;
import com.petcare.admin.dto.AdminManagementDtos.ProductView;
import com.petcare.admin.dto.AdminManagementDtos.ServiceItemRequest;
import com.petcare.admin.dto.AdminManagementDtos.ServiceItemView;
import com.petcare.admin.dto.AdminManagementDtos.StaffRequest;
import com.petcare.admin.dto.AdminManagementDtos.StaffScheduleRequest;
import com.petcare.admin.dto.AdminManagementDtos.StaffScheduleView;
import com.petcare.admin.dto.AdminManagementDtos.StaffSkillUpdateRequest;
import com.petcare.admin.dto.AdminManagementDtos.StaffSkillView;
import com.petcare.admin.dto.AdminManagementDtos.StaffView;
import com.petcare.admin.dto.AdminManagementDtos.StoreConfigUpdateRequest;
import com.petcare.admin.dto.AdminManagementDtos.StoreConfigView;
import com.petcare.admin.dto.AdminManagementDtos.StoreUpdateRequest;
import com.petcare.admin.dto.AdminManagementDtos.StoreView;
import com.petcare.admin.dto.AdminManagementDtos.UserBanRequest;
import com.petcare.admin.dto.AdminManagementDtos.UserBanResult;
import com.petcare.admin.dto.AdminManagementDtos.UserView;
import com.petcare.admin.service.AdminManagementService;
import com.petcare.booking.dto.BookingResponse;
import com.petcare.common.api.ApiResponse;
import com.petcare.common.pagination.PageResponse;
import com.petcare.product.dto.ProductOrderResponse;
import com.petcare.common.security.SecurityContextHelper;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminManagementController {

    private final AdminManagementService service;

    public AdminManagementController(AdminManagementService service) {
        this.service = service;
    }

    @GetMapping("/stores/{id}")
    @PreAuthorize("hasAuthority('store:info:read')")
    public ResponseEntity<ApiResponse<StoreView>> getStore(@PathVariable Long id) {
        return ok(service.getStore(id));
    }

    @PatchMapping("/stores/{id}")
    @PreAuthorize("hasAuthority('store:info:update')")
    public ResponseEntity<ApiResponse<StoreView>> updateStore(
            @PathVariable Long id, @Valid @RequestBody StoreUpdateRequest request) {
        return ok(service.updateStore(id, request, operatorId()));
    }

    @GetMapping("/stores/{id}/config")
    @PreAuthorize("hasAuthority('store:config:read')")
    public ResponseEntity<ApiResponse<StoreConfigView>> getStoreConfig(@PathVariable Long id) {
        return ok(service.getStoreConfig(id));
    }

    @PutMapping("/stores/{id}/config")
    @PreAuthorize("hasAuthority('store:config:update')")
    public ResponseEntity<ApiResponse<StoreConfigView>> updateStoreConfig(
            @PathVariable Long id, @Valid @RequestBody StoreConfigUpdateRequest request) {
        return ok(service.updateStoreConfig(id, request, operatorId()));
    }

    @GetMapping("/service-items")
    @PreAuthorize("hasAuthority('service:item:read')")
    public ResponseEntity<ApiResponse<PageResponse<ServiceItemView>>> listServiceItems(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return ok(service.listServiceItems(page, size, status));
    }

    @PostMapping("/service-items")
    @PreAuthorize("hasAuthority('service:item:create')")
    public ResponseEntity<ApiResponse<ServiceItemView>> createServiceItem(
            @Valid @RequestBody ServiceItemRequest request) {
        return ok(service.createServiceItem(request, operatorId()));
    }

    @PutMapping("/service-items/{id}")
    @PreAuthorize("hasAuthority('service:item:update')")
    public ResponseEntity<ApiResponse<ServiceItemView>> updateServiceItem(
            @PathVariable Long id, @Valid @RequestBody ServiceItemRequest request) {
        return ok(service.updateServiceItem(id, request, operatorId()));
    }

    @PostMapping("/service-items/{id}/disable")
    @PreAuthorize("hasAuthority('service:item:disable')")
    public ResponseEntity<ApiResponse<ServiceItemView>> disableServiceItem(@PathVariable Long id) {
        return ok(service.disableServiceItem(id, operatorId()));
    }

    @GetMapping("/staff")
    @PreAuthorize("hasAuthority('staff:profile:read')")
    public ResponseEntity<ApiResponse<PageResponse<StaffView>>> listStaff(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return ok(service.listStaff(page, size, status));
    }

    @PostMapping("/staff")
    @PreAuthorize("hasAuthority('staff:profile:create')")
    public ResponseEntity<ApiResponse<StaffView>> createStaff(@Valid @RequestBody StaffRequest request) {
        return ok(service.createStaff(request, operatorId()));
    }

    @PutMapping("/staff/{id}")
    @PreAuthorize("hasAuthority('staff:profile:update')")
    public ResponseEntity<ApiResponse<StaffView>> updateStaff(
            @PathVariable Long id, @Valid @RequestBody StaffRequest request) {
        return ok(service.updateStaff(id, request, operatorId()));
    }

    @PostMapping("/staff/{id}/disable")
    @PreAuthorize("hasAuthority('staff:profile:disable')")
    public ResponseEntity<ApiResponse<StaffView>> disableStaff(@PathVariable Long id) {
        return ok(service.disableStaff(id, operatorId()));
    }

    @PostMapping("/staff/{id}/enable")
    @PreAuthorize("hasAuthority('staff:profile:enable')")
    public ResponseEntity<ApiResponse<StaffView>> enableStaff(@PathVariable Long id) {
        return ok(service.enableStaff(id, operatorId()));
    }

    @PutMapping("/staff/{id}/skills")
    @PreAuthorize("hasAuthority('staff:skill:manage')")
    public ResponseEntity<ApiResponse<StaffSkillView>> replaceStaffSkills(
            @PathVariable Long id, @Valid @RequestBody StaffSkillUpdateRequest request) {
        return ok(service.replaceStaffSkills(id, request.serviceCategoryIds(), operatorId()));
    }

    @GetMapping("/staff/{id}/skills")
    @PreAuthorize("hasAuthority('staff:skill:manage')")
    public ResponseEntity<ApiResponse<StaffSkillView>> getStaffSkills(@PathVariable Long id) {
        return ok(service.getStaffSkills(id));
    }

    @GetMapping("/staff/{id}/schedules")
    @PreAuthorize("hasAuthority('staff:schedule:read')")
    public ResponseEntity<ApiResponse<PageResponse<StaffScheduleView>>> listSchedules(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ok(service.listSchedules(id, page, size));
    }

    @PostMapping("/staff/{id}/schedules")
    @PreAuthorize("hasAuthority('staff:schedule:manage')")
    public ResponseEntity<ApiResponse<StaffScheduleView>> createSchedule(
            @PathVariable Long id, @Valid @RequestBody StaffScheduleRequest request) {
        return ok(service.createSchedule(id, request, operatorId()));
    }

    @PutMapping("/staff/{id}/schedules/{scheduleId}")
    @PreAuthorize("hasAuthority('staff:schedule:manage')")
    public ResponseEntity<ApiResponse<StaffScheduleView>> updateSchedule(
            @PathVariable Long id, @PathVariable Long scheduleId,
            @Valid @RequestBody StaffScheduleRequest request) {
        return ok(service.updateSchedule(id, scheduleId, request, operatorId()));
    }

    @GetMapping("/products")
    @PreAuthorize("hasAuthority('product:item:read')")
    public ResponseEntity<ApiResponse<PageResponse<ProductView>>> listProducts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return ok(service.listProducts(page, size, status));
    }

    @PostMapping("/products")
    @PreAuthorize("hasAuthority('product:item:create')")
    public ResponseEntity<ApiResponse<ProductView>> createProduct(@Valid @RequestBody ProductRequest request) {
        return ok(service.createProduct(request, operatorId()));
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasAuthority('product:item:update')")
    public ResponseEntity<ApiResponse<ProductView>> updateProduct(
            @PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return ok(service.updateProduct(id, request, operatorId()));
    }

    @PostMapping("/products/{id}/disable")
    @PreAuthorize("hasAuthority('product:item:disable')")
    public ResponseEntity<ApiResponse<ProductView>> disableProduct(@PathVariable Long id) {
        return ok(service.disableProduct(id, operatorId()));
    }

    @PutMapping("/products/{id}/stock")
    @PreAuthorize("hasAuthority('product:stock:update')")
    public ResponseEntity<ApiResponse<ProductView>> updateProductStock(
            @PathVariable Long id, @Valid @RequestBody ProductStockUpdateRequest request) {
        return ok(service.updateProductStock(id, request.stock(), operatorId()));
    }

    @GetMapping("/product-carousel-images")
    @PreAuthorize("hasAuthority('product:item:read')")
    public ResponseEntity<ApiResponse<List<ProductCarouselImageView>>> listProductCarouselImages() {
        return ok(service.listProductCarouselImages());
    }

    @PutMapping("/product-carousel-images")
    @PreAuthorize("hasAuthority('product:item:update')")
    public ResponseEntity<ApiResponse<List<ProductCarouselImageView>>> replaceProductCarouselImages(
            @Valid @RequestBody ProductCarouselImagesUpdateRequest request) {
        return ok(service.replaceProductCarouselImages(request, operatorId()));
    }

    @GetMapping("/operation-logs")
    @PreAuthorize("hasAuthority('admin:operation-log:read')")
    public ResponseEntity<ApiResponse<PageResponse<OperationLogView>>> listOperationLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String module) {
        return ok(service.listOperationLogs(page, size, module));
    }

    // ─── User management ───

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('user:profile:read')")
    public ResponseEntity<ApiResponse<PageResponse<UserView>>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ok(service.listUsers(page, size, status, keyword));
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasAuthority('user:profile:read')")
    public ResponseEntity<ApiResponse<UserView>> getUser(@PathVariable Long id) {
        return ok(service.getUser(id));
    }

    @PostMapping("/users/{id}/ban")
    @PreAuthorize("hasAuthority('user:profile:ban')")
    public ResponseEntity<ApiResponse<UserBanResult>> banUser(
            @PathVariable Long id,
            @RequestBody(required = false) UserBanRequest request) {
        String reason = request != null ? request.reason() : null;
        return ok(service.banUser(id, reason, operatorId()));
    }

    @PostMapping("/users/{id}/unban")
    @PreAuthorize("hasAuthority('user:profile:ban')")
    public ResponseEntity<ApiResponse<UserView>> unbanUser(@PathVariable Long id) {
        return ok(service.unbanUser(id, operatorId()));
    }

    @GetMapping("/users/{id}/bookings")
    @PreAuthorize("hasAuthority('user:profile:read')")
    public ResponseEntity<ApiResponse<PageResponse<BookingResponse>>> listUserBookings(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ok(service.listUserBookings(id, page, size));
    }

    @GetMapping("/users/{id}/orders")
    @PreAuthorize("hasAuthority('user:profile:read')")
    public ResponseEntity<ApiResponse<PageResponse<ProductOrderResponse>>> listUserOrders(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ok(service.listUserOrders(id, page, size));
    }

    private Long operatorId() {
        return SecurityContextHelper.getCurrentAdminId()
                .orElseThrow(() -> new IllegalStateException("No admin identity"));
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(ApiResponse.ok(data));
    }
}
