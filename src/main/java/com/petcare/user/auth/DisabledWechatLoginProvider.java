package com.petcare.user.auth;

import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;

/**
 * Disabled WeChat login provider.
 * Always throws an exception indicating WeChat login is not enabled.
 * Does NOT create users, generate openid, or issue tokens.
 *
 * <p>Instantiated by {@code WechatLoginConfig} as the {@code @ConditionalOnMissingBean}
 * fallback when {@code petcare.wechat.mode} is {@code disabled} or absent — no longer
 * a component-scanned {@code @Service}.
 */
public class DisabledWechatLoginProvider implements WechatLoginProvider {

    @Override
    public WechatLoginResult login(String code) {
        throw new BusinessException(ErrorCode.WECHAT_LOGIN_NOT_ENABLED, "微信登录暂未启用");
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}
