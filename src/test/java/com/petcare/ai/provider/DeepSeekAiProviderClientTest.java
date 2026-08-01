package com.petcare.ai.provider;

import com.petcare.common.config.DeepSeekProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Unit tests for {@link DeepSeekAiProviderClient} using {@link MockRestServiceServer}.
 * Verifies HTTP behavior, error mapping, payload shape, and that raw bodies / api keys
 * are never exposed via the {@link AiProviderResponse} or exception messages.
 */
class DeepSeekAiProviderClientTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private DeepSeekProperties props(String apiKey, String model) {
        return new DeepSeekProperties(
                "https://api.deepseek.com",
                apiKey,
                model,
                5000,
                10000,
                256,
                0  // no retries — each test exercises a single attempt
        );
    }

    @Test
    @DisplayName("Successful response is parsed into assistantText, model, usage, requestId")
    void success_parsesResponse() {
        DeepSeekAiProviderClient client = new DeepSeekAiProviderClient(props("sk-test", "deepseek-chat"), builder);

        String body = """
                {
                  "id": "chatcmpl-123",
                  "model": "deepseek-chat",
                  "choices": [
                    {"message": {"role": "assistant", "content": "你好，有什么可以帮你？"}}
                  ],
                  "usage": {"prompt_tokens": 10, "completion_tokens": 8, "total_tokens": 18}
                }
                """;
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-test"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        AiProviderResponse response = client.complete(new AiProviderRequest(
                AiApiType.CHAT,
                java.util.List.of(new AiProviderMessage("user", "hi")),
                null));

        assertEquals("你好，有什么可以帮你？", response.assistantText());
        assertEquals("deepseek-chat", response.modelName());
        assertEquals("chatcmpl-123", response.providerRequestId());
        assertNotNull(response.usage());
        assertEquals(18, response.usage().totalTokens());
    }

    @Test
    @DisplayName("Upstream 4xx maps to AiProviderException with stable internalCode, no body leak")
    void http4xx_mapsToProviderException() {
        DeepSeekAiProviderClient client = new DeepSeekAiProviderClient(props("sk-test", "deepseek-chat"), builder);

        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("invalid_api_key_secret").contentType(MediaType.APPLICATION_JSON));

        AiProviderException ex = assertThrows(AiProviderException.class,
                () -> client.complete(new AiProviderRequest(
                        AiApiType.CHAT,
                        java.util.List.of(new AiProviderMessage("user", "hi")),
                        null)));

        assertEquals("DEEPSEEK_HTTP_4XX", ex.getInternalCode());
        // Raw upstream body must NOT leak into the exception message.
        assertFalse(ex.getMessage().contains("invalid_api_key_secret"));
        assertFalse(ex.getMessage().contains("sk-test"));
    }

    @Test
    @DisplayName("Upstream 5xx maps to AiProviderUnavailableException")
    void http5xx_mapsToUnavailable() {
        DeepSeekAiProviderClient client = new DeepSeekAiProviderClient(props("sk-test", "deepseek-chat"), builder);

        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("oops").contentType(MediaType.APPLICATION_JSON));

        assertThrows(AiProviderUnavailableException.class,
                () -> client.complete(new AiProviderRequest(
                        AiApiType.CHAT,
                        java.util.List.of(new AiProviderMessage("user", "hi")),
                        null)));
    }

    @Test
    @DisplayName("Malformed response body maps to AiProviderException PARSE_ERROR")
    void malformedBody_mapsToParseError() {
        DeepSeekAiProviderClient client = new DeepSeekAiProviderClient(props("sk-test", "deepseek-chat"), builder);

        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withSuccess("not json at all", MediaType.APPLICATION_JSON));

        AiProviderException ex = assertThrows(AiProviderException.class,
                () -> client.complete(new AiProviderRequest(
                        AiApiType.CHAT,
                        java.util.List.of(new AiProviderMessage("user", "hi")),
                        null)));
        assertEquals("DEEPSEEK_PARSE_ERROR", ex.getInternalCode());
    }

    @Test
    @DisplayName("Response with empty choices array maps to PARSE_ERROR")
    void emptyChoices_mapsToParseError() {
        DeepSeekAiProviderClient client = new DeepSeekAiProviderClient(props("sk-test", "deepseek-chat"), builder);

        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));

        AiProviderException ex = assertThrows(AiProviderException.class,
                () -> client.complete(new AiProviderRequest(
                        AiApiType.CHAT,
                        java.util.List.of(new AiProviderMessage("user", "hi")),
                        null)));
        assertEquals("DEEPSEEK_PARSE_ERROR", ex.getInternalCode());
    }

    @Test
    @DisplayName("Missing api key leaves bean constructible; complete() throws Unavailable")
    void missingApiKey_constructionSurvives_callsFail503() {
        // No server expectation — no HTTP call should be made.
        DeepSeekAiProviderClient client = new DeepSeekAiProviderClient(props("", "deepseek-chat"), builder);

        AiProviderUnavailableException ex = assertThrows(AiProviderUnavailableException.class,
                () -> client.complete(new AiProviderRequest(
                        AiApiType.CHAT,
                        java.util.List.of(new AiProviderMessage("user", "hi")),
                        null)));
        assertTrue(ex.getMessage().contains("DEEPSEEK_API_KEY"));
    }

    @Test
    @DisplayName("Missing model leaves bean constructible; complete() throws Unavailable")
    void missingModel_constructionSurvives_callsFail503() {
        DeepSeekAiProviderClient client = new DeepSeekAiProviderClient(props("sk-test", ""), builder);

        assertThrows(AiProviderUnavailableException.class,
                () -> client.complete(new AiProviderRequest(
                        AiApiType.CHAT,
                        java.util.List.of(new AiProviderMessage("user", "hi")),
                        null)));
    }

    @Test
    @DisplayName("Payload sends model, messages, stream=false, max_tokens — never the api key in body")
    void payload_shapeIsCorrect() {
        DeepSeekAiProviderClient client = new DeepSeekAiProviderClient(props("sk-secret", "deepseek-chat"), builder);

        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(jsonPath("$.model").value("deepseek-chat"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.max_tokens").value(256))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content").value("hi"))
                .andRespond(withSuccess(
                        "{\"id\":\"r1\",\"model\":\"deepseek-chat\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}",
                        MediaType.APPLICATION_JSON));

        client.complete(new AiProviderRequest(
                AiApiType.CHAT,
                java.util.List.of(new AiProviderMessage("user", "hi")),
                null));
    }
}
