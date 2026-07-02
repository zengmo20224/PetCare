package com.petcare.user.controller;

import com.petcare.common.api.ApiResponse;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import com.petcare.common.security.SecurityContextHelper;
import com.petcare.user.dto.ChangePasswordRequest;
import com.petcare.user.dto.MySecurityQuestionView;
import com.petcare.user.dto.UpdateSecurityQuestionsRequest;
import com.petcare.user.dto.UpdateUserProfileRequest;
import com.petcare.user.dto.UserProfileResponse;
import com.petcare.user.service.UserPasswordApplicationService;
import com.petcare.user.service.UserProfileService;
import com.petcare.user.service.UserSecurityQuestionApplicationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for current user profile operations.
 * All endpoints require ROLE_USER explicitly — ADMIN tokens get 403.
 * Current user ID is always derived from SecurityContext, never from request parameters.
 */
@RestController
@RequestMapping("/api/v1/user")
@PreAuthorize("hasRole('USER')")
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final UserSecurityQuestionApplicationService securityQuestionService;
    private final UserPasswordApplicationService passwordService;

    public UserProfileController(UserProfileService userProfileService,
                                 UserSecurityQuestionApplicationService securityQuestionService,
                                 UserPasswordApplicationService passwordService) {
        this.userProfileService = userProfileService;
        this.securityQuestionService = securityQuestionService;
        this.passwordService = passwordService;
    }

    @GetMapping("/profile")
    public ApiResponse<UserProfileResponse> getCurrentProfile() {
        Long currentUserId = requireCurrentUserId();
        UserProfileResponse profile = userProfileService.getCurrentProfile(currentUserId);
        return ApiResponse.ok(profile);
    }

    @PutMapping("/profile")
    public ApiResponse<UserProfileResponse> updateCurrentProfile(
            @Valid @RequestBody UpdateUserProfileRequest request) {
        Long currentUserId = requireCurrentUserId();
        UserProfileResponse updated = userProfileService.updateCurrentProfile(currentUserId, request);
        return ApiResponse.ok(updated);
    }

    /**
     * 查看当前用户已设置的安全问题（仅题目，不回答案）。
     */
    @GetMapping("/security-questions")
    public ApiResponse<List<MySecurityQuestionView>> getMySecurityQuestions() {
        Long currentUserId = requireCurrentUserId();
        List<MySecurityQuestionView> questions = securityQuestionService.getMyQuestions(currentUserId);
        return ApiResponse.ok(questions);
    }

    /**
     * 整体更新当前用户的安全问题（强制 2 个有效密保，事务覆盖）。
     */
    @PutMapping("/security-questions")
    public ApiResponse<Void> updateMySecurityQuestions(
            @Valid @RequestBody UpdateSecurityQuestionsRequest request) {
        Long currentUserId = requireCurrentUserId();
        securityQuestionService.updateMyQuestions(currentUserId, request);
        return ApiResponse.ok(null);
    }

    /**
     * 修改密码（旧密码路径）。密保重置路径见 /api/v1/auth/forgot-password/reset。
     */
    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Long currentUserId = requireCurrentUserId();
        passwordService.changePassword(currentUserId, request);
        return ApiResponse.ok(null);
    }

    private Long requireCurrentUserId() {
        return SecurityContextHelper.getCurrentUserId()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录"));
    }
}
