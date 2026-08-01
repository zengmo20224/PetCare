package com.petcare.user.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import com.petcare.common.security.JwtTokenService;
import com.petcare.user.dto.WechatLoginResponse;
import com.petcare.user.entity.User;
import com.petcare.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the WeChat login flow.
 *
 * <p>Owns the multi-step business logic the controller must not embed (AGENTS.md §4):
 * <ol>
 *   <li>Resolve openid via the active {@link WechatLoginProvider}.</li>
 *   <li>Look up the user by openid; create one on first login.</li>
 *   <li>Block login for non-ACTIVE users.</li>
 *   <li>Sign a USER JWT and return a {@link WechatLoginResponse}.</li>
 * </ol>
 *
 * <p>Account creation is wrapped in {@code @Transactional} so the new user row and
 * any future side effects commit atomically.
 */
@Service
public class WechatLoginApplicationService {

    private final WechatLoginProvider wechatLoginProvider;
    private final UserService userService;
    private final JwtTokenService jwtTokenService;

    public WechatLoginApplicationService(WechatLoginProvider wechatLoginProvider,
                                         UserService userService,
                                         JwtTokenService jwtTokenService) {
        this.wechatLoginProvider = wechatLoginProvider;
        this.userService = userService;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * Processes a WeChat login code: resolves the openid, ensures a user row exists,
     * and returns a fresh access token.
     */
    @Transactional
    public WechatLoginResponse login(String code) {
        WechatLoginResult result = wechatLoginProvider.login(code);
        String openid = result.openid();

        User user = userService.getOne(
                new LambdaQueryWrapper<User>().eq(User::getOpenid, openid)
        );

        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            user.setUnionid(result.unionid());
            user.setNickname(buildDefaultNickname(openid));
            user.setStatus("ACTIVE");
            userService.save(user);
        } else if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.USER_BANNED, "该账号已被封禁，无法登录");
        }

        String token = jwtTokenService.signUserToken(user.getId());

        return new WechatLoginResponse(
                "Bearer",
                token,
                jwtTokenService.getExpirationSeconds(),
                new WechatLoginResponse.UserInfo(
                        String.valueOf(user.getId()),
                        user.getNickname()
                )
        );
    }

    /**
     * Builds a default nickname for a freshly created WeChat user:
     * "微信用户" + last 4 chars of the openid, mirroring common mini-program UX.
     */
    private static String buildDefaultNickname(String openid) {
        String suffix = openid != null && openid.length() >= 4
                ? openid.substring(openid.length() - 4)
                : (openid != null ? openid : "");
        return "微信用户" + suffix;
    }
}
