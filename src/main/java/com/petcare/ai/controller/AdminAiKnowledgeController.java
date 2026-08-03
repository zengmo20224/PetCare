package com.petcare.ai.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.petcare.ai.dto.KnowledgeRebuildResponse;
import com.petcare.ai.rag.KnowledgeIndexingService;
import com.petcare.common.api.ApiResponse;

/**
 * 管理端 AI 知识库端点（V2 AI Agent，D-013，M8.0）。
 * <p>
 * 仅当 {@link KnowledgeIndexingService} Bean 存在（即 {@code petcare.ai.rag-enabled=true}）时装配，
 * 避免未启用 RAG 时注入失败。
 */
@RestController
@RequestMapping("/api/v1/admin/ai/knowledge")
@ConditionalOnBean(KnowledgeIndexingService.class)
public class AdminAiKnowledgeController {

    private final KnowledgeIndexingService knowledgeIndexingService;

    public AdminAiKnowledgeController(KnowledgeIndexingService knowledgeIndexingService) {
        this.knowledgeIndexingService = knowledgeIndexingService;
    }

    /**
     * 手动触发全量知识库重建（FAQ/商品/服务/门店 → embed → PgVector）。
     * 需要 {@code ai:knowledge:rebuild}（7056）权限。
     */
    @PostMapping("/rebuild")
    @PreAuthorize("hasAuthority('ai:knowledge:rebuild')")
    public ResponseEntity<ApiResponse<KnowledgeRebuildResponse>> rebuild() {
        long start = System.currentTimeMillis();
        int count = knowledgeIndexingService.rebuildAll();
        long duration = System.currentTimeMillis() - start;
        return ResponseEntity.ok(ApiResponse.ok(
                new KnowledgeRebuildResponse(count, duration, "知识库重建完成")));
    }
}
