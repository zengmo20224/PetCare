package com.petcare.ai.provider;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;

/**
 * Mock AI Provider for testing.
 * Can be configured to return fixed responses, token usage, or errors.
 * Tests never access the internet or need a real DEEPSEEK_API_KEY.
 * <p>
 * M8.1 扩展：支持 {@link #withSequence} 多轮响应队列（用于 Agent 工具调用两轮编排测试）。
 */
public class MockAiProviderClient implements AiProviderClient {

    private AiProviderResponse nextResponse;
    private RuntimeException nextException;
    private Queue<AiProviderResponse> responseQueue;
    private int callCount;
    private AiProviderRequest lastCapturedRequest;

    public MockAiProviderClient() {
        this.callCount = 0;
    }

    /**
     * Configures the mock to return a successful response.
     */
    public MockAiProviderClient withSuccess(String assistantText) {
        this.nextResponse = new AiProviderResponse(
                assistantText,
                "mock-model",
                new AiProviderUsage(100, 50, 150),
                "mock-request-id"
        );
        this.nextException = null;
        this.responseQueue = null;
        return this;
    }

    /**
     * M8.1：配置一个有序响应队列，complete() 按调用顺序依次返回。
     * 用于 Agent 两轮编排：第一轮返回工具调用指令，第二轮返回最终回复。
     */
    public MockAiProviderClient withSequence(AiProviderResponse... responses) {
        this.responseQueue = new ArrayDeque<>(Arrays.asList(responses));
        this.nextResponse = null;
        this.nextException = null;
        return this;
    }

    /**
     * M8.1：用文本快速构造序列响应（token 数固定 mock 值）。
     */
    public MockAiProviderClient withSequence(String... assistantTexts) {
        AiProviderResponse[] responses = Arrays.stream(assistantTexts)
                .map(t -> new AiProviderResponse(t, "mock-model", new AiProviderUsage(100, 50, 150), "mock-request-id"))
                .toArray(AiProviderResponse[]::new);
        return withSequence(responses);
    }

    /**
     * Configures the mock to throw an exception.
     */
    public MockAiProviderClient withError(RuntimeException exception) {
        this.nextException = exception;
        this.nextResponse = null;
        return this;
    }

    /**
     * Configures the mock to simulate provider unavailability.
     */
    public MockAiProviderClient withUnavailable() {
        return withError(new AiProviderUnavailableException("Mock provider unavailable"));
    }

    /**
     * Configures the mock to simulate a general provider error.
     */
    public MockAiProviderClient withProviderError() {
        return withError(new AiProviderException("MOCK_ERROR", "Mock provider error"));
    }

    @Override
    public AiProviderResponse complete(AiProviderRequest request) {
        callCount++;
        this.lastCapturedRequest = request;
        if (nextException != null) {
            throw nextException;
        }
        // M8.1：优先消费序列队列
        if (responseQueue != null && !responseQueue.isEmpty()) {
            return responseQueue.poll();
        }
        if (nextResponse != null) {
            return nextResponse;
        }
        throw new IllegalStateException("MockAiProviderClient not configured. Call withSuccess()/withSequence()/withError() first.");
    }

    /**
     * Returns the number of times complete() was called.
     */
    public int getCallCount() {
        return callCount;
    }

    /**
     * Resets the mock state.
     */
    public void reset() {
        this.nextResponse = null;
        this.nextException = null;
        this.responseQueue = null;
        this.callCount = 0;
        this.lastCapturedRequest = null;
    }

    /**
     * Returns the most recent {@link AiProviderRequest} passed to {@link #complete},
     * or null if not yet called. Useful for asserting multi-turn history wiring.
     */
    public AiProviderRequest getLastCapturedRequest() {
        return lastCapturedRequest;
    }
}
