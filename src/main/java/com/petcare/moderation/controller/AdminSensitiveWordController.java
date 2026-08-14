package com.petcare.moderation.controller;

import com.petcare.common.api.ApiResponse;
import com.petcare.common.pagination.PageResponse;
import com.petcare.moderation.dto.SensitiveWordCreateRequest;
import com.petcare.moderation.dto.SensitiveWordResponse;
import com.petcare.moderation.service.SensitiveWordManagementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin sensitive word management endpoints.
 * Requires community:sensitive-word:manage permission.
 *
 * <p>B4 修复：业务规则（查重/唯一索引兜底/缓存驱逐）全部下沉到
 * {@link SensitiveWordManagementService}，Controller 只做协议与鉴权，
 * 不再直接依赖 Mapper（AGENTS.md §4）。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/moderation/sensitive-words")
public class AdminSensitiveWordController {

    private final SensitiveWordManagementService sensitiveWordManagementService;

    public AdminSensitiveWordController(SensitiveWordManagementService sensitiveWordManagementService) {
        this.sensitiveWordManagementService = sensitiveWordManagementService;
    }

    /**
     * List sensitive words with optional status filter.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('community:sensitive-word:manage')")
    public ResponseEntity<ApiResponse<PageResponse<SensitiveWordResponse>>> listSensitiveWords(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                sensitiveWordManagementService.listSensitiveWords(status, page, size)));
    }

    /**
     * Create a new sensitive word.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('community:sensitive-word:manage')")
    public ResponseEntity<ApiResponse<SensitiveWordResponse>> createSensitiveWord(
            @Valid @RequestBody SensitiveWordCreateRequest request) {
        SensitiveWordResponse response = sensitiveWordManagementService.createSensitiveWord(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    /**
     * Partially update a sensitive word (e.g., level, category).
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('community:sensitive-word:manage')")
    public ResponseEntity<ApiResponse<SensitiveWordResponse>> updateSensitiveWord(
            @PathVariable Long id,
            @RequestBody SensitiveWordCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                sensitiveWordManagementService.updateSensitiveWord(id, request)));
    }

    /**
     * Disable a sensitive word.
     */
    @PostMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('community:sensitive-word:manage')")
    public ResponseEntity<ApiResponse<Void>> disableSensitiveWord(@PathVariable Long id) {
        sensitiveWordManagementService.disableSensitiveWord(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
