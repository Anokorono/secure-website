package com.example.website.controller;

import com.example.website.common.ApiResult;
import com.example.website.entity.IpBlacklist;
import com.example.website.entity.LoginLog;
import com.example.website.repository.IpBlacklistRepository;
import com.example.website.repository.LoginLogRepository;
import com.example.website.service.RegisterRiskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 管理员后台控制器
 * 提供登录日志查询、IP 黑名单管理等管理功能。
 * 管理员判定：基于 Spring Security 的 ROLE_ADMIN 授权（用户表 role 字段），
 * 角色在登录时由 AuthController 注入，避免硬编码用户名。
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private LoginLogRepository loginLogRepository;

    @Autowired
    private IpBlacklistRepository ipBlacklistRepository;

    @Autowired
    private RegisterRiskService registerRiskService;

    /**
     * 管理员页面
     */
    @GetMapping("")
    public String adminPage(Model model) {
        if (!isAdmin()) {
            return "redirect:/home";
        }
        model.addAttribute("username", currentUsername());
        return "admin";
    }

    /**
     * 登录日志查询（JSON，分页）
     */
    @GetMapping("/logs")
    @ResponseBody
    public ApiResult<?> logs(@RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "20") int size) {
        if (!isAdmin()) {
            return ApiResult.fail(403, "无权限访问");
        }
        if (size > 100) size = 100;
        Pageable pageable = PageRequest.of(Math.max(page, 0), size);
        List<LoginLog> list = loginLogRepository.findByOrderByCreateTimeDesc(pageable);
        List<?> data = list.stream().map(l -> {
            var m = new java.util.HashMap<String, Object>();
            m.put("username", l.getUsername());
            m.put("ip", l.getIp());
            m.put("success", l.getSuccess());
            m.put("message", l.getMessage());
            m.put("time", l.getCreateTime() == null ? "" : l.getCreateTime().format(DT));
            return m;
        }).collect(Collectors.toList());
        return ApiResult.success("ok", data);
    }

    /**
     * IP 黑名单查询（JSON）
     */
    @GetMapping("/blacklist")
    @ResponseBody
    public ApiResult<?> blacklist() {
        if (!isAdmin()) {
            return ApiResult.fail(403, "无权限访问");
        }
        List<IpBlacklist> list = ipBlacklistRepository.findByOrderByCreateTimeDesc();
        List<?> data = list.stream().map(b -> {
            var m = new java.util.HashMap<String, Object>();
            m.put("ip", b.getIp());
            m.put("reason", b.getReason());
            m.put("expireTime", b.getExpireTime() == null ? "" : b.getExpireTime().format(DT));
            m.put("createTime", b.getCreateTime() == null ? "" : b.getCreateTime().format(DT));
            return m;
        }).collect(Collectors.toList());
        return ApiResult.success("ok", data);
    }

    /**
     * 手动解除某个 IP 的黑名单
     */
    @PostMapping("/unban")
    @ResponseBody
    public ApiResult<?> unban(@RequestParam String ip) {
        if (!isAdmin()) {
            return ApiResult.fail(403, "无权限访问");
        }
        if (ip == null || ip.trim().isEmpty()) {
            return ApiResult.fail("IP 不能为空");
        }
        try {
            registerRiskService.removeBlacklist(ip.trim());
            log.info("[管理员] 手动解除黑名单：{}", ip);
            return ApiResult.success("已解除 " + ip + " 的黑名单");
        } catch (Exception e) {
            log.error("[管理员] 解除黑名单失败：{}", ip, e);
            return ApiResult.fail("操作失败，请稍后再试");
        }
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        // 基于角色授权判断，而非硬编码用户名，支持扩展更多管理员
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return auth.getName();
    }
}
