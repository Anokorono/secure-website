package com.example.website.config;

import com.example.website.entity.User;
import com.example.website.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 管理员初始化器
 * 应用启动时确保 admin 账号存在且拥有 ADMIN 角色，
 * 管理员密码通过环境变量 ADMIN_PASSWORD 注入（不提供默认值，防弱口令）。
 */
@Component
public class AdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    @Autowired
    private UserService userService;

    @Value("${admin.username:admin}")
    private String adminUsername;

    @Value("${admin.password:}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        User existing = userService.findByUsername(adminUsername);

        if (existing == null) {
            // admin 账号不存在，需用 ADMIN_PASSWORD 环境变量提供的密码创建
            if (adminPassword == null || adminPassword.isEmpty()) {
                log.warn("[初始化] 未检测到 admin 账号，且未配置 admin.password。"
                        + "请设置环境变量 ADMIN_PASSWORD 后重启，以自动创建管理员账号。");
                return;
            }
            try {
                User admin = userService.createUser(adminUsername, adminPassword);
                admin.setRole("ADMIN");
                userService.updateRole(admin.getUsername(), "ADMIN");
                log.info("[初始化] 已创建管理员账号：{}", adminUsername);
            } catch (Exception e) {
                log.error("[初始化] 创建管理员账号失败", e);
            }
            return;
        }

        // admin 账号已存在，确保其为 ADMIN 角色（兼容旧数据）
        if (!"ADMIN".equalsIgnoreCase(existing.getRole())) {
            userService.updateRole(adminUsername, "ADMIN");
            log.info("[初始化] 已将 {} 的角色修正为 ADMIN", adminUsername);
        }
    }
}
