package com.petcare.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcare.common.config.DeepSeekProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Real DeepSeek LLM adapter for {@link AiProviderClient}.
 *
 * <p>Activated only when {@code petcare.ai.provider-enabled=true} (see {@code AiConfig}).
 * Calls {@code POST {base-url}/chat/completions} with the OpenAI-compatible schema.
 *
 * <p>Architecture constraints enforced by {@code AiProviderArchitectureTest}:
 * <ul>
 *   <li>No Mapper / DataSource / MyBatis / BaseEntity types in this class.</li>
 *   <li>{@link AiProviderRequest}/{@link AiProviderResponse} must not carry api key, base url,
 *       model name, sql, raw error, or headers — so those never cross this boundary.</li>
 *   <li>Upstream failures are mapped to {@link AiProviderUnavailableException} (unreachable/timeout/5xx)
 *       or {@link AiProviderException} (4xx/parse error). Raw body and headers are never leaked.</li>
 * </ul>
 */
public class DeepSeekAiProviderClient implements AiProviderClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekAiProviderClient.class);

    /** Max number of recent messages preserved when building the upstream payload. */
    private static final int MAX_HISTORY_MESSAGES = 20;

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(60);
    private static final int DEFAULT_MAX_TOKENS = 1024;
    private static final int DEFAULT_MAX_RETRIES = 1;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final Integer maxTokens;
    private final int maxRetries;
    /** Null when configuration is incomplete; {@link #complete} then throws 503. */
    private final String misconfigurationMessage;

    public DeepSeekAiProviderClient(DeepSeekProperties properties, RestClient.Builder builder) {
        if (properties == null) {
            this.misconfigurationMessage = "DeepSeek 配置缺失";
            this.restClient = null;
            this.objectMapper = null;
            this.model = null;
            this.maxTokens = null;
            this.maxRetries = DEFAULT_MAX_RETRIES;
            log.warn("DeepSeekAiProviderClient disabled: {}", this.misconfigurationMessage);
            return;
        }

        String apiKey = properties.apiKey();
        String configModel = properties.model();
        if (apiKey == null || apiKey.isBlank()) {
            this.misconfigurationMessage = "AI 服务未配置 DEEPSEEK_API_KEY";
        } else if (configModel == null || configModel.isBlank()) {
            this.misconfigurationMessage = "AI 服务未配置 DEEPSEEK_MODEL";
        } else {
            this.misconfigurationMessage = null;
        }

        String baseUrl = properties.baseUrl() == null || properties.baseUrl().isBlank()
                ? "https://api.deepseek.com"
                : properties.baseUrl();
        Duration connectTimeout = properties.connectTimeout() != null && properties.connectTimeout() > 0
                ? Duration.ofMillis(properties.connectTimeout())
                : DEFAULT_CONNECT_TIMEOUT;
        Duration readTimeout = properties.readTimeout() != null && properties.readTimeout() > 0
                ? Duration.ofMillis(properties.readTimeout())
                : DEFAULT_READ_TIMEOUT;

        this.model = configModel;
        this.maxTokens = properties.maxTokens() != null && properties.maxTokens() > 0
                ? properties.maxTokens()
                : DEFAULT_MAX_TOKENS;
        this.maxRetries = properties.maxRetries() != null && properties.maxRetries() >= 0
                ? properties.maxRetries()
                : DEFAULT_MAX_RETRIES;
        this.objectMapper = new ObjectMapper();

        if (this.misconfigurationMessage != null) {
            // Build a client without Authorization header so the bean can still be constructed;
            // every call will fail fast in {@link #complete}.
            this.restClient = builder.baseUrl(baseUrl).build();
            log.warn("DeepSeekAiProviderClient disabled: {}", this.misconfigurationMessage);
        } else {
            this.restClient = builder
                    .baseUrl(baseUrl)
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .build();
            log.info("DeepSeekAiProviderClient initialized: model={}, maxTokens={}, maxRetries={}",
                    model, this.maxTokens, this.maxRetries);
        }
    }

    @Override
    public AiProviderResponse complete(AiProviderRequest request) {
        if (misconfigurationMessage != null) {
            log.warn("DeepSeek call rejected [DEEPSEEK_NOT_CONFIGURED]: {}", misconfigurationMessage);
            throw new AiProviderUnavailableException(misconfigurationMessage);
        }

        Map<String, Object> payload = buildPayload(request);

        RestClient.ResponseSpec spec = restClient.post()
                .uri("/chat/completions")
                .body(payload)
                .retrieve();

        // Retries only for transient (timeout / 5xx) failures, never for 4xx.
        Exception lastTransient = null;
        int attempts = Math.max(1, maxRetries + 1);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                String rawJson = spec.body(String.class);
                return parseResponse(rawJson);
            } catch (ResourceAccessException e) {
                // Connect / read timeout / unknown host
                lastTransient = e;
                log.warn("DeepSeek transient failure (attempt {}/{}): {}",
                        attempt, attempts, e.getMessage());
            } catch (HttpServerErrorException e) {
                // 5xx — upstream fault, worth retrying
                lastTransient = e;
                log.warn("DeepSeek upstream 5xx (attempt {}/{}): status={}",
                        attempt, attempts, e.getStatusCode());
            } catch (HttpClientErrorException e) {
                // 4xx — client error (auth, quota, bad request). Do not retry.
                log.error("DeepSeek upstream 4xx [DEEPSEEK_HTTP_4XX]: status={}", e.getStatusCode());
                throw new AiProviderException("DEEPSEEK_HTTP_4XX", "上游拒绝请求");
            } catch (AiProviderException e) {
                // Parse error thrown inside parseResponse — propagate as-is.
                throw e;
            } catch (RuntimeException e) {
                log.error("DeepSeek unexpected error [DEEPSEEK_UNEXPECTED]: {}", e.getMessage());
                throw new AiProviderException("DEEPSEEK_UNEXPECTED", "调用失败");
            }
        }

        // Exhausted retries.
        if (lastTransient != null) {
            log.warn("DeepSeek call failed after {} attempts [DEEPSEEK_UNAVAILABLE]: {}",
                    attempts, lastTransient.getMessage());
            throw new AiProviderUnavailableException("AI 服务暂时不可用");
        }
        // Defensive — should never reach here.
        throw new AiProviderUnavailableException("AI 服务暂时不可用");
    }

    private Map<String, Object> buildPayload(AiProviderRequest request) {
        List<Map<String, String>> upstreamMessages = new ArrayList<>();
        List<AiProviderMessage> source = request.messages();
        int from = Math.max(0, source.size() - MAX_HISTORY_MESSAGES);
        for (int i = from; i < source.size(); i++) {
            AiProviderMessage m = source.get(i);
            Map<String, String> entry = new HashMap<>();
            entry.put("role", m.role());
            entry.put("content", m.content());
            upstreamMessages.add(entry);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("messages", upstreamMessages);
        payload.put("stream", false);
        payload.put("max_tokens", maxTokens);
        return payload;
    }

    private AiProviderResponse parseResponse(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);

            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                log.error("DeepSeek response missing choices [DEEPSEEK_PARSE_ERROR]");
                throw new AiProviderException("DEEPSEEK_PARSE_ERROR", "响应格式异常");
            }

            JsonNode message = choices.get(0).path("message").path("content");
            String assistantText = message.isMissingNode() ? "" : message.asText("");
            if (assistantText.isBlank()) {
                assistantText = "";
            }

            String modelName = root.path("model").asText(model);
            String providerRequestId = root.path("id").asText(null);

            JsonNode usage = root.path("usage");
            int promptTokens = usage.path("prompt_tokens").asInt(0);
            int completionTokens = usage.path("completion_tokens").asInt(0);
            int totalTokens = usage.path("total_tokens").asInt(promptTokens + completionTokens);
            AiProviderUsage usageRecord = new AiProviderUsage(promptTokens, completionTokens, totalTokens);

            return new AiProviderResponse(assistantText, modelName, usageRecord, providerRequestId);
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            log.error("DeepSeek parse failure [DEEPSEEK_PARSE_ERROR]: {}", e.getMessage());
            throw new AiProviderException("DEEPSEEK_PARSE_ERROR", "响应解析失败");
        }
    }
}
