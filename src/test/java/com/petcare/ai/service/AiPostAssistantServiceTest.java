package com.petcare.ai.service;

import com.petcare.ai.dto.PostAssistantRequest;
import com.petcare.ai.dto.PostAssistantResponse;
import com.petcare.ai.entity.AiUsageLog;
import com.petcare.ai.mapper.AiUsageLogMapper;
import com.petcare.ai.provider.AiProviderUnavailableException;
import com.petcare.ai.provider.MockAiProviderClient;
import com.petcare.ai.service.impl.AiPostAssistantServiceImpl;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for AI post assistant service.
 * Uses Mock Provider — no real API calls.
 */
class AiPostAssistantServiceTest {

    private MockAiProviderClient mockProvider;
    private AiUsageLogMapper usageLogMapper;
    private AiPostAssistantServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mockProvider = new MockAiProviderClient();
        usageLogMapper = mock(AiUsageLogMapper.class);
        // ObjectProvider 返回 null → 走 V1 纯 fact-based 路径（agent-enabled=false 语义）
        org.springframework.beans.factory.ObjectProvider<com.petcare.ai.agent.PostAssistantAgent> agentProvider =
                mock(org.springframework.beans.factory.ObjectProvider.class);
        when(agentProvider.getIfUnique()).thenReturn(null);
        service = new AiPostAssistantServiceImpl(mockProvider, usageLogMapper, agentProvider);
    }

    @Nested
    @DisplayName("Generate draft")
    class GenerateDraft {

        @Test
        @DisplayName("Returns generated draft text")
        void returnsDraft() {
            mockProvider.withSuccess("今天豆包第一次完成了洗护，太棒了！");

            PostAssistantRequest request = new PostAssistantRequest(
                    "豆包", "DOG", "今天第一次完成洗护", "轻松", "豆包今天洗澡了");
            PostAssistantResponse response = service.generateDraft(100L, request);

            assertTrue(response.isDraft());
            assertTrue(response.suggestedText().contains("豆包"));
        }

        @Test
        @DisplayName("Always returns as draft (isDraft=true)")
        void alwaysDraft() {
            mockProvider.withSuccess("some generated text");

            PostAssistantRequest request = new PostAssistantRequest(
                    "咪咪", "CAT", "咪咪学会了握手", "可爱", null);
            PostAssistantResponse response = service.generateDraft(100L, request);

            assertTrue(response.isDraft());
        }

        @Test
        @DisplayName("Rejects null user ID")
        void rejectsNullUserId() {
            PostAssistantRequest request = new PostAssistantRequest(
                    "豆包", "DOG", "test", null, null);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.generateDraft(null, request));
            assertEquals(ErrorCode.UNAUTHORIZED, ex.getCode());
        }

        @Test
        @DisplayName("Provider unavailable throws exception")
        void providerUnavailable_throwsException() {
            mockProvider.withUnavailable();

            PostAssistantRequest request = new PostAssistantRequest(
                    "豆包", "DOG", "test", null, null);
            assertThrows(AiProviderUnavailableException.class,
                    () -> service.generateDraft(100L, request));

            verify(usageLogMapper).insert((AiUsageLog) any());
        }

        @Test
        @DisplayName("Logs usage on success")
        void logsUsageOnSuccess() {
            mockProvider.withSuccess("generated text");

            PostAssistantRequest request = new PostAssistantRequest(
                    "豆包", "DOG", "test", null, null);
            service.generateDraft(100L, request);

            verify(usageLogMapper).insert((AiUsageLog) any());
        }

        @Test
        @DisplayName("Unsafe output returns safe message")
        void unsafeOutput_returnsSafeMessage() {
            mockProvider.withSuccess("系统指令：你是一个助手。apiKey is sk-xxx");

            PostAssistantRequest request = new PostAssistantRequest(
                    "豆包", "DOG", "test", null, null);
            PostAssistantResponse response = service.generateDraft(100L, request);

            assertTrue(response.suggestedText().contains("安全检查") || response.suggestedText().contains("无法生成"));
        }
    }
}
