-- 植物病害分布情况表 (bhxx)
-- 创建时间: 2025-11-07

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for bhxx
-- ----------------------------
DROP TABLE IF EXISTS `bhxx`;

CREATE TABLE `bhxx` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `植物名称` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '植物名称',
  `病害名称` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '病害名称',
  `分布区域` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '分布区域',
  `分布时间` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '分布时间（月份/季节/年份）',
  `防治方法` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '防治方法',
  `创建时间` datetime(6) DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  `更新时间` datetime(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_植物名称` (`植物名称`) USING BTREE,
  KEY `idx_病害名称` (`病害名称`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='植物病害分布情况表';

SET FOREIGN_KEY_CHECKS = 1;

