-- 修复 detect_results 表的 username 字段问题
-- 将 username 字段改为可空，以匹配 Java 实体类

-- 1. 修改 username 字段为可空
ALTER TABLE `detect_results` 
MODIFY COLUMN `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL;

-- 2. 修改外键约束，允许 ON DELETE SET NULL
ALTER TABLE `detect_results` 
DROP FOREIGN KEY `fk_detect_username`;

ALTER TABLE `detect_results` 
ADD CONSTRAINT `fk_detect_username` 
FOREIGN KEY (`username`) REFERENCES `users` (`username`) 
ON DELETE SET NULL ON UPDATE CASCADE;

-- 3. 可选：如果已有数据，可以用 user_id 关联填充 username
UPDATE `detect_results` dr 
JOIN `users` u ON dr.user_id = u.id 
SET dr.username = u.username 
WHERE dr.username IS NULL;

-- 4. 将缺失的 predicted_class 补齐为 model_label
UPDATE `detect_results`
SET predicted_class = model_label
WHERE (predicted_class IS NULL OR predicted_class = '')
  AND model_label IS NOT NULL;

