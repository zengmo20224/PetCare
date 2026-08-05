package com.petcare.ai.agent;

/**
 * Agent 运行时上下文（对标 docs/09 §6.1）。
 * <p>
 * 构造期一次性解析当前用户身份，注入所有 Tool，避免每个 Tool 各自读 SecurityContext。
 * 不可变 record。
 *
 * @param currentUserId  当前登录用户 ID；用户端 Agent 必填（null 表示未登录，Tool 应拒绝）
 * @param agentType      Agent 类型（审计用）
 * @param conversationId 会话标识（审计关联用，可空）
 */
public record AgentContext(
        Long currentUserId,
        AgentType agentType,
        String conversationId
) {
    /**
     * 用户端权限即"登录态"（docs/09 §6.3：ai:customer-service:chat 由 SecurityContext 校验，不进 admin_permission 表）。
     */
    public void requireUser() {
        if (currentUserId == null) {
            throw new IllegalStateException("AgentContext missing currentUserId for user-scoped tool");
        }
    }
}
