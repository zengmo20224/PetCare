package com.petcare.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 检索配置（V2 AI Agent，D-013）。
 * <p>
 * 绑定 {@code petcare.ai.rag.*}。详见 {@code docs/09-ai-agent-design.md} §4.5、§11。
 */
@ConfigurationProperties(prefix = "petcare.ai.rag")
public record RagProperties(
        Integer topK
) {
    /**
     * 默认召回数（与 application.yml 默认值对齐）。
     */
    public int effectiveTopK() {
        return topK == null || topK <= 0 ? 5 : topK;
    }
}
