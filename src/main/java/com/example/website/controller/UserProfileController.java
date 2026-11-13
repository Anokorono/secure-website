package com.example.website.controller;

import com.example.website.common.ApiResult;
import com.example.website.entity.User;
import com.example.website.service.UserService;
import com.example.website.util.PasswordValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpSession;

/**
 * 用户个人中心控制器
 * 提供改昵称、改密码等个人资料操作。
 * 所有操作均以当前登录用户为对象，不可越权操作他人账户。
 */
@Controller
@RequestMapping("/profile")
public class UserProfileController {

    private static final Logger log = LoggerFactory.getLogger(UserProfileController.class);

    @Autowired
    private UserService userService;

    /**
     * 修改昵称
     */
    @PostMapping("/nickname")
    @ResponseBody
    public ApiResult<?> updateNickname(@RequestParam String nickname, HttpSession session) {
        String username = currentUsername();
        if (username == null) {
            return ApiResult.fail("未登录或登录已过期");
        }

        // 昵称长度与字符校验
        if (nickname == null || nickname.trim().isEmpty()) {
            return ApiResult.fail("昵称不能为空");
        }
        String safe = nickname.trim();
        if (safe.length() > 32) {
            return ApiResult.fail("昵称长度不能超过32个字符");
        }

        try {
            userService.updateNickname(username, safe);
            session.setAttribute("nickname", safe);
            return ApiResult.success("昵称修改成功");
        } catch (Exception e) {
            log.error("[个人中心] 修改昵称失败：{}", username, e);
            return ApiResult.fail("修改失败，请稍后再试");
        }
    }

    /**
     * 修改密码（需校验原密码）
     */
    @PostMapping("/password")
    @ResponseBody
    public ApiResult<?> updatePassword(@RequestParam String oldPassword,
                                       @RequestParam String newPassword,
                                       @RequestParam(required = false) String confirm,
                                       HttpSession session) {
        String username = currentUsername();
        if (username == null) {
            return ApiResult.fail("未登录或登录已过期");
        }

        if (oldPassword == null || oldPassword.isEmpty()) {
            return ApiResult.fail("请输入原密码");
        }
        // 新密码强度校验（复用注册时的规则）
        String pwdErr = PasswordValidator.validate(newPassword);
        if (pwdErr != null) {
            return ApiResult.fail(pwdErr);
        }
        if (!newPassword.equals(confirm)) {
            return ApiResult.fail("两次输入的新密码不一致");
        }
        if (newPassword.equals(oldPassword)) {
            return ApiResult.fail("新密码不能与原密码相同");
        }

        User user = userService.findByUsername(username);
        if (user == null) {
            return ApiResult.fail("用户不存在");
        }

        // 校验原密码
        if (!userService.checkPassword(user, oldPassword)) {
            return ApiResult.fail("原密码错误");
        }

        try {
            userService.updatePassword(username, newPassword);
            // 改密后强制重新登录，防止会话被旧凭据继续使用
            SecurityContextHolder.clearContext();
            session.invalidate();
            return ApiResult.success("密码修改成功，请重新登录").redirect("/auth/login");
        } catch (Exception e) {
            log.error("[个人中心] 修改密码失败：{}", username, e);
            return ApiResult.fail("修改失败，请稍后再试");
        }
    }

    /**
     * 获取当前登录用户名（基于 Spring Security 上下文）
     */
    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return auth.getName();
    }
}
