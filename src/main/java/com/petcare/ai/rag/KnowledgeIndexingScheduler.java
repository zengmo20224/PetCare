package com.petcare.ai.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 知识库定时重建任务（V2 AI Agent，D-013，M8.0）。
 * <p>
 * 默认 cron 由 {@code petcare.ai.knowledge.rebuild-cron} 配置（默认每日 03:00）。
 * 仅当 {@link KnowledgeIndexingService} Bean 存在（rag-enabled）时装配。
 * <p>
 * <b>幂等</b>（A6 修复后为真）：rebuildAll 执行前 {@code removeAll()} 全清旧向量，
 * 重复执行不产生重复向量；重建期间进程内互斥，与管理员手动触发并发时后者直接拒绝。
 */
@Component
@ConditionalOnBean(KnowledgeIndexingService.class)
@ConditionalOnProperty(prefix = "petcare.ai.knowledge", name = "rebuild-cron")
public class KnowledgeIndexingScheduler {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexingScheduler.class);

    private final KnowledgeIndexingService knowledgeIndexingService;

    public KnowledgeIndexingScheduler(KnowledgeIndexingService knowledgeIndexingService) {
        this.knowledgeIndexingService = knowledgeIndexingService;
    }

    @Scheduled(cron = "${petcare.ai.knowledge.rebuild-cron}")
    public void scheduledRebuild() {
        log.info("[RAG] 定时知识库重建任务触发");
        try {
            int count = knowledgeIndexingService.rebuildAll();
            log.info("[RAG] 定时知识库重建完成：{} 条", count);
        } catch (Exception e) {
            // 定时任务异常不得影响应用，记录后等下次触发
            log.error("[RAG] 定时知识库重建失败：{}", e.getMessage(), e);
        }
    }
}
