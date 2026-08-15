package com.petcare.user.controller;

import com.petcare.common.api.ApiResponse;
import com.petcare.common.security.AuthCookieService;
import com.petcare.user.auth.UserAuthService;
import com.petcare.user.auth.WechatLoginApplicationService;
import com.petcare.user.dto.*;
import jakarta.servlet.http.HttpServletResponse;
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
 * 双轨（2026-08-15）：登录/注册/微信登录同时 Set-Cookie（HttpOnly）与 body 返回
 * accessToken（小程序通道保留 header）；logout 服务端清 cookie。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class UserAuthController {

    private final WechatLoginApplicationService wechatLoginApplicationService;
    private final UserAuthService userAuthService;
    private final AuthCookieService authCookieService;

    public UserAuthController(WechatLoginApplicationService wechatLoginApplicationService,
                              UserAuthService userAuthService,
                              AuthCookieService authCookieService) {
        this.wechatLoginApplicationService = wechatLoginApplicationService;
        this.userAuthService = userAuthService;
        this.authCookieService = authCookieService;
    }

    /**
     * WeChat login. Public endpoint (no auth required).
     * Behaviour depends on {@code petcare.wechat.mode}:
     * {@code disabled} returns 422 wechat_login_not_enabled,
     * {@code mock} derives a deterministic openid for dev/tests,
     * {@code real} calls the WeChat jscode2session API.
     */
    @PostMapping("/wechat-login")
    public ApiResponse<WechatLoginResponse> wechatLogin(@Valid @RequestBody WechatLoginRequest request,
                                                        HttpServletResponse response) {
        WechatLoginResponse body = wechatLoginApplicationService.login(request.code());
        authCookieService.writeUserCookie(response, body.accessToken());
        return ApiResponse.ok(body);
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
    public ApiResponse<PasswordLoginResponse> register(@Valid @RequestBody RegisterRequest request,
                                                       HttpServletResponse response) {
        PasswordLoginResponse body = userAuthService.register(request);
        authCookieService.writeUserCookie(response, body.accessToken());
        return ApiResponse.ok(body);
    }

    /**
     * Login with phone + password. Public endpoint.
     */
    @PostMapping("/login")
    public ApiResponse<PasswordLoginResponse> login(@Valid @RequestBody PasswordLoginRequest request,
                                                    HttpServletResponse response) {
        PasswordLoginResponse body = userAuthService.login(request);
        authCookieService.writeUserCookie(response, body.accessToken());
        return ApiResponse.ok(body);
    }

    /**
     * Logout: clears the HttpOnly user cookie (public——token 已过期时也要能清干净).
     * JWT 无服务端状态，token 本身按过期时间自然失效.
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletResponse response) {
        authCookieService.clearUserCookie(response);
        return ApiResponse.ok(null);
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
