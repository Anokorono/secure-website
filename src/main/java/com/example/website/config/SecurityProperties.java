package com.example.website.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 安全相关配置
 */
@Component
@ConfigurationProperties(prefix = "security")
public class SecurityProperties {
    /** 同一IP注册间隔限制（秒） */
    private long registerIntervalSeconds = 300;
    /** 同一IP最大注册次数 */
    private int maxRegisterCountPerIp = 3;
    /** IP 拉黑时长（分钟） */
    private long blacklistDurationMinutes = 60;
    /** DDoS 防护：每秒最大请求数 */
    private int ddosMaxRequestsPerSecond = 10;
    /** DDoS 防护：触发限流后封禁时长（秒） */
    private long ddosBanDurationSeconds = 30;
    /** 是否信任代理转发头（X-Forwarded-For 等）。仅在部署于可信反向代理后设为 true，否则可被伪造 IP 绕过风控 */
    private boolean trustProxyHeaders = false;
    /** 登录失败锁定：连续失败多少次后锁定 */
    private int loginMaxFailCount = 5;
    /** 登录失败锁定：首次锁定时长（秒） */
    private long loginFirstLockSeconds = 30;
    /** 登录失败锁定：锁定档位倍增基数（每次锁定后时长 = 上一档 × 该值，2 表示翻倍） */
    private int loginLockMultiplier = 2;
    /** IP 白名单（不受限流和风控限制） */
    private List<String> ipWhitelist = new ArrayList<>();

    public long getRegisterIntervalSeconds() { return registerIntervalSeconds; }
    public void setRegisterIntervalSeconds(long registerIntervalSeconds) { this.registerIntervalSeconds = registerIntervalSeconds; }
    public int getMaxRegisterCountPerIp() { return maxRegisterCountPerIp; }
    public void setMaxRegisterCountPerIp(int maxRegisterCountPerIp) { this.maxRegisterCountPerIp = maxRegisterCountPerIp; }
    public long getBlacklistDurationMinutes() { return blacklistDurationMinutes; }
    public void setBlacklistDurationMinutes(long blacklistDurationMinutes) { this.blacklistDurationMinutes = blacklistDurationMinutes; }
    public int getDdosMaxRequestsPerSecond() { return ddosMaxRequestsPerSecond; }
    public void setDdosMaxRequestsPerSecond(int ddosMaxRequestsPerSecond) { this.ddosMaxRequestsPerSecond = ddosMaxRequestsPerSecond; }
    public long getDdosBanDurationSeconds() { return ddosBanDurationSeconds; }
    public void setDdosBanDurationSeconds(long ddosBanDurationSeconds) { this.ddosBanDurationSeconds = ddosBanDurationSeconds; }
    public boolean isTrustProxyHeaders() { return trustProxyHeaders; }
    public void setTrustProxyHeaders(boolean trustProxyHeaders) { this.trustProxyHeaders = trustProxyHeaders; }
    public int getLoginMaxFailCount() { return loginMaxFailCount; }
    public void setLoginMaxFailCount(int loginMaxFailCount) { this.loginMaxFailCount = loginMaxFailCount; }
    public long getLoginFirstLockSeconds() { return loginFirstLockSeconds; }
    public void setLoginFirstLockSeconds(long loginFirstLockSeconds) { this.loginFirstLockSeconds = loginFirstLockSeconds; }
    public int getLoginLockMultiplier() { return loginLockMultiplier; }
    public void setLoginLockMultiplier(int loginLockMultiplier) { this.loginLockMultiplier = loginLockMultiplier; }
    public List<String> getIpWhitelist() { return ipWhitelist; }
    public void setIpWhitelist(List<String> ipWhitelist) { this.ipWhitelist = ipWhitelist; }

    /** 判断 IP 是否在白名单中 */
    public boolean isWhitelisted(String ip) {
        if (ip == null || ipWhitelist == null) return false;
        // 安全保险：拒绝通配符与全零地址，防止误配导致风控失效
        if ("0.0.0.0".equals(ip) || "0:0:0:0:0:0:0:0".equals(ip) || "*".equals(ip)) {
            return false;
        }
        return ipWhitelist.contains(ip);
    }
}
