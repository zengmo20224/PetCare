package com.petcare.ai.rag;

import java.util.List;

import com.petcare.ai.domain.AiOutputSafetyPolicy;
import com.petcare.ai.rag.KnowledgeSource.RetrievedKnowledge;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RAG 检索服务（V2 RAG，D-013，M8.0）。
 * <p>
 * 把用户 query 向量化 → 在 PgVector 召回 top-K → 返回归一化结果。
 * <b>不直接拼 prompt</b>（那是 M8.1 客服升级的事），本类只负责"检索 + 返回结果"。
 * <p>
 * A1 安全修复（B5，docs/09 §4.5）：召回结果回灌前在本类统一过 {@link AiOutputSafetyPolicy}——
 * 向量库存的是派生知识副本（FAQ/商品/服务描述，管理员可编辑），若被注入
 * "忽略以上规则"类指令文本，会在下游污染 system prompt。命中安全策略的片段
 * 直接丢弃并告警，不进入任何 prompt。
 */
public class RagRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RagRetrievalService.class);

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final int defaultTopK;

    public RagRetrievalService(EmbeddingModel embeddingModel,
                               EmbeddingStore<TextSegment> embeddingStore,
                               int defaultTopK) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.defaultTopK = defaultTopK;
    }

    /**
     * 检索 top-K 相关知识。
     *
     * @param query 用户查询文本
     * @return 召回结果（score 为 cosine 相似度，越大越相关）
     */
    public List<RetrievedKnowledge> retrieveRelevant(String query) {
        return retrieveRelevant(query, defaultTopK);
    }

    public List<RetrievedKnowledge> retrieveRelevant(String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        dev.langchain4j.data.embedding.Embedding queryEmbedding = embeddingModel.embed(query).content();
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(
                dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(topK)
                        .build());
        return result.matches().stream()
                .map(this::toRetrievedKnowledge)
                .filter(k -> {
                    if (AiOutputSafetyPolicy.isUnsafe(k.content())) {
                        log.warn("[AI] RAG segment dropped by output safety policy "
                                + "(possible prompt injection in knowledge store, score={})", k.score());
                        return false;
                    }
                    return true;
                })
                .toList();
    }

    private RetrievedKnowledge toRetrievedKnowledge(EmbeddingMatch<TextSegment> match) {
        TextSegment segment = match.embedded() == null ? TextSegment.from("") : match.embedded();
        return new RetrievedKnowledge(
                segment.text(),
                match.score() == null ? 0.0 : match.score(),
                segment.metadata() == null ? java.util.Map.of() : segment.metadata().toMap()
        );
    }
}
