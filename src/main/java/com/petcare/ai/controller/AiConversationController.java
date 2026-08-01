package com.petcare.ai.controller;

import com.petcare.ai.dto.*;
import com.petcare.ai.service.AiConversationApplicationService;
import com.petcare.common.api.ApiResponse;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import com.petcare.common.pagination.PageResponse;
import com.petcare.common.security.SecurityContextHelper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * User-facing AI conversation endpoints.
 * User identity comes from security context, never from request body.
 */
@RestController
@RequestMapping("/api/v1/ai/conversations")
public class AiConversationController {

    private final AiConversationApplicationService conversationService;

    public AiConversationController(AiConversationApplicationService conversationService) {
        this.conversationService = conversationService;
    }

    /**
     * Create a new AI conversation.
     * Only CUSTOMER_SERVICE and PET_CHAT types are allowed.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<AiConversationResponse>> createConversation(
            @Valid @RequestBody AiConversationCreateRequest request) {
        Long currentUserId = resolveCurrentUserId();
        AiConversationResponse response = conversationService.createConversation(currentUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    /**
     * List current user's conversations, paginated.
     */
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<PageResponse<AiConversationResponse>>> getMyConversations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long currentUserId = resolveCurrentUserId();
        PageResponse<AiConversationResponse> response = conversationService.getMyConversations(currentUserId, page, size);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Get conversation detail. Ownership is verified.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AiConversationResponse>> getConversation(@PathVariable Long id) {
        Long currentUserId = resolveCurrentUserId();
        AiConversationResponse response = conversationService.getConversation(currentUserId, id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Send a message in a conversation and get AI response.
     * Routes to customer service or pet chat based on conversation type.
     */
    @PostMapping("/{id}/messages")
    public ResponseEntity<ApiResponse<AiMessageResponse>> sendMessage(
            @PathVariable Long id,
            @Valid @RequestBody AiMessageCreateRequest request) {
        Long currentUserId = resolveCurrentUserId();
        AiMessageResponse response = conversationService.sendMessage(currentUserId, id, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * List messages in a conversation. Ownership is verified.
     */
    @GetMapping("/{id}/messages")
    public ResponseEntity<ApiResponse<PageResponse<AiMessageResponse>>> getMessages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long currentUserId = resolveCurrentUserId();
        PageResponse<AiMessageResponse> response = conversationService.getMessages(currentUserId, id, page, size);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Resolves current user ID from the security context.
     * Returns 401 if no user identity is available (anonymous or admin token).
     * D-004 修订（2026-07-21）：用户端 AI 客服已激活，依赖已实现的 User JWT。
     */
    private Long resolveCurrentUserId() {
        return SecurityContextHelper.getCurrentUserId()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录"));
    }
}
