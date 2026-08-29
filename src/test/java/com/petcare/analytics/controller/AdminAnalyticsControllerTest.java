package com.petcare.analytics.controller;

import com.petcare.admin.entity.AdminPermission;
import com.petcare.admin.entity.AdminRole;
import com.petcare.admin.entity.AdminRolePermission;
import com.petcare.admin.entity.AdminUser;
import com.petcare.admin.service.AdminPermissionService;
import com.petcare.admin.service.AdminRolePermissionService;
import com.petcare.admin.service.AdminRoleService;
import com.petcare.admin.service.AdminUserService;
import com.petcare.common.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 统计工作台端点集成测试：认证 401、鉴权 403、授权 200、参数校验 400 与导出响应头。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminAnalyticsControllerTest {

    private static final String BASE = "/api/v1/admin/analytics";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AdminUserService adminUserService;
    @Autowired
    private AdminRoleService adminRoleService;
    @Autowired
    private AdminPermissionService adminPermissionService;
    @Autowired
    private AdminRolePermissionService adminRolePermissionService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtTokenService jwtTokenService;

    private String adminToken;
    private String noPermToken;

    @BeforeEach
    void setUp() {
        Long adminId = createTestAdmin("analytics_admin", "password123456", "ANALYTICS_MGR");
        grantPermission("ANALYTICS_MGR", "analytics:dashboard:read");
        adminToken = jwtTokenService.signAdminToken(adminId, "analytics_admin", "ANALYTICS_MGR");

        Long noPermAdminId = createTestAdmin("analytics_noperm", "password123456", "OTHER_MGR");
        grantPermission("OTHER_MGR", "product:order:read");
        noPermToken = jwtTokenService.signAdminToken(noPermAdminId, "analytics_noperm", "OTHER_MGR");
    }

    @Test
    @DisplayName("未认证访问统计接口返回 401")
    void overviewUnauthenticated() throws Exception {
        mockMvc.perform(get(BASE + "/overview")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-29"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("无 analytics:dashboard:read 权限返回 403")
    void overviewWithoutPermission() throws Exception {
        mockMvc.perform(get(BASE + "/overview")
                        .header("Authorization", "Bearer " + noPermToken)
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-29"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("有权限时概览返回聚合结构")
    void overviewWithPermission() throws Exception {
        mockMvc.perform(get(BASE + "/overview")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-29"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.startDate").value("2026-08-01"))
                .andExpect(jsonPath("$.data.totalRevenue").isNumber())
                .andExpect(jsonPath("$.data.bookingCompletionRate").isNumber());
    }

    @Test
    @DisplayName("每日趋势与 Top 榜单对有权限管理员开放")
    void trendAndTopItemsWithPermission() throws Exception {
        mockMvc.perform(get(BASE + "/daily-trend")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("startDate", "2026-08-27")
                        .param("endDate", "2026-08-29"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));

        mockMvc.perform(get(BASE + "/top-items")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("startDate", "2026-08-27")
                        .param("endDate", "2026-08-29")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.services").isArray())
                .andExpect(jsonPath("$.data.products").isArray());
    }

    @Test
    @DisplayName("开始日期晚于结束日期返回 400 validation_error")
    void overviewRejectsInvertedRange() throws Exception {
        mockMvc.perform(get(BASE + "/overview")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("startDate", "2026-08-29")
                        .param("endDate", "2026-08-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("validation_error"));
    }

    @Test
    @DisplayName("导出接口返回 xlsx 内容与附件响应头")
    void exportReturnsWorkbookAttachment() throws Exception {
        mockMvc.perform(get(BASE + "/export")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-29"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"petcare-analytics-2026-08-01_2026-08-29.xlsx\""))
                .andExpect(result -> assertThatBytesNotEmpty(result.getResponse().getContentAsByteArray()));
    }

    private void assertThatBytesNotEmpty(byte[] bytes) {
        org.assertj.core.api.Assertions.assertThat(bytes).isNotEmpty();
    }

    private Long createTestAdmin(String username, String rawPassword, String role) {
        AdminUser admin = new AdminUser();
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode(rawPassword));
        admin.setRole(role);
        admin.setStatus("ACTIVE");
        adminUserService.save(admin);
        return admin.getId();
    }

    private void grantPermission(String roleCode, String permissionCode) {
        AdminRole role = new AdminRole();
        role.setRoleCode(roleCode);
        role.setRoleName(roleCode);
        role.setStatus("ACTIVE");
        adminRoleService.save(role);

        AdminPermission perm = new AdminPermission();
        perm.setPermissionCode(permissionCode);
        perm.setPermissionName(permissionCode);
        perm.setModule("analytics");
        perm.setStatus("ACTIVE");
        adminPermissionService.save(perm);

        AdminRolePermission rp = new AdminRolePermission();
        rp.setRoleId(role.getId());
        rp.setPermissionId(perm.getId());
        adminRolePermissionService.save(rp);
    }
}
