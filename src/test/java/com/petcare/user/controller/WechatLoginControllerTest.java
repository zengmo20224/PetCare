package com.petcare.user.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the WeChat login endpoint under mock mode.
 *
 * <p>{@code @TestPropertySource} overrides {@code petcare.wechat.mode=mock} on top of
 * the default {@code test} profile, exercising the full provider → application service →
 * token-signing path with a deterministic openid.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "petcare.wechat.mode=mock")
class WechatLoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("mock mode: WeChat login returns 200 with a non-empty access token")
    void wechatLoginIssuesToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/wechat-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"mock-code-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.id").isNotEmpty())
                .andExpect(jsonPath("$.data.user.nickname").isNotEmpty());
    }

    @Test
    @DisplayName("mock mode: the same code resolves to the same user id on re-login")
    void sameCode_resolvesToSameUserId() throws Exception {
        MvcResult first = mockMvc.perform(post("/api/v1/auth/wechat-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"deterministic-code\"}"))
                .andExpect(status().isOk())
                .andReturn();

        com.fasterxml.jackson.databind.JsonNode firstBody =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(first.getResponse().getContentAsString());
        String userId = firstBody.at("/data/user/id").asText();

        mockMvc.perform(post("/api/v1/auth/wechat-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"deterministic-code\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.id").value(userId));
    }
}
