package com.petcare.moderation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a sensitive word.
 */
public record SensitiveWordCreateRequest(
        @NotBlank(message = "敏感词不能为空")
        @Size(min = 1, max = 100, message = "敏感词长度需要在1到100之间")
        String word,
        @Size(max = 32, message = "分类长度不能超过 32")
        String category,
        @Min(value = 1, message = "风险等级最低为1")
        @Max(value = 3, message = "风险等级最高为3")
        int level
) {
}
