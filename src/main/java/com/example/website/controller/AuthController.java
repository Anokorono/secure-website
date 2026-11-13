package com.example.website.controller;

import com.example.website.common.ApiResult;
import com.example.website.entity.User;
import com.example.website.service.LoginLogService;
import com.example.website.service.RegisterRiskService;
import com.example.website.service.UserService;
import com.example.website.util.IpUtil;
import com.example.website.util.PasswordValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 登录注册控制器
 */
@Controller
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private RegisterRiskService registerRiskService;

    @Autowired
    private LoginLogService loginLogService;

    @Autowired
    private IpUtil ipUtil;

    /**
     * 登录页面
     */
    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    /**
     * 注册页面
     */
    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    /**
     * 登录接口
     * 登录时从数据库检索账户，没有则提示未注册，跳转注册界面
     */
    @PostMapping("/login")
    @ResponseBody
    public ApiResult<?> doLogin(@RequestParam String username,
                                @RequestParam String password,
                                @RequestParam(required = false) String captcha,
                                HttpServletRequest request) {
        String ip = ipUtil.getClientIp(request);

        // 参数基础校验
        String usernameErr = PasswordValidator.validateUsername(username);
        if (usernameErr != null) {
            loginLogService.record(username, ip, false, usernameErr);
            return ApiResult.fail(usernameErr);
        }
        // 密码长度上限校验，防止超长字符串消耗 BCrypt 计算资源
        if (password == null || password.isEmpty()) {
            return ApiResult.fail("密码不能为空");
        }
        if (password.length() > 64) {
            return ApiResult.fail("用户名或密码错误");
        }

        // 检索用户
        User user = userService.findByUsername(username);

        // 防用户枚举：无论用户是否存在，均采用统一文案。
        // 但为对"不存在的用户"也施加一致的失败计数与验证码逻辑，使用虚拟目标处理。
        if (user == null) {
            // 未注册用户：记录一次失败，不泄露"未注册"信息，统一提示
            loginLogService.record(username, ip, false, "登录失败");
            // 对不存在的用户也强制校验验证码（若已触发），并消耗一致的时间（BCrypt 虚拟校验防时序侧信道）
            userService.dummyPasswordCheck(password);
            return ApiResult.fail("用户名或密码错误");
        }

        // 账号锁定检查（连续失败超阈值）
        if (userService.isLocked(user)) {
            long remainSeconds = userService.getLockRemainSeconds(user);
            loginLogService.record(username, ip, false, "账号已锁定");
            Map<String, Object> lockData = new HashMap<>();
            lockData.put("lockRemainSeconds", remainSeconds);
            return ApiResult.fail(2001, "登录失败次数过多，账号已被临时锁定，请稍后再试")
                    .data(lockData);
        }

        // 连续失败达到阈值后强制验证码，防自动化爆破
        if (userService.needCaptcha(user) && !CaptchaController.verify(request, captcha)) {
            // 验证码错误同样累计失败次数，防止攻击者绕过验证码持续爆破而不触发账号锁定
            userService.recordLoginFail(user);
            loginLogService.record(username, ip, false, "验证码错误");
            return ApiResult.fail(2002, "验证码错误或已失效，请重试");
        }

        // 校验密码（失败累计，统一文案防枚举）
        if (!userService.checkPassword(user, password)) {
            userService.recordLoginFail(user);
            loginLogService.record(username, ip, false, "密码错误");
            return ApiResult.fail("用户名或密码错误");
        }

        // 校验状态
        if (user.getStatus() != 1) {
            loginLogService.record(username, ip, false, "账号已禁用");
            return ApiResult.fail("用户名或密码错误");
        }

        // 登录成功，重置失败计数
        userService.resetLoginFail(user);

        // 登录成功，写入 Spring Security 上下文与 session
        String role = "ADMIN".equalsIgnoreCase(user.getRole()) ? "ROLE_ADMIN" : "ROLE_USER";
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                user.getUsername(), null, Collections.singletonList(new SimpleGrantedAuthority(role)));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        session.setAttribute("userId", user.getId());
        session.setAttribute("username", user.getUsername());
        session.setAttribute("nickname", user.getNickname());
        session.setAttribute("role", user.getRole());

        loginLogService.record(username, ip, true, "登录成功");
        log.info("[登录] 登录成功：{} IP：{}", username, ip);

        Map<String, Object> data = new HashMap<>();
        data.put("nickname", user.getNickname());
        return ApiResult.success("登录成功", data).redirect("/home");
    }

    /**
     * 注册接口
     * 注册时进行风险管控：
     * 1. 同一IP短时间无法注册第二个用户
     * 2. 密码必须包含大小写数字，长度不少于8位
     * 3. 短时间注册第二个或同时注册多个用户，拦截并拉黑
     */
    @PostMapping("/register")
    @ResponseBody
    public ApiResult<?> doRegister(@RequestParam String username,
                                   @RequestParam String password,
                                   @RequestParam(required = false) String confirm,
                                   HttpServletRequest request) {
        String ip = ipUtil.getClientIp(request);

        // 串行化同一 IP 的注册流程，防止并发绕过频次限制（TOCTOU）
        synchronized (registerRiskService.getRegisterLock(ip)) {
            return doRegisterInternal(username, password, confirm, ip);
        }
    }

    private ApiResult<?> doRegisterInternal(String username, String password, String confirm, String ip) {
        // 1. IP 黑名单与频率检查
        String riskMsg = registerRiskService.checkRegisterAllowed(ip);
        if (riskMsg != null) {
            registerRiskService.recordRegister(ip, username, false);
            return ApiResult.fail(2003, riskMsg);
        }

        // 2. 两次密码一致性
        if (password == null || !password.equals(confirm)) {
            return ApiResult.fail("两次输入的密码不一致");
        }

        // 3. 用户名与密码强度校验
        String usernameErr = PasswordValidator.validateUsername(username);
        if (usernameErr != null) {
            return ApiResult.fail(usernameErr);
        }
        String passwordErr = PasswordValidator.validate(password);
        if (passwordErr != null) {
            return ApiResult.fail(passwordErr);
        }

        // 4. 用户名是否已存在
        if (userService.existsByUsername(username)) {
            registerRiskService.recordRegister(ip, username, false);
            return ApiResult.fail("用户名已被注册");
        }

        // 5. 创建用户
        try {
            userService.createUser(username, password);
            registerRiskService.recordRegister(ip, username, true);
            log.info("[注册] 注册成功：{} IP：{}", username, ip);
            // 注册成功后跳转回登录界面
            return ApiResult.success("注册成功，即将跳转到登录页面").redirect("/auth/login");
        } catch (IllegalArgumentException e) {
            registerRiskService.recordRegister(ip, username, false);
            return ApiResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("[注册] 注册异常", e);
            registerRiskService.recordRegister(ip, username, false);
            // 异常情况视为可疑，拉黑（日志脱敏，去除换行防 log forging）
            registerRiskService.blacklistIp(ip, "注册异常：" + sanitize(e.getMessage()));
            return ApiResult.fail(2003, "注册异常，IP已被临时拉黑");
        }
    }

    /** 日志安全清洗：去除换行等控制字符，防日志注入 */
    private String sanitize(String s) {
        if (s == null) return null;
        return s.replaceAll("[\\r\\n\\t]", "_");
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    @ResponseBody
    public ApiResult<?> logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ApiResult.success("已退出登录").redirect("/auth/login");
    }
}
