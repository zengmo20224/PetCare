package com.petcare.ai.agent;

/**
 * V2 Agent 类型枚举（对标 docs/09 §6.2 / phase17 ai_tool_call_log.agent_type）。
 * <p>
 * 仅用于审计与 Tool 白名单分组，不进 provider 包（架构守卫）。
 */
public enum AgentType {
    /** 智能客服 Agent（升级 V1 客服，RAG + 只读 Tool）。 */
    CUSTOMER_SERVICE,
    /** 经营分析 Agent（升级 V1 报告，M8.2）。 */
    ANALYSIS,
    /** 社区发帖助手 Agent（M8.3）。 */
    POST_ASSISTANT,
    /** 内容审核 Agent（M8.3 文本 / M8.4 图片）。 */
    MODERATION
}
