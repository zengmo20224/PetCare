package com.petcare.ai.service;

import com.petcare.ai.domain.CustomerServiceContext;
import com.petcare.ai.domain.CustomerServiceContextBuilder;
import com.petcare.ai.dto.AiMessageCreateRequest;
import com.petcare.ai.dto.AiMessageResponse;
import com.petcare.ai.entity.AiConversation;
import com.petcare.ai.entity.AiMessage;
import com.petcare.ai.mapper.AiConversationMapper;
import com.petcare.ai.mapper.AiMessageMapper;
import com.petcare.ai.mapper.AiUsageLogMapper;
import com.petcare.ai.provider.AiProviderMessage;
import com.petcare.ai.provider.AiProviderRequest;
import com.petcare.ai.provider.MockAiProviderClient;
import com.petcare.ai.service.impl.AiConversationApplicationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies multi-turn conversation history is correctly fed to the provider.
 * Regression guard for the single-turn amnesia fix (D-004 修订 2026-07-21).
 */
class AiConversationMultiTurnTest {

    private AiConversationMapper conversationMapper;
    private AiMessageMapper messageMapper;
    private AiUsageLogMapper usageLogMapper;
    private MockAiProviderClient mockProvider;
    private CustomerServiceContextBuilder contextBuilder;
    private AiConversationApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        conversationMapper = mock(AiConversationMapper.class);
        messageMapper = mock(AiMessageMapper.class);
        usageLogMapper = mock(AiUsageLogMapper.class);
        mockProvider = new MockAiProviderClient();
        contextBuilder = mock(CustomerServiceContextBuilder.class);

        // V1 路径：ObjectProvider 返回 null（无 RAG，走全量塞）
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<com.petcare.ai.rag.RagRetrievalService> nullRagProvider =
                org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(nullRagProvider.getIfUnique()).thenReturn(null);

        // V1 路径：ObjectProvider 返回 null（agent-enabled=false，无工具调用）
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<com.petcare.ai.agent.CustomerServiceAgent> nullAgentProvider =
                org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(nullAgentProvider.getIfUnique()).thenReturn(null);

        service = new AiConversationApplicationServiceImpl(
                conversationMapper, messageMapper, usageLogMapper,
                mockProvider, contextBuilder, nullRagProvider, nullAgentProvider
        );
    }

    @Test
    @DisplayName("PET_CHAT: prior turns are included as history before the current message")
    void petChat_includesHistory() {
        setupConversation(1L, 100L, "PET_CHAT");
        // Simulate two prior turns already persisted.
        when(messageMapper.selectList(any())).thenReturn(buildHistory(
                "你好", "你好呀，想聊什么？",
                "我家有只金毛", "金毛很可爱！"
        ));
        stubMessageInsert();

        mockProvider.withSuccess("金毛每天需要较多运动量。");

        AiMessageResponse response = service.sendMessage(100L, 1L,
                new AiMessageCreateRequest("它需要多少运动量？"));

        assertEquals("金毛每天需要较多运动量。", response.content());

        AiProviderRequest captured = mockProvider.getLastCapturedRequest();
        assertNotNull(captured);
        List<AiProviderMessage> msgs = captured.messages();
        // system + 4 history + current user = 6
        assertEquals(6, msgs.size());
        assertEquals("system", msgs.get(0).role());
        assertEquals("你好", msgs.get(1).content());
        assertEquals("assistant", msgs.get(2).role());
        assertEquals("它需要多少运动量？", msgs.get(5).content());
    }

    @Test
    @DisplayName("CUSTOMER_SERVICE: prior turns are included after system prompt")
    void customerService_includesHistory() {
        setupConversation(2L, 100L, "CUSTOMER_SERVICE");
        when(messageMapper.selectList(any())).thenReturn(buildHistory(
                "营业时间", "9:00-18:00"
        ));
        stubMessageInsert();
        when(contextBuilder.build()).thenReturn(new CustomerServiceContext(
                "宠物乐园", null, "9:00-18:00", null, null, null,
                List.of(), List.of(), List.of(), true
        ));

        mockProvider.withSuccess("洗护服务请到店咨询。");

        service.sendMessage(100L, 2L,
                new AiMessageCreateRequest("洗护多少钱？"));

        AiProviderRequest captured = mockProvider.getLastCapturedRequest();
        assertNotNull(captured);
        // system + 2 history + current user = 4
        assertEquals(4, captured.messages().size());
        assertEquals("system", captured.messages().get(0).role());
        assertEquals("营业时间", captured.messages().get(1).content());
        assertEquals("洗护多少钱？", captured.messages().get(3).content());
    }

    @Test
    @DisplayName("History is capped to the most recent N turns")
    void history_isCapped() {
        setupConversation(3L, 100L, "PET_CHAT");
        // Build 15 turns (30 messages) — exceeds the 10-turn cap.
        List<AiMessage> longHistory = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            longHistory.add(msg("user", "u" + i));
            longHistory.add(msg("assistant", "a" + i));
        }
        // selectList returns DESC ordering (newest first) — service reverses internally.
        java.util.Collections.reverse(longHistory);
        when(messageMapper.selectList(any())).thenReturn(longHistory);
        stubMessageInsert();

        mockProvider.withSuccess("ok");

        service.sendMessage(100L, 3L, new AiMessageCreateRequest("next"));

        AiProviderRequest captured = mockProvider.getLastCapturedRequest();
        // system + 20 capped history + current user = 22
        assertEquals(22, captured.messages().size());
        // Oldest kept should be turn 6 (u6), since turns 1-5 dropped off.
        assertEquals("u6", captured.messages().get(1).content());
        assertEquals("next", captured.messages().get(21).content());
    }

    private void setupConversation(Long id, Long userId, String type) {
        AiConversation conv = new AiConversation();
        conv.setId(id);
        conv.setUserId(userId);
        conv.setConversationType(type);
        when(conversationMapper.selectById(id)).thenReturn(conv);
    }

    private void stubMessageInsert() {
        doAnswer(inv -> {
            AiMessage m = inv.getArgument(0);
            m.setId(10L);
            return 1;
        }).when(messageMapper).insert(any(AiMessage.class));
    }

    private List<AiMessage> buildHistory(String... pairs) {
        List<AiMessage> list = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            list.add(msg("user", pairs[i]));
            list.add(msg("assistant", pairs[i + 1]));
        }
        // selectList returns DESC; mirror that so the service's reverse yields oldest-first.
        java.util.Collections.reverse(list);
        return list;
    }

    private AiMessage msg(String role, String content) {
        AiMessage m = new AiMessage();
        m.setRole(role);
        m.setContent(content);
        return m;
    }
}
