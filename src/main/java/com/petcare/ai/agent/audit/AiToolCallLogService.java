package com.petcare.ai.agent.audit;

import com.petcare.ai.agent.AgentContext;
import com.petcare.ai.agent.tool.AgentToolArgs;
import com.petcare.ai.agent.tool.AgentToolResult;
import com.petcare.ai.mapper.AiToolCallLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Tool 调用审计服务（对标 docs/09 §6.2 + booking/wallet 审计范式）。
 * <p>
 * <b>降级哲学</b>：审计写入失败不阻塞对话（Tool 已执行完，结果已拿到）。
 * {@link AiToolCallLogMapper} 通过 {@link ObjectProvider} 可选注入——
 * agent-enabled=false 时 Mapper Bean 可能不存在（虽实际总在），用 ObjectProvider 更稳妥。
 * <p>
 * <b>脱敏</b>：args/result 限长 500，error_message 限长 1000（对标 phase17 列定义）。
 * 不写堆栈/SQL/Provider 原始错误。
 */
@Service
public class AiToolCallLogService {

    private static final Logger log = LoggerFactory.getLogger(AiToolCallLogService.class);

    private static final int MAX_ARGS = 500;
    private static final int MAX_RESULT = 500;
    private static final int MAX_ERROR = 1000;

    private final ObjectProvider<AiToolCallLogMapper> mapperProvider;

    public AiToolCallLogService(ObjectProvider<AiToolCallLogMapper> mapperProvider) {
        this.mapperProvider = mapperProvider;
    }

    /**
     * 记录一次 Tool 调用。永不抛异常（审计失败仅 warn 日志）。
     *
     * @param ctx           Agent 上下文
     * @param toolName      工具名
     * @param args          入参（已脱敏前的原值，内部会截断）
     * @param result        Tool 执行结果
     * @param usageLogId    关联的 ai_usage_log.id（可空）
     * @param durationMs    耗时毫秒
     */
    public void logCall(AgentContext ctx, String toolName, AgentToolArgs args,
                        AgentToolResult result, Long usageLogId, long durationMs) {
        try {
            AiToolCallLogMapper mapper = mapperProvider.getIfAvailable();
            if (mapper == null) {
                return;
            }
            AiToolCallLog entity = new AiToolCallLog();
            entity.setUsageLogId(usageLogId);
            entity.setAgentType(ctx.agentType().name());
            entity.setToolName(toolName);
            entity.setUserId(ctx.currentUserId());
            entity.setAdminId(null); // 用户端 Agent，adminId 恒空
            entity.setArgsSummary(sanitizeArgs(args));
            entity.setResultSummary(result != null && result.success() ? truncate(result.summary(), MAX_RESULT) : null);
            entity.setDurationMs((int) Math.min(durationMs, Integer.MAX_VALUE));
            entity.setSuccess(result != null && result.success() ? 1 : 0);
            entity.setErrorMessage(result != null && !result.success() ? truncate(result.errorMessage(), MAX_ERROR) : null);
            mapper.insert(entity);
        } catch (Exception e) {
            // 审计失败不阻塞对话
            log.warn("[AI] Failed to write ai_tool_call_log: tool={}, agent={}, err={}",
                    toolName, ctx.agentType(), e.getMessage());
        }
    }

    /** 入参脱敏：转 key=value 串并截断。 */
    private static String sanitizeArgs(AgentToolArgs args) {
        if (args == null || args.raw() == null || args.raw().isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        args.raw().forEach((k, v) -> {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(k).append('=').append(v);
        });
        return truncate(sb.toString(), MAX_ARGS);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
