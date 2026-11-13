package com.example.website.service;

import com.example.website.entity.LoginLog;
import com.example.website.repository.LoginLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 登录日志服务
 */
@Service
public class LoginLogService {

    @Autowired
    private LoginLogRepository loginLogRepository;

    public void record(String username, String ip, boolean success, String message) {
        LoginLog log = new LoginLog();
        log.setUsername(username);
        log.setIp(ip);
        log.setSuccess(success ? 1 : 0);
        log.setMessage(message);
        loginLogRepository.save(log);
    }
}
