package com.example.website.security;

import com.example.website.config.SecurityProperties;
import com.example.website.util.IpUtil;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * DDoS 防御过滤器
 * 基于 Guava RateLimiter 令牌桶算法限流：
 * 1. 每个 IP 每秒请求次数超过阈值则封禁
 * 2. 封禁时长由 security.ddos-ban-duration-seconds 配置决定
 */
@Component
@Order(1)
public class DdosDefenseFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(DdosDefenseFilter.class);

    @Autowired
    private SecurityProperties securityProperties;

    @Autowired
    private IpUtil ipUtil;

    /** IP -> 令牌桶限流器（5分钟未访问自动清除） */
    private final Cache<String, RateLimiter> rateLimiterCache =
            CacheBuilder.newBuilder().expireAfterAccess(5, TimeUnit.MINUTES).build();

    /** 被临时封禁的 IP -> 封禁截止时间戳（毫秒） */
    private final Cache<String, Long> bannedIpCache =
            CacheBuilder.newBuilder().build();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String ip = ipUtil.getClientIp(req);

        // 白名单 IP 直接放行（不限流）
        if (securityProperties.isWhitelisted(ip)) {
            chain.doFilter(request, response);
            return;
        }

        // 放行静态资源（与 SecurityConfig 放行路径保持一致，含 /img/）
        String uri = req.getRequestURI();
        if (uri.startsWith("/css/") || uri.startsWith("/js/") || uri.startsWith("/images/")
                || uri.startsWith("/img/") || uri.startsWith("/fonts/") || uri.equals("/favicon.ico")) {
            chain.doFilter(request, response);
            return;
        }

        // 检查是否仍处于封禁期
        Long banUntil = bannedIpCache.getIfPresent(ip);
        if (banUntil != null && banUntil > System.currentTimeMillis()) {
            log.warn("[DDoS] IP 仍在封禁期：{}", ip);
            resp.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，IP已被临时封禁\"}");
            return;
        }
        // 已过封禁期则移除
        if (banUntil != null) {
            bannedIpCache.invalidate(ip);
        }

        // 令牌桶限流（每秒生成 maxRequests 个令牌，每个请求消耗一个）
        try {
            RateLimiter rateLimiter = rateLimiterCache.get(ip, () ->
                    RateLimiter.create(securityProperties.getDdosMaxRequestsPerSecond())
            );
            if (!rateLimiter.tryAcquire()) {
                long banSeconds = securityProperties.getDdosBanDurationSeconds();
                long banUntilTime = System.currentTimeMillis() + banSeconds * 1000;
                bannedIpCache.put(ip, banUntilTime);
                log.warn("[DDoS] IP 触发限流，封禁 {} 秒：{}", banSeconds, ip);
                resp.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                resp.setContentType("application/json;charset=UTF-8");
                resp.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}");
                return;
            }
        } catch (Exception e) {
            log.error("[DDoS] 限流异常", e);
        }

        chain.doFilter(request, response);
    }
}
