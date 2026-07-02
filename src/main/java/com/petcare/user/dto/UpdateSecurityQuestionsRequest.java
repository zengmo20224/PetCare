package com.petcare.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * "我的-密保管理"更新请求：整体覆盖该用户的全部安全问题。
 * 服务端强制恰好 2 个有效密保（题目不重复、答案非空、题目在白名单）才落库。
 */
public record UpdateSecurityQuestionsRequest(
        @Valid
        @Size(min = 2, max = 2, message = "必须设置 2 个安全问题")
        List<QuestionItem> securityQuestions
) {
    public record QuestionItem(
            @NotNull(message = "安全问题不能为空")
            @Min(value = 0, message = "安全问题选择无效")
            Integer questionIndex,

            @NotBlank(message = "安全答案不能为空")
            String answer
    ) {}
}
