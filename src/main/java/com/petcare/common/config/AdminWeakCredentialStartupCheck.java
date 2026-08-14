package com.petcare.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * H-2 安全修复：prod profile 下拒绝带已知弱口令超管账号启动。
 *
 * <p>背景：dev 种子数据（data-dev.sql）中的超管 {@code admin/admin123456} 哈希随仓库公开。
 * 即便运维误将 dev 种子导入生产库（旧版 docker-compose 默认挂载就会如此），
 * 攻击者也可直接用该口令登录管理端获得全部权限。</p>
 *
 * <p>本检查与 {@link SecurityStartupValidator}（JWT 密钥缺失拒绝启动）同属
 * "不安全配置拒绝启动"防线：prod 启动后逐个校验 admin_user 未删除账号的密码哈希，
 * 命中已知弱口令立即抛异常中止启动（容器 restart 策略下会持续失败，
 * 与 JWT_SECRET 缺失行为一致，直到管理员修正凭据）。dev/test profile 不装配。</p>
 */
@Component
@Profile("prod")
public class AdminWeakCredentialStartupCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminWeakCredentialStartupCheck.class);

    /**
     * 已知泄漏的种子弱口令（data-dev.sql 及部署文档 docs/13 中公开过明文）。
     * 仅需覆盖种子哈希对应的明文——BCrypt 校验命中即证明"生产库里有仓库公开的演示凭据"。
     */
    private static final List<String> KNOWN_WEAK_PASSWORDS = List.of(
            "admin123456",
            "user123456"
    );

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public AdminWeakCredentialStartupCheck(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT username, password FROM admin_user WHERE deleted = 0");

        for (Map<String, Object> row : rows) {
            String username = String.valueOf(row.get("username"));
            String passwordHash = String.valueOf(row.get("password"));
            for (String weak : KNOWN_WEAK_PASSWORDS) {
                if (passwordEncoder.matches(weak, passwordHash)) {
                    log.error("[SECURITY] Admin account '{}' is using a known weak seed password. "
                            + "Refusing to run with prod profile. "
                            + "Fix: set a strong password for this account (or remove the dev seed), then restart.",
                            username);
                    throw new IllegalStateException(
                            "Unsafe prod startup: admin account '" + username
                                    + "' uses a publicly known seed password. "
                                    + "Change it before running with the prod profile.");
                }
            }
        }
    }
}
