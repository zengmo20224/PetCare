package com.petcare.ai.provider;



/**
 * Mock AI Provider for testing.
 * Can be configured to return fixed responses, token usage, or errors.
 * Tests never access the internet or need a real DEEPSEEK_API_KEY.
 */
public class MockAiProviderClient implements AiProviderClient {

    private AiProviderResponse nextResponse;
    private RuntimeException nextException;
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
        return this;
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
        if (nextResponse != null) {
            return nextResponse;
        }
        throw new IllegalStateException("MockAiProviderClient not configured. Call withSuccess() or withError() first.");
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
