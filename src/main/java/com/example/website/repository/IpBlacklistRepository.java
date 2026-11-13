package com.example.website.repository;

import com.example.website.entity.IpBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface IpBlacklistRepository extends JpaRepository<IpBlacklist, Long> {

    Optional<IpBlacklist> findByIp(String ip);

    /**
     * 查询某IP是否在黑名单中且未过期
     */
    @Query("SELECT b FROM IpBlacklist b WHERE b.ip = :ip AND b.expireTime > :now")
    Optional<IpBlacklist> findValidBlacklist(@Param("ip") String ip, @Param("now") LocalDateTime now);

    /**
     * 清理已过期的黑名单记录
     */
    @Modifying
    @Query("DELETE FROM IpBlacklist b WHERE b.expireTime <= :now")
    int deleteExpired(@Param("now") LocalDateTime now);

    /** 按拉黑时间倒序查询全部黑名单 */
    List<IpBlacklist> findByOrderByCreateTimeDesc();
}
