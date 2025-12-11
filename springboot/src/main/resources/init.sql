SET NAMES utf8mb4;

SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for admin
-- ----------------------------
DROP TABLE IF EXISTS `admin`;

CREATE TABLE `admin`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `password` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'admin',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `username`(`username`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of admin
-- ----------------------------

-- ----------------------------
-- Table structure for announcements
-- ----------------------------
DROP TABLE IF EXISTS `announcements`;

CREATE TABLE `announcements`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `content` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `published` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of announcements
-- ----------------------------
INSERT INTO `announcements` VALUES (1, '欢迎使用智慧农情平台', '系统已完成部署，请管理员及时完善病害知识库。', 1, '2024-10-01 09:30:00.000000', '2024-10-01 09:30:00.000000');

-- ----------------------------
-- Table structure for chat_memberships
-- ----------------------------
DROP TABLE IF EXISTS `chat_memberships`;

CREATE TABLE `chat_memberships`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `room_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `joined_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_chat_memberships_room_user`(`room_id`, `user_id`) USING BTREE,
  INDEX `idx_chat_memberships_user`(`user_id`) USING BTREE,
  CONSTRAINT `fk_chat_memberships_room` FOREIGN KEY (`room_id`) REFERENCES `chat_rooms` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_chat_memberships_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of chat_memberships
-- ----------------------------
INSERT INTO `chat_memberships` VALUES (1, 1, 1, '2024-10-30 22:56:31.264187');
INSERT INTO `chat_memberships` VALUES (2, 1, 2, '2024-10-30 22:56:31.264187');
INSERT INTO `chat_memberships` VALUES (3, 2, 5, '2025-10-31 22:35:18.869826');
INSERT INTO `chat_memberships` VALUES (4, 2, 1, '2025-10-31 22:35:18.869826');

-- ----------------------------
-- Table structure for chat_messages
-- ----------------------------
DROP TABLE IF EXISTS `chat_messages`;

CREATE TABLE `chat_messages`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `room_id` bigint NOT NULL,
  `sender_id` bigint NOT NULL,
  `content` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_chat_messages_room`(`room_id`) USING BTREE,
  INDEX `idx_chat_messages_sender`(`sender_id`) USING BTREE,
  CONSTRAINT `fk_chat_messages_room` FOREIGN KEY (`room_id`) REFERENCES `chat_rooms` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_chat_messages_sender` FOREIGN KEY (`sender_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of chat_messages
-- ----------------------------
INSERT INTO `chat_messages` VALUES (1, 1, 2, '管理员好，最新的识别模型已经更新完毕。', '2024-10-31 09:05:12.000000');
INSERT INTO `chat_messages` VALUES (2, 1, 2, '请二位', '2025-10-31 17:06:15.108168');
INSERT INTO `chat_messages` VALUES (3, 2, 5, '123456', '2025-10-31 22:35:34.866329');
INSERT INTO `chat_messages` VALUES (4, 2, 5, '??????', '2025-10-31 23:56:09.099885');

-- ----------------------------
-- Table structure for chat_rooms
-- ----------------------------
DROP TABLE IF EXISTS `chat_rooms`;

CREATE TABLE `chat_rooms`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `direct_key` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_chat_rooms_direct_key`(`direct_key`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of chat_rooms
-- ----------------------------
INSERT INTO `chat_rooms` VALUES (1, 'direct', NULL, 'admin|sale_supervisor', '2024-10-30 22:56:31.153402');
INSERT INTO `chat_rooms` VALUES (2, 'direct', NULL, 'admin|mtj', '2025-10-31 22:35:18.869826');

-- ----------------------------
-- Table structure for detect_results
-- ----------------------------
DROP TABLE IF EXISTS `detect_results`;

CREATE TABLE `detect_results`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `predicted_class` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `confidence` double NOT NULL DEFAULT 0,
  `advice` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `image_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `model_label` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `disease_id` bigint NULL DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_detect_username_created`(`username`, `created_at`) USING BTREE,
  INDEX `idx_detect_predicted_class`(`predicted_class`) USING BTREE,
  INDEX `FKs3a979rv8lxfwc26cd5ldo6if`(`disease_id`) USING BTREE,
  INDEX `FK6k2uxugdqj6y1chmy20yt9nfa`(`user_id`) USING BTREE,
  CONSTRAINT `FK6k2uxugdqj6y1chmy20yt9nfa` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_detect_username` FOREIGN KEY (`username`) REFERENCES `users` (`username`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `FKs3a979rv8lxfwc26cd5ldo6if` FOREIGN KEY (`disease_id`) REFERENCES `diseases` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of detect_results
-- ----------------------------

-- ----------------------------
-- Table structure for disease_info
-- ----------------------------
DROP TABLE IF EXISTS `disease_info`;

CREATE TABLE `disease_info`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `class_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `common_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `symptoms` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `advice` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `sample_image` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of disease_info
-- ----------------------------

-- ----------------------------
-- Table structure for diseases
-- ----------------------------
DROP TABLE IF EXISTS `diseases`;

CREATE TABLE `diseases`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `description` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `advice` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NULL DEFAULT NULL,
  `model_label` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `plant` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_diseases_model_label`(`model_label`)
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of diseases
-- ----------------------------
INSERT INTO `diseases` (`id`, `name`, `description`, `created_at`, `updated_at`, `model_label`, `plant`) VALUES
  (1, '玉米灰斑病', '病原为尾孢菌，常在高温高湿条件下爆发。叶片初现小褐点，后扩展成长条形灰褐斑。', '2024-09-01 08:00:00.000000', '2024-09-15 10:15:00.000000', 'Corn_(maize)___Cercospora_leaf_spot Gray_leaf_spot', 'Corn'),
  (2, '草莓叶灼病', '主要发生在幼叶及老叶，初现褐色小点，逐渐扩展成不规则斑块。', '2024-09-10 14:12:00.000000', NULL, 'Strawberry___Leaf_scorch', 'Strawberry'),
  (3, '番茄早疫病', '病斑呈同心轮纹状，严重时可导致整叶枯死。', '2024-09-18 09:45:00.000000', NULL, 'Tomato___Early_blight', 'Tomato');

-- ----------------------------
-- Table structure for feedback
-- ----------------------------
DROP TABLE IF EXISTS `feedback`;

CREATE TABLE `feedback`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `user_id` bigint NULL DEFAULT NULL,
  `test_image` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `response_data` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `test_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_feedback_user_id`(`user_id`) USING BTREE,
  CONSTRAINT `fk_feedback_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of feedback
-- ----------------------------

-- ----------------------------
-- Table structure for user_friendships
-- ----------------------------
DROP TABLE IF EXISTS `user_friendships`;

CREATE TABLE `user_friendships`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `friend_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `UKrpnh12kt2hf9rrfo7aya72375`(`user_id`, `friend_id`) USING BTREE,
  INDEX `FK9bn6wxhlyeednpi1o3ff9f9ma`(`friend_id`) USING BTREE,
  CONSTRAINT `FK9bn6wxhlyeednpi1o3ff9f9ma` FOREIGN KEY (`friend_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `FKc3loqbh68jxojn42c5wsxhtwv` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user_friendships
-- ----------------------------
INSERT INTO `user_friendships` VALUES (1, '2025-10-31 22:35:18.866804', 5, 1);

-- ----------------------------
-- Table structure for users
-- ----------------------------
DROP TABLE IF EXISTS `users`;

CREATE TABLE `users`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `password_hash` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `nickname` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `email` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `avatar_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `role` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'user',
  `user_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'farmer',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_users_username`(`username`) USING BTREE,
  UNIQUE INDEX `uk_users_email`(`email`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of users
-- ----------------------------
INSERT INTO `users` (`id`, `username`, `password_hash`, `created_at`, `nickname`, `email`, `avatar_url`, `enabled`, `user_type`) VALUES
  (1, 'admin', '$2a$12$oZ52ZP9ktYwntyht8zOz7eXTFNSXDF.nxlT81aBNWbR.ZOqgRjJtO', '2024-01-01 09:00:00.000000', '系统管理员', 'admin@example.com', NULL, 1, 'admin'),
  (2, 'sale_supervisor', '$2a$12$EFVyhj5.x0ilHVqKy35PT.nDbJHxgBpJAnxmQGS4FBbb/zD5BQGO.', '2024-05-20 08:30:00.000000', '种植顾问', 'supervisor@example.com', NULL, 1, 'farmer'),
  (3, 'demo_user', '$2a$12$oLK5a609XvhzEH94PFzzduBBbQe5txsDo3pO8qAW0tEHiwhq4rkOe', '2024-07-12 10:15:00.000000', '示例用户', 'demo@example.com', NULL, 1, 'farmer'),
  (4, 'support_admin', '$2a$10$Rzz9cdXyq6bFJQxM/jKYoudB3ONLnKZnzdnSQuI7zPEd4dEXygKlG', '2025-10-31 22:30:58.852051', '运维管理员', 'support@example.com', NULL, 1, 'admin'),
  (5, 'mtj', '$2a$10$qQMPpxMLrn1Lq20LgHqr2e4fyNPyw43/hfMOLrNUhPKISF2wOCCh2', '2025-10-31 22:33:30.557067', '农情专家', 'expert@example.com', NULL, 1, 'expert');

SET FOREIGN_KEY_CHECKS = 1;
