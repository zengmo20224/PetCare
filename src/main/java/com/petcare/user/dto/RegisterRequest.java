package com.petcare.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * User registration request with phone, password, and security questions.
 * Security questions are chosen from preset list by index (0-based).
 */
public record RegisterRequest(
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 32, message = "密码长度 8-32 位")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "密码必须包含数字和字母")
        String password,

        @NotBlank(message = "昵称不能为空")
        @Size(max = 64, message = "昵称最长 64 字符")
        String nickname,

        @Valid
        List<SecurityQuestionItem> securityQuestions
) {
    public record SecurityQuestionItem(
            @NotNull(message = "安全问题不能为空")
            @Min(value = 0, message = "安全问题选择无效")
            Integer questionIndex,

            @NotBlank(message = "安全答案不能为空")
            String answer
    ) {}
}
