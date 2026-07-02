package com.petcare.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.petcare.user.dto.MySecurityQuestionView;
import com.petcare.user.dto.PresetSecurityQuestions;
import com.petcare.user.dto.UpdateSecurityQuestionsRequest;
import com.petcare.user.entity.UserSecurityQuestion;
import com.petcare.user.service.UserSecurityQuestionApplicationService;
import com.petcare.user.service.UserSecurityQuestionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserSecurityQuestionApplicationServiceImpl implements UserSecurityQuestionApplicationService {

    private final UserSecurityQuestionService securityQuestionService;
    private final PasswordEncoder passwordEncoder;

    public UserSecurityQuestionApplicationServiceImpl(
            UserSecurityQuestionService securityQuestionService,
            PasswordEncoder passwordEncoder) {
        this.securityQuestionService = securityQuestionService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public List<MySecurityQuestionView> getMyQuestions(Long userId) {
        List<UserSecurityQuestion> questions = securityQuestionService.list(
                new LambdaQueryWrapper<UserSecurityQuestion>()
                        .eq(UserSecurityQuestion::getUserId, userId)
                        .orderByAsc(UserSecurityQuestion::getSort)
        );

        return questions.stream()
                .map(q -> new MySecurityQuestionView(q.getId(), q.getQuestion(), q.getSort()))
                .toList();
    }

    /**
     * 整体覆盖更新：删旧建新。事务保证原子性。
     * 归一化规则必须与 {@code UserAuthService.register}/{@code resetPassword} 一致：
     * {@code answer.trim().toLowerCase()} 再 BCrypt，否则忘记密码校验会失败。
     */
    @Override
    @Transactional
    public void updateMyQuestions(Long userId, UpdateSecurityQuestionsRequest request) {
        List<UpdateSecurityQuestionsRequest.QuestionItem> items = request.securityQuestions();

        validateItems(items);

        // 删除该用户全部旧密保
        securityQuestionService.remove(
                new LambdaQueryWrapper<UserSecurityQuestion>()
                        .eq(UserSecurityQuestion::getUserId, userId)
        );

        // 写入新密保（sort 按 0,1 顺序）
        for (int i = 0; i < items.size(); i++) {
            UpdateSecurityQuestionsRequest.QuestionItem item = items.get(i);
            UserSecurityQuestion q = new UserSecurityQuestion();
            q.setUserId(userId);
            q.setQuestion(PresetSecurityQuestions.QUESTIONS.get(item.questionIndex()));
            // 关键：归一化规则与注册/忘记密码完全一致
            q.setAnswerHash(passwordEncoder.encode(item.answer().trim().toLowerCase()));
            q.setSort(i);
            securityQuestionService.save(q);
        }
    }

    /**
     * 服务端二次校验（DTO 注解已校验基础规则，这里校验语义）：
     * - questionIndex 在预设范围内
     * - 两个问题不能重复
     * - 两个答案不能相同（归一化后）
     */
    private void validateItems(List<UpdateSecurityQuestionsRequest.QuestionItem> items) {
        for (UpdateSecurityQuestionsRequest.QuestionItem item : items) {
            int idx = item.questionIndex();
            if (idx < 0 || idx >= PresetSecurityQuestions.QUESTIONS.size()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "安全问题选择无效");
            }
        }

        // 题目不能重复
        Set<Integer> indexes = items.stream()
                .map(UpdateSecurityQuestionsRequest.QuestionItem::questionIndex)
                .collect(Collectors.toSet());
        if (indexes.size() != items.size()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "两个安全问题不能相同");
        }

        // 答案不能相同（归一化后比较）
        Set<String> normalizedAnswers = items.stream()
                .map(i -> i.answer().trim().toLowerCase())
                .collect(Collectors.toSet());
        if (normalizedAnswers.size() != items.size()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "不同问题的答案不能相同");
        }
    }
}
