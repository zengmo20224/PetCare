package com.petcare.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response for the WeChat login endpoint.
 * Mirrors {@link PasswordLoginResponse} so the H5 client can treat both flows uniformly.
 * User ID serialized as String for snowflake precision.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WechatLoginResponse(
        String tokenType,
        String accessToken,
        int expiresInSeconds,
        UserInfo user
) {
    public record UserInfo(
            String id,
            String nickname
    ) {}
}
