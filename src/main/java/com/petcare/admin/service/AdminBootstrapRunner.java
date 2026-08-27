package com.petcare.admin.service;

import com.petcare.admin.entity.AdminUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 生产首次引导：一次性创建初始管理员（上线缺口方案 A，2026-08-23）。
 *
 * <p><b>背景</b>：生产 compose 不挂载演示种子（H-2），且 {@code AdminWeakCredentialStartupCheck}
 * 会拒绝仓库公开的弱口令超管——空库上无法登录管理端，需要一个安全的初始化路径。</p>
 *
 * <p><b>用法</b>（在 VPS 上执行一次；不影响已运行的 api 容器）：
 * <pre>
 * docker compose run --rm \
 *   -e BOOTSTRAP_ADMIN_USERNAME=opsadmin \
 *   -e BOOTSTRAP_ADMIN_PASSWORD='&lt;openssl rand 生成的强口令&gt;' \
 *   api /bin/sh -c 'exec java $JAVA_OPTS -jar /app/app.jar --bootstrap-admin'
 * </pre></p>
 *
 * <p><b>安全约束</b>：
 * <ul>
 *   <li>仅在显式传入 {@code --bootstrap-admin} 参数时执行，常规启动零开销零副作用；</li>
 *   <li>凭据经环境变量注入而非命令行参数——避免 {@code ps}/shell 历史泄露；</li>
 *   <li>密码强度校验（≥8 位含字母数字）+ 拒绝仓库已知种子弱口令，与 prod 弱凭据启动检查口径一致；</li>
 *   <li>同名账号已存在时直接失败退出，不覆盖、不改密。</li>
 * </ul></p>
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,32}$");
    static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,64}$");
    private static final List<String> KNOWN_SEED_PASSWORDS = List.of("admin123456", "user123456");

    private final AdminUserService adminUserService;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapRunner(AdminUserService adminUserService, PasswordEncoder passwordEncoder) {
        this.adminUserService = adminUserService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("bootstrap-admin")) {
            return;
        }

        String username = requiredEnv("BOOTSTRAP_ADMIN_USERNAME");
        String password = requiredEnv("BOOTSTRAP_ADMIN_PASSWORD");
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException(
                    "BOOTSTRAP_ADMIN_USERNAME 无效：3-32 位，仅允许字母/数字/下划线");
        }
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException(
                    "BOOTSTRAP_ADMIN_PASSWORD 无效：至少 8 位且必须同时包含字母和数字");
        }
        if (KNOWN_SEED_PASSWORDS.contains(password)) {
            throw new IllegalArgumentException(
                    "BOOTSTRAP_ADMIN_PASSWORD 是仓库公开的种子弱口令，禁止用于生产");
        }
        Long existing = adminUserService.count(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AdminUser>()
                        .eq(AdminUser::getUsername, username));
        if (existing != null && existing > 0) {
            throw new IllegalStateException(
                    "管理员账号 '" + username + "' 已存在，bootstrap 不会覆盖或改密。"
                            + "如需重置密码请走管理端改密流程。");
        }

        AdminUser admin = new AdminUser();
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRole("SUPER_ADMIN");
        admin.setStatus("ACTIVE");
        adminUserService.save(admin);

        log.info("[BOOTSTRAP] 初始管理员 '{}' 创建成功（SUPER_ADMIN）。"
                + "请妥善保管凭据；本命令可重复执行以创建其他管理员。", username);
    }

    /** 包内可见以便测试桩替换；生产实现直接读进程环境变量。 */
    String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少环境变量 " + name
                    + "（bootstrap 凭据必须经环境变量注入，避免 ps/shell 历史泄露）");
        }
        return value.trim();
    }
}
