package com.example.website.service;

import com.example.website.config.SecurityProperties;
import com.example.website.entity.IpBlacklist;
import com.example.website.entity.IpRegisterRecord;
import com.example.website.repository.IpBlacklistRepository;
import com.example.website.repository.IpRegisterRecordRepository;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 注册风险管控服务
 * 1. 同一IP在指定时间窗口内只能注册一次
 * 2. 同一IP累计注册次数超限则拉黑
 * 3. 拉黑期间禁止任何注册行为
 */
@Service
public class RegisterRiskService {

    private static final Logger log = LoggerFactory.getLogger(RegisterRiskService.class);

    /**
     * 按 IP 的注册锁，串行化同一 IP 的注册流程，防止并发绕过频次限制。
     * 使用带过期时间的 Guava Cache 而非无界 ConcurrentHashMap，避免海量不同 IP 长期占用内存。
     */
    private final Cache<String, Object> registerLocks = CacheBuilder.newBuilder()
            .maximumSize(100000)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();

    @Autowired
    private IpRegisterRecordRepository registerRecordRepository;

    @Autowired
    private IpBlacklistRepository blacklistRepository;

    @Autowired
    private SecurityProperties securityProperties;

    /**
     * 获取指定 IP 的注册锁对象（用于串行化同一 IP 的注册）
     */
    public Object getRegisterLock(String ip) {
        try {
            return registerLocks.get(ip, Object::new);
        } catch (Exception e) {
            // 极端情况下回退为一个新对象，保证功能不中断
            return new Object();
        }
    }

    /**
     * 检查 IP 是否允许注册
     * @return 错误信息，null 表示允许
     */
    public String checkRegisterAllowed(String ip) {
        LocalDateTime now = LocalDateTime.now();

        // 0. 白名单 IP 直接放行（不受注册频率和次数限制）
        if (securityProperties.isWhitelisted(ip)) {
            return null;
        }


        // 1. 检查是否在黑名单中
        if (isBlacklisted(ip, now)) {
            log.warn("[注册风控] IP 在黑名单中：{}", ip);
            return "您的IP已被拉黑，请稍后再试";
        }

        // 2. 检查时间窗口内的注册记录
        LocalDateTime windowStart = now.minusSeconds(securityProperties.getRegisterIntervalSeconds());
        List<IpRegisterRecord> recentRecords = registerRecordRepository
                .findByIpAndCreateTimeAfter(ip, windowStart);
        if (!recentRecords.isEmpty()) {
            long waitSeconds = securityProperties.getRegisterIntervalSeconds()
                    - java.time.Duration.between(recentRecords.get(0).getCreateTime(), now).getSeconds();
            log.warn("[注册风控] IP 注册过于频繁：{}", ip);
            return "该IP注册过于频繁，请" + Math.max(waitSeconds, 1) + "秒后再试";
        }

        // 3. 检查累计注册次数
        long totalSuccess = registerRecordRepository.countByIpAndSuccess(ip, 1);
        if (totalSuccess >= securityProperties.getMaxRegisterCountPerIp()) {
            // 触发拉黑
            blacklistIp(ip, "累计注册次数超限(" + totalSuccess + "次)");
            log.warn("[注册风控] IP 累计注册超限，已拉黑：{} 次数={}", ip, totalSuccess);
            return "该IP注册次数已达上限，已被拉黑";
        }

        return null;
    }

    /**
     * 记录一次注册行为
     */
    @Transactional
    public void recordRegister(String ip, String username, boolean success) {
        IpRegisterRecord record = new IpRegisterRecord();
        record.setIp(ip);
        record.setUsername(username);
        record.setSuccess(success ? 1 : 0);
        record.setCreateTime(LocalDateTime.now());
        registerRecordRepository.save(record);
    }

    /**
     * 拉黑指定 IP
     */
    @Transactional
    public void blacklistIp(String ip, String reason) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expire = now.plusMinutes(securityProperties.getBlacklistDurationMinutes());

        IpBlacklist exist = blacklistRepository.findByIp(ip).orElse(null);
        if (exist != null) {
            exist.setReason(reason);
            exist.setExpireTime(expire);
            blacklistRepository.save(exist);
        } else {
            IpBlacklist blacklist = new IpBlacklist();
            blacklist.setIp(ip);
            blacklist.setReason(reason);
            blacklist.setExpireTime(expire);
            blacklist.setCreateTime(now);
            blacklistRepository.save(blacklist);
        }
        log.warn("[注册风控] IP 已拉黑：{} 原因：{} 解封时间：{}", ip, reason, expire);
    }

    /**
     * 手动移除某 IP 的黑名单记录（管理员操作）
     */
    @Transactional
    public void removeBlacklist(String ip) {
        blacklistRepository.findByIp(ip).ifPresent(b -> blacklistRepository.delete(b));
    }

    /**
     * 判断 IP 是否在黑名单中（未过期）
     */
    public boolean isBlacklisted(String ip, LocalDateTime now) {
        return blacklistRepository.findValidBlacklist(ip, now).isPresent();
    }

    public boolean isBlacklisted(String ip) {
        return isBlacklisted(ip, LocalDateTime.now());
    }

    /**
     * 定时清理过期黑名单（每 5 分钟）
     */
    @Scheduled(fixedRate = 5 * 60 * 1000)
    @Transactional
    public void cleanExpiredBlacklist() {
        int deleted = blacklistRepository.deleteExpired(LocalDateTime.now());
        if (deleted > 0) {
            log.info("[注册风控] 清理过期黑名单 {} 条", deleted);
        }
    }
}
