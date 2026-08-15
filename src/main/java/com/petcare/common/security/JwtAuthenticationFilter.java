package com.petcare.common.security;

import com.petcare.admin.security.AdminUserDetailsService;
import com.petcare.user.security.UserAuthLoadingService;
import com.petcare.user.security.UserPrincipal;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT authentication filter that extracts tokens via Authorization header
 * <b>or</b> HttpOnly cookie（双轨，2026-08-15）.
 *
 * Routes by tokenType:
 * - ADMIN -> AdminUserDetailsService -> AdminPrincipal
 * - USER  -> UserAuthLoadingService  -> UserPrincipal
 * - other -> no SecurityContext set, subsequent 401
 *
 * Token sources（优先级从高到低）:
 * 1. {@code Authorization: Bearer <token>}（小程序/存量客户端通道，永久保留）
 * 2. HttpOnly cookie：admin 路径先读 ADMIN_TOKEN 再 USER_TOKEN，其余反之
 *    （路径决定优先级、双名回退——同浏览器两种身份不互踩，共享端点如
 *    /api/v1/upload 不因选错 cookie 而 401；越权防护仍由下游 tokenType
 *    路由与 @PreAuthorize / authority 校验兜底）
 *
 * Behavior:
 * - No token from either source: does not error, lets Spring Security decide if
 *   auth is required (anonymous access still works on permitAll public reads)
 * - Token present but invalid/expired/disabled: rejected with 401 via the entry
 *   point and NEVER downgraded to anonymous (Phase 11-05 §6)
 * - Token valid: sets authentication in SecurityContext
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ADMIN_PATH_PREFIX = "/api/v1/admin";

    private final JwtTokenService jwtTokenService;
    private final AdminUserDetailsService adminUserDetailsService;
    private final UserAuthLoadingService userAuthLoadingService;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService,
                                   AdminUserDetailsService adminUserDetailsService,
                                   UserAuthLoadingService userAuthLoadingService,
                                   RestAuthenticationEntryPoint authenticationEntryPoint) {
        this.jwtTokenService = jwtTokenService;
        this.adminUserDetailsService = adminUserDetailsService;
        this.userAuthLoadingService = userAuthLoadingService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    /**
     * Must run on ASYNC dispatch (2026-08-15 验收缺陷修复)。
     * <p>
     * Spring MVC completes async requests (SseEmitter etc.) via ASYNC dispatch, and
     * Spring Security 6's AuthorizationFilter re-authorizes on dispatch. OncePerRequestFilter
     * skips async dispatch by default, leaving the SecurityContext empty → AccessDenied on a
     * committed response → Tomcat aborts the connection without the chunked terminator,
     * hanging frontend streaming readers.
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token == null) {
            // No token from header or cookie: anonymous path. Let Spring Security
            // decide (permitAll public reads succeed; protected endpoints get 401).
            filterChain.doFilter(request, response);
            return;
        }

        // A token is present (header or cookie), so it MUST authenticate successfully.
        // Any failure (malformed, bad signature, expired, disabled/deleted subject,
        // unknown type) is rejected with 401 and never downgraded to anonymous
        // (Phase 11-05 §6).
        try {
            JwtTokenService.TokenParseResult parseResult = jwtTokenService.parseTokenForFilter(token);

            if (parseResult == null) {
                rejectInvalidToken(request, response);
                return;
            }

            switch (parseResult.tokenType()) {
                case "ADMIN" -> authenticateAdmin(parseResult.subjectId(), request);
                case "USER" -> authenticateUser(parseResult.subjectId(), request);
                default -> {
                    rejectInvalidToken(request, response);
                    return;
                }
            }
        } catch (JwtException | IllegalArgumentException e) {
            rejectInvalidToken(request, response);
            return;
        } catch (org.springframework.security.core.AuthenticationException e) {
            rejectInvalidToken(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Resolves the token: Authorization Bearer header first (小程序/存量客户端通道),
     * then HttpOnly cookie. Cookie 按"路径偏好"顺序尝试：admin 路径先 ADMIN_TOKEN
     * 再 USER_TOKEN，其余先 USER_TOKEN 再 ADMIN_TOKEN——回退保证共享端点
     * （如 POST /api/v1/upload，管理端商品图与用户头像共用）不因路径选错 cookie
     * 而 401；越权由下游 tokenType 路由与 @PreAuthorize 兜底，不会构成提权。
     * Null when neither source carries a token.
     */
    private String resolveToken(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }
        if (request.getCookies() == null) {
            return null;
        }
        String first = request.getRequestURI().startsWith(ADMIN_PATH_PREFIX)
                ? AuthCookieService.ADMIN_COOKIE_NAME
                : AuthCookieService.USER_COOKIE_NAME;
        String second = first.equals(AuthCookieService.ADMIN_COOKIE_NAME)
                ? AuthCookieService.USER_COOKIE_NAME
                : AuthCookieService.ADMIN_COOKIE_NAME;
        String firstValue = cookieValue(request, first);
        return firstValue != null ? firstValue : cookieValue(request, second);
    }

    private String cookieValue(HttpServletRequest request, String name) {
        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * Rejects a request that carried a Bearer token that failed to authenticate.
     * Clears any partial security context and lets the REST entry point write the
     * unified 401 response, then stops the chain so the request never reaches the
     * controller as anonymous.
     */
    private void rejectInvalidToken(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        SecurityContextHolder.clearContext();
        authenticationEntryPoint.commence(request, response,
                new org.springframework.security.authentication.BadCredentialsException("无效或过期的令牌"));
    }

    private void authenticateAdmin(Long adminId, HttpServletRequest request) {
        UserDetails userDetails = adminUserDetailsService.loadUserByAdminId(adminId);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void authenticateUser(Long userId, HttpServletRequest request) {
        UserPrincipal userPrincipal = userAuthLoadingService.loadActiveUserById(userId);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userPrincipal, null, userPrincipal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
