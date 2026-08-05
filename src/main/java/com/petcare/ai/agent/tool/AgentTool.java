package com.petcare.ai.agent.tool;

import com.petcare.ai.agent.AgentContext;

/**
 * Agent 受限工具调用契约（对标 docs/09 §6.1）。
 * <p>
 * 所有 Tool 实现此接口，在 {@code ai/agent/tool/} 包下注册到 {@link com.petcare.ai.agent.registry.AgentToolRegistry}。
 * <b>架构边界（B7）</b>：Tool 实现只允许依赖业务 Service 接口（如 ProductCatalogApplicationService），
 * 严禁直接依赖 {@code *Mapper} / DataSource / MyBatis（由 AiAgentArchitectureTest 反射强制）。
 */
public interface AgentTool {

    /** 唯一名，白名单注册用（如 {@code getProductInfo}）。 */
    String name();

    /** 给 LLM 的能力说明（注入 system prompt，让 LLM 知道何时调它）。 */
    String description();

    /**
     * 是否只读。docs/09 B4：客服/分析/助手 Tool 必须 {@code true}。
     * 由架构守卫反射断言。
     */
    boolean readOnly();

    /**
     * 执行工具调用。
     * <p>
     * 实现要点：
     * <ul>
     *   <li>从 {@code input} 取参并做容错校验（LLM 输出不可信）；</li>
     *   <li>涉及用户私有数据（订单/预约）时，必须用 {@code ctx.currentUserId} 做越权校验，
     *       复用业务 ApplicationService 既有的归属校验，不可绕过；</li>
     *   <li>所有异常捕获后返回 {@link AgentToolResult#fail}，不向上抛（避免阻塞 Agent 编排）；</li>
     *   <li>返回的 summary/errorMessage 必须脱敏，可安全喂回 LLM。</li>
     * </ul>
     */
    AgentToolResult invoke(AgentToolArgs input, AgentContext ctx);
}
