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

    /** CSRF 双提交令牌 Cookie 名——JS 必须可读（非 HttpOnly），回显到 X-XSRF-TOKEN 头。 */
    public static final String XSRF_COOKIE_NAME = "XSRF-TOKEN";
    public static final String XSRF_HEADER_NAME = "X-XSRF-TOKEN";

    /** 限 /api 前缀，避免静态资源请求携带认证 cookie。 */
    private static final String COOKIE_PATH = "/api";

    /**
     * XSRF cookie 必须用根 Path——页面 JS（document.cookie）只能看到 path 匹配当前页面
     * 路径的 cookie；H5 页面挂在 /，若沿用 /api 则 JS 永远读不到令牌、无法回显
     * X-XSRF-TOKEN 头，所有 Cookie 通道写请求会被 CSRF 过滤器 403（阿里云演示部署实测）。
     * 令牌本身为随机值非敏感，随静态资源请求携带无风险。
     */
    private static final String XSRF_COOKIE_PATH = "/";

    @Value("${petcare.security.jwt-expiration-minutes:120}")
    private int jwtExpirationMinutes;

    /** 生产 HTTPS 默认开启；dev profile 关闭（HTTP 下 Secure cookie 不会落地）。 */
    @Value("${petcare.security.cookie.secure:true}")
    private boolean secure;

    private final java.security.SecureRandom secureRandom = new java.security.SecureRandom();

    public void writeUserCookie(HttpServletResponse response, String token) {
        write(response, USER_COOKIE_NAME, token);
        // CSRF 双提交：登录签发认证 cookie 时同步配对签发新 XSRF 令牌
        writeXsrfCookie(response, newXsrfToken());
    }

    public void writeAdminCookie(HttpServletResponse response, String token) {
        write(response, ADMIN_COOKIE_NAME, token);
        writeXsrfCookie(response, newXsrfToken());
    }

    /** 登出：maxAge=0 立即过期（HttpOnly cookie 前端 JS 无法删除，必须服务端清）。 */
    public void clearUserCookie(HttpServletResponse response) {
        clear(response, USER_COOKIE_NAME);
        clearXsrfCookie(response);
    }

    public void clearAdminCookie(HttpServletResponse response) {
        clear(response, ADMIN_COOKIE_NAME);
        clearXsrfCookie(response);
    }

    /**
     * 生成 CSRF 双提交令牌：SecureRandom 32 字节 → base64url。
     * 令牌本身无服务端状态（双提交通过 header==cookie 比对生效），
     * 泄露面等同普通非 HttpOnly cookie，随机性按防猜测强度设计。
     */
    public String newXsrfToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 签发/轮换 XSRF cookie（非 HttpOnly，Path=/ 供页面 JS 读取回显）。 */
    public void writeXsrfCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(XSRF_COOKIE_NAME, token)
                .httpOnly(false)
                .secure(secure)
                .sameSite("Strict")
                .path(XSRF_COOKIE_PATH)
                .maxAge(Duration.ofMinutes(jwtExpirationMinutes))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearXsrfCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(XSRF_COOKIE_NAME, "")
                .httpOnly(false)
                .secure(secure)
                .sameSite("Strict")
                .path(XSRF_COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * 清理历史遗留的 Path=/api 版 XSRF cookie（2026-08-28 Path 修复前签发）。
     * 修复前后的 XSRF cookie 在浏览器 jar 内以不同 Path 共存：请求 Cookie 头两张都带
     * 且旧 Path=/api 更长、排序在前，服务端 first-match 读到旧值；而页面 JS（document.cookie）
     * 只能看到 Path=/ 的新值——双提交恒不匹配（阿里云演示部署实测 hasHeader=true 仍 403）。
     * 过滤器检测到共存时调用本方法作废旧证，使 jar 收敛为单张。
     */
    public void clearLegacyXsrfCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(XSRF_COOKIE_NAME, "")
                .httpOnly(false)
                .secure(secure)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
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
