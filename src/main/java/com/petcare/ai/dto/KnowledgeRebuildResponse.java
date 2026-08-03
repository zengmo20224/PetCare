package com.petcare.ai.dto;

/**
 * 知识库重建响应（V2 AI Agent，D-013）。
 */
public record KnowledgeRebuildResponse(
        int indexedCount,
        long durationMs,
        String message
) {
}
