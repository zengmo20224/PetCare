package com.petcare.ai.service;

import com.petcare.ai.agent.ModerationAgent;
import com.petcare.ai.agent.ModerationAgent.ModerationVerdict;
import com.petcare.community.service.CommunityInteractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * M8.3 文本审核异步钩子（docs/09 §5.3.2）。
 *
 * <p>发帖/评论提交成功后由 {@code CommunityPostApplicationService} 调用，
 * 在单线程 daemon Executor 中执行 {@link ModerationAgent} 分类：</p>
 * <ul>
 *   <li>verdict=VIOLATION 且 confidence ≥ 阈值（默认 0.8，{@code petcare.ai.moderation.violation-threshold}）
 *       → 调 {@code CommunityInteractionService.createSystemAiReport} 产 PostReport 进人工队列；</li>
 *   <li>SUSPICIOUS → 仅告警日志（不进队，避免误报淹没人工队列）；</li>
 *   <li>NORMAL / Provider 失败 → 无动作（fail-open，敏感词规则仍在前置生效）。</li>
 * </ul>
 *
 * <p><b>边界（B4）</b>：本服务只产建议记录，绝不删帖/封号/改内容状态。
 * <b>审计</b>：AI 审核报告的 reason 带 {@code [AI审核]} 前缀，管理端据此过滤。</p>
 */
public class AiModerationReviewService {

    private static final Logger log = LoggerFactory.getLogger(AiModerationReviewService.class);

    /** PostReport.reason 的 AI 来源标记（管理端过滤依据）。 */
    public static final String AI_REPORT_PREFIX = "[AI审核]";

    private final ModerationAgent moderationAgent;
    private final CommunityInteractionService communityInteractionService;
    private final double violationThreshold;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ai-moderation-review");
        t.setDaemon(true);
        return t;
    });

    public AiModerationReviewService(ModerationAgent moderationAgent,
                                      CommunityInteractionService communityInteractionService,
                                      double violationThreshold) {
        this.moderationAgent = moderationAgent;
        this.communityInteractionService = communityInteractionService;
        this.violationThreshold = violationThreshold;
    }

    /** 发帖后异步审核。 */
    public void reviewPostAsync(Long postId, String content) {
        submit(postId, "POST", content);
    }

    /** 评论后异步审核（PostReport 挂在所属帖子上，reason 标明来自评论）。 */
    public void reviewCommentAsync(Long postId, Long commentId, String content) {
        submit(postId, "COMMENT#" + commentId, content);
    }

    private void submit(Long postId, String contentType, String content) {
        executor.submit(() -> {
            try {
                reviewInternal(postId, contentType, content);
            } catch (Exception e) {
                // 审核链路任何异常都不外溢（异步上下文无人接异常）
                log.warn("[AI] Moderation review task failed: postId={}, type={}, err={}",
                        postId, contentType, e.getMessage());
            }
        });
    }

    /** 同步审核（异步任务内部逻辑，公开供测试直接驱动）。任何异常不外溢。 */
    public void reviewInternal(Long postId, String contentType, String content) {
        try {
            ModerationVerdict verdict = moderationAgent.classify(contentType, content);

            if (verdict.isViolation() && verdict.confidence() >= violationThreshold) {
                String reason = AI_REPORT_PREFIX + " " + contentType + " 疑似违规（"
                        + verdict.type() + "，置信度 " + String.format("%.2f", verdict.confidence()) + "）";
                communityInteractionService.createSystemAiReport(postId, verdict.type(), reason);
                log.info("[AI] Moderation flagged {} postId={} type={} confidence={}",
                        contentType, postId, verdict.type(), verdict.confidence());
            } else if (verdict.isSuspicious()) {
                log.info("[AI] Moderation suspicious (below queue threshold) {} postId={} confidence={}",
                        contentType, postId, verdict.confidence());
            }
            // NORMAL：无动作
        } catch (Exception e) {
            // 审核链路任何异常都不外溢（异步上下文无人接异常，同步语义保持一致）
            log.warn("[AI] Moderation review failed: postId={}, type={}, err={}",
                    postId, contentType, e.getMessage());
        }
    }

    /** 优雅关闭（应用停机时排干队列，测试用）。 */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
