package com.petcare.ai.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.petcare.common.api.ApiResponse;
import com.petcare.common.pagination.PageResponse;
import com.petcare.community.dto.PostReportResponse;
import com.petcare.community.entity.PostReport;
import com.petcare.community.service.CommunityInteractionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端 AI 审核建议端点（M8.3，docs/09 §5.3.2）。
 *
 * <p>只读查看 {@code ModerationAgent} 产出的 PostReport 建议（reason 前缀 [AI审核]），
 * 供管理员/审核员评估后走既有举报处置流程。<b>本端点不提供任何处置动作</b>——
 * AI 建议不能自动删帖/封号（B4），处置仍在 AdminCommunityController 的举报处理。
 * 审核结果不展示给用户端（避免对抗）。</p>
 *
 * <p>权限：{@code ai:moderation:review}（7055，phase16 已落库，
 * 授予 SUPER_ADMIN / ADMIN / MODERATOR）。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/ai/moderation")
public class AdminModerationAgentController {

    private final CommunityInteractionService communityInteractionService;

    public AdminModerationAgentController(CommunityInteractionService communityInteractionService) {
        this.communityInteractionService = communityInteractionService;
    }

    /**
     * 分页列出 AI 审核建议（系统产生的 PostReport）。
     */
    @GetMapping("/reports")
    @PreAuthorize("hasAuthority('ai:moderation:review')")
    public ResponseEntity<ApiResponse<PageResponse<PostReportResponse>>> listAiReports(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<PostReport> result = communityInteractionService.listAiReports(status, page, size);
        var items = result.getRecords().stream().map(r -> new PostReportResponse(
                r.getId(), r.getPostId(), r.getReporterId(), r.getReasonType(),
                r.getReason(), r.getStatus(), r.getHandleResult(),
                r.getHandlerId(), r.getHandleTime(), r.getCreateTime())).toList();
        return ResponseEntity.ok(ApiResponse.ok(
                PageResponse.of(items, result.getTotal(), page, size)));
    }
}
