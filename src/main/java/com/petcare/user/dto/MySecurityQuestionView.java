package com.petcare.user.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.petcare.common.serialization.SnowflakeIdSerializer;

/**
 * 当前用户已设置的安全问题视图（仅含题目，不回答案哈希）。
 * 用于"我的-密保管理"页面展示。
 */
public record MySecurityQuestionView(
        @JsonSerialize(using = SnowflakeIdSerializer.class) Long id,
        String question,
        Integer sort
) {}
