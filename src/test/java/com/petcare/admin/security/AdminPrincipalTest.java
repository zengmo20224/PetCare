package com.petcare.admin.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AdminPrincipal} (M4 RBAC role 模型修复)。
 *
 * <p>验证：authorities 同时包含 ROLE_<role> 和权限码；
 * hasRole('SUPER_ADMIN') 能在 Spring Security 求值为 true。
 */
class AdminPrincipalTest {

    @Test
    @DisplayName("M4：SUPER_ADMIN 角色的 principal 包含 ROLE_SUPER_ADMIN authority")
    void superAdmin_hasRoleAuthority() {
        AdminPrincipal principal = new AdminPrincipal(
                1L, "admin", "SUPER_ADMIN", List.of("system:config", "booking:manage"));

        List<String> authStrings = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList();

        assertThat(authStrings).contains("ROLE_SUPER_ADMIN");
    }

    @Test
    @DisplayName("M4：权限码与角色 authority 共存")
    void permissionsAndRoleCoexist() {
        AdminPrincipal principal = new AdminPrincipal(
                1L, "manager", "MANAGER", List.of("system:config", "community:moderate"));

        List<String> authStrings = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList();

        assertThat(authStrings).containsExactlyInAnyOrder(
                "ROLE_MANAGER", "system:config", "community:moderate");
    }

    @Test
    @DisplayName("M4：getPermissionCodes 仅返回权限码，排除 ROLE_ authority")
    void getPermissionCodes_excludesRoleAuthority() {
        AdminPrincipal principal = new AdminPrincipal(
                1L, "admin", "SUPER_ADMIN", List.of("system:config", "booking:manage"));

        List<String> codes = principal.getPermissionCodes();

        assertThat(codes).containsExactlyInAnyOrder("system:config", "booking:manage");
        assertThat(codes).noneMatch(c -> c.startsWith("ROLE_"));
    }

    @Test
    @DisplayName("M4：角色为 null 时不注入 ROLE_ authority（保守处理）")
    void nullRole_noRoleAuthority() {
        AdminPrincipal principal = new AdminPrincipal(
                1L, "user", null, List.of("system:config"));

        List<String> authStrings = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList();

        assertThat(authStrings).containsExactly("system:config");
        assertThat(authStrings).noneMatch(a -> a.startsWith("ROLE_"));
    }

    @Test
    @DisplayName("M4：空权限码/null 权限码被过滤")
    void blankPermissionCodes_filtered() {
        // Arrays.asList 允许 null 元素（List.of 不允许）
        AdminPrincipal principal = new AdminPrincipal(
                1L, "admin", "SUPER_ADMIN", Arrays.asList("system:config", "", null, "  "));

        List<String> authStrings = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList();

        assertThat(authStrings).containsExactlyInAnyOrder("ROLE_SUPER_ADMIN", "system:config");
    }

    @Test
    @DisplayName("M4：hasRole 风格校验 —— Spring Security 通过 SimpleGrantedAuthority 匹配")
    void hasRoleStyleCheck_works() {
        AdminPrincipal principal = new AdminPrincipal(
                1L, "admin", "SUPER_ADMIN", List.of());

        // 模拟 @PreAuthorize("hasRole('SUPER_ADMIN')") 的内部求值：
        // hasRole('X') 等价于 authorities 含 'ROLE_X'
        boolean hasRole = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_SUPER_ADMIN"::equals);

        assertThat(hasRole).isTrue();
    }
}
