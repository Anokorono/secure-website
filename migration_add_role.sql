-- 数据库迁移脚本：为 sys_user 表新增 role 列，并将现有 admin 用户设为管理员角色
-- 使用方法：在 MySQL 中执行 source migration_add_role.sql
-- 说明：本脚本幂等，可重复执行（使用 ADD COLUMN IF NOT EXISTS 需 MySQL 8.0+ 或通过存储过程判断）

USE secure_website;

-- 1. 新增 role 列（若已存在则跳过）
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'secure_website'
      AND TABLE_NAME = 'sys_user'
      AND COLUMN_NAME = 'role'
);
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT ''USER'' COMMENT ''角色：USER普通用户 ADMIN管理员'' AFTER status',
    'SELECT ''role 列已存在，跳过'' AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 将 admin 用户设为管理员角色（若该用户存在）
UPDATE sys_user SET role = 'ADMIN' WHERE username = 'admin';
