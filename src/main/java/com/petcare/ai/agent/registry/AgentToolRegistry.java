package com.petcare.ai.agent.registry;

import com.petcare.ai.agent.AgentContext;
import com.petcare.ai.agent.tool.AgentTool;
import com.petcare.ai.agent.tool.AgentToolArgs;
import com.petcare.ai.agent.tool.AgentToolResult;
import com.petcare.ai.agent.tool.ToolDescriptor;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 工具注册表（对标 docs/09 §6.2）。
 * <p>
 * <b>白名单 + 双重校验（B7）</b>：
 * <ol>
 *   <li>构造期注入不可变的 Tool 集合（运行时不可扩，满足架构守卫）；</li>
 *   <li>{@link #invoke} 时校验 toolName 在白名单内；</li>
 *   <li>用户端 Tool 强制 {@link AgentContext#requireUser()}（登录态校验）。</li>
 * </ol>
 * 未声明或无权限的调用抛 {@link ToolNotAuthorizedException}，不执行 Tool、不调 LLM。
 */
public class AgentToolRegistry {

    private final Map<String, AgentTool> tools;

    public AgentToolRegistry(List<AgentTool> tools) {
        Map<String, AgentTool> map = new LinkedHashMap<>();
        for (AgentTool t : tools) {
            if (map.putIfAbsent(t.name(), t) != null) {
                throw new IllegalStateException("Duplicate AgentTool name: " + t.name());
            }
        }
        // 不可变（架构守卫 §10.2：每个 Agent 的 Tool 集合是 final 不可变）
        this.tools = Collections.unmodifiableMap(map);
    }

    /** 工具是否已注册（白名单查询）。 */
    public boolean contains(String toolName) {
        return tools.containsKey(toolName);
    }

    /** 所有已注册工具的元数据快照（注入 system prompt）。 */
    public List<ToolDescriptor> descriptors() {
        return tools.values().stream().map(ToolDescriptor::of).toList();
    }

    /** 已注册工具名集合（只读）。 */
    public Collection<String> names() {
        return tools.keySet();
    }

    /**
     * 执行工具调用（白名单 + 登录态双重校验）。
     *
     * @throws ToolNotAuthorizedException 工具未注册或用户未登录
     */
    public AgentToolResult invoke(String toolName, AgentToolArgs args, AgentContext ctx) {
        AgentTool tool = tools.get(toolName);
        if (tool == null) {
            throw new ToolNotAuthorizedException("Tool not in whitelist: " + toolName);
        }
        // 用户端 Agent：登录态是唯一权限（docs/09 §6.3）
        ctx.requireUser();
        return tool.invoke(args, ctx);
    }

}
