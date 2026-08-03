package com.petcare.ai.rag;

import java.util.Map;

/**
 * V2 RAG 知识数据源抽象（D-013，M8.0）。
 * <p>
 * 定义从 MySQL 业务库抽取的"待索引知识"统一形态，由 {@link KnowledgeIndexingService}
 * embed 后写入 PgVector。详见 {@code docs/09} §4.4。
 */
public final class KnowledgeSource {

    private KnowledgeSource() {
    }

    /** 知识来源类型（对应 PgVector ai_knowledge_doc.source_type）。 */
    public enum SourceType {
        FAQ,
        PRODUCT,
        SERVICE,
        STORE_INFO
    }

    /**
     * 一条待索引的知识文档（切片后，每条 1 片）。
     *
     * @param sourceType 来源类型
     * @param sourceId   MySQL 业务表主键（STORE_INFO 可为 null）
     * @param title      标题（FAQ=question，商品/服务=name）
     * @param content    待 embed 文本（FAQ=question+answer，商品/服务=name+description）
     * @param metadata   元数据（category/price 等，供检索过滤，可空）
     */
    public record KnowledgeDocument(
            SourceType sourceType,
            Long sourceId,
            String title,
            String content,
            Map<String, Object> metadata
    ) {
    }

    /** 检索召回结果。 */
    public record RetrievedKnowledge(
            String content,
            double score,
            Map<String, Object> metadata
    ) {
    }
}
