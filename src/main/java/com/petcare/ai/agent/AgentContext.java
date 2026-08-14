package com.petcare.ai.agent;

/**
 * Agent 运行时上下文（对标 docs/09 §6.1）。
 * <p>
 * 构造期一次性解析当前用户身份，注入所有 Tool，避免每个 Tool 各自读 SecurityContext。
 * 不可变 record。
 *
 * @param currentUserId  当前登录用户 ID；用户端 Agent 必填（null 表示未登录，Tool 应拒绝）。
 *                       管理端 Agent 场景下同填 adminId（登录态语义），审计归属以 {@code adminId} 为准
 * @param agentType      Agent 类型（审计用）
 * @param conversationId 会话标识（审计关联用，可空）
 * @param adminId        当前登录管理员 ID；仅管理端 Agent（如 ANALYSIS）填写，
 *                       用于 ai_tool_call_log 审计字段正确分流（A4 修复：admin/user ID 空间独立，
 *                       管理员 ID 写入 user_id 会错误关联到无关用户）
 */
public record AgentContext(
        Long currentUserId,
        AgentType agentType,
        String conversationId,
        Long adminId
) {
    /** 用户端 Agent 的兼容构造（adminId=null）。 */
    public AgentContext(Long currentUserId, AgentType agentType, String conversationId) {
        this(currentUserId, agentType, conversationId, null);
    }

    /** 管理端 Agent 工厂：currentUserId 同填 adminId（保持登录态校验语义），adminId 标记审计归属。 */
    public static AgentContext forAdmin(Long adminId, AgentType agentType) {
        return new AgentContext(adminId, agentType, null, adminId);
    }

    /**
     * 用户端权限即"登录态"（docs/09 §6.3：ai:customer-service:chat 由 SecurityContext 校验，不进 admin_permission 表）。
     */
    public void requireUser() {
        if (currentUserId == null) {
            throw new IllegalStateException("AgentContext missing currentUserId for user-scoped tool");
        }
    }

    /** 是否管理端 Agent 调用（审计字段分流依据）。 */
    public boolean isAdminCall() {
        return adminId != null;
    }
}
