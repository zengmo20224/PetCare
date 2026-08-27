package com.petcare.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import com.petcare.user.dto.ChangePasswordRequest;
import com.petcare.user.entity.User;
import com.petcare.user.service.UserPasswordApplicationService;
import com.petcare.user.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserPasswordApplicationServiceImpl implements UserPasswordApplicationService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final com.petcare.common.security.JwtRevocationRegistry revocationRegistry;

    public UserPasswordApplicationServiceImpl(UserService userService,
                                              PasswordEncoder passwordEncoder,
                                              com.petcare.common.security.JwtRevocationRegistry revocationRegistry) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.revocationRegistry = revocationRegistry;
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userService.getOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getId, userId)
                        .eq(User::getStatus, "ACTIVE")
        );
        if (user == null || user.getPasswordHash() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在或已禁用");
        }

        // 校验旧密码
        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "原密码不正确");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userService.updateById(user);

        // P2（2026-08-23）：改密后撤销该用户全部已签发 token（旧 JWT 立即失效）
        revocationRegistry.revokeUser(userId);
    }
}
