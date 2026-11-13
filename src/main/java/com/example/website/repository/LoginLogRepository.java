package com.example.website.repository;

import com.example.website.entity.LoginLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoginLogRepository extends JpaRepository<LoginLog, Long> {

    /** 按时间倒序分页查询登录日志 */
    List<LoginLog> findByOrderByCreateTimeDesc(Pageable pageable);
}
