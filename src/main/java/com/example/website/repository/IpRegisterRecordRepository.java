package com.example.website.repository;

import com.example.website.entity.IpRegisterRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface IpRegisterRecordRepository extends JpaRepository<IpRegisterRecord, Long> {

    /**
     * 查询某IP在指定时间之后的注册记录
     */
    List<IpRegisterRecord> findByIpAndCreateTimeAfter(String ip, LocalDateTime after);

    /**
     * 统计某IP历史成功注册次数
     */
    long countByIpAndSuccess(String ip, Integer success);

    /**
     * 查询某IP最近一条注册记录
     */
    @Query("SELECT r FROM IpRegisterRecord r WHERE r.ip = :ip ORDER BY r.createTime DESC")
    List<IpRegisterRecord> findLatestByIp(@Param("ip") String ip);
}
