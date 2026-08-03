package com.petcare.ai.rag;

import java.util.List;

import com.petcare.ai.rag.KnowledgeSource.RetrievedKnowledge;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;

/**
 * RAG 检索服务（V2 RAG，D-013，M8.0）。
 * <p>
 * 把用户 query 向量化 → 在 PgVector 召回 top-K → 返回归一化结果。
 * <b>不直接拼 prompt</b>（那是 M8.1 客服升级的事），本类只负责"检索 + 返回结果"。
 * <p>
 * 召回结果回灌前应由调用方过 {@code AiOutputSafetyPolicy}（防向量库注入污染，docs/09 §4.5 B5）；
 * 本 M8.0 实现先返回原始召回，M8.1 接入护栏。
 */
public class RagRetrievalService {

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
