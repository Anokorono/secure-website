package com.example.website.repository;

import com.example.website.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);

    /**
     * 原子累加登录失败次数，避免并发请求下"读-改-写"竞态导致计数丢失。
     * 返回更新后的失败次数（-1 表示用户不存在）。
     */
    @Modifying
    @Query(value = "UPDATE sys_user SET login_fail_count = login_fail_count + 1, update_time = NOW() WHERE id = :id", nativeQuery = true)
    int incrementLoginFailCount(@Param("id") Long id);

    /**
     * 原子设置账号锁定截止时间，并重置失败计数。
     */
    @Modifying
    @Query(value = "UPDATE sys_user SET lock_time = :lockTime, login_fail_count = 0, update_time = NOW() WHERE id = :id", nativeQuery = true)
    int lockUser(@Param("id") Long id, @Param("lockTime") LocalDateTime lockTime);

    /**
     * 原子自增失败次数，并在达到阈值时同时置锁、递增锁定档位、清零计数。
     * 单条 UPDATE 内完成"计数+判断+锁定+档位递增"，彻底消除并发竞态窗口。
     * 锁定截止时间由调用方按当前档位动态计算后传入。
     * 返回受影响行数（1 表示执行成功，0 表示用户不存在）。
     *
     * 注意：MySQL 的 UPDATE SET 从左到右顺序执行，后面对列的引用会读到
     * 前面赋值后的新值。因此 login_fail_count 的最终赋值必须放在所有
     * CASE 判断之后（判断都基于原始值 login_fail_count + 1）。
     */
    @Modifying
    @Query(value = "UPDATE sys_user SET " +
            "lock_time = CASE WHEN login_fail_count + 1 >= :threshold THEN :lockTime ELSE lock_time END, " +
            "lock_level = CASE WHEN login_fail_count + 1 >= :threshold THEN lock_level + 1 ELSE lock_level END, " +
            "login_fail_count = CASE WHEN login_fail_count + 1 >= :threshold THEN 0 ELSE login_fail_count + 1 END, " +
            "update_time = NOW() WHERE id = :id", nativeQuery = true)
    int incrementLoginFailAndLockIfNeeded(@Param("id") Long id,
                                          @Param("threshold") int threshold,
                                          @Param("lockTime") LocalDateTime lockTime);
}
