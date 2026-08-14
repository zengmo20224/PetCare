package com.petcare.ai.controller;

import com.petcare.ai.dto.PostAssistantRequest;
import com.petcare.ai.dto.PostAssistantResponse;
import com.petcare.ai.service.AiPostAssistantService;
import com.petcare.common.api.ApiResponse;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import com.petcare.common.security.SecurityContextHelper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * User-facing AI post assistant endpoint.
 * Generates suggested post drafts — never auto-publishes.
 *
 * <p>M8.3：解除 V1 的硬编码 401（D-004 时代"User JWT 未实现"的前提已过期，
 * 对齐 AiConversationController 的身份解析范式）。</p>
 */
@RestController
@RequestMapping("/api/v1/ai/post-assistant")
public class AiPostAssistantController {

    private final AiPostAssistantService postAssistantService;

    public AiPostAssistantController(AiPostAssistantService postAssistantService) {
        this.postAssistantService = postAssistantService;
    }

    /**
     * Generate a suggested post draft based on user-provided facts.
     * The generated content is a draft and must be confirmed by the user.
     */
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<PostAssistantResponse>> generateDraft(
            @Valid @RequestBody PostAssistantRequest request) {
        Long currentUserId = resolveCurrentUserId();
        PostAssistantResponse response = postAssistantService.generateDraft(currentUserId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Resolves current user ID from the security context (user JWT).
     */
    private Long resolveCurrentUserId() {
        return SecurityContextHelper.getCurrentUserId()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录"));
    }
}
