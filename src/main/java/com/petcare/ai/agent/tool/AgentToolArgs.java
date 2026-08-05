package com.petcare.ai.agent.tool;

import java.util.Map;

/**
 * Tool 入参的通用载体。
 * <p>
 * 由 {@link com.petcare.ai.agent.registry.ToolCallParser} 从 LLM 文本协议解析得到，
 * 再由各 Tool 自行按 key 取值并做类型转换与校验。刻意不用强类型泛型——LLM 输出不可信，
 * 统一 Map 便于在 Tool 内集中做容错（缺 key / 非数字 / 负数）。
 */
public interface AgentToolArgs {

    /**
     * 按参数名取值（字符串原值，未转换）。
     *
     * @return 参数值；不存在返回 null
     */
    String get(String key);

    /**
     * 取 Long 类型参数，做容错转换。
     *
     * @return 解析失败或缺失返回 null
     */
    default Long getAsLong(String key) {
        String v = get(key);
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 底层参数 Map（只读视图，用于审计摘要）。
     */
    Map<String, String> raw();
}
