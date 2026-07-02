package com.petcare.user.service;

import com.petcare.user.dto.MySecurityQuestionView;
import com.petcare.user.dto.UpdateSecurityQuestionsRequest;

import java.util.List;

/**
 * 已登录用户的安全问题应用服务。
 * 区别于 {@link com.petcare.user.auth.UserAuthService} 中的忘记密码流程，
 * 这里处理的是"已登录用户查看/更新自己的密保"。
 */
public interface UserSecurityQuestionApplicationService {

    /**
     * 查看当前用户已设置的安全问题（按 sort 排序，仅含题目，不回答案）。
     */
    List<MySecurityQuestionView> getMyQuestions(Long userId);

    /**
     * 整体更新当前用户的安全问题。服务端强制恰好 2 个有效密保才落库。
     * 归一化与加密规则与注册/忘记密码保持一致（answer.trim().toLowerCase() + BCrypt）。
     */
    void updateMyQuestions(Long userId, UpdateSecurityQuestionsRequest request);
}
