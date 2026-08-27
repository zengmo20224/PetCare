package com.petcare.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Set;

/**
 * CSRF 双提交校验（2026-08-23 审计 M7：SameSite=Strict 之外的纵深防线）。
 *
 * <p><b>只约束 Cookie 通道的写请求</b>——双提交防御针对的是"浏览器自动携带的环境凭证"：
 * <ul>
 *   <li>Bearer header 通道（小程序）：凭证非环境性，攻击站点无法注入 → 放行；</li>
 *   <li>无认证 cookie 的匿名请求：下游 401，无需 CSRF 判定 → 放行；</li>
 *   <li>认证 cookie 存在的非豁免写请求：必须携带 {@code X-XSRF-TOKEN} 头且与
 *       {@code XSRF-TOKEN} cookie 常量时间相等，否则 403。</li>
 * </ul></p>
 *
 * <p><b>豁免端点</b>：permitAll 认证入口（登录/注册/找回密码/登出等）。
 * 登录前无会话可骑乘；登出被 CSRF 仅造成"被登出"级骚扰，不破坏数据完整性，
 * 豁免以换取存量会话/异常令牌状态下的可靠退出。</p>
 *
 * <p><b>存量会话迁移</b>：安全方法（GET 等）且持有认证 cookie 但缺 XSRF cookie 时
 * 惰性补发新令牌——SPA 首屏的读请求即完成配对，后续写请求正常通过。</p>
 *
 * <p>注册于 {@link JwtAuthenticationFilter} 之后（不依赖 SecurityContext，
 * 直接依据原始 cookie/header 判定，与认证方式解耦）。</p>
 */
@Component
public class CsrfDoubleSubmitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CsrfDoubleSubmitFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    /** permitAll 认证入口豁免清单（精确路径 + 找回密码前缀）。 */
    private static final List<String> EXEMPT_PATHS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/wechat-login",
            "/api/v1/auth/test-login",
            "/api/v1/auth/logout",
            "/api/v1/admin/auth/login",
            "/api/v1/admin/auth/logout"
    );
    private static final String EXEMPT_PREFIX = "/api/v1/auth/forgot-password/";

    private final AuthCookieService authCookieService;

    public CsrfDoubleSubmitFilter(AuthCookieService authCookieService) {
        this.authCookieService = authCookieService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String method = request.getMethod();

        if (SAFE_METHODS.contains(method)) {
            // 惰性迁移：Cookie 会话存在但 XSRF cookie 缺失（升级前登录的存量会话）→ 补发
            if (hasAuthCookie(request) && !hasNonBlankXsrfCookie(request)) {
                authCookieService.writeXsrfCookie(response, authCookieService.newXsrfToken());
            }
            filterChain.doFilter(request, response);
            return;
        }

        // Bearer 通道：凭证由调用方显式注入，攻击页面无法伪造跨站携带 → 天然免疫 CSRF
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 匿名写请求：交给下游认证判定（401），不做 CSRF 噪音拦截
        if (!hasAuthCookie(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isExempt(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String cookieToken = cookieValue(request, AuthCookieService.XSRF_COOKIE_NAME);
        String headerToken = request.getHeader(AuthCookieService.XSRF_HEADER_NAME);
        if (!tokensMatch(cookieToken, headerToken)) {
            log.warn("CSRF double-submit check failed: uri={}, hasHeader={}",
                    request.getRequestURI(), headerToken != null && !headerToken.isBlank());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"success\":false,\"data\":null,\"error\":{\"code\":\"csrf_check_failed\","
                            + "\"message\":\"安全校验失败，请刷新页面后重试\",\"details\":[]},\"meta\":null}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** 常量时间比较，两侧均须非空白。 */
    static boolean tokensMatch(String cookieToken, String headerToken) {
        if (cookieToken == null || cookieToken.isBlank() || headerToken == null || headerToken.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                cookieToken.getBytes(StandardCharsets.UTF_8),
                headerToken.getBytes(StandardCharsets.UTF_8));
    }

    static boolean isExempt(String uri) {
        if (EXEMPT_PATHS.contains(uri)) {
            return true;
        }
        return uri != null && uri.startsWith(EXEMPT_PREFIX);
    }

    private boolean hasAuthCookie(HttpServletRequest request) {
        return cookieValue(request, AuthCookieService.USER_COOKIE_NAME) != null
                || cookieValue(request, AuthCookieService.ADMIN_COOKIE_NAME) != null;
    }

    private boolean hasNonBlankXsrfCookie(HttpServletRequest request) {
        String v = cookieValue(request, AuthCookieService.XSRF_COOKIE_NAME);
        return v != null && !v.isBlank();
    }

    static String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
