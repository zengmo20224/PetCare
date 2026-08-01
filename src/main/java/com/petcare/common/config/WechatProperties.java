package com.petcare.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WeChat login provider configuration properties.
 *
 * <p>{@code mode} selects which provider implementation is active:
 * <ul>
 *   <li>{@code disabled} (default) — endpoint returns 422 wechat_login_not_enabled.</li>
 *   <li>{@code mock} — derives a deterministic openid locally; used for dev/tests.</li>
 *   <li>{@code real} — calls the WeChat {@code jscode2session} API.</li>
 * </ul>
 *
 * <p>Secret and appid must come from environment variables; never hard-coded.
 */
@ConfigurationProperties(prefix = "petcare.wechat")
public record WechatProperties(
        String mode,
        String appid,
        String secret,
        Integer connectTimeout,
        Integer readTimeout
) {}
