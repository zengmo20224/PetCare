package com.petcare.booking.controller;

import com.petcare.booking.dto.BookingConfirmRequest;
import com.petcare.booking.dto.BookingReassignRequest;
import com.petcare.booking.dto.BookingRejectRequest;
import com.petcare.booking.dto.BookingResponse;
import com.petcare.booking.dto.AdminBookingCancelRequest;
import com.petcare.booking.service.BookingApplicationService;
import com.petcare.common.api.ApiResponse;
import com.petcare.common.pagination.PageResponse;
import com.petcare.common.security.SecurityContextHelper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin booking management endpoints.
 * All endpoints require authentication and specific permission codes.
 */
@RestController
@RequestMapping("/api/v1/admin/bookings")
public class AdminBookingController {

    private final BookingApplicationService bookingApplicationService;

    public AdminBookingController(BookingApplicationService bookingApplicationService) {
        this.bookingApplicationService = bookingApplicationService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('booking:booking:read')")
    public ResponseEntity<ApiResponse<PageResponse<BookingResponse>>> listBookings(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String bookingDate) {
        PageResponse<BookingResponse> response = bookingApplicationService.listAdminBookings(
                page, size, status, bookingDate);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('booking:booking:read')")
    public ResponseEntity<ApiResponse<BookingResponse>> getBooking(@PathVariable Long id) {
        BookingResponse response = bookingApplicationService.getAdminBooking(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('booking:booking:confirm')")
    public ResponseEntity<ApiResponse<BookingResponse>> confirmBooking(
            @PathVariable Long id,
            @RequestBody(required = false) BookingConfirmRequest request) {
        Long operatorId = SecurityContextHelper.getCurrentAdminId()
                .orElseThrow(() -> new IllegalStateException("No admin identity"));
        String remark = request != null ? request.merchantRemark() : null;
        BookingResponse response = bookingApplicationService.confirmBooking(id, remark, operatorId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('booking:booking:reject')")
    public ResponseEntity<ApiResponse<BookingResponse>> rejectBooking(
            @PathVariable Long id,
            @Valid @RequestBody BookingRejectRequest request) {
        Long operatorId = SecurityContextHelper.getCurrentAdminId()
                .orElseThrow(() -> new IllegalStateException("No admin identity"));
        BookingResponse response = bookingApplicationService.rejectBooking(id, request.reason(), operatorId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('booking:booking:start')")
    public ResponseEntity<ApiResponse<BookingResponse>> startBooking(@PathVariable Long id) {
        Long operatorId = SecurityContextHelper.getCurrentAdminId()
                .orElseThrow(() -> new IllegalStateException("No admin identity"));
        BookingResponse response = bookingApplicationService.startBooking(id, operatorId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('booking:booking:complete')")
    public ResponseEntity<ApiResponse<BookingResponse>> completeBooking(@PathVariable Long id) {
        Long operatorId = SecurityContextHelper.getCurrentAdminId()
                .orElseThrow(() -> new IllegalStateException("No admin identity"));
        BookingResponse response = bookingApplicationService.completeBooking(id, operatorId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('booking:booking:cancel')")
    public ResponseEntity<ApiResponse<BookingResponse>> cancelBooking(
            @PathVariable Long id,
            @Valid @RequestBody AdminBookingCancelRequest request) {
        Long operatorId = SecurityContextHelper.getCurrentAdminId()
                .orElseThrow(() -> new IllegalStateException("No admin identity"));
        BookingResponse response = bookingApplicationService.cancelBookingAdmin(id, request.reason(), operatorId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/reassign")
    @PreAuthorize("hasAuthority('booking:booking:reassign')")
    public ResponseEntity<ApiResponse<BookingResponse>> reassignBooking(
            @PathVariable Long id,
            @Valid @RequestBody BookingReassignRequest request) {
        Long operatorId = SecurityContextHelper.getCurrentAdminId()
                .orElseThrow(() -> new IllegalStateException("No admin identity"));
        BookingResponse response = bookingApplicationService.reassignBooking(id, request, operatorId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
