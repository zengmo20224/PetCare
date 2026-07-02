package com.petcare.user.service;

import com.petcare.user.dto.ChangePasswordRequest;

/**
 * 已登录用户修改密码应用服务（旧密码路径）。
 * 忘记密码路径（密保重置）见 {@link com.petcare.user.auth.UserAuthService#resetPassword}。
 */
public interface UserPasswordApplicationService {

    /**
     * 校验旧密码后设置新密码。
     *
     * @throws com.petcare.common.exception.BusinessException ErrorCode.INVALID_CREDENTIALS 旧密码错误
     */
    void changePassword(Long userId, ChangePasswordRequest request);
}
