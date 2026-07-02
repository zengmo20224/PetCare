package com.petcare.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * "我的-修改密码"请求：通过旧密码验证身份后设置新密码。
 * 另一条路径（忘记密码→密保重置）见 {@link ResetPasswordRequest}。
 */
public record ChangePasswordRequest(
        @NotBlank(message = "原密码不能为空")
        String oldPassword,

        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 32, message = "密码长度 8-32 位")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "密码必须包含数字和字母")
        String newPassword
) {}
