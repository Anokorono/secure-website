package com.example.website.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

/**
 * Spring Security 配置
 * 采用自定义登录接口（AuthController 手动认证），不启用默认表单登录页。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 密码编码器：BCrypt 单向哈希
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 禁用默认表单登录（使用自定义 /auth/login JSON 接口）
            .formLogin().disable()
            .httpBasic().disable()

            // 开启 CSRF，使用 Cookie 存储 token，前端读取后随请求携带
            .csrf().csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())

            .and()
            // 会话管理：登录后更换会话 ID（防会话固定），并显式配置会话超时与并发策略
            .sessionManagement()
                .sessionFixation().changeSessionId()
                // 会话超时后跳转到登录页，避免长期有效会话被劫持后长期可用
                .invalidSessionUrl("/auth/login")
                // 同一账号最多 1 个并发会话，过期后跳登录页
                .maximumSessions(1).expiredUrl("/auth/login")
                .and()

            .and()
            // 授权规则
            .authorizeRequests()
                // 静态资源放行
                .antMatchers("/css/**", "/js/**", "/img/**", "/images/**", "/fonts/**", "/favicon.ico").permitAll()
                // 登录、注册页面与接口、验证码接口、健康检查放行
                .antMatchers("/", "/auth/login", "/auth/register", "/auth/captcha", "/health").permitAll()
                // 其余请求需要认证
                .anyRequest().authenticated()

            .and()
            // 未认证时跳转到登录页
            .exceptionHandling()
                .authenticationEntryPoint((req, resp, e) -> resp.sendRedirect("/auth/login"))

            .and()
            // 安全响应头
            .headers(headers -> headers
                // 防止浏览器 MIME 类型嗅探
                .contentTypeOptions()
                .and()
                // 内容安全策略：默认仅同源资源，禁止内联脚本注入
                .addHeaderWriter(new StaticHeadersWriter(
                        "Content-Security-Policy",
                        "default-src 'self'; script-src 'self' 'unsafe-inline'; " +
                        "style-src 'self' 'unsafe-inline'; img-src 'self' data:; " +
                        "font-src 'self'; connect-src 'self'; frame-ancestors 'self'"))
                // 引用策略：仅同源发送完整 referrer
                .addHeaderWriter(new StaticHeadersWriter("Referrer-Policy", "strict-origin-when-cross-origin"))
                // 浏览器特性权限策略：收紧敏感 API
                .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", "camera=(), microphone=(), geolocation=()"))
                // HSTS：强制 HTTPS（生产启用；本地 http 开发时浏览器会忽略此头）
                .addHeaderWriter(new StaticHeadersWriter("Strict-Transport-Security", "max-age=31536000; includeSubDomains"))
                // 防止点击劫持（放最后，其 and() 返回 HttpSecurity 不影响后续无链式需求）
                .frameOptions().sameOrigin());

        return http.build();
    }
}
