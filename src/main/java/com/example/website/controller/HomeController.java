package com.example.website.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpSession;

/**
 * 首页控制器
 */
@Controller
public class HomeController {

    /**
     * 网站首页（需登录）
     */
    @GetMapping("/home")
    public String home(HttpSession session, Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return "redirect:/auth/login";
        }
        String username = auth.getName();
        Object nickname = session.getAttribute("nickname");
        model.addAttribute("username", username);
        model.addAttribute("nickname", nickname != null ? nickname : username);
        return "home";
    }

    /**
     * 根路径跳转登录
     */
    @GetMapping("/")
    public String index() {
        return "redirect:/auth/login";
    }
}
