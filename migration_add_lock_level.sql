-- 数据库迁移脚本：为 sys_user 表新增 lock_level 列（渐进式锁定档位）
-- 使用方法：在 MySQL 中执行 source migration_add_lock_level.sql
-- 说明：本脚本幂等，可重复执行

USE secure_website;

-- 新增 lock_level 列（若已存在则跳过）
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'secure_website'
      AND TABLE_NAME = 'sys_user'
      AND COLUMN_NAME = 'lock_level'
);
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN lock_level INT NOT NULL DEFAULT 0 COMMENT ''锁定档位：记录已锁定次数，用于渐进式加长锁定时长'' AFTER lock_time',
    'SELECT ''lock_level 列已存在，跳过'' AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
