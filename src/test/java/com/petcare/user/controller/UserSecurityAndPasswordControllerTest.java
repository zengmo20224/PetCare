package com.petcare.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcare.admin.entity.AdminUser;
import com.petcare.admin.service.AdminUserService;
import com.petcare.common.security.JwtTokenService;
import com.petcare.user.entity.User;
import com.petcare.user.entity.UserSecurityQuestion;
import com.petcare.user.service.UserSecurityQuestionService;
import com.petcare.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 已登录用户的"密保管理"和"修改密码"端点测试。
 * 覆盖：密保查看/更新、改密、权限隔离、DTO 校验、
 * 以及关键回归——更新密保后忘记密码流程仍能校验通过（归一化一致性）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserSecurityAndPasswordControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenService jwtTokenService;
    @Autowired
    private UserService userService;
    @Autowired
    private AdminUserService adminUserService;
    @Autowired
    private UserSecurityQuestionService userSecurityQuestionService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ObjectMapper objectMapper;

    private static final String PHONE = "13800138001";

    // ======================== 密保查看 ========================

    @Nested
    @DisplayName("GET /api/v1/user/security-questions")
    class GetMySecurityQuestions {

        @Test
        @DisplayName("返回当前用户已设密保（仅题目，不回答案）")
        void returnsMyQuestionsWithoutAnswer() throws Exception {
            User user = createUserWithPassword("Test1234");
            seedSecurityQuestions(user.getId());

            String token = jwtTokenService.signUserToken(user.getId());

            mockMvc.perform(get("/api/v1/user/security-questions")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].question").value("你的宠物叫什么名字？"))
                    .andExpect(jsonPath("$.data[0].sort").value(0))
                    .andExpect(jsonPath("$.data[1].question").value("你的家乡在哪里？"))
                    .andExpect(jsonPath("$.data[1].sort").value(1))
                    // 不回答案
                    .andExpect(jsonPath("$.data[0].answerHash").doesNotExist());
        }

        @Test
        @DisplayName("未登录返回 401")
        void noTokenReturns401() throws Exception {
            mockMvc.perform(get("/api/v1/user/security-questions"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ADMIN token 返回 403")
        void adminTokenReturns403() throws Exception {
            Long adminId = createTestAdmin("sqadmin", "SUPER_ADMIN");
            String adminToken = jwtTokenService.signAdminToken(adminId, "sqadmin", "SUPER_ADMIN");

            mockMvc.perform(get("/api/v1/user/security-questions")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isForbidden());
        }
    }

    // ======================== 密保更新 ========================

    @Nested
    @DisplayName("PUT /api/v1/user/security-questions")
    class UpdateMySecurityQuestions {

        @Test
        @DisplayName("更新成功：删旧建新，返回 2 个新题目")
        void updateReplacesAllQuestions() throws Exception {
            User user = createUserWithPassword("Test1234");
            seedSecurityQuestions(user.getId());

            String token = jwtTokenService.signUserToken(user.getId());
            String body = updateBody(2, "旺财", 4, "北京");

            mockMvc.perform(put("/api/v1/user/security-questions")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            // 验证：旧密保被删除，新密保已写入
            // updateBody(2,..,4,..) → index 2="你的家乡在哪里？"，index 4="你就读的第一所学校叫什么？"
            List<UserSecurityQuestion> all = listQuestions(user.getId());
            assertThat(all).hasSize(2);
            // 旧 seed 题目（index 0,2 旧值）应已被删除
            assertThat(all).extracting(UserSecurityQuestion::getQuestion)
                    .doesNotContain("你的宠物叫什么名字？");
            assertThat(all.get(0).getQuestion()).isEqualTo("你的家乡在哪里？");
            assertThat(all.get(0).getSort()).isEqualTo(0);
            assertThat(all.get(1).getQuestion()).isEqualTo("你就读的第一所学校叫什么？");
            assertThat(all.get(1).getSort()).isEqualTo(1);
        }

        @Test
        @DisplayName("不足 2 个密保 → 400")
        void lessThanTwoQuestionsRejected() throws Exception {
            User user = createUserWithPassword("Test1234");
            String token = jwtTokenService.signUserToken(user.getId());
            String body = objectMapper.writeValueAsString(Map.of(
                    "securityQuestions", List.of(
                            Map.of("questionIndex", 0, "answer", "旺财")
                    )
            ));

            mockMvc.perform(put("/api/v1/user/security-questions")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("两个问题重复 → 422")
        void duplicateQuestionsRejected() throws Exception {
            User user = createUserWithPassword("Test1234");
            String token = jwtTokenService.signUserToken(user.getId());
            // 两个 questionIndex 相同
            String body = updateBody(0, "答案A", 0, "答案B");

            mockMvc.perform(put("/api/v1/user/security-questions")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("两个答案相同 → 422")
        void duplicateAnswersRejected() throws Exception {
            User user = createUserWithPassword("Test1234");
            String token = jwtTokenService.signUserToken(user.getId());
            String body = updateBody(0, "相同答案", 1, "相同答案");

            mockMvc.perform(put("/api/v1/user/security-questions")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("ADMIN token 返回 403")
        void adminTokenReturns403() throws Exception {
            Long adminId = createTestAdmin("sqadmin2", "SUPER_ADMIN");
            String adminToken = jwtTokenService.signAdminToken(adminId, "sqadmin2", "SUPER_ADMIN");

            mockMvc.perform(put("/api/v1/user/security-questions")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateBody(0, "a", 1, "b")))
                    .andExpect(status().isForbidden());
        }
    }

    // ======================== 关键回归：更新密保后忘记密码能通过 ========================

    @Nested
    @DisplayName("密保更新后忘记密码流程（归一化一致性回归）")
    class SecurityQuestionNormalizationRegression {

        @Test
        @DisplayName("更新密保后，用新答案能通过忘记密码校验")
        void updatedQuestionsWorkWithForgotPassword() throws Exception {
            User user = createUserWithPassword("OldPass1234");
            String token = jwtTokenService.signUserToken(user.getId());

            // 1. 更新密保为新答案（注意大小写混合，验证归一化）
            String newAnswer1 = "MyPetName";
            String newAnswer2 = "MyHomeTown";
            mockMvc.perform(put("/api/v1/user/security-questions")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateBody(0, newAnswer1, 1, newAnswer2)))
                    .andExpect(status().isOk());

            // 2. 拉取该用户的密保问题（忘记密码步骤 1）
            MvcResult qResult = mockMvc.perform(post("/api/v1/auth/forgot-password/questions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("phone", PHONE))))
                    .andExpect(status().isOk())
                    .andReturn();

            // 3. 用新答案重置密码（忘记密码步骤 2）—— 答案小写化提交，验证归一化匹配
            com.fasterxml.jackson.databind.JsonNode dataNode = objectMapper
                    .readTree(qResult.getResponse().getContentAsString())
                    .path("data");
            assertThat(dataNode.isArray()).isTrue();
            assertThat(dataNode.size()).isEqualTo(2);

            // 取问题 id，用小写答案提交（注册时存的是 trim().toLowerCase()）
            String q1Id = dataNode.get(0).path("id").asText();
            String q2Id = dataNode.get(1).path("id").asText();

            String resetBody = objectMapper.writeValueAsString(Map.of(
                    "phone", PHONE,
                    "answers", List.of(
                            Map.of("questionId", q1Id, "answer", newAnswer1.toLowerCase()),
                            Map.of("questionId", q2Id, "answer", newAnswer2.toLowerCase())
                    ),
                    "newPassword", "NewPass5678"
            ));

            mockMvc.perform(post("/api/v1/auth/forgot-password/reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(resetBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    // ======================== 修改密码 ========================

    @Nested
    @DisplayName("PUT /api/v1/user/password")
    class ChangePassword {

        @Test
        @DisplayName("旧密码正确 → 改密成功，新密码可登录")
        void changePasswordWithCorrectOldPassword() throws Exception {
            User user = createUserWithPassword("Test1234");
            String token = jwtTokenService.signUserToken(user.getId());

            String body = objectMapper.writeValueAsString(Map.of(
                    "oldPassword", "Test1234",
                    "newPassword", "NewPass5678"
            ));

            mockMvc.perform(put("/api/v1/user/password")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            // 验证：新密码可匹配，旧密码不可
            User refreshed = userService.getById(user.getId());
            assertThat(passwordEncoder.matches("NewPass5678", refreshed.getPasswordHash())).isTrue();
            assertThat(passwordEncoder.matches("Test1234", refreshed.getPasswordHash())).isFalse();
        }

        @Test
        @DisplayName("P2：改密成功后旧 JWT 立即失效（401），跨秒后新签发 token 可用")
        void oldTokenInvalidatedAfterPasswordChange() throws Exception {
            User user = createUserWithPassword("Test1234");
            String oldToken = jwtTokenService.signUserToken(user.getId());

            // JWT iat 为秒级精度：确保撤销时刻严格晚于旧 token 签发秒，消除同秒边界抖动
            Thread.sleep(1100);

            String body = objectMapper.writeValueAsString(Map.of(
                    "oldPassword", "Test1234",
                    "newPassword", "NewPass5678"
            ));

            mockMvc.perform(put("/api/v1/user/password")
                            .header("Authorization", "Bearer " + oldToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            // 旧 token 重放：iat 早于撤销时刻 → 401（JWT iat 秒级精度，跨秒保证确定性）
            mockMvc.perform(get("/api/v1/user/security-questions")
                            .header("Authorization", "Bearer " + oldToken))
                    .andExpect(status().isUnauthorized());

            // 撤销后新签发的 token 不受影响（iat 晚于撤销时刻）
            // 同秒边界：撤销秒内新签发的 token 会被误拒一次，跨秒保证确定性
            Thread.sleep(1100);

            String freshToken = jwtTokenService.signUserToken(user.getId());
            mockMvc.perform(get("/api/v1/user/security-questions")
                            .header("Authorization", "Bearer " + freshToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("旧密码错误 → 401")
        void wrongOldPasswordRejected() throws Exception {
            User user = createUserWithPassword("Test1234");
            String token = jwtTokenService.signUserToken(user.getId());

            String body = objectMapper.writeValueAsString(Map.of(
                    "oldPassword", "WrongOld99",
                    "newPassword", "NewPass5678"
            ));

            mockMvc.perform(put("/api/v1/user/password")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("新密码不符合规则（无数字）→ 400")
        void weakNewPasswordRejected() throws Exception {
            User user = createUserWithPassword("Test1234");
            String token = jwtTokenService.signUserToken(user.getId());

            String body = objectMapper.writeValueAsString(Map.of(
                    "oldPassword", "Test1234",
                    "newPassword", "onlyletters"
            ));

            mockMvc.perform(put("/api/v1/user/password")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("ADMIN token → 403")
        void adminTokenReturns403() throws Exception {
            Long adminId = createTestAdmin("pwadmin", "SUPER_ADMIN");
            String adminToken = jwtTokenService.signAdminToken(adminId, "pwadmin", "SUPER_ADMIN");

            String body = objectMapper.writeValueAsString(Map.of(
                    "oldPassword", "Test1234",
                    "newPassword", "NewPass5678"
            ));

            mockMvc.perform(put("/api/v1/user/password")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden());
        }
    }

    // ======================== Helpers ========================

    private User createUserWithPassword(String rawPassword) {
        User user = new User();
        user.setPhone(PHONE);
        user.setNickname("密保测试用户");
        user.setStatus("ACTIVE");
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setOpenid("test_openid_" + PHONE);
        user.setUnionid("test_unionid_" + PHONE);
        userService.save(user);
        return user;
    }

    private void seedSecurityQuestions(Long userId) {
        UserSecurityQuestion q1 = new UserSecurityQuestion();
        q1.setUserId(userId);
        q1.setQuestion("你的宠物叫什么名字？");
        q1.setAnswerHash(passwordEncoder.encode("tomato"));
        q1.setSort(0);
        userSecurityQuestionService.save(q1);

        UserSecurityQuestion q2 = new UserSecurityQuestion();
        q2.setUserId(userId);
        q2.setQuestion("你的家乡在哪里？");
        q2.setAnswerHash(passwordEncoder.encode("shanghai"));
        q2.setSort(1);
        userSecurityQuestionService.save(q2);
    }

    private List<UserSecurityQuestion> listQuestions(Long userId) {
        return userSecurityQuestionService.list(
                new LambdaQueryWrapper<UserSecurityQuestion>()
                        .eq(UserSecurityQuestion::getUserId, userId)
                        .orderByAsc(UserSecurityQuestion::getSort)
        );
    }

    /** 生成更新密保请求体 */
    private String updateBody(int idx1, String ans1, int idx2, String ans2) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "securityQuestions", List.of(
                        Map.of("questionIndex", idx1, "answer", ans1),
                        Map.of("questionIndex", idx2, "answer", ans2)
                )
        ));
    }

    private Long createTestAdmin(String username, String role) {
        AdminUser admin = new AdminUser();
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode("password123456"));
        admin.setRole(role);
        admin.setStatus("ACTIVE");
        adminUserService.save(admin);
        return admin.getId();
    }
}
