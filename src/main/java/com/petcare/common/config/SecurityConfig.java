package com.petcare.common.config;

import com.petcare.common.security.JwtAuthenticationFilter;
import com.petcare.common.security.RateLimitFilter;
import com.petcare.common.security.RestAccessDeniedHandler;
import com.petcare.common.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration with JWT authentication and RBAC.
 *
 * Public endpoints:
 * - GET /api/v1/system/health
 * - POST /api/v1/admin/auth/login
 * - POST /api/v1/auth/register
 * - POST /api/v1/auth/login
 * - POST /api/v1/auth/forgot-password/*
 * - POST /api/v1/auth/wechat-login
 * - POST /api/v1/auth/test-login (only active in test profile)
 * - Anonymous GET on public catalog/community whitelist (service/product/topic/post reads)
 *
 * All other /api/v1/** endpoints require authentication.
 * Writes, user-private resources, admin backend and AI endpoints stay authenticated.
 * Method-level security enabled via @EnableMethodSecurity.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RateLimitFilter rateLimitFilter,
                          RestAuthenticationEntryPoint restAuthenticationEntryPoint,
                          RestAccessDeniedHandler restAccessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
        this.restAccessDeniedHandler = restAccessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/system/health").permitAll()
                        .requestMatchers("/api/v1/admin/auth/login").permitAll()
                        .requestMatchers("/api/v1/auth/register").permitAll()
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/auth/forgot-password/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/security-questions").permitAll()
                        .requestMatchers("/api/v1/auth/wechat-login").permitAll()
                        .requestMatchers("/api/v1/auth/test-login").permitAll()
                        // Uploaded static files
                        .requestMatchers("/uploads/**").permitAll()
                        // Anonymous public catalog reads (GET only, explicit real paths)
                        .requestMatchers(HttpMethod.GET, "/api/v1/service-categories").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/service-items").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/service-items/{id}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/product-categories").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/product-carousel-images").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/products").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/{id}").permitAll()
                        // Anonymous public community reads (GET only, explicit real paths)
                        .requestMatchers(HttpMethod.GET, "/api/v1/topics").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/topics/{id}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/tags").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/tags/popular").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/posts").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/posts/{id}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/posts/{postId}/comments").permitAll()
                        // Anonymous public announcement reads
                        .requestMatchers(HttpMethod.GET, "/api/v1/announcements").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/announcements/{id}").permitAll()
                        // Anonymous public store reads (for pickup store selection)
                        .requestMatchers(HttpMethod.GET, "/api/v1/stores").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/stores/{id}").permitAll()
                        // Anonymous public marketing activity reads (GET only)
                        .requestMatchers(HttpMethod.GET, "/api/v1/activities").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/activities/{id}").permitAll()
                        // Anonymous availability query (GET only)
                        .requestMatchers(HttpMethod.GET, "/api/v1/bookings/availability").permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
