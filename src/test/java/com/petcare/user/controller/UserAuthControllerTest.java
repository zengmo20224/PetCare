package com.petcare.user.controller;

import com.petcare.user.entity.User;
import com.petcare.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for user registration, login, and password recovery.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    // === Preset Security Questions ===

    @Test
    @DisplayName("GET /security-questions returns preset list")
    void getSecurityQuestionsReturnsPresetList() throws Exception {
        mockMvc.perform(get("/api/v1/auth/security-questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0]").exists())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(5)));
    }

    // === Register ===

    @Test
    @DisplayName("register with valid data returns token and user info")
    void registerWithValidDataReturnsToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "phone": "13900001111",
                                    "password": "test123456",
                                    "nickname": "新用户",
                                    "securityQuestions": [
                                        {"questionIndex": 0, "answer": "豆豆"},
                                        {"questionIndex": 1, "answer": "火锅"}
                                    ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.nickname").value("新用户"));
    }

    @Test
    @DisplayName("register with duplicate phone returns 422")
    void registerWithDuplicatePhoneReturns422() throws Exception {
        createUser("13900002222");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "phone": "13900002222",
                                    "password": "test123456",
                                    "nickname": "重复用户",
                                    "securityQuestions": [
                                        {"questionIndex": 0, "answer": "答案1"},
                                        {"questionIndex": 1, "answer": "答案2"}
                                    ]
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("phone_already_registered"));
    }

    @Test
    @DisplayName("register with invalid phone format returns 400")
    void registerWithInvalidPhoneReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "phone": "123",
                                    "password": "test123456",
                                    "nickname": "测试",
                                    "securityQuestions": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_error"));
    }

    @Test
    @DisplayName("register with short password returns 400")
    void registerWithShortPasswordReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "phone": "13900003333",
                                    "password": "123",
                                    "nickname": "短密码",
                                    "securityQuestions": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_error"));
    }

    @Test
    @DisplayName("register with only 1 security question returns 422")
    void registerWithOnlyOneSecurityQuestionReturns422() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "phone": "13900004444",
                                    "password": "test123456",
                                    "nickname": "问题不足",
                                    "securityQuestions": [
                                        {"questionIndex": 0, "answer": "答案1"}
                                    ]
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("business_rule_violation"));
    }

    @Test
    @DisplayName("register with duplicate question index returns 422")
    void registerWithDuplicateQuestionIndexReturns422() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "phone": "13900005555",
                                    "password": "test123456",
                                    "nickname": "重复问题",
                                    "securityQuestions": [
                                        {"questionIndex": 0, "answer": "答案A"},
                                        {"questionIndex": 0, "answer": "答案B"}
                                    ]
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("business_rule_violation"));
    }

    @Test
    @DisplayName("register with out-of-range question index returns 422")
    void registerWithOutOfRangeQuestionIndexReturns422() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "phone": "13900006666",
                                    "password": "test123456",
                                    "nickname": "越界",
                                    "securityQuestions": [
                                        {"questionIndex": 0, "answer": "答案1"},
                                        {"questionIndex": 999, "answer": "答案2"}
                                    ]
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("business_rule_violation"));
    }

    /**
     * D3 regression: a null questionIndex inside a securityQuestions item must be
     * rejected as 400 validation_error (bean validation cascade), not leak as a
     * 500 internal_error via NPE in the service layer.
     */
    @Test
    @DisplayName("register with null questionIndex returns 400 (validation cascade)")
    void registerWithNullQuestionIndexReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "phone": "13900008888",
                                    "password": "test123456",
                                    "nickname": "空索引",
                                    "securityQuestions": [
                                        {"questionIndex": null, "answer": "答案1"},
                                        {"questionIndex": 1, "answer": "答案2"}
                                    ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_error"));
    }

    // === Login ===

    @Test
    @DisplayName("login with correct password returns token")
    void loginWithCorrectPasswordReturnsToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "phone": "13900007777",
                                    "password": "test123456",
                                    "nickname": "登录测试",
                                    "securityQuestions": [
                                        {"questionIndex": 0, "answer": "答案1"},
                                        {"questionIndex": 1, "answer": "答案2"}
                                    ]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "phone": "13900007777",
                                    "password": "test123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.nickname").value("登录测试"));
    }

    @Test
    @DisplayName("login with wrong password returns 401")
    void loginWithWrongPasswordReturns401() throws Exception {
        createUser("13900008888");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "phone": "13900008888",
                                    "password": "wrongpass1"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("invalid_credentials"));
    }

    @Test
    @DisplayName("login with non-existent phone returns 401")
    void loginWithNonExistentPhoneReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "phone": "19999999999",
                                    "password": "whatever"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    // === Forgot Password ===

    @Test
    @DisplayName("forgot-password questions returns security questions without answers")
    void forgotPasswordReturnsSecurityQuestions() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "phone": "13900009999",
                                    "password": "test123456",
                                    "nickname": "找回密码",
                                    "securityQuestions": [
                                        {"questionIndex": 0, "answer": "豆豆"},
                                        {"questionIndex": 2, "answer": "上海"}
                                    ]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/forgot-password/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": "13900009999"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].question").exists())
                .andExpect(jsonPath("$.data[1].question").exists());
    }

    @Test
    @DisplayName("forgot-password reset with correct answers changes password")
    void forgotPasswordResetWithCorrectAnswersChangesPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "phone": "13900010000",
                                    "password": "oldpass1",
                                    "nickname": "重置密码",
                                    "securityQuestions": [
                                        {"questionIndex": 0, "answer": "豆豆"},
                                        {"questionIndex": 1, "answer": "火锅"}
                                    ]
                                }
                                """))
                .andExpect(status().isOk());

        String questionsJson = mockMvc.perform(post("/api/v1/auth/forgot-password/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": "13900010000"}
                                """))
                .andReturn().getResponse().getContentAsString();

        String questionId = extractIdFromJson(questionsJson);

        mockMvc.perform(post("/api/v1/auth/forgot-password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {
                                    "phone": "13900010000",
                                    "answers": [{"questionId": "%s", "answer": "豆豆"}],
                                    "newPassword": "newpass1"
                                }
                                """, questionId)))
                .andExpect(status().isOk());

        // New password works
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "phone": "13900010000",
                                    "password": "newpass1"
                                }
                                """))
                .andExpect(status().isOk());

        // Old password fails
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "phone": "13900010000",
                                    "password": "oldpass1"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("forgot-password reset with wrong answer returns 422")
    void forgotPasswordResetWithWrongAnswerReturns422() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "phone": "13900011111",
                                    "password": "oldpass1",
                                    "nickname": "错误答案",
                                    "securityQuestions": [
                                        {"questionIndex": 0, "answer": "豆豆"},
                                        {"questionIndex": 1, "answer": "火锅"}
                                    ]
                                }
                                """))
                .andExpect(status().isOk());

        String questionsJson = mockMvc.perform(post("/api/v1/auth/forgot-password/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": "13900011111"}
                                """))
                .andReturn().getResponse().getContentAsString();

        String questionId = extractIdFromJson(questionsJson);

        mockMvc.perform(post("/api/v1/auth/forgot-password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {
                                    "phone": "13900011111",
                                    "answers": [{"questionId": "%s", "answer": "错误的答案"}],
                                    "newPassword": "newpass1"
                                }
                                """, questionId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("security_answer_incorrect"));
    }

    @Test
    @DisplayName("forgot-password questions for non-existent phone returns 401")
    void forgotPasswordForNonExistentPhoneReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": "19999999999"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    // === Helper ===

    private User createUser(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickname("测试用户");
        user.setStatus("ACTIVE");
        userService.save(user);
        return user;
    }

    private String extractIdFromJson(String json) {
        int idStart = json.indexOf("\"id\":\"") + 6;
        int idEnd = json.indexOf("\"", idStart);
        return json.substring(idStart, idEnd);
    }
}
