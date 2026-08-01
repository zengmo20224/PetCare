package com.petcare.notification.controller;

import com.petcare.common.api.ApiResponse;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import com.petcare.common.pagination.PageResponse;
import com.petcare.notification.dto.AdminAnnouncementRequest;
import com.petcare.notification.dto.AdminAnnouncementResponse;
import com.petcare.notification.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin announcement management endpoints.
 * All endpoints require admin authentication.
 */
@RestController
@RequestMapping("/api/v1/admin/announcements")
public class AdminAnnouncementController {

    private final NotificationService notificationService;

    public AdminAnnouncementController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:config') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<AdminAnnouncementResponse>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        PageResponse<AdminAnnouncementResponse> result = notificationService.adminListAnnouncements(page, size, status);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:config') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AdminAnnouncementResponse>> create(
            @Valid @RequestBody AdminAnnouncementRequest request) {
        AdminAnnouncementResponse result = notificationService.createAnnouncement(
                request.title(), request.content(), request.status(), request.sort());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:config') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AdminAnnouncementResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody AdminAnnouncementRequest request) {
        AdminAnnouncementResponse result = notificationService.updateAnnouncement(
                id, request.title(), request.content(), request.status(), request.sort());
        if (result == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "公告不存在");
        }
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:config') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        notificationService.deleteAnnouncement(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
