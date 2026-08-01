package com.petcare.user.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the WeChat login endpoint under the default test profile
 * ({@code petcare.wechat.mode=disabled}).
 * Asserts the endpoint returns 422 wechat_login_not_enabled and issues no token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WechatLoginDisabledControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("disabled mode: WeChat login returns 422 with wechat_login_not_enabled")
    void wechatLoginReturns422() throws Exception {
        mockMvc.perform(post("/api/v1/auth/wechat-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"test-code\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("wechat_login_not_enabled"));
    }

    @Test
    @DisplayName("disabled mode: WeChat login does not issue a token")
    void wechatLoginDoesNotIssueToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/wechat-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"test-code\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("WeChat login with missing code returns 400")
    void wechatLoginWithMissingCodeReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/wechat-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_error"));
    }

    @Test
    @DisplayName("disabled mode: WeChat login does not require authentication")
    void wechatLoginDoesNotRequireAuth() throws Exception {
        mockMvc.perform(post("/api/v1/auth/wechat-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"test-code\"}"))
                .andExpect(status().isUnprocessableEntity()); // 422, not 401
    }
}
