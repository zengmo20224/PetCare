package com.petcare.common.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 认证 Cookie 读写（HttpOnly 双轨改造，2026-08-15）。
 *
 * <p>背景：JWT 此前仅在登录响应 body 返回、由前端存 localStorage——任何 XSS 即可窃取
 * token 完成账号接管。改造为双轨：登录响应<b>同时</b> Set-Cookie（HttpOnly，JS 不可读）
 * 与 body 返回 accessToken（微信小程序运行时无 cookie，必须保留 header 通道）。</p>
 *
 * <p>安全属性：
 * <ul>
 *   <li>{@code HttpOnly}——JS 不可读，XSS 无法窃取；</li>
 *   <li>{@code SameSite=Strict}——第三方站点请求不携带，抵御 CSRF（与 cookie 上线
 *       同提交，避免出现 CSRF disabled + cookie 的裸奔窗口）；</li>
 *   <li>{@code Path=/api}——静态资源请求不携带；</li>
 *   <li>{@code Secure}——生产 HTTPS 下开启；本地 HTTP 调试经 dev profile 关闭
 *       （{@code petcare.security.cookie.secure}）；</li>
 *   <li>ADMIN/USER <b>双 cookie 名</b>——过滤器按 tokenType 路由 principal，
 *       同浏览器互不干扰。</li>
 * </ul></p>
 *
 * <p>不设 Domain（同域反代部署下设 Domain 反而放宽到子域）。JWT 本身无服务端状态，
 * logout 仅清 cookie；token 失效仍以过期时间为准（与 header 通道一致）。</p>
 */
@Component
public class AuthCookieService {

    public static final String ADMIN_COOKIE_NAME = "ADMIN_TOKEN";
    public static final String USER_COOKIE_NAME = "USER_TOKEN";

    /** 限 /api 前缀，避免静态资源请求携带认证 cookie。 */
    private static final String COOKIE_PATH = "/api";

    @Value("${petcare.security.jwt-expiration-minutes:120}")
    private int jwtExpirationMinutes;

    /** 生产 HTTPS 默认开启；dev profile 关闭（HTTP 下 Secure cookie 不会落地）。 */
    @Value("${petcare.security.cookie.secure:true}")
    private boolean secure;

    public void writeUserCookie(HttpServletResponse response, String token) {
        write(response, USER_COOKIE_NAME, token);
    }

    public void writeAdminCookie(HttpServletResponse response, String token) {
        write(response, ADMIN_COOKIE_NAME, token);
    }

    /** 登出：maxAge=0 立即过期（HttpOnly cookie 前端 JS 无法删除，必须服务端清）。 */
    public void clearUserCookie(HttpServletResponse response) {
        clear(response, USER_COOKIE_NAME);
    }

    public void clearAdminCookie(HttpServletResponse response) {
        clear(response, ADMIN_COOKIE_NAME);
    }

    private void write(HttpServletResponse response, String name, String token) {
        ResponseCookie cookie = ResponseCookie.from(name, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(Duration.ofMinutes(jwtExpirationMinutes))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clear(HttpServletResponse response, String name) {
        ResponseCookie cookie = ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
