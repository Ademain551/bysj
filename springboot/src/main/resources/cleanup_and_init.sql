-- 数据库清理和初始化脚本
-- 1. 删除多余的表
-- 2. 清空用户数据
-- 3. 创建系统管理员账户

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ==================== 删除多余的表 ====================
-- 删除 admin 表（用户信息已在 users 表中管理）
DROP TABLE IF EXISTS `admin`;

-- 删除 disease_info 表（已有 diseases 表）
DROP TABLE IF EXISTS `disease_info`;

-- ==================== 清空用户相关数据 ====================
-- 清空关联数据（需要先删除外键关联的数据）
DELETE FROM `user_friendships`;
DELETE FROM `chat_memberships`;
DELETE FROM `chat_messages`;
DELETE FROM `chat_rooms`;
DELETE FROM `detect_results`;
DELETE FROM `feedback`;

-- 清空用户表
DELETE FROM `users`;

-- ==================== 创建系统管理员账户 ====================
-- 用户名: admin601
-- 密码: 88888888
-- 密码哈希: 使用 BCrypt 加密后的值（$2a$10$...）
INSERT INTO `users` (`username`, `password_hash`, `created_at`, `nickname`, `email`, `avatar_url`, `enabled`, `role`, `user_type`) 
VALUES (
    'admin601', 
    '$2b$12$NnB7nUvkgX/uT1WsdHAdcuYyxmjjO2MKYJ5QcwwK3A7OMrnUaXjK2',  -- 88888888 的 BCrypt 哈希
    NOW(),
    '系统管理员',
    'admin601@example.com',
    NULL,
    1,
    'admin',
    'admin'
);

SET FOREIGN_KEY_CHECKS = 1;

-- 验证
SELECT id, username, nickname, role, user_type, enabled FROM users WHERE username = 'admin601';

