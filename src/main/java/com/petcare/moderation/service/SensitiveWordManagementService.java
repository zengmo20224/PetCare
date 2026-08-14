package com.petcare.moderation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import com.petcare.common.pagination.PageResponse;
import com.petcare.moderation.dto.SensitiveWordCreateRequest;
import com.petcare.moderation.dto.SensitiveWordResponse;
import com.petcare.moderation.entity.SensitiveWord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 敏感词管理应用服务（B4 修复：从 AdminSensitiveWordController 下沉）。
 *
 * <p>修复前 Controller 直接注入 {@code SensitiveWordMapper} 完成业务流程，
 * 违反 AGENTS.md §4"Controller 不得直接调用 Mapper"。本服务承载全部业务规则：</p>
 * <ul>
 *   <li>创建/改词前查重（ACTIVE 语义级校验）；</li>
 *   <li>{@code uk_word} 唯一索引兜底（migration-phase19）：并发下先查后插的竞态
 *       由 DuplicateKeyException 收敛为业务错误，不再产生重复行；</li>
 *   <li>每次变更后驱逐敏感词缓存（ContentModerationService 的 Caffeine）。</li>
 * </ul>
 */
@Service
public class SensitiveWordManagementService {

    private final SensitiveWordService sensitiveWordService;
    private final ContentModerationService contentModerationService;

    public SensitiveWordManagementService(SensitiveWordService sensitiveWordService,
                                          ContentModerationService contentModerationService) {
        this.sensitiveWordService = sensitiveWordService;
        this.contentModerationService = contentModerationService;
    }

    public PageResponse<SensitiveWordResponse> listSensitiveWords(String status, int page, int size) {
        LambdaQueryWrapper<SensitiveWord> wrapper = new LambdaQueryWrapper<SensitiveWord>()
                .eq(SensitiveWord::getDeleted, 0);
        if (status != null && !status.isEmpty()) {
            wrapper.eq(SensitiveWord::getStatus, status);
        }
        wrapper.orderByDesc(SensitiveWord::getCreateTime);

        Page<SensitiveWord> pageResult = sensitiveWordService.page(
                new Page<>(page, size), wrapper);

        var items = pageResult.getRecords().stream().map(this::toResponse).toList();
        return PageResponse.of(items, pageResult.getTotal(), page, size);
    }

    @Transactional
    public SensitiveWordResponse createSensitiveWord(SensitiveWordCreateRequest request) {
        // 语义级查重（ACTIVE 同词）
        requireWordAbsent(request.word(), null);

        SensitiveWord word = new SensitiveWord();
        word.setWord(request.word());
        word.setCategory(request.category());
        word.setLevel(request.level());
        word.setStatus("ACTIVE");
        try {
            sensitiveWordService.save(word);
        } catch (DuplicateKeyException e) {
            // uk_word 兜底：并发下先查后插的竞态在此收敛为业务错误
            throw new BusinessException(ErrorCode.COMMUNITY_SENSITIVE_WORD_DUPLICATE, "该敏感词已存在");
        }
        contentModerationService.evictSensitiveWordCache();
        return toResponse(word);
    }

    @Transactional
    public SensitiveWordResponse updateSensitiveWord(Long id, SensitiveWordCreateRequest request) {
        SensitiveWord word = requireWordExists(id);

        if (request.word() != null) {
            // 改词同样查重（排除自身）
            requireWordAbsent(request.word(), id);
            word.setWord(request.word());
        }
        if (request.category() != null) {
            word.setCategory(request.category());
        }
        if (request.level() >= 1 && request.level() <= 3) {
            word.setLevel(request.level());
        }
        try {
            sensitiveWordService.updateById(word);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.COMMUNITY_SENSITIVE_WORD_DUPLICATE, "该敏感词已存在");
        }
        contentModerationService.evictSensitiveWordCache();
        return toResponse(word);
    }

    @Transactional
    public void disableSensitiveWord(Long id) {
        SensitiveWord word = requireWordExists(id);

        if ("DISABLED".equals(word.getStatus())) {
            throw new BusinessException(ErrorCode.COMMUNITY_SENSITIVE_WORD_DUPLICATE, "该敏感词已禁用");
        }

        word.setStatus("DISABLED");
        sensitiveWordService.updateById(word);
        contentModerationService.evictSensitiveWordCache();
    }

    private SensitiveWord requireWordExists(Long id) {
        SensitiveWord word = sensitiveWordService.getOne(
                new LambdaQueryWrapper<SensitiveWord>()
                        .eq(SensitiveWord::getId, id)
                        .eq(SensitiveWord::getDeleted, 0)
        );
        if (word == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "敏感词不存在");
        }
        return word;
    }

    private void requireWordAbsent(String word, Long excludeId) {
        LambdaQueryWrapper<SensitiveWord> wrapper = new LambdaQueryWrapper<SensitiveWord>()
                .eq(SensitiveWord::getWord, word)
                .eq(SensitiveWord::getStatus, "ACTIVE")
                .eq(SensitiveWord::getDeleted, 0);
        if (excludeId != null) {
            wrapper.ne(SensitiveWord::getId, excludeId);
        }
        if (sensitiveWordService.count(wrapper) > 0) {
            throw new BusinessException(ErrorCode.COMMUNITY_SENSITIVE_WORD_DUPLICATE, "该敏感词已存在");
        }
    }

    private SensitiveWordResponse toResponse(SensitiveWord sw) {
        return new SensitiveWordResponse(
                sw.getId(), sw.getWord(), sw.getCategory(), sw.getLevel(),
                sw.getStatus(), sw.getCreateTime());
    }
}
