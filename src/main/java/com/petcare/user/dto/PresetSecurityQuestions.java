package com.petcare.user.dto;

import java.util.List;

/**
 * Preset security questions for password recovery.
 * Users must choose from this list — they cannot create custom questions.
 */
public final class PresetSecurityQuestions {

    private PresetSecurityQuestions() {}

    public static final List<String> QUESTIONS = List.of(
            "你的宠物叫什么名字？",
            "你最喜欢的食物是什么？",
            "你的家乡在哪里？",
            "你母亲的名字是什么？",
            "你就读的第一所学校叫什么？",
            "你最好的朋友叫什么名字？",
            "你最喜欢的电影是什么？",
            "你的宠物是什么品种？"
    );

    /**
     * M2 防用户枚举：未注册手机号查询安全问题时返回的占位响应。
     * 取前 2 个预设问题，id 用负数占位（不可能命中真实记录，
     * 后续 resetPassword 时 getById 返回 null → SECURITY_ANSWER_INCORRECT）。
     * 结构与注册用户返回的 {@code List<SecurityQuestionView>} 完全一致，
     * 攻击者无法通过响应差异判断账号是否存在。
     */
    public static final List<SecurityQuestionView> PLACEHOLDER_VIEWS = List.of(
            new SecurityQuestionView(-1L, QUESTIONS.get(0)),
            new SecurityQuestionView(-2L, QUESTIONS.get(1))
    );

    public static boolean isValid(String question) {
        return QUESTIONS.contains(question);
    }
}
