package com.petcare.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.petcare.ai.domain.*;
import com.petcare.ai.dto.*;
import com.petcare.ai.entity.AiConversation;
import com.petcare.ai.entity.AiMessage;
import com.petcare.ai.entity.AiUsageLog;
import com.petcare.ai.enums.AiConversationType;
import com.petcare.ai.mapper.AiConversationMapper;
import com.petcare.ai.mapper.AiMessageMapper;
import com.petcare.ai.mapper.AiUsageLogMapper;
import com.petcare.ai.provider.*;
import com.petcare.ai.service.AiConversationApplicationService;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import com.petcare.common.pagination.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of AI conversation application service.
 * Handles conversation lifecycle, message routing, and AI response generation.
 */
@Service
public class AiConversationApplicationServiceImpl implements AiConversationApplicationService {

    private static final Logger log = LoggerFactory.getLogger(AiConversationApplicationServiceImpl.class);

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final AiUsageLogMapper usageLogMapper;
    private final AiProviderClient providerClient;
    private final CustomerServiceContextBuilder contextBuilder;

    public AiConversationApplicationServiceImpl(
            AiConversationMapper conversationMapper,
            AiMessageMapper messageMapper,
            AiUsageLogMapper usageLogMapper,
            AiProviderClient providerClient,
            CustomerServiceContextBuilder contextBuilder) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.usageLogMapper = usageLogMapper;
        this.providerClient = providerClient;
        this.contextBuilder = contextBuilder;
    }

    @Override
    @Transactional
    public AiConversationResponse createConversation(Long currentUserId, AiConversationCreateRequest request) {
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }

        String type = request.conversationType();
        if (AiConversationType.ADMIN_ANALYSIS.getCode().equals(type)) {
            throw new BusinessException(ErrorCode.AI_CONVERSATION_TYPE_INVALID, "用户不能创建管理员分析会话");
        }

        AiConversation conversation = new AiConversation();
        conversation.setUserId(currentUserId);
        conversation.setConversationType(type);
        conversation.setTitle(request.title());
        conversationMapper.insert(conversation);

        return toConversationResponse(conversation);
    }

    @Override
    public PageResponse<AiConversationResponse> getMyConversations(Long currentUserId, int page, int size) {
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }

        Page<AiConversation> pageParam = new Page<>(page, size);
        Page<AiConversation> result = conversationMapper.selectPage(pageParam,
                new QueryWrapper<AiConversation>()
                        .eq("user_id", currentUserId)
                        .eq("deleted", 0)
                        .orderByDesc("create_time"));

        List<AiConversationResponse> items = result.getRecords().stream()
                .map(this::toConversationResponse)
                .toList();

        return PageResponse.of(items, result.getTotal(), page, size);
    }

    @Override
    public AiConversationResponse getConversation(Long currentUserId, Long conversationId) {
        AiConversation conversation = findAndVerifyOwnership(currentUserId, conversationId);
        return toConversationResponse(conversation);
    }

    @Override
    public AiMessageResponse sendMessage(Long currentUserId, Long conversationId, AiMessageCreateRequest request) {
        AiConversation conversation = findAndVerifyOwnership(currentUserId, conversationId);
        String type = conversation.getConversationType();

        // Step 1: Save user message in a short transaction
        AiMessage userMsg = new AiMessage();
        userMsg.setConversationId(conversationId);
        userMsg.setRole("user");
        userMsg.setContent(request.content());
        messageMapper.insert(userMsg);

        // Step 2: Route to appropriate AI service based on conversation type.
        // History is loaded BEFORE the current user message so the provider receives prior turns
        // as multi-turn context; the current message is appended separately inside the handlers.
        List<AiProviderMessage> history = loadHistory(conversationId);

        String assistantText;
        AiApiType apiType;

        if (AiConversationType.CUSTOMER_SERVICE.getCode().equals(type)) {
            assistantText = handleCustomerService(currentUserId, history, request.content());
            apiType = AiApiType.CUSTOMER_SERVICE;
        } else if (AiConversationType.PET_CHAT.getCode().equals(type)) {
            assistantText = handlePetChat(currentUserId, history, request.content());
            apiType = AiApiType.CHAT;
        } else {
            throw new BusinessException(ErrorCode.AI_CONVERSATION_TYPE_INVALID, "不支持的会话类型");
        }

        // Step 3: Save assistant message
        AiMessage assistantMsg = new AiMessage();
        assistantMsg.setConversationId(conversationId);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(assistantText);
        messageMapper.insert(assistantMsg);

        return toMessageResponse(assistantMsg);
    }

    @Override
    public PageResponse<AiMessageResponse> getMessages(Long currentUserId, Long conversationId, int page, int size) {
        findAndVerifyOwnership(currentUserId, conversationId);

        Page<AiMessage> pageParam = new Page<>(page, size);
        Page<AiMessage> result = messageMapper.selectPage(pageParam,
                new QueryWrapper<AiMessage>()
                        .eq("conversation_id", conversationId)
                        .orderByAsc("create_time"));

        List<AiMessageResponse> items = result.getRecords().stream()
                .map(this::toMessageResponse)
                .toList();

        return PageResponse.of(items, result.getTotal(), page, size);
    }

    /**
     * Handles customer service message with context grounding and multi-turn history.
     */
    private String handleCustomerService(Long currentUserId, List<AiProviderMessage> history, String userQuestion) {
        CustomerServiceContext context = contextBuilder.build();

        // If no trusted context and question requires grounding, return fallback
        if (!context.hasData() && CustomerServiceGroundingPolicy.requiresGrounding(userQuestion)) {
            return CustomerServiceGroundingPolicy.getNoContextFallback();
        }

        List<AiProviderMessage> messages = PromptFactory.buildCustomerServiceMessages(context, history, userQuestion);

        try {
            AiProviderResponse response = providerClient.complete(
                    new AiProviderRequest(AiApiType.CUSTOMER_SERVICE, messages, null));

            String output = response.assistantText();

            // Check for fabricated business facts
            if (CustomerServiceGroundingPolicy.isFabricatedBusinessFact(output, context)) {
                return CustomerServiceGroundingPolicy.getNoContextFallback();
            }

            // Check output safety
            if (AiOutputSafetyPolicy.isUnsafe(output)) {
                return "抱歉，无法生成回复。";
            }

            // Log successful usage
            logSuccessUsage(currentUserId, AiApiType.CUSTOMER_SERVICE, response);

            return output;
        } catch (AiProviderUnavailableException e) {
            logFailedUsage(currentUserId, AiApiType.CUSTOMER_SERVICE, "provider_unavailable");
            throw e;
        } catch (AiProviderException e) {
            logFailedUsage(currentUserId, AiApiType.CUSTOMER_SERVICE, e.getInternalCode());
            throw e;
        }
    }

    /**
     * Handles pet chat message with medical safety checks and multi-turn history.
     */
    private String handlePetChat(Long currentUserId, List<AiProviderMessage> history, String userMessage) {
        // Step 1: Check high-risk symptoms BEFORE calling Provider
        if (HighRiskSymptomDetector.isHighRisk(userMessage)) {
            return HighRiskSymptomDetector.getFixedSafetyResponse();
        }

        List<AiProviderMessage> messages = PromptFactory.buildPetChatMessages(history, userMessage);

        try {
            AiProviderResponse response = providerClient.complete(
                    new AiProviderRequest(AiApiType.CHAT, messages, null));

            String output = response.assistantText();

            // Step 2: Post-processing medical safety check
            if (PetMedicalSafetyPolicy.isViolation(output)) {
                return PetMedicalSafetyPolicy.getSafeFallback();
            }

            // Step 3: General output safety check
            if (AiOutputSafetyPolicy.isUnsafe(output)) {
                return "抱歉，无法生成回复。";
            }

            logSuccessUsage(currentUserId, AiApiType.CHAT, response);

            return output;
        } catch (AiProviderUnavailableException e) {
            logFailedUsage(currentUserId, AiApiType.CHAT, "provider_unavailable");
            throw e;
        } catch (AiProviderException e) {
            logFailedUsage(currentUserId, AiApiType.CHAT, e.getInternalCode());
            throw e;
        }
    }

    private void logSuccessUsage(Long userId, AiApiType apiType, AiProviderResponse response) {
        try {
            AiUsageLog usageLog = new AiUsageLog();
            usageLog.setUserId(userId);
            usageLog.setApiType(apiType.name());
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

    private void logFailedUsage(Long userId, AiApiType apiType, String errorCode) {
        try {
            AiUsageLog usageLog = new AiUsageLog();
            usageLog.setUserId(userId);
            usageLog.setApiType(apiType.name());
            usageLog.setSuccess(0);
            usageLog.setErrorMessage(errorCode);
            usageLogMapper.insert(usageLog);
        } catch (Exception e) {
            log.warn("Failed to log AI usage: {}", e.getMessage());
        }
    }

    private AiConversation findAndVerifyOwnership(Long currentUserId, Long conversationId) {
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }

        AiConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.AI_CONVERSATION_NOT_FOUND, "会话不存在");
        }

        if (!currentUserId.equals(conversation.getUserId())) {
            throw new BusinessException(ErrorCode.AI_CONVERSATION_FORBIDDEN, "无权访问该会话");
        }

        return conversation;
    }

    /**
     * Loads prior conversation messages (oldest-first) for multi-turn context.
     * Called BEFORE the current user message is persisted by the caller, so the result
     * naturally excludes the in-flight turn. Capped at the most recent N rows to bound tokens;
     * further truncation by turns happens inside {@link PromptFactory}.
     */
    private List<AiProviderMessage> loadHistory(Long conversationId) {
        int limit = PromptFactory.MAX_HISTORY_TURNS * 2 + 4;
        List<AiMessage> rows = messageMapper.selectList(
                new QueryWrapper<AiMessage>()
                        .eq("conversation_id", conversationId)
                        .in("role", "user", "assistant")
                        .orderByDesc("create_time")
                        .last("LIMIT " + limit));
        // Reverse to oldest-first for prompt construction.
        List<AiProviderMessage> history = new ArrayList<>(rows.size());
        for (int i = rows.size() - 1; i >= 0; i--) {
            AiMessage m = rows.get(i);
            history.add(new AiProviderMessage(m.getRole(), m.getContent()));
        }
        return history;
    }

    private AiConversationResponse toConversationResponse(AiConversation c) {
        return new AiConversationResponse(
                c.getId(),
                c.getConversationType(),
                c.getTitle(),
                c.getCreateTime()
        );
    }

    private AiMessageResponse toMessageResponse(AiMessage m) {
        return new AiMessageResponse(
                m.getId(),
                m.getConversationId(),
                m.getRole(),
                m.getContent(),
                m.getCreateTime()
        );
    }
}
