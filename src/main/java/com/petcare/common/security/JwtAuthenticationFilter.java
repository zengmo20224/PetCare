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
 * JWT authentication filter that extracts Bearer tokens from Authorization header.
 *
 * Routes by tokenType:
 * - ADMIN -> AdminUserDetailsService -> AdminPrincipal
 * - USER  -> UserAuthLoadingService  -> UserPrincipal
 * - other -> no SecurityContext set, subsequent 401
 *
 * Behavior:
 * - Only processes Authorization: Bearer <token>
 * - Token missing: does not error, lets Spring Security decide if auth is required
 *   (anonymous access still works on permitAll public reads)
 * - Token present but invalid/expired/disabled: rejected with 401 via the entry
 *   point and NEVER downgraded to anonymous (Phase 11-05 §6)
 * - Token valid: sets authentication in SecurityContext
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

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
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            // No Bearer token present: anonymous path. Let Spring Security decide
            // (permitAll public reads succeed; protected endpoints get 401).
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        // A Bearer token is present, so it MUST authenticate successfully. Any failure
        // (malformed, bad signature, expired, disabled/deleted subject, unknown type)
        // is rejected with 401 and never downgraded to anonymous (Phase 11-05 §6).
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
