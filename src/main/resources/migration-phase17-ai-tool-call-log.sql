-- ============================================================
-- migration-phase17-ai-tool-call-log.sql
-- CI-DB-019：V2 AI Agent Tool 调用审计日志表
-- 关联：CR-20260801-004、docs/09-ai-agent-design.md §6.2、docs/08-pending-decisions.md D-013
-- ============================================================
-- 范围：
--   新增 ai_tool_call_log 表（MySQL 业务库，与 ai_usage_log 同库；**不在 PgVector**）
--   解决 docs/09 §6.2 C-1 矛盾：明确该表归属 MySQL，可与 ai_usage_log 外键关联
--
-- 设计依据：
--   - docs/09 §6.2：每次 Tool 调用记审计（agent/tool/userId/args 摘要/耗时/成功），与 AiUsageLog 关联
--   - 对标 ai_usage_log 字段范式（schema.sql L717-L734）
--   - 字段向后兼容：args 摘要限长防泄露全量入参；error_message 限长
--
-- 归属库说明（docs/09 C-1 修订）：
--   ai_tool_call_log 属业务审计数据 → MySQL（与 ai_usage_log 同库）
--   PgVector 只存派生知识副本（ai_knowledge_doc / ai_embedding），不存审计日志（B6 约束）
-- ============================================================

CREATE TABLE IF NOT EXISTS `ai_tool_call_log` (
  `id`              BIGINT       NOT NULL COMMENT '主键，雪花 ID',
  `usage_log_id`    BIGINT       DEFAULT NULL COMMENT '关联 ai_usage_log.id（同库外键，一次 Agent 编排的 LLM 调用与 Tool 调用关联）',
  `agent_type`      VARCHAR(32)  NOT NULL COMMENT 'Agent 类型：CUSTOMER_SERVICE / ANALYSIS / POST_ASSISTANT / MODERATION',
  `tool_name`       VARCHAR(64)  NOT NULL COMMENT 'Tool 唯一名（白名单注册名，如 getProductInfo / getMyOrderStatus）',
  `user_id`         BIGINT       DEFAULT NULL COMMENT '调用方用户 ID（用户端 Agent）',
  `admin_id`        BIGINT       DEFAULT NULL COMMENT '调用方管理员 ID（管理端 Agent）',
  `args_summary`    VARCHAR(500) DEFAULT NULL COMMENT '入参摘要（脱敏后，限长防泄露全量入参；不含敏感字段原值）',
  `result_summary`  VARCHAR(500) DEFAULT NULL COMMENT '出参摘要（脱敏后，便于排查；Tool 完整结果返回给 Agent 不在此存）',
  `duration_ms`     INT          DEFAULT NULL COMMENT 'Tool 执行耗时（毫秒）',
  `success`         TINYINT      NOT NULL DEFAULT 1 COMMENT '是否成功：0-失败 1-成功',
  `error_message`   VARCHAR(1000) DEFAULT NULL COMMENT '错误信息（脱敏，不含堆栈/SQL/Provider 原始错误，对标 ai_usage_log）',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_tool_usage_log` (`usage_log_id`),
  KEY `idx_tool_agent_type` (`agent_type`),
  KEY `idx_tool_user_id` (`user_id`),
  KEY `idx_tool_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Agent Tool 调用审计日志表（与 ai_usage_log 同库 MySQL）';
