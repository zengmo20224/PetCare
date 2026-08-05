package com.petcare.ai.agent.tool;

/**
 * Tool 执行结果（对标 docs/09 §6.1）。
 * <p>
 * 不可变 record。{@code summary} 是脱敏后的文本摘要，会被喂回 LLM 作为第二轮生成的上下文，
 * 因此必须只含"可向用户展示"的公开信息，绝不含密钥/堆栈/他人隐私。
 *
 * @param success      是否执行成功
 * @param summary      成功时的结果摘要（喂回 LLM，已脱敏）
 * @param errorMessage 失败时的脱敏错误说明（如"未找到相关订单"，不暴露订单存在性）
 */
public record AgentToolResult(
        boolean success,
        String summary,
        String errorMessage
) {
    /** 成功结果工厂。 */
    public static AgentToolResult ok(String summary) {
        return new AgentToolResult(true, summary == null ? "" : summary, null);
    }

    /** 失败结果工厂（errorMessage 必须脱敏）。 */
    public static AgentToolResult fail(String errorMessage) {
        return new AgentToolResult(false, null, errorMessage == null ? "工具调用失败" : errorMessage);
    }

    /** 喂回 LLM 的统一文本：成功用 summary，失败用 errorMessage。 */
    public String toLLMText() {
        return success ? summary : ("工具调用失败：" + errorMessage);
    }
}
