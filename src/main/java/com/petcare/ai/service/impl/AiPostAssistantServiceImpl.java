package com.petcare.ai.service.impl;

import com.petcare.ai.agent.PostAssistantAgent;
import com.petcare.ai.domain.AiOutputSafetyPolicy;
import com.petcare.ai.domain.PromptFactory;
import com.petcare.ai.dto.PostAssistantRequest;
import com.petcare.ai.dto.PostAssistantResponse;
import com.petcare.ai.entity.AiUsageLog;
import com.petcare.ai.mapper.AiUsageLogMapper;
import com.petcare.ai.provider.*;
import com.petcare.ai.service.AiPostAssistantService;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Implementation of AI post assistant service.
 * Generates suggested post drafts based only on user-provided facts.
 * Never auto-publishes or modifies post review status.
 *
 * <p>M8.3：agent-enabled=true 时优先走 {@link PostAssistantAgent}（结合宠物档案个性化），
 * 否则走 V1 纯 fact-based 路径（降级）。两条路径都<b>只返回草稿</b>，
 * 不触碰发帖/审核状态（B4/T9）。</p>
 */
@Service
public class AiPostAssistantServiceImpl implements AiPostAssistantService {

    private static final Logger log = LoggerFactory.getLogger(AiPostAssistantServiceImpl.class);

    private final AiProviderClient providerClient;
    private final AiUsageLogMapper usageLogMapper;
    /** M8.3：助手 Agent（可选，agent-enabled=false 时为 null，走 V1 路径）。 */
    private final PostAssistantAgent postAssistantAgent;

    public AiPostAssistantServiceImpl(
            AiProviderClient providerClient,
            AiUsageLogMapper usageLogMapper,
            ObjectProvider<PostAssistantAgent> postAssistantAgentProvider) {
        this.providerClient = providerClient;
        this.usageLogMapper = usageLogMapper;
        this.postAssistantAgent = postAssistantAgentProvider.getIfUnique();
    }

    @Override
    public PostAssistantResponse generateDraft(Long currentUserId, PostAssistantRequest request) {
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }

        // M8.3：Agent 路径（个性化草稿，含宠物档案 Tool + 审计）
        if (postAssistantAgent != null) {
            try {
                PostAssistantAgent.AgentReply reply = postAssistantAgent.handle(currentUserId, request);
                logSuccessUsage(currentUserId, reply.usageResponse());
                return new PostAssistantResponse(reply.text());
            } catch (AiProviderUnavailableException e) {
                logFailedUsage(currentUserId, "provider_unavailable");
                throw e;
            } catch (AiProviderException e) {
                logFailedUsage(currentUserId, e.getInternalCode());
                throw e;
            }
        }

        // V1 降级路径：纯 fact-based，无宠物档案
        List<AiProviderMessage> messages = PromptFactory.buildPostAssistantMessages(
                request.petName(),
                request.petType(),
                request.event(),
                request.tone(),
                request.originalText()
        );

        try {
            AiProviderResponse response = providerClient.complete(
                    new AiProviderRequest(AiApiType.CONTENT_GENERATE, messages, null));

            String output = response.assistantText();

            // Check output safety
            if (AiOutputSafetyPolicy.isUnsafe(output)) {
                return new PostAssistantResponse("抱歉，生成内容未通过安全检查，请修改后重试。");
            }

            // Log successful usage
            logSuccessUsage(currentUserId, response);

            // Always return as draft
            return new PostAssistantResponse(output);
        } catch (AiProviderUnavailableException e) {
            logFailedUsage(currentUserId, "provider_unavailable");
            throw e;
        } catch (AiProviderException e) {
            logFailedUsage(currentUserId, e.getInternalCode());
            throw e;
        }
    }

    private void logSuccessUsage(Long userId, AiProviderResponse response) {
        try {
            AiUsageLog usageLog = new AiUsageLog();
            usageLog.setUserId(userId);
            usageLog.setApiType(AiApiType.CONTENT_GENERATE.name());
            usageLog.setModelName(response.modelName());
            if (response.usage() != null) {
                usageLog.setPromptTokens(response.usage().promptTokens());
                usageLog.setCompletionTokens(response.usage().completionTokens());
                usageLog.setTotalTokens(response.usage().totalTokens());
            }
            usageLog.setSuccess(1);
            usageLogMapper.insert(usageLog);
        } catch (Exception e) {
            log.warn("Failed to log AI usage: {}", e.getMessage());
        }
    }

    private void logFailedUsage(Long userId, String errorCode) {
        try {
            AiUsageLog usageLog = new AiUsageLog();
            usageLog.setUserId(userId);
            usageLog.setApiType(AiApiType.CONTENT_GENERATE.name());
            usageLog.setSuccess(0);
            usageLog.setErrorMessage(errorCode);
            usageLogMapper.insert(usageLog);
        } catch (Exception e) {
            log.warn("Failed to log AI usage: {}", e.getMessage());
        }
    }
}
