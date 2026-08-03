package com.petcare.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PgVector 连接配置（V2 AI Agent，D-013）。
 * <p>
 * 绑定 {@code petcare.ai.pgvector.*}（独立 PG 实例，与主 MySQL 业务库物理隔离）。
 * <b>绝不绑定 {@code spring.datasource.*}</b>——主数据源仍由 profile 提供给 MyBatis-Plus。
 * 详见 {@code docs/09-ai-agent-design.md} §4.2、§3.1。
 */
@ConfigurationProperties(prefix = "petcare.ai.pgvector")
public record PgVectorProperties(
        String host,
        Integer port,
        String database,
        String username,
        String password
) {
    /**
     * 拼 PostgreSQL JDBC URL（PgVectorEmbeddingStore 用）。
     */
    public String jdbcUrl() {
        return "jdbc:postgresql://" + host + ":" + port + "/" + database;
    }
}
