package com.petcare.ai.agent.tool;

/**
 * Tool 元数据快照（注入 system prompt 让 LLM 知道可用工具）。
 * <p>
 * 刻意只暴露 name + description，不暴露入参 schema（LLM 协议已在 prompt 文本中示范）。
 */
public record ToolDescriptor(
        String name,
        String description
) {
    public static ToolDescriptor of(AgentTool tool) {
        return new ToolDescriptor(tool.name(), tool.description());
    }
}
