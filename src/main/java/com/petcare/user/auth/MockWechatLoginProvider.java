package com.petcare.user.auth;

import com.petcare.common.config.WechatProperties;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Mock WeChat login provider for development and tests.
 *
 * <p>Derives a deterministic openid from the supplied code via SHA-256, so the
 * same code always resolves to the same user row. No external HTTP call is made
 * and no real WeChat secret is needed. Account creation and token signing are
 * intentionally left to {@code WechatLoginApplicationService}.
 */
public class MockWechatLoginProvider implements WechatLoginProvider {

    /** Length of the hex suffix appended after the {@code mock_} prefix. */
    private static final int HEX_SUFFIX_LENGTH = 16;

    private final WechatProperties properties;

    public MockWechatLoginProvider(WechatProperties properties) {
        this.properties = properties;
    }

    @Override
    public WechatLoginResult login(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.WECHAT_JS_CODE_INVALID, "微信授权码不能为空");
        }
        String openid = "mock_" + sha256Hex(code).substring(0, HEX_SUFFIX_LENGTH);
        // unionid / userId intentionally null: account creation belongs to the application service.
        return new WechatLoginResult(openid, null, null);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) {
                    hex.append('0');
                }
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JLS; reaching here is a JVM misconfiguration.
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
