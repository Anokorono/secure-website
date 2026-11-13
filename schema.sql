-- 安全网站数据库初始化脚本
-- 使用方法：在 MySQL 中执行 source schema.sql

CREATE DATABASE IF NOT EXISTS secure_website
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE secure_website;

-- 用户表
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(64)  NOT NULL COMMENT '用户名',
    password    VARCHAR(255) NOT NULL COMMENT 'BCrypt 哈希后的密码',
    nickname    VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1正常 0禁用',
    role        VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT '角色：USER普通用户 ADMIN管理员',
    login_fail_count INT      NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
    lock_time   DATETIME     DEFAULT NULL COMMENT '登录锁定截止时间',
    lock_level  INT          NOT NULL DEFAULT 0 COMMENT '锁定档位：记录已锁定次数，用于渐进式加长锁定时长',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- IP 注册记录表（用于风险管控）
DROP TABLE IF EXISTS ip_register_record;
CREATE TABLE ip_register_record (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    ip          VARCHAR(64) NOT NULL COMMENT 'IP 地址',
    username    VARCHAR(64) NOT NULL COMMENT '注册的用户名',
    success     TINYINT     NOT NULL DEFAULT 1 COMMENT '是否成功：1成功 0失败',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    PRIMARY KEY (id),
    KEY idx_ip_time (ip, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IP 注册记录表';

-- IP 黑名单表
DROP TABLE IF EXISTS ip_blacklist;
CREATE TABLE ip_blacklist (
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    ip            VARCHAR(64) NOT NULL COMMENT 'IP 地址',
    reason        VARCHAR(255) DEFAULT NULL COMMENT '拉黑原因',
    expire_time   DATETIME    NOT NULL COMMENT '解封时间',
    create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '拉黑时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ip (ip),
    KEY idx_expire (expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IP 黑名单表';

-- 登录日志表
DROP TABLE IF EXISTS login_log;
CREATE TABLE login_log (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(64) NOT NULL COMMENT '用户名',
    ip          VARCHAR(64) NOT NULL COMMENT 'IP 地址',
    success     TINYINT     NOT NULL DEFAULT 0 COMMENT '是否成功：1成功 0失败',
    message     VARCHAR(255) DEFAULT NULL COMMENT '提示信息',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    PRIMARY KEY (id),
    KEY idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志表';
