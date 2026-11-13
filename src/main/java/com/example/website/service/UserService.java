package com.example.website.service;

import com.example.website.config.SecurityProperties;
import com.example.website.entity.User;
import com.example.website.repository.UserRepository;
import com.example.website.util.PasswordValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 用户服务
 * 密码存储采用 BCrypt 单向哈希（不可逆）。
 */
@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SecurityProperties securityProperties;

    /**
     * 根据用户名查询用户
     */
    public User findByUsername(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }

    /**
     * 判断用户是否存在
     */
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    /**
     * 校验登录密码
     */
    public boolean checkPassword(User user, String rawPassword) {
        if (user == null || rawPassword == null) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, user.getPassword());
    }

    /**
     * 对不存在的用户执行一次虚拟 BCrypt 校验，
     * 使"用户不存在"与"密码错误"两条分支的耗时一致，防止时序侧信道枚举用户名。
     */
    public void dummyPasswordCheck(String rawPassword) {
        if (rawPassword == null) rawPassword = "";
        // 对一个固定盐的 BCrypt 哈希做匹配，消耗与真实校验相当的时间
        passwordEncoder.matches(rawPassword,
                "$2a$10$IDmhkAN3yVRzPUrkdW88HOegOREhsYwjdnNZY2eR4mvoxBlTmoiCG");
    }

    /**
     * 判断是否需要校验验证码（登录失败次数达到阈值，但尚未锁定）
     */
    public boolean needCaptcha(User user) {
        if (user == null) return false;
        int maxFail = securityProperties.getLoginMaxFailCount();
        int fail = user.getLoginFailCount() == null ? 0 : user.getLoginFailCount();
        return fail >= maxFail - 1;
    }

    /**
     * 判断账号是否处于登录锁定状态
     */
    public boolean isLocked(User user) {
        if (user == null || user.getLockTime() == null) {
            return false;
        }
        return user.getLockTime().isAfter(LocalDateTime.now());
    }

    /**
     * 计算账号锁定的剩余秒数（未锁定或已过期返回 0）
     */
    public long getLockRemainSeconds(User user) {
        if (user == null || user.getLockTime() == null) {
            return 0;
        }
        long seconds = java.time.Duration.between(LocalDateTime.now(), user.getLockTime()).getSeconds();
        return Math.max(0, seconds);
    }

    /**
     * 记录一次登录失败，连续失败达到阈值则锁定。
     * 使用单条数据库原子更新（计数 + 判断 + 锁定 + 档位递增）一次完成，
     * 避免并发登录请求下"读-改-写"竞态导致计数丢失、从而绕过账号锁定。
     *
     * 渐进式锁定：锁定时长随锁定档位翻倍递增。
     *   第 1 档：首次锁定时长（默认 30 秒）
     *   第 2 档：30×2 = 60 秒（1 分钟）
     *   第 3 档：60×2 = 120 秒（2 分钟）
     *   第 4 档：120×2 = 240 秒（4 分钟）
     *   第 5 档：240×2 = 480 秒（8 分钟）……依次递推
     */
    @Transactional
    public void recordLoginFail(User user) {
        if (user == null) return;
        int maxFail = securityProperties.getLoginMaxFailCount();
        int currentLevel = user.getLockLevel() == null ? 0 : user.getLockLevel();
        LocalDateTime lockUntil = calcLockUntil(currentLevel);

        // 原子：自增失败次数，达到阈值则同时置锁、递增档位并清零计数
        userRepository.incrementLoginFailAndLockIfNeeded(user.getId(), maxFail, lockUntil);
    }

    /**
     * 根据当前锁定档位计算本次锁定的截止时间。
     * 时长 = 首次锁定时长 × multiplier^level（level 从 0 起，即当前已锁过的档数）。
     */
    private LocalDateTime calcLockUntil(int currentLevel) {
        long first = securityProperties.getLoginFirstLockSeconds();
        int multiplier = Math.max(1, securityProperties.getLoginLockMultiplier());
        // 防止指数过大导致溢出，封顶为 24 小时
        long seconds = first;
        for (int i = 0; i < currentLevel && i < 30; i++) {
            if (seconds > 86400L / multiplier) {
                seconds = 86400L;
                break;
            }
            seconds *= multiplier;
        }
        return LocalDateTime.now().plusSeconds(seconds);
    }

    /**
     * 登录成功后重置失败计数、锁定档位与锁定时间
     */
    @Transactional
    public void resetLoginFail(User user) {
        if (user == null) return;
        user.setLoginFailCount(0);
        user.setLockTime(null);
        user.setLockLevel(0);
        userRepository.save(user);
    }

    /**
     * 修改昵称
     */
    @Transactional
    public void updateNickname(String username, String nickname) {
        User user = findByUsername(username);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        user.setNickname(nickname);
        userRepository.save(user);
    }

    /**
     * 修改密码（BCrypt 重新哈希）
     */
    @Transactional
    public void updatePassword(String username, String newPassword) {
        User user = findByUsername(username);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /**
     * 修改用户角色（仅管理员初始化等受信场景使用）
     */
    @Transactional
    public void updateRole(String username, String role) {
        User user = findByUsername(username);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        user.setRole(role);
        userRepository.save(user);
    }

    /**
     * 创建用户（密码 BCrypt 单向哈希）
     */
    @Transactional
    public User createUser(String username, String rawPassword) {
        String usernameErr = PasswordValidator.validateUsername(username);
        if (usernameErr != null) {
            throw new IllegalArgumentException(usernameErr);
        }
        String passwordErr = PasswordValidator.validate(rawPassword);
        if (passwordErr != null) {
            throw new IllegalArgumentException(passwordErr);
        }
        if (existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已被注册");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setNickname(username);
        user.setStatus(1);
        user.setRole("USER");
        user.setLoginFailCount(0);
        user.setLockLevel(0);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        return userRepository.save(user);
    }
}
