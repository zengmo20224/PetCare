-- ============================================================
-- schema-pgvector.sql
-- CI-DB-017：PgVector 知识库 schema（V2 AI Agent）
-- 关联：CR-20260801-004、docs/09-ai-agent-design.md §4.3、docs/08-pending-decisions.md D-013
-- ============================================================
-- 重要说明：
--   1. 此文件挂载到 **独立的 PostgreSQL (PgVector) 容器** 的 /docker-entrypoint-initdb.d/，
--      首次创建数据卷时由 PG 官方镜像自动执行；**不属于 MySQL 业务库**。
--   2. PgVector 只存"派生知识副本"（FAQ 文本、商品/服务描述的 embedding），业务真源仍在 MySQL。
--      边界约束见 docs/09 §2 B6。
--   3. 换 embedding 模型 = DROP + 重建 ai_embedding 表（ai_knowledge_doc 不动，文本不丢）；
--      多模型共存的 model_name 字段已预留，但同表的 vector 列类型维度需统一。
--
-- 设计依据：docs/09-ai-agent-design.md §4.3（HNSW 索引，单门店规模 < 1 万条，查询 < 50ms）
-- 验证：pgvector 0.8.6（pgvector/pgvector:pg16 镜像自带，含 hnsw + ivfflat 访问方法）
-- ============================================================

-- 1. 启用向量扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. 知识文档表（存切片文本 + 元数据 + 内容哈希，不含向量）
CREATE TABLE IF NOT EXISTS ai_knowledge_doc (
    id           BIGSERIAL PRIMARY KEY,
    source_type  VARCHAR(32) NOT NULL,                  -- FAQ / PRODUCT / SERVICE / STORE_INFO / POLICY
    source_id    BIGINT,                                -- 对应 MySQL 业务表主键（POLICY 等可空）
    chunk_index  INT NOT NULL,                          -- 切片序号（一篇文档切多段）
    title        VARCHAR(255),
    content      TEXT NOT NULL,                         -- 切片文本（ground truth，用于注入 prompt）
    metadata     JSONB,                                 -- {category, price, tags...} 供元数据过滤
    source_hash  VARCHAR(64) NOT NULL,                  -- 内容哈希，幂等更新判断
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(source_type, source_id, chunk_index)
);

-- 3. 向量表（与文档分离：换模型时只重建此表，文本不动）
--    维度 384 = all-MiniLM-L6-v2 默认；换模型需 DROP+重建本表并同步 AI_EMBEDDING_DIM
CREATE TABLE IF NOT EXISTS ai_embedding (
    doc_id       BIGINT NOT NULL REFERENCES ai_knowledge_doc(id) ON DELETE CASCADE,
    embedding    vector(384) NOT NULL,
    model_name   VARCHAR(64) NOT NULL,                  -- 记录用哪个模型生成，便于多模型共存与排查
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(doc_id, model_name)
);

-- 4. HNSW 索引（PgVector 推荐，单门店规模查询 < 50ms）
CREATE INDEX IF NOT EXISTS ai_embedding_vec_idx ON ai_embedding
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- 5. 辅助索引：按来源类型过滤召回（如"只在 SERVICE 类目召回"）
CREATE INDEX IF NOT EXISTS ai_knowledge_doc_source_idx ON ai_knowledge_doc(source_type, source_id);
