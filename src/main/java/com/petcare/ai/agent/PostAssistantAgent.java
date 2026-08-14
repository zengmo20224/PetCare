package com.petcare.ai.agent;

import com.petcare.ai.agent.audit.AiToolCallLogService;
import com.petcare.ai.agent.registry.AgentToolRegistry;
import com.petcare.ai.agent.registry.ToolNotAuthorizedException;
import com.petcare.ai.agent.tool.AgentToolArgs;
import com.petcare.ai.agent.tool.AgentToolResult;
import com.petcare.ai.agent.tool.GetMyPetProfileTool;
import com.petcare.ai.domain.AiOutputSafetyPolicy;
import com.petcare.ai.domain.PostAssistantFactPolicy;
import com.petcare.ai.dto.PostAssistantRequest;
import com.petcare.ai.provider.AiApiType;
import com.petcare.ai.provider.AiProviderClient;
import com.petcare.ai.provider.AiProviderException;
import com.petcare.ai.provider.AiProviderMessage;
import com.petcare.ai.provider.AiProviderRequest;
import com.petcare.ai.provider.AiProviderResponse;
import com.petcare.ai.provider.AiProviderUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 社区发帖助手 Agent（V2，对标 docs/09 §5.3.1）。
 * <p>
 * 与 V1 {@code AiPostAssistantServiceImpl}（只用用户提交的事实）的差异：
 * 通过只读 Tool {@code getMyPetProfile} 注入当前用户宠物档案（名称/品种/体型），
 * 生成个性化草稿。仍然只输出<b>草稿</b>——发布由用户确认后走
 * {@code CommunityPostApplicationService} 发帖事务，本类不触碰任何发帖/审核状态（B4/T9）。
 * <p>
 * 与客服/分析 Agent 的差异：宠物档案是生成草稿的<b>必要前置事实</b>，
 * 由编排层编程式调用 Tool（白名单 + 审计走 Registry），无需 LLM 决定是否调用——
 * 省一轮 Provider 往返。
 * <p>
 * <b>边界</b>：复用 {@link PostAssistantFactPolicy}（只用提供的事实，不编造）；
 * 输出过 {@link AiOutputSafetyPolicy}；异常分级透传（V1 语义）。
 */
public class PostAssistantAgent {

    private static final Logger log = LoggerFactory.getLogger(PostAssistantAgent.class);

    private final AiProviderClient providerClient;
    private final AgentToolRegistry toolRegistry;
    private final AiToolCallLogService auditService;

    public PostAssistantAgent(AiProviderClient providerClient,
                              AgentToolRegistry toolRegistry,
                              AiToolCallLogService auditService) {
        this.providerClient = providerClient;
        this.toolRegistry = toolRegistry;
        this.auditService = auditService;
    }

    /**
     * 生成个性化帖子草稿。
     *
     * @param currentUserId 当前登录用户 ID（非空）
     * @param request       用户提交的事实（宠物称呼/类型/事件/语气/原始文案）
     * @return 草稿文本 + usage
     */
    public AgentReply handle(Long currentUserId, PostAssistantRequest request) {
        AgentContext ctx = new AgentContext(currentUserId, AgentType.POST_ASSISTANT, null);
        ctx.requireUser();

        // Step 1: 编程式调宠物档案 Tool（白名单 + 审计）
        String petProfile = fetchPetProfile(ctx);

        // Step 2: 构建 prompt（fact policy + 档案事实 + 用户输入）
        List<AiProviderMessage> messages = buildMessages(petProfile, request);

        // Step 3: 调 Provider（异常分级透传，由上层处理降级——V1 语义）
        AiProviderResponse response = providerClient.complete(
                new AiProviderRequest(AiApiType.CONTENT_GENERATE, messages, null));

        String output = response.assistantText();

        // Step 4: 输出护栏
        if (AiOutputSafetyPolicy.isUnsafe(output)) {
            output = "抱歉，生成内容未通过安全检查，请修改后重试。";
        }

        return new AgentReply(output, response);
    }

    private String fetchPetProfile(AgentContext ctx) {
        long start = System.currentTimeMillis();
        AgentToolResult result;
        try {
            result = toolRegistry.invoke(GetMyPetProfileTool.NAME, AgentToolArgs.EMPTY, ctx);
        } catch (ToolNotAuthorizedException e) {
            log.warn("[AI] PostAssistant tool not authorized: {}", e.getMessage());
            result = AgentToolResult.fail("宠物档案不可用");
        } catch (Exception e) {
            log.warn("[AI] PostAssistant tool error: {}", e.getMessage());
            result = AgentToolResult.fail("宠物档案不可用");
        }
        auditService.logCall(ctx, GetMyPetProfileTool.NAME, AgentToolArgs.EMPTY,
                result, null, System.currentTimeMillis() - start);
        return result.toLLMText();
    }

    private List<AiProviderMessage> buildMessages(String petProfile, PostAssistantRequest request) {
        List<AiProviderMessage> messages = new ArrayList<>();
        messages.add(new AiProviderMessage("system", buildSystemPrompt(petProfile)));

        StringBuilder userContent = new StringBuilder();
        userContent.append("请根据以下信息生成一篇社区帖子草稿：\n");
        if (request.petName() != null && !request.petName().isBlank()) {
            userContent.append("宠物称呼：").append(request.petName()).append("\n");
        }
        if (request.petType() != null && !request.petType().isBlank()) {
            userContent.append("宠物类型：").append(request.petType()).append("\n");
        }
        if (request.event() != null && !request.event().isBlank()) {
            userContent.append("事件：").append(request.event()).append("\n");
        }
        if (request.tone() != null && !request.tone().isBlank()) {
            userContent.append("语气风格：").append(request.tone()).append("\n");
        }
        if (request.originalText() != null && !request.originalText().isBlank()) {
            userContent.append("用户原始文案：").append(request.originalText()).append("\n");
        }
        userContent.append("\n注意：只使用以上信息和宠物档案中的真实信息，不要编造任何未提及的细节。");

        messages.add(new AiProviderMessage("user", userContent.toString()));
        return messages;
    }

    private String buildSystemPrompt(String petProfile) {
        StringBuilder sb = new StringBuilder();
        sb.append(PostAssistantFactPolicy.getSystemInstruction()).append("\n");
        sb.append("生成的内容是给用户确认的帖子草稿，不是最终发布内容。\n\n");
        sb.append("【宠物档案】（当前用户登记的真实数据，可直接使用）\n")
          .append(petProfile == null || petProfile.isBlank() ? "（未登记宠物档案，仅用用户提供的信息）" : petProfile)
          .append("\n");
        return sb.toString();
    }

    /**
     * Agent 回复（草稿文本 + usage）。
     */
    public record AgentReply(String text, AiProviderResponse usageResponse) {}
}
