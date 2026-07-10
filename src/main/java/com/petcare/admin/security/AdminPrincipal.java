package com.petcare.admin.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Spring Security UserDetails implementation for admin users.
 * Contains adminId, username, role, and permission-based authorities.
 * Does NOT contain the password.
 *
 * <p>M4 安全修复：authorities 现在同时包含权限码（如 {@code system:config}）
 * 和角色（形式为 {@code ROLE_<role>}，如 {@code ROLE_SUPER_ADMIN}）。
 * 这样 {@code @PreAuthorize("hasRole('SUPER_ADMIN')")} 才能正确求值，
 * 而不是永远为假。权限码仍保留以支持细粒度 {@code hasAuthority(...)} 检查。
 */
public class AdminPrincipal implements UserDetails {

    /** Spring Security 的 hasRole() 会自动去掉此前缀，故 ROLE_ 前缀是必须的。 */
    public static final String ROLE_AUTHORITY_PREFIX = "ROLE_";

    private final Long adminId;
    private final String username;
    private final String role;
    private final Collection<? extends GrantedAuthority> authorities;

    public AdminPrincipal(Long adminId, String username, String role,
                          Collection<String> permissionCodes) {
        this.adminId = adminId;
        this.username = username;
        this.role = role;
        this.authorities = buildAuthorities(role, permissionCodes);
    }

    /**
     * 构造 authority 集合：角色（ROLE_<role>）+ 权限码。
     * 角色为 null/空时不注入 ROLE_ authority（保守处理）。
     */
    private static Collection<? extends GrantedAuthority> buildAuthorities(
            String role, Collection<String> permissionCodes) {
        List<GrantedAuthority> list = new ArrayList<>();
        if (role != null && !role.isBlank()) {
            list.add(new SimpleGrantedAuthority(ROLE_AUTHORITY_PREFIX + role));
        }
        if (permissionCodes != null) {
            for (String code : permissionCodes) {
                if (code != null && !code.isBlank()) {
                    list.add(new SimpleGrantedAuthority(code));
                }
            }
        }
        return List.copyOf(list);
    }

    public Long getAdminId() {
        return adminId;
    }

    public String getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        // Password is never stored in the principal
        return null;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * Returns the list of permission code strings (excludes ROLE_ authorities).
     */
    public List<String> getPermissionCodes() {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> !a.startsWith(ROLE_AUTHORITY_PREFIX))
                .toList();
    }
}
