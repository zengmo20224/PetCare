package com.petcare.admin.service.impl;

import com.petcare.admin.dto.AdminLoginRequest;
import com.petcare.admin.dto.AdminLoginResponse;
import com.petcare.admin.dto.AdminMeResponse;
import com.petcare.admin.entity.AdminUser;
import com.petcare.admin.security.AdminPrincipal;
import com.petcare.admin.service.AdminAuthService;
import com.petcare.admin.service.AdminUserService;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import com.petcare.common.security.JwtTokenService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Admin authentication service implementation.
 * Handles login with BCrypt password verification and JWT token issuance.
 */
@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    private final AdminUserService adminUserService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final com.petcare.common.security.LoginAttemptService loginAttemptService;

    public AdminAuthServiceImpl(AdminUserService adminUserService,
                                PasswordEncoder passwordEncoder,
                                JwtTokenService jwtTokenService,
                                com.petcare.common.security.LoginAttemptService loginAttemptService) {
        this.adminUserService = adminUserService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public AdminLoginResponse login(AdminLoginRequest request) {
        // M5：管理后台更严锁定（默认 3 次/15 分钟），凭证比对前拦截
        if (loginAttemptService.isLocked(
                com.petcare.common.security.LoginAttemptService.Channel.ADMIN, request.username())) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, "登录尝试过于频繁，请稍后再试");
        }

        // Find admin by username, only ACTIVE and not deleted
        AdminUser admin = adminUserService.getOne(
                new LambdaQueryWrapper<AdminUser>()
                        .eq(AdminUser::getUsername, request.username())
                        .eq(AdminUser::getStatus, "ACTIVE")
        );

        // Do not distinguish between "user not found" and "wrong password"
        if (admin == null) {
            loginAttemptService.recordFailure(
                    com.petcare.common.security.LoginAttemptService.Channel.ADMIN, request.username());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }

        // BCrypt password verification
        if (!passwordEncoder.matches(request.password(), admin.getPassword())) {
            loginAttemptService.recordFailure(
                    com.petcare.common.security.LoginAttemptService.Channel.ADMIN, request.username());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }

        loginAttemptService.onSuccess(
                com.petcare.common.security.LoginAttemptService.Channel.ADMIN, request.username());

        // Update last login time
        admin.setLastLoginTime(LocalDateTime.now());
        adminUserService.updateById(admin);

        // Sign JWT
        String token = jwtTokenService.signAdminToken(admin.getId(), admin.getUsername(), admin.getRole());

        return new AdminLoginResponse(
                "Bearer",
                token,
                jwtTokenService.getExpirationSeconds(),
                new AdminLoginResponse.AdminSummary(
                        admin.getId(),
                        admin.getUsername(),
                        admin.getNickname(),
                        admin.getRole()
                )
        );
    }

    @Override
    public AdminMeResponse getCurrentAdmin(AdminPrincipal principal) {
        return new AdminMeResponse(
                principal.getAdminId(),
                principal.getUsername(),
                null, // nickname not stored in token/principal
                principal.getRole(),
                principal.getPermissionCodes()
        );
    }
}
