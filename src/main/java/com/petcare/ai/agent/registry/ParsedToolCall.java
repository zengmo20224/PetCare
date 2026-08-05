package com.petcare.ai.agent.registry;

import com.petcare.ai.agent.tool.AgentToolArgs;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LLM 文本协议解析出的工具调用请求。
 * <p>
 * 同时实现 {@link AgentToolArgs}，便于直接传入 {@code AgentTool.invoke}。
 * 注意：record 组件 {@code args} 即作为 {@link AgentToolArgs#raw()} 的数据源。
 *
 * @param toolName 解析出的工具名（未经白名单校验）
 * @param args     解析出的参数（key→原字符串值，未做类型转换）
 * @param rawText  原始协议文本（审计用，如 {@code [TOOL:getProductInfo(productId=123)]}）
 */
public record ParsedToolCall(
        String toolName,
        Map<String, String> args,
        String rawText
) implements AgentToolArgs {

    public ParsedToolCall {
        args = args == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(args));
    }

    @Override
    public String get(String key) {
        return args.get(key);
    }

    @Override
    public Map<String, String> raw() {
        return args;
    }
}
