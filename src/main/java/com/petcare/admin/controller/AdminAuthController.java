package com.petcare.admin.controller;

import com.petcare.admin.dto.AdminLoginRequest;
import com.petcare.admin.dto.AdminLoginResponse;
import com.petcare.admin.dto.AdminMeResponse;
import com.petcare.admin.security.AdminPrincipal;
import com.petcare.admin.service.AdminAuthService;
import com.petcare.common.api.ApiResponse;
import com.petcare.common.security.AuthCookieService;
import com.petcare.common.security.SecurityContextHelper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin authentication endpoints.
 * Login is public; /me requires authentication.
 * 双轨（2026-08-15）：登录同时 Set-Cookie（HttpOnly）与 body 返回 accessToken；
 * logout 服务端清 cookie（HttpOnly 前端 JS 删不掉）。
 */
@RestController
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final AuthCookieService authCookieService;

    public AdminAuthController(AdminAuthService adminAuthService,
                               AuthCookieService authCookieService) {
        this.adminAuthService = adminAuthService;
        this.authCookieService = authCookieService;
    }

    /**
     * Admin login. Public endpoint (no auth required).
     */
    @PostMapping("/login")
    public ApiResponse<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request,
                                                 HttpServletResponse response) {
        AdminLoginResponse body = adminAuthService.login(request);
        authCookieService.writeAdminCookie(response, body.accessToken());
        return ApiResponse.ok(body);
    }

    /**
     * Logout: clears the HttpOnly admin cookie (public——token 已过期时也要能清干净).
     * JWT 无服务端状态，token 本身按过期时间自然失效.
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletResponse response) {
        authCookieService.clearAdminCookie(response);
        return ApiResponse.ok(null);
    }

    /**
     * Get current admin info. Requires valid Bearer token.
     */
    @GetMapping("/me")
    public ApiResponse<AdminMeResponse> me() {
        AdminPrincipal principal = SecurityContextHelper.getAdminPrincipal()
                .orElseThrow(() -> new IllegalStateException("No authenticated admin found in security context"));
        AdminMeResponse response = adminAuthService.getCurrentAdmin(principal);
        return ApiResponse.ok(response);
    }
}
