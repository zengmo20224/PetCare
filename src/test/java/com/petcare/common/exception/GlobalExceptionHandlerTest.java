package com.petcare.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for the global exception handler.
 * Verifies that validation errors, business exceptions, and unexpected errors
 * are all converted into the unified ApiResponse format.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(GlobalExceptionHandlerTest.TestController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Test-only controller that triggers various exception types.
     */
    @RestController
    @RequestMapping("/api/v1/test")
    static class TestController {

        @PostMapping("/validate")
        public void validate(@Valid @RequestBody TestRequest request) {
            // Bean validation will fail before reaching here
        }

        @PostMapping("/business")
        public void business() {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "测试业务异常");
        }

        @PostMapping("/unexpected")
        public void unexpected() {
            throw new RuntimeException("Unexpected error");
        }
    }

    record TestRequest(
            @NotBlank(message = "名称不能为空") String name
    ) {}

    @Test
    @WithMockUser
    @DisplayName("Validation error returns 400 with field errors")
    void handleValidation_shouldReturn400WithFieldErrors() throws Exception {
        String body = """
                {"name": ""}
                """;

        mockMvc.perform(post("/api/v1/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("validation_error"))
                .andExpect(jsonPath("$.error.message").value("请求参数不合法"))
                .andExpect(jsonPath("$.error.details").isArray());
    }

    @Test
    @WithMockUser
    @DisplayName("Business exception returns 422 with error code")
    void handleBusiness_shouldReturn422WithErrorCode() throws Exception {
        mockMvc.perform(post("/api/v1/test/business")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("business_rule_violation"))
                .andExpect(jsonPath("$.error.message").value("测试业务异常"));
    }

    @Test
    @WithMockUser
    @DisplayName("Unexpected exception returns 500 with generic message")
    void handleUnexpected_shouldReturn500WithGenericMessage() throws Exception {
        mockMvc.perform(post("/api/v1/test/unexpected")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("internal_error"))
                .andExpect(jsonPath("$.error.message").value("服务内部错误，请稍后重试"));
    }

    /**
     * D2 regression: wrong HTTP method on an existing path must return 405,
     * not leak as a 500 internal_error.
     * /api/v1/test/business only accepts POST; GET must yield Method Not Allowed.
     */
    @Test
    @WithMockUser
    @DisplayName("Unsupported HTTP method returns 405, not 500")
    void handleMethodNotSupported_shouldReturn405Not500() throws Exception {
        mockMvc.perform(get("/api/v1/test/business"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("method_not_allowed"));
    }
}
