-- ============================================================
-- migration-phase18-ai-chat-memory.sql
-- CI-DB-020：V2 AI Agent ChatMemory 持久化表
-- 关联：CR-20260801-004、docs/09-ai-agent-design.md §7.3、docs/08-pending-decisions.md D-013
-- ============================================================
-- 范围：
--   新增 ai_chat_memory 表（MySQL 业务库）
--   解决 docs/09 §7.3 C-4 矛盾：明确 ChatMemoryStore 适配方式
--
-- 设计依据：
--   - docs/09 §7.3：用 langchain4j MessageWindowChatMemory + 自定义 ChatMemoryStore
--   - 不复用 V1 ai_message 业务表（结构化对话历史），避免污染
--   - langchain4j ChatMemoryStore 标准接口存序列化 message，本表承载该序列化内容
--
-- 归属库说明（docs/09 C-4 修订）：
--   ai_chat_memory 属 Agent 运行态记忆 → MySQL（与 ai_message 同库，但独立表）
--   V1 ai_message 仍保留作结构化对话历史展示（客服对话记录），职责分离
-- ============================================================

CREATE TABLE IF NOT EXISTS `ai_chat_memory` (
  `id`              VARCHAR(128) NOT NULL COMMENT '记忆会话 ID（langchain4j memory id，对应 Agent 会话标识，非雪花）',
  `agent_type`      VARCHAR(32)  NOT NULL COMMENT 'Agent 类型：CUSTOMER_SERVICE / ANALYSIS / POST_ASSISTANT / MODERATION',
  `serialized_messages` LONGTEXT NOT NULL COMMENT 'langchain4j 序列化的 ChatMessage 列表（JSON），由自定义 JdbcChatMemoryStore 读写',
  `message_count`   INT          NOT NULL DEFAULT 0 COMMENT '当前记忆窗口内消息数（便于排查 MessageWindow 上限）',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（每次追加消息更新）',
  PRIMARY KEY (`id`),
  KEY `idx_memory_agent_type` (`agent_type`),
  KEY `idx_memory_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Agent ChatMemory 持久化表（langchain4j 自定义 Store 承载）';
