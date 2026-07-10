package com.petcare.moderation.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petcare.common.api.ApiResponse;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import com.petcare.common.pagination.PageResponse;
import com.petcare.moderation.dto.SensitiveWordCreateRequest;
import com.petcare.moderation.dto.SensitiveWordResponse;
import com.petcare.moderation.entity.SensitiveWord;
import com.petcare.moderation.mapper.SensitiveWordMapper;
import com.petcare.moderation.service.ContentModerationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin sensitive word management endpoints.
 * Requires community:sensitive-word:manage permission.
 */
@RestController
@RequestMapping("/api/v1/admin/moderation/sensitive-words")
public class AdminSensitiveWordController {

    private final SensitiveWordMapper sensitiveWordMapper;
    private final ContentModerationService contentModerationService;

    public AdminSensitiveWordController(SensitiveWordMapper sensitiveWordMapper,
                                        ContentModerationService contentModerationService) {
        this.sensitiveWordMapper = sensitiveWordMapper;
        this.contentModerationService = contentModerationService;
    }

    /**
     * List sensitive words with optional status filter.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('community:sensitive-word:manage')")
    public ResponseEntity<ApiResponse<PageResponse<SensitiveWordResponse>>> listSensitiveWords(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        LambdaQueryWrapper<SensitiveWord> wrapper = new LambdaQueryWrapper<SensitiveWord>()
                .eq(SensitiveWord::getDeleted, 0);
        if (status != null && !status.isEmpty()) {
            wrapper.eq(SensitiveWord::getStatus, status);
        }
        wrapper.orderByDesc(SensitiveWord::getCreateTime);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SensitiveWord> pageResult =
                sensitiveWordMapper.selectPage(
                        new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size),
                        wrapper);

        var items = pageResult.getRecords().stream()
                .map(sw -> new SensitiveWordResponse(
                        sw.getId(), sw.getWord(), sw.getCategory(), sw.getLevel(),
                        sw.getStatus(), sw.getCreateTime()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(
                PageResponse.of(items, pageResult.getTotal(), page, size)));
    }

    /**
     * Create a new sensitive word.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('community:sensitive-word:manage')")
    public ResponseEntity<ApiResponse<SensitiveWordResponse>> createSensitiveWord(
            @Valid @RequestBody SensitiveWordCreateRequest request) {
        // Check for duplicate active word
        boolean exists = sensitiveWordMapper.exists(
                new LambdaQueryWrapper<SensitiveWord>()
                        .eq(SensitiveWord::getWord, request.word())
                        .eq(SensitiveWord::getStatus, "ACTIVE")
                        .eq(SensitiveWord::getDeleted, 0)
        );
        if (exists) {
            throw new BusinessException(ErrorCode.COMMUNITY_SENSITIVE_WORD_DUPLICATE,
                    "该敏感词已存在");
        }

        SensitiveWord word = new SensitiveWord();
        word.setWord(request.word());
        word.setCategory(request.category());
        word.setLevel(request.level());
        word.setStatus("ACTIVE");
        sensitiveWordMapper.insert(word);
        contentModerationService.evictSensitiveWordCache();

        SensitiveWordResponse response = new SensitiveWordResponse(
                word.getId(), word.getWord(), word.getCategory(), word.getLevel(),
                word.getStatus(), word.getCreateTime());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    /**
     * Partially update a sensitive word (e.g., level, category).
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('community:sensitive-word:manage')")
    public ResponseEntity<ApiResponse<SensitiveWordResponse>> updateSensitiveWord(
            @PathVariable Long id,
            @RequestBody SensitiveWordCreateRequest request) {
        SensitiveWord word = sensitiveWordMapper.selectOne(
                new LambdaQueryWrapper<SensitiveWord>()
                        .eq(SensitiveWord::getId, id)
                        .eq(SensitiveWord::getDeleted, 0)
        );
        if (word == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "敏感词不存在");
        }

        if (request.word() != null) {
            word.setWord(request.word());
        }
        if (request.category() != null) {
            word.setCategory(request.category());
        }
        if (request.level() >= 1 && request.level() <= 3) {
            word.setLevel(request.level());
        }
        sensitiveWordMapper.updateById(word);
        contentModerationService.evictSensitiveWordCache();

        SensitiveWordResponse response = new SensitiveWordResponse(
                word.getId(), word.getWord(), word.getCategory(), word.getLevel(),
                word.getStatus(), word.getCreateTime());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Disable a sensitive word.
     */
    @PostMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('community:sensitive-word:manage')")
    public ResponseEntity<ApiResponse<Void>> disableSensitiveWord(@PathVariable Long id) {
        SensitiveWord word = sensitiveWordMapper.selectOne(
                new LambdaQueryWrapper<SensitiveWord>()
                        .eq(SensitiveWord::getId, id)
                        .eq(SensitiveWord::getDeleted, 0)
        );
        if (word == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "敏感词不存在");
        }

        if ("DISABLED".equals(word.getStatus())) {
            throw new BusinessException(ErrorCode.COMMUNITY_SENSITIVE_WORD_DUPLICATE,
                    "该敏感词已禁用");
        }

        word.setStatus("DISABLED");
        sensitiveWordMapper.updateById(word);
        contentModerationService.evictSensitiveWordCache();
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
