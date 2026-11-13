package com.example.website.util;

import com.example.website.config.SecurityProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

/**
 * IP 地址工具类
 * 默认仅信任 socket 地址（getRemoteAddr），避免 X-Forwarded-For 等头被伪造绕过风控。
 * 仅当部署在可信反向代理（Nginx）后，且在配置中开启 security.trust-proxy-headers=true 时，
 * 才解析代理转发头。
 */
@Component
public class IpUtil {

    @Autowired
    private SecurityProperties securityProperties;

    /**
     * 获取客户端真实 IP
     */
    public String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }

        String ip = null;

        // 仅在明确信任代理时才读取转发头，否则一律用 socket 地址（防伪造）
        if (securityProperties.isTrustProxyHeaders()) {
            ip = resolveFromProxyHeaders(request);
        }

        if (isBlank(ip)) {
            ip = request.getRemoteAddr();
        }

        // IPv6 本地地址统一
        if ("0:0:0:0:0:0:0:1".equals(ip)) {
            ip = "127.0.0.1";
        }
        return isBlank(ip) ? "unknown" : ip;
    }

    /**
     * 从代理转发头解析真实 IP（仅在信任代理时调用）
     * 取 X-Forwarded-For 最右侧（最靠近可信反向代理的一跳），
     * 因为最左侧地址可被客户端伪造；只有紧邻可信代理的那一跳才可靠。
     */
    private String resolveFromProxyHeaders(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        // 多级代理时取最后一个（最靠近可信代理），防伪造最左侧
        if (ip != null && ip.contains(",")) {
            String[] parts = ip.split(",");
            ip = parts[parts.length - 1].trim();
        }
        return ip;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
