package com.petcare.ai.provider;

import java.util.List;
import java.util.stream.Collectors;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

/**
 * V2 AI Provider 实现：基于 langchain4j {@link ChatModel}（D-013，M8.0）。
 * <p>
 * 作为 {@link AiProviderClient} 端口的第三实现，与 V1 {@link DeepSeekAiProviderClient} 并存：
 * 由 {@code petcare.ai.provider-type=langchain4j} + {@code agent-enabled=true} 激活
 * （{@code RagConfig} 装配 {@link ChatModel} bean）。
 * <p>
 * <b>架构守卫</b>（{@link AiProviderArchitectureTest}）：本类字段仅含 {@link ChatModel}
 * （langchain4j 类型），不含 DataSource/Mapper/MyBatis——纯 LLM 调用，RAG 检索由 {@code ai.rag} 包承担。
 * <p>
 * <b>降级哲学</b>（对标 {@link DeepSeekAiProviderClient}）：配置缺失（{@code chatModel} 为 null）
 * 时 Bean 仍可构造，{@link #complete} 调用时抛 {@link AiProviderUnavailableException}（503），
 * 不影响应用启动。
 */
public class LangChain4jProviderClient implements AiProviderClient {

    /** langchain4j ChatModel（OpenAiChatModel 或测试 mock）。null=未配置，调用时抛 503。 */
    private final ChatModel chatModel;

    public LangChain4jProviderClient(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public AiProviderResponse complete(AiProviderRequest request) {
        if (chatModel == null) {
            throw new AiProviderUnavailableException("langchain4j ChatModel 未配置（AI_AGENT_ENABLED / AI_RAG_ENABLED 未启用）");
        }
        try {
            List<ChatMessage> messages = toLangChain4jMessages(request.messages());
            ChatResponse response = chatModel.chat(ChatRequest.builder().messages(messages).build());
            return toAiProviderResponse(response);
        } catch (AiProviderException e) {
            throw e; // 本类内部已映射的分级异常，原样抛出
        } catch (RuntimeException e) {
            throw mapException(e);
        }
    }

    /** 把 AiProviderMessage（role+content）映射为 langchain4j ChatMessage。 */
    private List<ChatMessage> toLangChain4jMessages(List<AiProviderMessage> messages) {
        return messages.stream().map(m -> {
            String role = m.role();
            String content = m.content();
            return switch (role == null ? "user" : role.toLowerCase()) {
                case "system" -> SystemMessage.from(content);
                case "assistant" -> AiMessage.from(content);
                default -> UserMessage.from(content);
            };
        }).collect(Collectors.toList());
    }

    /** 把 langchain4j ChatResponse 映射为端口 AiProviderResponse。 */
    private AiProviderResponse toAiProviderResponse(ChatResponse response) {
        String assistantText = response.aiMessage() == null ? "" : response.aiMessage().text();
        if (assistantText == null) {
            assistantText = "";
        }
        String modelName = response.modelName();
        if (modelName == null || modelName.isBlank()) {
            modelName = "langchain4j-unknown";
        }
        TokenUsage usage = response.tokenUsage();
        int prompt = usage == null ? 0 : usage.inputTokenCount() == null ? 0 : usage.inputTokenCount();
        int completion = usage == null ? 0 : usage.outputTokenCount() == null ? 0 : usage.outputTokenCount();
        String providerRequestId = response.id();
        return new AiProviderResponse(
                assistantText,
                modelName,
                new AiProviderUsage(prompt, completion, prompt + completion),
                providerRequestId == null ? "langchain4j-" + System.nanoTime() : providerRequestId
        );
    }

    /**
     * 异常分级（对标 DeepSeekAiProviderClient）。
     * langchain4j 异常类型归一为 {@link AiProviderUnavailableException}（503，可重试）或
     * {@link AiProviderException}（上游 4xx/解析错，不重试）。原始 cause message 不外泄。
     * <p>
     * 检查 cause chain（langchain4j 常把 IOException/TimeoutException 包进 RuntimeException）。
     */
    private AiProviderException mapException(RuntimeException e) {
        if (isUnavailable(e)) {
            return new AiProviderUnavailableException("langchain4j provider 不可达");
        }
        return new AiProviderException("LANGCHAIN4J_UNEXPECTED", "langchain4j 调用失败");
    }

    /** 沿 cause chain 检查是否为不可达类异常（超时/连接/中断）。 */
    private boolean isUnavailable(Throwable e) {
        Throwable cur = e;
        int depth = 0;
        while (cur != null && depth < 10) {
            String name = cur.getClass().getName();
            if (name.contains("Timeout") || name.contains("ConnectException")
                    || name.contains("Interrupted") || name.contains("Unreachable")
                    || name.contains("UnknownHost")) {
                return true;
            }
            cur = cur.getCause();
            depth++;
        }
        return false;
    }
}
