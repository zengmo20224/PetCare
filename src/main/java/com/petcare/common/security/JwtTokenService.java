package com.petcare.common.security;

import com.petcare.common.config.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT token service for signing and parsing admin and user access tokens.
 *
 * ADMIN JWT Claims:
 * - sub = adminId, username, role, tokenType = ADMIN
 *
 * USER JWT Claims:
 * - sub = userId, tokenType = USER
 * - No phone, openid, unionid, nickname, avatar, admin role/permissions.
 *
 * Prohibited from containing: password hash, phone, openid, unionid,
 * full permission list, API key, DB connection info.
 */
@Service
public class JwtTokenService {

    private static final String TOKEN_TYPE_ADMIN = "ADMIN";
    private static final String TOKEN_TYPE_USER = "USER";

    private final SecretKey signingKey;
    private final String issuer;
    private final int expirationMinutes;

    public JwtTokenService(SecurityProperties securityProperties) {
        this.signingKey = Keys.hmacShaKeyFor(
                securityProperties.jwtSecret().getBytes(StandardCharsets.UTF_8));
        this.issuer = securityProperties.jwtIssuer();
        this.expirationMinutes = securityProperties.jwtExpirationMinutes();
    }

    /**
     * Signs a new admin access token using the configured expiration.
     *
     * @param adminId  the admin user's ID
     * @param username the admin's username
     * @param role     the admin's role code (e.g. SUPER_ADMIN, MANAGER, STAFF)
     * @return signed JWT string
     */
    public String signAdminToken(Long adminId, String username, String role) {
        return signAdminToken(adminId, username, role, getExpirationSeconds());
    }

    /**
     * Signs a new admin access token with a custom expiration in seconds.
     * Used internally and for testing.
     *
     * @param adminId           the admin user's ID
     * @param username          the admin's username
     * @param role              the admin's role code
     * @param expirationSeconds custom expiration in seconds from now
     * @return signed JWT string
     */
    public String signAdminToken(Long adminId, String username, String role, int expirationSeconds) {
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(expirationSeconds);

        return Jwts.builder()
                .subject(String.valueOf(adminId))
                .claim("username", username)
                .claim("role", role)
                .claim("tokenType", TOKEN_TYPE_ADMIN)
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Parses and validates a JWT token.
     * Verifies signature, expiration, and issuer.
     *
     * @param token the JWT string
     * @return parsed claims
     * @throws JwtException if the token is invalid, expired, has wrong signature, or wrong issuer
     */
    public Claims parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (!issuer.equals(claims.getIssuer())) {
            throw new JwtException("Invalid token issuer");
        }

        return claims;
    }

    /**
     * Extracts the admin ID from the token subject.
     * Rejects USER tokens.
     *
     * @param token the JWT string
     * @return admin ID
     * @throws JwtException if the token is invalid or is not an ADMIN token
     */
    public Long getAdminId(String token) {
        Claims claims = parseToken(token);
        requireTokenType(claims, TOKEN_TYPE_ADMIN);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * Extracts the username from the token.
     *
     * @param token the JWT string
     * @return username
     */
    public String getUsername(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }

    /**
     * Extracts the role from the token.
     *
     * @param token the JWT string
     * @return role code
     */
    public String getRole(String token) {
        Claims claims = parseToken(token);
        return claims.get("role", String.class);
    }

    /**
     * Returns the configured expiration duration in seconds.
     *
     * @return expiration in seconds
     */
    public int getExpirationSeconds() {
        return expirationMinutes * 60;
    }

    /**
     * Signs a new USER access token using the configured expiration.
     * Only contains sub (userId), tokenType=USER, iss, iat, exp.
     * No phone, openid, unionid, nickname, avatar, or admin data.
     */
    public String signUserToken(Long userId) {
        return signUserToken(userId, getExpirationSeconds());
    }

    /**
     * Signs a new USER access token with a custom expiration in seconds.
     */
    public String signUserToken(Long userId, int expirationSeconds) {
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(expirationSeconds);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("tokenType", TOKEN_TYPE_USER)
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Extracts the user ID from a USER token.
     * Rejects ADMIN tokens.
     *
     * @throws JwtException if the token is invalid or is not a USER token
     */
    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        requireTokenType(claims, TOKEN_TYPE_USER);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * Parses token once and returns both tokenType and subject ID.
     * Used by the filter for single-pass token processing.
     *
     * @return TokenParseResult with tokenType and subjectId, or null if tokenType is invalid
     * @throws JwtException if the token is invalid, expired, or has wrong issuer
     */
    public TokenParseResult parseTokenForFilter(String token) {
        Claims claims = parseToken(token);
        String tokenType = claims.get("tokenType", String.class);
        if (tokenType == null
                || (!TOKEN_TYPE_ADMIN.equals(tokenType) && !TOKEN_TYPE_USER.equals(tokenType))) {
            return null;
        }
        Long subjectId = Long.parseLong(claims.getSubject());
        // P2（2026-08-23）：携带 iat 供 JwtRevocationRegistry 做改密撤销判定
        long issuedAtEpochSeconds = claims.getIssuedAt() == null
                ? 0L : claims.getIssuedAt().getTime() / 1000;
        return new TokenParseResult(tokenType, subjectId, issuedAtEpochSeconds);
    }

    /**
     * Single-pass token parse result for filter use.
     */
    public record TokenParseResult(String tokenType, Long subjectId, long issuedAtEpochSeconds) {
    }

    /**
     * Extracts the tokenType claim from a token.
     */
    public String getTokenType(String token) {
        Claims claims = parseToken(token);
        return claims.get("tokenType", String.class);
    }

    private void requireTokenType(Claims claims, String expectedType) {
        String actualType = claims.get("tokenType", String.class);
        if (!expectedType.equals(actualType)) {
            throw new JwtException("Token type mismatch: expected " + expectedType);
        }
    }
}
