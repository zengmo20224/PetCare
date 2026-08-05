package com.petcare.ai.agent.registry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 LLM 输出中的工具调用协议文本（对标 docs/09 §6 + 计划第 2.3 步）。
 * <p>
 * 协议格式：{@code [TOOL:toolName(key1=value1, key2=value2)]}
 * <ul>
 *   <li>工具名：字母数字下划线；</li>
 *   <li>参数：逗号分隔的 key=value，value 不含逗号/右括号/右方括号；</li>
 *   <li>一次只解析<b>第一个</b>匹配（计划约定：最多 1 轮工具调用，防无限循环）。</li>
 * </ul>
 * 线程安全（无状态，仅静态方法）。
 */
public final class ToolCallParser {

    private ToolCallParser() {}

    /**
     * 匹配 [TOOL:name(args)]。
     * <ul>
     *   <li>group(1) = 工具名</li>
     *   <li>group(2) = 参数原始串（可能为空）</li>
     * </ul>
     */
    private static final Pattern TOOL_CALL = Pattern.compile(
            "\\[TOOL:(\\w+)\\s*(?:\\(([^)]*)\\))?\\]",
            Pattern.CASE_INSENSITIVE);

    /**
     * 从 LLM 输出中解析第一个工具调用。
     *
     * @return 存在则 Optional 有值；无匹配返回 empty
     */
    public static Optional<ParsedToolCall> findFirst(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) {
            return Optional.empty();
        }
        Matcher m = TOOL_CALL.matcher(llmOutput);
        if (!m.find()) {
            return Optional.empty();
        }
        String toolName = m.group(1);
        String argsRaw = m.group(2);
        Map<String, String> args = parseArgs(argsRaw);
        return Optional.of(new ParsedToolCall(toolName, args, m.group(0)));
    }

    /**
     * 解析 "key1=value1, key2=value2" 为 Map。
     * 容错：跳过格式不合法的片段（LLM 输出不可信）。
     */
    private static Map<String, String> parseArgs(String argsRaw) {
        Map<String, String> args = new LinkedHashMap<>();
        if (argsRaw == null || argsRaw.isBlank()) {
            return args;
        }
        for (String pair : argsRaw.split(",")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String k = pair.substring(0, eq).trim();
            String v = pair.substring(eq + 1).trim();
            if (!k.isEmpty()) {
                args.put(k, v);
            }
        }
        return args;
    }
}
