package com.petcare.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

/**
 * {@link LangChain4jProviderClient} 单元测试（不起 Spring，手动构造）。
 * <p>
 * 验证：①消息映射 ②ChatResponse 解析 ③未配置时抛 503 ④异常分级。
 * 对标 V1 {@code DeepSeekAiProviderClientTest} 纯 Mockito 风格。
 */
class LangChain4jProviderClientTest {

    @Test
    @DisplayName("未配置 ChatModel 时 complete 抛 AiProviderUnavailableException")
    void complete_unconfigured_throwsUnavailable() {
        LangChain4jProviderClient client = new LangChain4jProviderClient(null);
        AiProviderRequest req = req();
        assertThrows(AiProviderUnavailableException.class, () -> client.complete(req));
    }

    @Test
    @DisplayName("正常调用：消息映射 + Response 解析 token 与文本")
    void complete_normal_mapsMessagesAndParsesResponse() {
        ChatModel stubModel = new StubChatModel(
                ChatResponse.builder()
                        .aiMessage(AiMessage.from("你好，客服帮你解答"))
                        .tokenUsage(new TokenUsage(20, 10))
                        .id("resp-123")
                        .modelName("deepseek-chat")
                        .build());
        LangChain4jProviderClient client = new LangChain4jProviderClient(stubModel);

        AiProviderResponse resp = client.complete(new AiProviderRequest(
                AiApiType.CUSTOMER_SERVICE,
                List.of(
                        new AiProviderMessage("system", "你是客服"),
                        new AiProviderMessage("user", "营业时间")
                ),
                "corr-1"));

        assertEquals("你好，客服帮你解答", resp.assistantText());
        assertEquals("deepseek-chat", resp.modelName());
        assertEquals(20, resp.usage().promptTokens());
        assertEquals(10, resp.usage().completionTokens());
        assertEquals(30, resp.usage().totalTokens());
        assertEquals("resp-123", resp.providerRequestId());
    }

    @Test
    @DisplayName("模型抛连接超时 → AiProviderUnavailableException（503）")
    void complete_timeout_throwsUnavailable() {
        ChatModel timeoutModel = new ThrowingChatModel(
                new RuntimeException(new java.net.SocketTimeoutException("read timeout")));
        LangChain4jProviderClient client = new LangChain4jProviderClient(timeoutModel);
        assertThrows(AiProviderUnavailableException.class, () -> client.complete(req()));
    }

    @Test
    @DisplayName("模型抛其他异常 → AiProviderException（上游错误，不重试）")
    void complete_otherError_throwsProviderError() {
        ChatModel errModel = new ThrowingChatModel(new RuntimeException("400 bad request"));
        LangChain4jProviderClient client = new LangChain4jProviderClient(errModel);
        AiProviderException ex = assertThrows(AiProviderException.class, () -> client.complete(req()));
        assertEquals("LANGCHAIN4J_UNEXPECTED", ex.getInternalCode());
    }

    @Test
    @DisplayName("AiProviderException 本类映射的异常原样抛出（不被二次包装）")
    void complete_internalMappedException_passesThrough() {
        ChatModel model = new ThrowingChatModel(new AiProviderUnavailableException("already mapped"));
        LangChain4jProviderClient client = new LangChain4jProviderClient(model);
        assertTrue(assertThrows(AiProviderUnavailableException.class, () -> client.complete(req()))
                .getMessage().contains("already mapped"));
    }

    private AiProviderRequest req() {
        return new AiProviderRequest(AiApiType.CUSTOMER_SERVICE,
                List.of(new AiProviderMessage("user", "test")), null);
    }

    /** 极简 ChatModel 桩：固定返回预设 ChatResponse。 */
    private static class StubChatModel implements ChatModel {
        private final ChatResponse response;

        StubChatModel(ChatResponse response) {
            this.response = response;
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return response;
        }
    }

    /** 抛固定异常的 ChatModel 桩（用于异常分级测试）。 */
    private static class ThrowingChatModel implements ChatModel {
        private final RuntimeException toThrow;

        ThrowingChatModel(RuntimeException toThrow) {
            this.toThrow = toThrow;
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            throw toThrow;
        }
    }
}
