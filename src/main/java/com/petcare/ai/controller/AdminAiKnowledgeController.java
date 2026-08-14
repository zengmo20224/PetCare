package com.petcare.ai.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.petcare.admin.entity.AdminOperationLog;
import com.petcare.admin.service.AdminOperationLogService;
import com.petcare.ai.dto.KnowledgeRebuildResponse;
import com.petcare.ai.rag.KnowledgeIndexingService;
import com.petcare.common.api.ApiResponse;
import com.petcare.common.security.SecurityContextHelper;

/**
 * 管理端 AI 知识库端点（V2 AI Agent，D-013，M8.0）。
 * <p>
 * 仅当 {@link KnowledgeIndexingService} Bean 存在（即 {@code petcare.ai.rag-enabled=true}）时装配，
 * 避免未启用 RAG 时注入失败。
 * <p>
 * A7 修复：重建为管理端高危操作（全清 + 全量重 embed），照 AiAnalysisApplicationServiceImpl
 * 范式补 {@link AdminOperationLog} 审计（成功/失败都留痕）；并发触发由
 * {@link KnowledgeIndexingService} 内部互斥标志拒绝。
 */
@RestController
@RequestMapping("/api/v1/admin/ai/knowledge")
@ConditionalOnBean(KnowledgeIndexingService.class)
public class AdminAiKnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(AdminAiKnowledgeController.class);

    private final KnowledgeIndexingService knowledgeIndexingService;
    private final AdminOperationLogService adminOperationLogService;

    public AdminAiKnowledgeController(KnowledgeIndexingService knowledgeIndexingService,
                                      AdminOperationLogService adminOperationLogService) {
        this.knowledgeIndexingService = knowledgeIndexingService;
        this.adminOperationLogService = adminOperationLogService;
    }

    /**
     * 手动触发全量知识库重建（FAQ/商品/服务/门店 → embed → PgVector）。
     * 需要 {@code ai:knowledge:rebuild}（7056）权限。
     */
    @PostMapping("/rebuild")
    @PreAuthorize("hasAuthority('ai:knowledge:rebuild')")
    public ResponseEntity<ApiResponse<KnowledgeRebuildResponse>> rebuild() {
        Long adminId = SecurityContextHelper.getCurrentAdminId()
                .orElseThrow(() -> new IllegalStateException("No admin identity"));
        long start = System.currentTimeMillis();
        try {
            int count = knowledgeIndexingService.rebuildAll();
            long duration = System.currentTimeMillis() - start;
            logAdminOperation(adminId, "SUCCESS", null);
            return ResponseEntity.ok(ApiResponse.ok(
                    new KnowledgeRebuildResponse(count, duration, "知识库重建完成")));
        } catch (Exception e) {
            // 审计只记脱敏 message（并发拒绝等业务异常），不记堆栈
            logAdminOperation(adminId, "FAIL", e.getMessage());
            throw e;
        }
    }

    private void logAdminOperation(Long adminId, String result, String errorMessage) {
        try {
            AdminOperationLog opLog = new AdminOperationLog();
            opLog.setAdminId(adminId);
            opLog.setModule("AI知识库");
            opLog.setOperation("重建知识库");
            opLog.setRequestMethod("POST");
            opLog.setRequestUrl("/api/v1/admin/ai/knowledge/rebuild");
            opLog.setResult(result);
            opLog.setErrorMessage(errorMessage);
            adminOperationLogService.save(opLog);
        } catch (Exception ex) {
            log.warn("Failed to log admin operation: {}", ex.getMessage());
        }
    }
}
