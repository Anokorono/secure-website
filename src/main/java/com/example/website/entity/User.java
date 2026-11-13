package com.example.website.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_user")
@DynamicUpdate
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @JsonIgnore
    @Column(nullable = false, length = 255)
    private String password;

    @Column(length = 64)
    private String nickname;

    @Column(nullable = false, columnDefinition = "tinyint")
    private Integer status = 1;

    /**
     * 角色：USER 普通用户，ADMIN 管理员。
     * 以字符串存储（而非布尔），便于未来扩展更多角色。
     */
    @Column(nullable = false, length = 16)
    private String role = "USER";

    /** 连续登录失败次数 */
    @JsonIgnore
    @Column(name = "login_fail_count", nullable = false)
    private Integer loginFailCount = 0;

    /** 登录锁定截止时间（null 表示未锁定） */
    @JsonIgnore
    @Column(name = "lock_time")
    private LocalDateTime lockTime;

    /**
     * 锁定档位：记录已进入过多少次锁定，用于渐进式加长锁定时长。
     * 0 表示从未锁定；每触发一次锁定 +1；登录成功后重置为 0。
     */
    @JsonIgnore
    @Column(name = "lock_level", nullable = false)
    private Integer lockLevel = 0;

    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createTime == null) createTime = now;
        updateTime = now;
    }

    @PreUpdate
    public void preUpdate() {
        updateTime = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Integer getLoginFailCount() { return loginFailCount; }
    public void setLoginFailCount(Integer loginFailCount) { this.loginFailCount = loginFailCount; }
    public LocalDateTime getLockTime() { return lockTime; }
    public void setLockTime(LocalDateTime lockTime) { this.lockTime = lockTime; }
    public Integer getLockLevel() { return lockLevel; }
    public void setLockLevel(Integer lockLevel) { this.lockLevel = lockLevel; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
