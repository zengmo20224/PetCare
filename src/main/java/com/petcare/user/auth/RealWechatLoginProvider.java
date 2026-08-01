package com.petcare.user.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcare.common.config.WechatProperties;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Real WeChat login provider that calls {@code GET /sns/jscode2session}.
 *
 * <p>Activated only when {@code petcare.wechat.mode=real} (see {@code WechatLoginConfig}).
 *
 * <p>Security constraints (AGENTS.md §4):
 * <ul>
 *   <li>Raw upstream JSON, {@code errmsg} text, and stack traces are never exposed
 *       in exception messages — only stable error codes and generic Chinese text.</li>
 *   <li>Logs may reference {@code errcode} numerically but never {@code secret}.</li>
 *   <li>Upstream 4xx/5xx/timeout/network failures all collapse to
 *       {@link ErrorCode#WECHAT_UNAVAILABLE}.</li>
 * </ul>
 */
public class RealWechatLoginProvider implements WechatLoginProvider {

    private static final Logger log = LoggerFactory.getLogger(RealWechatLoginProvider.class);

    private static final String JSCODE2SESSION_URL =
            "https://api.weixin.qq.com/sns/jscode2session";

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(15);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String appid;
    private final String secret;

    public RealWechatLoginProvider(WechatProperties properties, RestClient.Builder builder) {
        this.appid = properties.appid();
        this.secret = properties.secret();
        this.objectMapper = new ObjectMapper();

        Duration connectTimeout = properties.connectTimeout() != null && properties.connectTimeout() > 0
                ? Duration.ofMillis(properties.connectTimeout())
                : DEFAULT_CONNECT_TIMEOUT;
        Duration readTimeout = properties.readTimeout() != null && properties.readTimeout() > 0
                ? Duration.ofMillis(properties.readTimeout())
                : DEFAULT_READ_TIMEOUT;

        // Mirror the DeepSeekAiProviderClient pattern: derive timeouts from properties but
        // build the RestClient from the auto-configured builder without replacing its
        // request factory, so Spring Boot's RestClientBuilder + MockRestServiceServer
        // (in tests) remain in control of the underlying HTTP layer.
        this.restClient = builder.baseUrl(JSCODE2SESSION_URL).build();

        log.info("RealWechatLoginProvider initialized: appid present={}, connectTimeout={}ms, readTimeout={}ms",
                appid != null && !appid.isBlank(), connectTimeout.toMillis(), readTimeout.toMillis());
    }

    @Override
    public WechatLoginResult login(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.WECHAT_JS_CODE_INVALID, "微信授权码不能为空");
        }
        if (appid == null || appid.isBlank() || secret == null || secret.isBlank()) {
            // Configuration error — surface a safe, generic message; never echo secret.
            log.error("RealWechatLoginProvider called without appid/secret configured");
            throw new BusinessException(ErrorCode.WECHAT_UNAVAILABLE, "微信登录未正确配置");
        }

        String rawJson;
        try {
            rawJson = restClient.get()
                    .uri("/sns/jscode2session?appid={appid}&secret={secret}"
                            + "&js_code={code}&grant_type=authorization_code",
                            appid, secret, code)
                    .retrieve()
                    .body(String.class);
        } catch (ResourceAccessException e) {
            log.warn("WeChat jscode2session network/timeout failure: {}", e.getMessage());
            throw new BusinessException(ErrorCode.WECHAT_UNAVAILABLE, "微信服务暂不可用");
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.warn("WeChat jscode2session upstream HTTP failure: status={}", e.getStatusCode());
            throw new BusinessException(ErrorCode.WECHAT_UNAVAILABLE, "微信服务暂不可用");
        } catch (RuntimeException e) {
            log.warn("WeChat jscode2session unexpected failure: {}", e.getMessage());
            throw new BusinessException(ErrorCode.WECHAT_UNAVAILABLE, "微信服务暂不可用");
        }

        if (rawJson == null || rawJson.isBlank()) {
            log.warn("WeChat jscode2session returned empty body");
            throw new BusinessException(ErrorCode.WECHAT_UNAVAILABLE, "微信服务暂不可用");
        }

        try {
            JsonNode root = objectMapper.readTree(rawJson);
            int errcode = root.path("errcode").asInt(0);
            if (errcode != 0) {
                // Log only the numeric code; never echo errmsg (may contain secret-adjacent hints).
                log.warn("WeChat jscode2session rejected code: errcode={}", errcode);
                throw new BusinessException(ErrorCode.WECHAT_JS_CODE_INVALID, "微信授权码无效");
            }
            String openid = root.path("openid").asText(null);
            if (openid == null || openid.isBlank()) {
                log.warn("WeChat jscode2session returned success without openid");
                throw new BusinessException(ErrorCode.WECHAT_UNAVAILABLE, "微信服务暂不可用");
            }
            String unionid = root.path("unionid").asText(null);
            if (unionid != null && unionid.isBlank()) {
                unionid = null;
            }
            // userId intentionally null — account linkage happens in the application service.
            return new WechatLoginResult(openid, unionid, null);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("WeChat jscode2session response parse failure: {}", e.getMessage());
            throw new BusinessException(ErrorCode.WECHAT_UNAVAILABLE, "微信服务暂不可用");
        }
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
