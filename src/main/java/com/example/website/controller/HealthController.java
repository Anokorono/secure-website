package com.example.website.controller;

import com.example.website.common.ApiResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查控制器
 * 供负载均衡、容器编排（K8s/Docker healthcheck）探活使用，无需鉴权即可访问。
 * 返回应用与数据库连接状态。
 */
@RestController
public class HealthController {

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/health")
    public ApiResult<Map<String, Object>> health() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "UP");
        data.put("timestamp", System.currentTimeMillis());

        // 检查数据库连通性
        boolean dbOk = false;
        if (jdbcTemplate != null) {
            try {
                Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
                dbOk = one != null && one == 1;
            } catch (Exception ignored) {
                dbOk = false;
            }
        }
        data.put("database", dbOk ? "UP" : "DOWN");

        if (dbOk) {
            return ApiResult.success("UP", data);
        }
        return ApiResult.fail(503, "数据库不可用");
    }
}
