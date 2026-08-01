package com.petcare.user.controller;

import com.petcare.common.api.ApiResponse;
import com.petcare.user.auth.UserAuthService;
import com.petcare.user.auth.WechatLoginApplicationService;
import com.petcare.user.dto.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * User authentication endpoints.
 * Public endpoints: register, login, forgot-password, wechat-login placeholder.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class UserAuthController {

    private final WechatLoginApplicationService wechatLoginApplicationService;
    private final UserAuthService userAuthService;

    public UserAuthController(WechatLoginApplicationService wechatLoginApplicationService,
                              UserAuthService userAuthService) {
        this.wechatLoginApplicationService = wechatLoginApplicationService;
        this.userAuthService = userAuthService;
    }

    /**
     * WeChat login. Public endpoint (no auth required).
     * Behaviour depends on {@code petcare.wechat.mode}:
     * {@code disabled} returns 422 wechat_login_not_enabled,
     * {@code mock} derives a deterministic openid for dev/tests,
     * {@code real} calls the WeChat jscode2session API.
     */
    @PostMapping("/wechat-login")
    public ApiResponse<WechatLoginResponse> wechatLogin(@Valid @RequestBody WechatLoginRequest request) {
        WechatLoginResponse response = wechatLoginApplicationService.login(request.code());
        return ApiResponse.ok(response);
    }

    /**
     * Returns the preset security questions for the registration form.
     * Public endpoint.
     */
    @GetMapping("/security-questions")
    public ApiResponse<List<String>> getPresetSecurityQuestions() {
        return ApiResponse.ok(PresetSecurityQuestions.QUESTIONS);
    }

    /**
     * Register a new user with phone + password + security questions.
     * Public endpoint.
     */
    @PostMapping("/register")
    public ApiResponse<PasswordLoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        PasswordLoginResponse response = userAuthService.register(request);
        return ApiResponse.ok(response);
    }

    /**
     * Login with phone + password. Public endpoint.
     */
    @PostMapping("/login")
    public ApiResponse<PasswordLoginResponse> login(@Valid @RequestBody PasswordLoginRequest request) {
        PasswordLoginResponse response = userAuthService.login(request);
        return ApiResponse.ok(response);
    }

    /**
     * Step 1 of forgot-password: get security questions for a phone number.
     * Public endpoint. Never returns answers.
     */
    @PostMapping("/forgot-password/questions")
    public ApiResponse<List<SecurityQuestionView>> getSecurityQuestions(
            @Valid @RequestBody ForgotPasswordQuestionsRequest request) {
        List<SecurityQuestionView> questions = userAuthService.getSecurityQuestions(request);
        return ApiResponse.ok(questions);
    }

    /**
     * Step 2 of forgot-password: answer questions and reset password.
     * Public endpoint.
     */
    @PostMapping("/forgot-password/reset")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        userAuthService.resetPassword(request);
        return ApiResponse.ok(null);
    }
}
