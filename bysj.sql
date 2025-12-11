/*
 Navicat Premium Data Transfer

 Source Server         : local
 Source Server Type    : MySQL
 Source Server Version : 80043
 Source Host           : localhost:3306
 Source Schema         : bysj

 Target Server Type    : MySQL
 Target Server Version : 80043
 File Encoding         : 65001

 Date: 21/11/2025 14:21:49
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

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
-- Table structure for bhxx
-- ----------------------------
DROP TABLE IF EXISTS `bhxx`;
CREATE TABLE `bhxx`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `植物名称` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '植物名称',
  `病害名称` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '病害名称',
  `分布区域` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '分布区域',
  `分布时间` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '分布时间（月份/季节/年份）',
  `防治方法` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '防治方法',
  `创建时间` datetime(6) NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  `更新时间` datetime(6) NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_植物名称`(`植物名称`) USING BTREE,
  INDEX `idx_病害名称`(`病害名称`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 294 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '植物病害分布情况表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bhxx
-- ----------------------------
INSERT INTO `bhxx` VALUES (255, '苹果', '苹果黑星病', '主要发生在云南省苹果种植较集中的冷凉高海拔地区，如昭通市、丽江市、迪庆州等地。', '3-6月（苹果展叶至幼果期，多雨潮湿时发病重）', '病害发生前（防）：选用抗病品种，增施有机肥改善通风透光；清除落叶和病残体集中处理；萌芽前和生长期根据病情喷施保护性或内吸性杀菌剂。 病害发生后（治）：发病后应根据田间实际病情，结合当地植保部门或农业技术人员建议，选择对苹果黑星病 有效的药剂进行防治，并注意轮换用药和安全间隔期。', '2025-11-20 12:48:21.148960', '2025-11-20 12:48:21.148960');
INSERT INTO `bhxx` VALUES (256, '苹果', '苹果黑腐病', '云南省苹果园分布区，尤以昭通市、丽江市等老果园较多地区发病偏重。', '5-9月（雨水较多、气温偏高阶段病斑和果腐发生明显）', '病害发生前（防）：修剪并烧毁病枝、病果和枯枝；雨季前后喷施对黑腐病有效的杀菌剂；注意园内排水和通风。 病害发生后（治）：发病后应根据田间实际病情，结合当地植保部门或农业技术人员建议，选择对苹果黑腐病 有效的药剂进行防治，并注意轮换用药和安全间隔期。', '2025-11-20 12:48:21.149303', '2025-11-20 12:48:21.149303');
INSERT INTO `bhxx` VALUES (257, '苹果', '苹果赤锈病', '在云南省苹果与针叶树混栽或相邻分布的果园区域，如昭通市、丽江市部分山区果园。', '3-5月（苹果展叶至幼果期，遇连续降雨时易发病）', '病害发生前（防）：避免在苹果园附近种植病源针叶树或清除病瘤；萌芽至展叶期喷施保护性杀菌剂；加强修剪和清园。 病害发生后（治）：发病后应根据田间实际病情，结合当地植保部门或农业技术人员建议，选择对苹果赤锈病 有效的药剂进行防治，并注意轮换用药和安全间隔期。', '2025-11-20 12:48:21.149626', '2025-11-20 12:48:21.149626');
INSERT INTO `bhxx` VALUES (258, '苹果', '无', '无', '无', '无', '2025-11-20 12:48:21.149828', '2025-11-20 12:48:21.149828');
INSERT INTO `bhxx` VALUES (259, '无植物', '背景无叶片', '无', '无', '无', '2025-11-20 12:48:21.150019', '2025-11-20 12:48:21.150019');
INSERT INTO `bhxx` VALUES (260, '蓝莓', '无', '无', '无', '无', '2025-11-20 12:48:21.150197', '2025-11-20 12:48:21.150197');
INSERT INTO `bhxx` VALUES (261, '樱桃', '无', '无', '无', '无', '2025-11-20 12:48:21.150378', '2025-11-20 12:48:21.150378');
INSERT INTO `bhxx` VALUES (262, '樱桃', '樱桃白粉病', '云南省樱桃栽培区，如昆明市周边、曲靖市、昭通市等地的樱桃园。', '3-6月（樱桃展叶至结果期，晴暖干燥、昼夜温差大时易流行）', '病害发生前（防）：合理修剪、改善通风透光；发病初期及时喷施对白粉病有效的杀菌剂；注意清除病叶病梢。 病害发生后（治）：发病后应根据田间实际病情，结合当地植保部门或农业技术人员建议，选择对樱桃白粉病 有效的药剂进行防治，并注意轮换用药和安全间隔期。', '2025-11-20 12:48:21.150579', '2025-11-20 12:48:21.150579');
INSERT INTO `bhxx` VALUES (263, '玉米', '玉米灰斑病', '云南省玉米主产区的中低海拔地区，如曲靖市、昆明市、红河州、文山州等地的玉米田。', '6-9月（玉米拔节以后至灌浆期，高温高湿或降雨多时易暴发）', '病害发生前（防）：选择抗病品种，合理密植和轮作，减少玉米残株留在田间；在病害流行前或发病初期喷施系统性杀菌剂。 病害发生后（治）：发病后应根据田间实际病情，结合当地植保部门或农业技术人员建议，选择对玉米灰斑病 有效的药剂进行防治，并注意轮换用药和安全间隔期。', '2025-11-20 12:48:21.150781', '2025-11-20 12:48:21.150781');
INSERT INTO `bhxx` VALUES (264, '玉米', '玉米普通锈病', '在云南省各玉米产区普遍发生，以气候偏凉爽、湿度大的高原玉米区较为多见，如昭通市、曲靖市等地。', '6-9月（玉米拔节至抽雄期为主要发病阶段）', '病害发生前（防）：选用抗锈病品种；适当早播避开发病高峰；发病初期及时喷施对锈病有效的杀菌剂。 病害发生后（治）：发病后应根据田间实际病情，结合当地植保部门或农业技术人员建议，选择对玉米普通锈病 有效的药剂进行防治，并注意轮换用药和安全间隔期。', '2025-11-20 12:48:21.150979', '2025-11-20 12:48:21.150979');
INSERT INTO `bhxx` VALUES (265, '玉米', '无', '无', '无', '无', '2025-11-20 12:48:21.151157', '2025-11-20 12:48:21.151157');
INSERT INTO `bhxx` VALUES (266, '玉米', '玉米大斑病', '云南省中高海拔玉米种植区，如昭通市、曲靖市、昆明市部分山区等地。', '7-9月（玉米大喇叭口期至灌浆期，阴雨连绵时病斑扩展快）', '病害发生前（防）：推广抗病品种，实行与豆类或其他作物轮作，清除病残体；在发病初期喷施针对大斑病的杀菌剂。 病害发生后（治）：发病后应根据田间实际病情，结合当地植保部门或农业技术人员建议，选择对玉米大斑病 有效的药剂进行防治，并注意轮换用药和安全间隔期。', '2025-11-20 12:48:21.151340', '2025-11-20 12:48:21.151340');
INSERT INTO `bhxx` VALUES (267, '葡萄', '葡萄黑腐病', '云南省葡萄主产区，如昆明市、玉溪市、红河州、大理州等地的葡萄园。', '4-7月（展叶后至坐果、果实膨大期，连续降雨时易流行）', '病害发生前（防）：清除并深埋病果和病枝；合理修剪和整形，保持通风透光；生长期根据病情喷施保护性或内吸性杀菌剂。 病害发生后（治）：发病后应根据田间实际病情，结合当地植保部门或农业技术人员建议，选择对葡萄黑腐病 有效的药剂进行防治，并注意轮换用药和安全间隔期。', '2025-11-20 12:48:21.151526', '2025-11-20 12:48:21.151526');
INSERT INTO `bhxx` VALUES (268, '葡萄', '葡萄虎眼病（Esca病）', '云南省气候较温暖、葡萄栽培历史较长的老龄葡萄园，如红河州、玉溪市、大理州部分地区。', '6-9月（夏季高温期叶片症状明显，植株衰退加重）', '病害发生前（防）：避免大伤口修剪并及时保护剪口；清除病株和严重病枝并销毁；加强栽培管理，降低植株衰弱程度。 病害发生后（治）：发病后应根据田间实际病情，结合当地植保部门或农业技术人员建议，选择对葡萄虎眼病（Esca病） 有效的药剂进行防治，并注意轮换用药和安全间隔期。', '2025-11-20 12:48:21.151720', '2025-11-20 12:48:21.151720');
INSERT INTO `bhxx` VALUES (269, '葡萄', '无', '无', '无', '无', '2025-11-20 12:48:21.151900', '2025-11-20 12:48:21.151900');
INSERT INTO `bhxx` VALUES (270, '葡萄', '葡萄叶枯病（伊萨氏叶斑病）', '云南省葡萄栽培偏湿润地区，如红河州、玉溪市、大理州部分山地果园。', '6-9月（雨水多、叶片郁闭、湿度大的时期易发病）', '病害发生前（防）：合理密植与修剪，改善冠层通风；清除病叶；生长期适时喷施广谱性杀菌剂。 病害发生后（治）：发病后应根据田间实际病情，结合当地植保部门或农业技术人员建议，选择对葡萄叶枯病（伊萨氏叶斑病） 有效的药剂进行防治，并注意轮换用药和安全间隔期。', '2025-11-20 12:48:21.152082', '2025-11-20 12:48:21.152082');
INSERT INTO `bhxx` VALUES (271, '柑橘', '柑橘黄龙病', '主要分布在云南省南部和东南部柑橘主产区，如红河州、文山州、玉溪市、普洱市、西双版纳州等地。', '全年均可见症状，以3-12月新梢生长及结果期为明显。', '病害发生前（防）：严格检疫，使用无病苗木；综合防治传播媒介木虱，控制虫口密度；发现疑似病株及时挖除并无害化处理。 病害发生后（治）：发病后应根据田间实际病情，结合当地植保部门或农业技术人员建议，选择对柑橘黄龙病 有效的药剂进行防治，并注意轮换用药和安全间隔期。', '2025-11-20 12:48:21.152266', '2025-11-20 12:48:21.152266');
INSERT INTO `bhxx` VALUES (272, '桃', '桃细菌性斑点病（穿孔病）', '云南省桃树种植区，如昆明市、玉溪市、曲靖市、大理州等地的桃园和苗圃。', '3-6月（桃树发芽展叶至幼果期，多雨高湿年份病斑多）', '病害发生前（防）：选择较抗病品种；采前后喷施含铜制剂或其他细菌性药剂；注意修剪和清园，减少带菌病残体。 病害发生后（治）：发病后应根据田间实际病情，结合当地植保部门或农业技术人员建议，选择对桃细菌性斑点病（穿孔病） 有效的药剂进行防治，并注意轮换用药和安全间隔期。', '2025-11-20 12:48:21.152461', '2025-11-20 12:48:21.152461');
INSERT INTO `bhxx` VALUES (273, '桃', '无', '无', '无', '无', '2025-11-20 12:48:21.152686', '2025-11-20 12:48:21.152686');
INSERT INTO `bhxx` VALUES (274, '甜椒', '甜椒细菌性斑点病', '云南省设施和露地甜椒、辣椒产区，如昆明市、曲靖市、玉溪市、红河州、文山州等地。', '4-9月（辣椒生长期，多雨或喷灌频繁时叶片和果实易感病）', '病害发生前（防）：选用健壮无病种苗；避免带菌种子并进行种子消毒；改善通风排水，控制氮肥用量；发病初期喷施含铜制剂等细菌性药剂。 病害发生后（治）：发病后应根据田间实际病情，结合当地植保部门或农业技术人员建议，选择对甜椒细菌性斑点病 有效的药剂进行防治，并注意轮换用药和安全间隔期。', '2025-11-20 12:48:21.152869', '2025-11-20 12:48:21.152869');
INSERT INTO `bhxx` VALUES (275, '甜椒', '无', '无', '无', '无', '2025-11-20 12:48:21.153044', '2025-11-20 12:48:21.153044');
INSERT INTO `bhxx` VALUES (276, '马铃薯', '马铃薯早疫病', '云南省冷凉山区马铃薯主产区，如昭通市、曲靖市、昆明市、大理州、丽江市等地。', '4-7月（马铃薯现蕾到中后期，植株逐渐衰老时发病增多）', '病害发生前（防）：合理施肥，防止偏施氮肥和缺钾；清除病残体；在发病初期喷施保护性或内吸性杀菌剂。 病害发生后（治）：发病后应根据田间实际病情，结合当地植保部门或农业技术人员建议，选择对马铃薯早疫病 有效的药剂进行防治，并注意轮换用药和安全间隔期。', '2025-11-20 12:48:21.153226', '2025-11-20 12:48:21.153226');
INSERT INTO `bhxx` VALUES (277, '马铃薯', '无', '无', '无', '无', '2025-11-20 12:48:21.153406', '2025-11-20 12:48:21.153406');
INSERT INTO `bhxx` VALUES (278, '马铃薯', '马铃薯晚疫病', '云南省高海拔、多雾多雨的马铃薯种植区，如昭通市、曲靖市、大理州、丽江市等地。', '6-8月（持续低温高湿或降雨频繁时易暴发流行）', '病害发生前（防）：选用抗病品种，实行高垄栽培和合理密植；雨季前和发病初期及时喷施针对晚疫病的杀菌剂；收获后处理残株和病薯。 病害发生后（治）：发病后应根据田间实际病情，结合当地植保部门或农业技术人员建议，选择对马铃薯晚疫病 有效的药剂进行防治，并注意轮换用药和安全间隔期。', '2025-11-20 12:48:21.153588', '2025-11-20 12:48:21.153588');
INSERT INTO `bhxx` VALUES (279, '树莓', '无', '无', '无', '无', '2025-11-20 12:48:21.153763', '2025-11-20 12:48:21.153763');
INSERT INTO `bhxx` VALUES (280, '大豆', '无', '无', '无', '无', '2025-11-20 12:48:21.153933', '2025-11-20 12:48:21.153933');
INSERT INTO `bhxx` VALUES (281, '南瓜', '南瓜白粉病', '云南省葫芦科蔬菜集中种植区，如昆明市、玉溪市、曲靖市、红河州等地。', '5-9月（南瓜生长中后期、天气干燥少雨且昼夜温差大时发病较重）', '病害发生前（防）：合理密植和整枝打叶，加强通风；发病初期喷施对白粉病有效的药剂；加施有机肥提高植株抗性。 病害发生后（治）：发病后应根据田间实际病情，结合当地植保部门或农业技术人员建议，选择对南瓜白粉病 有效的药剂进行防治，并注意轮换用药和安全间隔期。', '2025-11-20 12:48:21.154114', '2025-11-20 12:48:21.154114');
INSERT INTO `bhxx` VALUES (282, '草莓', '无', '无', '无', '无', '2025-11-20 12:48:21.154331', '2025-11-20 12:48:21.154331');
INSERT INTO `bhxx` VALUES (283, '草莓', '草莓叶枯病（叶灼病）', '云南省草莓设施栽培区和冷凉高原种植区，如昆明市周边、曲靖市、大理州等地。', '2-5月及9-11月（春季和秋季气温适宜且湿度偏高时易发生）', '病害发生前（防）：选用健壮苗，合理密植，铺设地膜减少雨水飞溅；清除病叶；发病初期喷施广谱性杀菌剂。 病害发生后（治）：发病后应根据田间实际病情，结合当地植保部门或农业技术人员建议，选择对草莓叶枯病（叶灼病） 有效的药剂进行防治，并注意轮换用药和安全间隔期。', '2025-11-20 12:48:21.154539', '2025-11-20 12:48:21.154539');
INSERT INTO `bhxx` VALUES (284, '番茄', '番茄细菌性斑点病', '云南省番茄设施和露地栽培区，如昆明市、玉溪市、曲靖市、红河州、文山州等地。', '3-7月（番茄生长期，雨水多或叶面长时间潮湿时发病明显）', '病害发生前（防）：选择健康种子并进行种子消毒；避免大水漫灌，减少叶面长期积水；发病初期喷施含铜制剂等细菌性药剂。 病害发生后（治）：发病后应根据田间实际病情，结合当地植保部门或农业技术人员建议，选择对番茄细菌性斑点病 有效的药剂进行防治，并注意轮换用药和安全间隔期。', '2025-11-20 12:48:21.154761', '2025-11-20 12:48:21.154761');
INSERT INTO `bhxx` VALUES (285, '番茄', '番茄早疫病', '在云南省番茄主产区普遍发生，如昆明市、玉溪市、曲靖市、红河州等地。', '4-7月（番茄中后期叶片老化、气温较高时病害加重）', '病害发生前（防）：实施轮作，清除病残体；合理施肥提高抗性；在发病初期及时喷施针对早疫病的杀菌剂。 病害发生后（治）：发病后应根据田间实际病情，结合当地植保部门或农业技术人员建议，选择对番茄早疫病 有效的药剂进行防治，并注意轮换用药和安全间隔期。', '2025-11-20 12:48:21.154952', '2025-11-20 12:48:21.154952');
INSERT INTO `bhxx` VALUES (286, '番茄', '无', '无', '无', '无', '2025-11-20 12:48:21.156496', '2025-11-20 12:48:21.156496');
INSERT INTO `bhxx` VALUES (287, '番茄', '番茄晚疫病', '云南省凉爽多雨或高海拔番茄种植区，如昆明市部分高海拔乡镇、曲靖市、大理州等地。', '6-10月（阴雨连绵、空气湿度大的时期易暴发流行）', '病害发生前（防）：选用抗病品种，避免低洼积水地种植；加强通风排湿；在流行前和发病初期喷施晚疫病专用杀菌剂。 病害发生后（治）：发病后应根据田间实际病情，结合当地植保部门或农业技术人员建议，选择对番茄晚疫病 有效的药剂进行防治，并注意轮换用药和安全间隔期。', '2025-11-20 12:48:21.156700', '2025-11-20 12:48:21.156700');
INSERT INTO `bhxx` VALUES (288, '番茄', '番茄叶霉病', '主要危害云南省设施番茄种植区，如昆明市郊区、玉溪市、红河州等大棚种植区域。', '3-6月及9-11月（棚内湿度长期偏高、通风不良时多见）', '病害发生前（防）：合理密植和整枝，控制棚内湿度，及时通风；发病初期喷施内吸性杀菌剂；清除病叶并带出棚外处理。 病害发生后（治）：发病后应根据田间实际病情，结合当地植保部门或农业技术人员建议，选择对番茄叶霉病 有效的药剂进行防治，并注意轮换用药和安全间隔期。', '2025-11-20 12:48:21.156888', '2025-11-20 12:48:21.156888');
INSERT INTO `bhxx` VALUES (289, '番茄', '番茄尾孢叶斑病（Septoria叶斑病）', '云南省番茄栽培区中叶片郁闭、湿度较大的地块，如昆明市、玉溪市、曲靖市等地蔬菜基地。', '4-7月（番茄生长中后期、雨水较多年份易发生）', '病害发生前（防）：合理密植与轮作，注意清除病残体；在病害初期喷施广谱性杀菌剂。 病害发生后（治）：发病后应根据田间实际病情，结合当地植保部门或农业技术人员建议，选择对番茄尾孢叶斑病（Septoria叶斑病） 有效的药剂进行防治，并注意轮换用药和安全间隔期。', '2025-11-20 12:48:21.157076', '2025-11-20 12:48:21.157076');
INSERT INTO `bhxx` VALUES (290, '番茄', '二斑叶螨为害番茄', '云南省番茄及蔬菜主产区的设施栽培地，如昆明市、玉溪市、曲靖市等地。', '5-9月（高温干燥季节螨类快速繁殖期）', '病害发生前（防）：加强水肥管理，保持适度空气湿度；及时清除杂草，减少虫源；在螨量较低时采用生物或化学杀螨剂防治。 病害发生后（治）：发病后应根据田间实际病情，结合当地植保部门或农业技术人员建议，选择对二斑叶螨为害番茄 有效的药剂进行防治，并注意轮换用药和安全间隔期。', '2025-11-20 12:48:21.157264', '2025-11-20 12:48:21.157264');
INSERT INTO `bhxx` VALUES (291, '番茄', '番茄靶斑病', '云南省南部及中部温暖湿润番茄种植区，如红河州、文山州、玉溪市、普洱市等地。', '5-9月（叶片郁闭、降雨频繁时病斑扩展快）', '病害发生前（防）：避免连作，清除病残体；改善通风透光条件；发病初期喷施针对靶斑病的杀菌剂。 病害发生后（治）：发病后应根据田间实际病情，结合当地植保部门或农业技术人员建议，选择对番茄靶斑病 有效的药剂进行防治，并注意轮换用药和安全间隔期。', '2025-11-20 12:48:21.157449', '2025-11-20 12:48:21.157449');
INSERT INTO `bhxx` VALUES (292, '番茄', '番茄花叶病毒病', '云南省各番茄产区的育苗和生产地，如昆明市、玉溪市、曲靖市、红河州等地。', '2-10月（番茄育苗至结果期均可见，以温度适宜、管理粗放时较多）', '病害发生前（防）：使用无病种子和健康苗木；加强温室卫生，减少机械操作传播；拔除严重病株集中处理；合理轮作，避免连作重茬。 病害发生后（治）：发病后应根据田间实际病情，结合当地植保部门或农业技术人员建议，选择对番茄花叶病毒病 有效的药剂进行防治，并注意轮换用药和安全间隔期。', '2025-11-20 12:48:21.157633', '2025-11-20 12:48:21.157633');
INSERT INTO `bhxx` VALUES (293, '番茄', '番茄黄化曲叶病毒病', '云南省中低海拔番茄主产区，特别是白粉虱危害较重的地区，如红河州、文山州、玉溪市、西双版纳州等地。', '3-7月（番茄生长前中期，温度较高且白粉虱密度大时易流行）', '病害发生前（防）：选用抗病或耐病品种；加强对白粉虱等传播媒介的综合防治；使用防虫网、银灰地膜等物理措施；尽量采用无毒苗移栽。 病害发生后（治）：发病后应根据田间实际病情，结合当地植保部门或农业技术人员建议，选择对番茄黄化曲叶病毒病 有效的药剂进行防治，并注意轮换用药和安全间隔期。', '2025-11-20 12:48:21.157819', '2025-11-20 12:48:21.157819');

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
  UNIQUE INDEX `UK5vf9q12htnnl9t50329xno550`(`room_id`, `user_id`) USING BTREE,
  INDEX `idx_chat_memberships_user`(`user_id`) USING BTREE,
  CONSTRAINT `fk_chat_memberships_room` FOREIGN KEY (`room_id`) REFERENCES `chat_rooms` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_chat_memberships_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 13 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of chat_memberships
-- ----------------------------
INSERT INTO `chat_memberships` VALUES (6, 3, 6, '2025-11-12 23:56:25.068109');
INSERT INTO `chat_memberships` VALUES (7, 4, 12, '2025-11-19 14:13:20.348896');
INSERT INTO `chat_memberships` VALUES (8, 4, 11, '2025-11-19 14:13:20.354890');
INSERT INTO `chat_memberships` VALUES (9, 5, 13, '2025-11-19 20:42:13.137743');
INSERT INTO `chat_memberships` VALUES (10, 5, 11, '2025-11-19 20:42:13.140743');
INSERT INTO `chat_memberships` VALUES (11, 6, 15, '2025-11-19 21:41:11.829379');
INSERT INTO `chat_memberships` VALUES (12, 6, 13, '2025-11-19 21:41:11.831380');
INSERT INTO `chat_memberships` VALUES (13, 7, 16, '2025-11-20 17:00:45.746157');
INSERT INTO `chat_memberships` VALUES (14, 7, 11, '2025-11-20 17:00:45.750178');
INSERT INTO `chat_memberships` VALUES (15, 8, 16, '2025-11-20 17:00:58.180900');
INSERT INTO `chat_memberships` VALUES (16, 8, 12, '2025-11-20 17:00:58.183907');
INSERT INTO `chat_memberships` VALUES (17, 9, 15, '2025-11-20 17:15:27.560942');
INSERT INTO `chat_memberships` VALUES (18, 9, 11, '2025-11-20 17:15:27.562946');

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
) ENGINE = InnoDB AUTO_INCREMENT = 15 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of chat_messages
-- ----------------------------
INSERT INTO `chat_messages` VALUES (11, 4, 11, '😀', '2025-11-20 14:41:39.684850');
INSERT INTO `chat_messages` VALUES (12, 4, 11, '🍅', '2025-11-20 14:41:46.940586');
INSERT INTO `chat_messages` VALUES (13, 4, 11, '🌿', '2025-11-20 14:41:50.842061');
INSERT INTO `chat_messages` VALUES (14, 4, 12, '哈哈哈', '2025-11-20 15:13:23.138651');
INSERT INTO `chat_messages` VALUES (15, 6, 15, '[图片] /uploads/chat/9a8e0adb9e01451884b684d65e45f186.jpg', '2025-11-20 17:14:29.243769');
INSERT INTO `chat_messages` VALUES (16, 6, 15, '[附件] 20221108900213马童杰.pdf /uploads/chat/78ba6cea36634227afb8874973c7ff3e.pdf', '2025-11-20 17:14:47.579115');
INSERT INTO `chat_messages` VALUES (17, 6, 15, '🤣', '2025-11-20 17:15:10.370897');
INSERT INTO `chat_messages` VALUES (18, 6, 15, '🍅', '2025-11-20 17:15:12.451217');
INSERT INTO `chat_messages` VALUES (19, 6, 15, '🌿', '2025-11-20 17:15:14.095023');

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
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of chat_rooms
-- ----------------------------
INSERT INTO `chat_rooms` VALUES (1, 'direct', NULL, 'admin|sale_supervisor', '2024-10-30 22:56:31.153402');
INSERT INTO `chat_rooms` VALUES (2, 'direct', NULL, 'admin|mtj', '2025-10-31 22:35:18.869826');
INSERT INTO `chat_rooms` VALUES (3, 'direct', NULL, '001|admin601', '2025-11-12 23:56:25.056552');
INSERT INTO `chat_rooms` VALUES (4, 'direct', NULL, '11111111|88888888', '2025-11-19 14:13:20.342886');
INSERT INTO `chat_rooms` VALUES (5, 'direct', NULL, '12345678|88888888', '2025-11-19 20:42:13.133233');
INSERT INTO `chat_rooms` VALUES (6, 'direct', NULL, '12345678|82828282', '2025-11-19 21:41:11.826379');
INSERT INTO `chat_rooms` VALUES (7, 'direct', NULL, '00000000|88888888', '2025-11-20 17:00:45.741897');
INSERT INTO `chat_rooms` VALUES (8, 'direct', NULL, '00000000|11111111', '2025-11-20 17:00:58.178839');
INSERT INTO `chat_rooms` VALUES (9, 'direct', NULL, '82828282|88888888', '2025-11-20 17:15:27.558430');

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
  INDEX `idx_detect_results_username_created_at`(`username`, `created_at`) USING BTREE,
  INDEX `idx_detect_results_model_label`(`model_label`) USING BTREE,
  CONSTRAINT `FK6k2uxugdqj6y1chmy20yt9nfa` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_detect_username` FOREIGN KEY (`username`) REFERENCES `users` (`username`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `FKs3a979rv8lxfwc26cd5ldo6if` FOREIGN KEY (`disease_id`) REFERENCES `diseases` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 54 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of detect_results
-- ----------------------------
INSERT INTO `detect_results` VALUES (52, 'Tomato___Late_blight', 0.9562494158744812, '注意田间管理，加强虫害监测与综合防治', '/uploads/74ea557c75444b56a9d5a248ebda828c.jpg', '2025-11-20 14:20:41.305975', 'Tomato___Late_blight', NULL, 11, '88888888');
INSERT INTO `detect_results` VALUES (53, 'Apple___Bacterial_spot', 0.38000649213790894, '注意田间管理，加强虫害监测与综合防治', '/uploads/691af55940f5461296f8b5f962a3345e.jpg', '2025-11-20 15:15:33.940757', 'Apple___Bacterial_spot', NULL, 12, '11111111');
INSERT INTO `detect_results` VALUES (54, 'Tomato___Late_blight', 0.9562494158744812, '注意田间管理，加强虫害监测与综合防治', '/uploads/98af42a84b3a400b9e7de0172efc7aff.jpg', '2025-11-20 16:59:55.525179', 'Tomato___Late_blight', NULL, 16, '00000000');
INSERT INTO `detect_results` VALUES (56, 'Tomato___Bacterial_spot', 0.6008535325527191, '注意田间管理，加强虫害监测与综合防治', '/uploads/47acef58b3354102992c400d9d6cac7f.jpg', '2025-11-20 17:12:45.943826', 'Tomato___Bacterial_spot', NULL, 6, 'admin601');
INSERT INTO `detect_results` VALUES (57, 'Tomato___Late_blight', 0.9562494158744812, '注意田间管理，加强虫害监测与综合防治', '/uploads/522f61aa6b604a7cad3bb4fdfeee1a1b.jpg', '2025-11-20 21:38:58.305949', 'Tomato___Late_blight', NULL, 15, '82828282');
INSERT INTO `detect_results` VALUES (58, 'Corn___Cercospora_leaf_spot Gray_leaf_spot', 0.8212624490261078, '注意田间管理，加强虫害监测与综合防治', '/uploads/14596fad2df140d7b33470366e81232d.jpg', '2025-11-20 21:39:22.010010', 'Corn___Cercospora_leaf_spot Gray_leaf_spot', NULL, 15, '82828282');

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
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

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
  UNIQUE INDEX `uk_diseases_model_label`(`model_label`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of diseases
-- ----------------------------

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
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of feedback
-- ----------------------------

-- ----------------------------
-- Table structure for fzwp
-- ----------------------------
DROP TABLE IF EXISTS `fzwp`;
CREATE TABLE `fzwp`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `创建时间` datetime(6) NULL DEFAULT NULL,
  `物品图片` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `物品名称` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `上架时间` datetime(6) NULL DEFAULT NULL,
  `主要功能` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `物品价格` decimal(10, 2) NULL DEFAULT NULL,
  `应对病害` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `更新时间` datetime(6) NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of fzwp
-- ----------------------------
INSERT INTO `fzwp` VALUES (1, '2025-11-19 15:03:09.222293', '/uploads/fzwp/4fd6a91c01b74ff9814236ea0f0ee0ba.jpg', '风格的服饰', '2025-11-19 15:03:09.212474', '', 22.00, '', '2025-11-19 15:03:09.222293');
INSERT INTO `fzwp` VALUES (2, '2025-11-19 15:03:45.930959', '/uploads/fzwp/e5046701761945ff9c6cf81695424bc2.jpg', 'ijo', '2025-11-19 15:03:45.929448', '', 0.00, '', '2025-11-19 15:03:45.930959');

-- ----------------------------
-- Table structure for guide_article_comments
-- ----------------------------
DROP TABLE IF EXISTS `guide_article_comments`;
CREATE TABLE `guide_article_comments`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `article_id` bigint NOT NULL,
  `author_id` bigint NOT NULL,
  `parent_id` bigint NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `FK75ohgkv7ugou700xhjygbbygj`(`article_id`) USING BTREE,
  INDEX `FKpqx4ljkymja36s7vw3866m21l`(`author_id`) USING BTREE,
  INDEX `FKspq09rm1wh7ymxqayxdn321vr`(`parent_id`) USING BTREE,
  CONSTRAINT `FK75ohgkv7ugou700xhjygbbygj` FOREIGN KEY (`article_id`) REFERENCES `guide_articles` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `FKpqx4ljkymja36s7vw3866m21l` FOREIGN KEY (`author_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `FKspq09rm1wh7ymxqayxdn321vr` FOREIGN KEY (`parent_id`) REFERENCES `guide_article_comments` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of guide_article_comments
-- ----------------------------
INSERT INTO `guide_article_comments` VALUES (1, '比较方便的巴萨不对吧', '2025-11-20 16:11:07.601672', 1, 12, NULL);
INSERT INTO `guide_article_comments` VALUES (2, '阿达伟大我', '2025-11-20 16:11:15.273661', 1, 12, NULL);
INSERT INTO `guide_article_comments` VALUES (3, '阿达伟大ddw', '2025-11-20 16:26:03.842352', 2, 11, NULL);
INSERT INTO `guide_article_comments` VALUES (4, '返回给风格化法国', '2025-11-20 16:43:45.224856', 2, 15, 3);

-- ----------------------------
-- Table structure for guide_article_recommendations
-- ----------------------------
DROP TABLE IF EXISTS `guide_article_recommendations`;
CREATE TABLE `guide_article_recommendations`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `note` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `article_id` bigint NOT NULL,
  `fzwp_id` bigint NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `FKa56rd487yekkbxir6hjxk420y`(`article_id`) USING BTREE,
  INDEX `FKfhui3r9ab2kugqlg7upwlvaky`(`fzwp_id`) USING BTREE,
  CONSTRAINT `FKa56rd487yekkbxir6hjxk420y` FOREIGN KEY (`article_id`) REFERENCES `guide_articles` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `FKfhui3r9ab2kugqlg7upwlvaky` FOREIGN KEY (`fzwp_id`) REFERENCES `fzwp` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of guide_article_recommendations
-- ----------------------------
INSERT INTO `guide_article_recommendations` VALUES (1, NULL, 1, 1);
INSERT INTO `guide_article_recommendations` VALUES (2, NULL, 2, 2);

-- ----------------------------
-- Table structure for guide_articles
-- ----------------------------
DROP TABLE IF EXISTS `guide_articles`;
CREATE TABLE `guide_articles`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `cover_image_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `image_urls` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `author_id` bigint NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `FK18baqx1ef545x2vj0gn14ljq4`(`author_id`) USING BTREE,
  CONSTRAINT `FK18baqx1ef545x2vj0gn14ljq4` FOREIGN KEY (`author_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of guide_articles
-- ----------------------------
INSERT INTO `guide_articles` VALUES (1, '伟大啊麦当劳看完的空间那就开始吧当年马上都那么撒是你吧点击江阿不都开往北京的吧卡无边的恐惧把控但是就不打搅卡巴是', NULL, '2025-11-20 16:10:51.418637', NULL, '湖底归属地', '2025-11-20 16:10:51.418637', 12);
INSERT INTO `guide_articles` VALUES (2, '刚好放假回国法国海军北海v改过一次v有\n<img src=\"/uploads/chat/7c185d62f994428d838db7d8f1440fe6.jpg\" alt=\"图片\" />', '/uploads/chat/d71de65886b849359e59bcc990a908fd.jpg', '2025-11-20 16:25:38.560254', '[\"/uploads/chat/7c185d62f994428d838db7d8f1440fe6.jpg\",\"/uploads/chat/29b08b26056d470c95e36277d61a3caf.jpg\",\"/uploads/chat/59b92bd56f934c67a6a4b5daa29f3af0.jpg\"]', '恢复和官方公布', '2025-11-20 16:44:21.971768', 11);

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
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of user_friendships
-- ----------------------------
INSERT INTO `user_friendships` VALUES (3, '2025-11-19 14:13:20.332897', 12, 11);
INSERT INTO `user_friendships` VALUES (4, '2025-11-19 20:42:13.126726', 13, 11);
INSERT INTO `user_friendships` VALUES (5, '2025-11-19 21:41:11.822869', 15, 13);
INSERT INTO `user_friendships` VALUES (6, '2025-11-20 17:00:45.737266', 16, 11);
INSERT INTO `user_friendships` VALUES (7, '2025-11-20 17:00:58.176237', 16, 12);
INSERT INTO `user_friendships` VALUES (8, '2025-11-20 17:15:27.555919', 15, 11);

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
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_users_username`(`username`) USING BTREE,
  UNIQUE INDEX `uk_users_email`(`email`) USING BTREE,
  UNIQUE INDEX `UKdu5v5sr43g5bfnji4vb8hg5s3`(`phone`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 16 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of users
-- ----------------------------
INSERT INTO `users` VALUES (6, 'admin601', '$2a$10$ZHeD3738g.Rj4HwtqWQkDukxRSWkEp.VwVZyXoareEefX0yWSJvZ.', '2025-11-12 23:50:57.705493', '系统管理员', 'admin601@example.com', NULL, 1, 'admin', 'admin', NULL);
INSERT INTO `users` VALUES (11, '88888888', '$2a$10$Ne.FsZ37IIHDfLDCjOCXouleqQKIm3FulC7LuLofCLt9lLkMWbp/a', '2025-11-13 20:58:45.749810', '农情专家', NULL, '/uploads/avatars/a404527240b242d5af863ef9deb3cd91.jpg', 1, 'user', 'expert', NULL);
INSERT INTO `users` VALUES (12, '11111111', '$2a$10$wJ8PrtlgPJHMrnHf75M6p.5NuAKtK02rNLh6HEOvcESMzfFix9AfS', '2025-11-19 14:13:05.182408', '11111111', NULL, NULL, 1, 'user', 'expert', '11111111111');
INSERT INTO `users` VALUES (13, '12345678', '$2a$10$lOlOJg7c/2x5lsRDoxx/1e.mGpVntvyPiUef2KMjXncxyT2iTrVGC', '2025-11-19 20:41:35.136681', '张炳林', NULL, 'https://api.dicebear.com/7.x/identicon/svg?seed=12345678', 1, 'user', 'farmer', '12345678900');
INSERT INTO `users` VALUES (15, '82828282', '$2a$10$EqMfRgv.SQ8zGw/y2EdXiOT.ZDooYcV6pe2UClkLfTKV0UkRTiHly', '2025-11-19 21:39:58.651357', 'laffey-82', NULL, '/uploads/avatars/b7a6c6430fc544f88f265b607332fb46.jpg', 1, 'user', 'farmer', '18888888888');
INSERT INTO `users` VALUES (16, '00000000', '$2a$10$yslZI0ik02FEPUvyOnse9.cQtceCefaWK21EZoOA6cpckl5wLtYim', '2025-11-20 16:58:38.255280', 'jkl', NULL, '/uploads/avatars/d4b14cb972a44b00a4e6bc0b81e6ec67.jpg', 1, 'user', 'expert', '12345678901');

-- ----------------------------
-- Procedure structure for migrate_users_cleanup
-- ----------------------------
DROP PROCEDURE IF EXISTS `migrate_users_cleanup`;
delimiter ;;
CREATE PROCEDURE `migrate_users_cleanup`()
BEGIN
  DECLARE v_db VARCHAR(128);
  DECLARE v_exists_user INT DEFAULT 0;

  -- Variables for iterating constraints referencing `user`
  DECLARE v_constraint_name VARCHAR(128);
  DECLARE v_table_name VARCHAR(128);
  DECLARE v_delete_rule VARCHAR(16);
  DECLARE v_update_rule VARCHAR(16);
  DECLARE done INT DEFAULT 0;

  DECLARE cur CURSOR FOR
    SELECT rc.CONSTRAINT_NAME, rc.TABLE_NAME, rc.DELETE_RULE, rc.UPDATE_RULE
    FROM information_schema.REFERENTIAL_CONSTRAINTS rc
    WHERE rc.CONSTRAINT_SCHEMA = DATABASE()
      AND rc.REFERENCED_TABLE_NAME = 'user';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  SET v_db = DATABASE();

  -- Safety: disable FK checks during migration
  SET @old_fk_checks := @@FOREIGN_KEY_CHECKS;
  SET FOREIGN_KEY_CHECKS = 0;

  -- 0) Remove duplicate unique index on chat_memberships if present
  IF EXISTS (
    SELECT 1 FROM information_schema.STATISTICS s
    WHERE s.TABLE_SCHEMA = v_db AND s.TABLE_NAME = 'chat_memberships' AND s.INDEX_NAME = 'UK5vf9q12htnnl9t50329xno550'
  ) THEN
    SET @sql := 'ALTER TABLE `chat_memberships` DROP INDEX `UK5vf9q12htnnl9t50329xno550`';
    PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;

  -- 1) Align users.user_type default with application ('farmer')
  IF EXISTS (
    SELECT 1 FROM information_schema.COLUMNS c
    WHERE c.TABLE_SCHEMA = v_db AND c.TABLE_NAME = 'users' AND c.COLUMN_NAME = 'user_type' AND (c.COLUMN_DEFAULT IS NULL OR c.COLUMN_DEFAULT <> 'farmer')
  ) THEN
    SET @sql := 'ALTER TABLE `users` MODIFY COLUMN `user_type` varchar(32) NULL DEFAULT ''farmer''';
    PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;

  -- 2) Ensure detect_results.user_id FK uses ON DELETE CASCADE
  IF EXISTS (
    SELECT 1 FROM information_schema.KEY_COLUMN_USAGE k
    JOIN information_schema.REFERENTIAL_CONSTRAINTS rc
      ON rc.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA AND rc.CONSTRAINT_NAME = k.CONSTRAINT_NAME
    WHERE k.TABLE_SCHEMA = v_db AND k.TABLE_NAME = 'detect_results' AND k.COLUMN_NAME = 'user_id' AND rc.REFERENCED_TABLE_NAME = 'users'
  ) THEN
    -- Find the FK name
    SELECT rc.CONSTRAINT_NAME INTO @dr_fk_name
    FROM information_schema.KEY_COLUMN_USAGE k
    JOIN information_schema.REFERENTIAL_CONSTRAINTS rc
      ON rc.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA AND rc.CONSTRAINT_NAME = k.CONSTRAINT_NAME
    WHERE k.TABLE_SCHEMA = v_db AND k.TABLE_NAME = 'detect_results' AND k.COLUMN_NAME = 'user_id' AND rc.REFERENCED_TABLE_NAME = 'users'
    LIMIT 1;

    -- Drop and recreate with CASCADE if needed
    SELECT rc.DELETE_RULE INTO @dr_delete_rule
    FROM information_schema.REFERENTIAL_CONSTRAINTS rc
    WHERE rc.CONSTRAINT_SCHEMA = v_db AND rc.CONSTRAINT_NAME = @dr_fk_name;

    IF @dr_delete_rule <> 'CASCADE' THEN
      SET @sql := CONCAT('ALTER TABLE `detect_results` DROP FOREIGN KEY `', @dr_fk_name, '`');
      PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

      SET @sql := CONCAT(
        'ALTER TABLE `detect_results` ADD CONSTRAINT `', @dr_fk_name,
        '` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE RESTRICT'
      );
      PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
  END IF;

  -- 3) Ensure user_friendships FKs use ON DELETE CASCADE
  -- Drop and recreate both FKs referencing users
  -- Drop all FKs referencing users for this table
  SET @drop_fk_sql := (
    SELECT GROUP_CONCAT(CONCAT('ALTER TABLE `user_friendships` DROP FOREIGN KEY `', rc.CONSTRAINT_NAME, '`') SEPARATOR '; ')
    FROM information_schema.REFERENTIAL_CONSTRAINTS rc
    WHERE rc.CONSTRAINT_SCHEMA = v_db AND rc.TABLE_NAME = 'user_friendships' AND rc.REFERENCED_TABLE_NAME = 'users'
  );
  IF @drop_fk_sql IS NOT NULL AND LENGTH(@drop_fk_sql) > 0 THEN
    SET @sql := @drop_fk_sql; PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
  -- Recreate with CASCADE for (user_id) and (friend_id) if columns exist
  IF EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = v_db AND TABLE_NAME = 'user_friendships' AND COLUMN_NAME = 'user_id') THEN
    SET @sql := 'ALTER TABLE `user_friendships` ADD CONSTRAINT `fk_user_friendships_user` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE RESTRICT';
    PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = v_db AND TABLE_NAME = 'user_friendships' AND COLUMN_NAME = 'friend_id') THEN
    SET @sql := 'ALTER TABLE `user_friendships` ADD CONSTRAINT `fk_user_friendships_friend` FOREIGN KEY (`friend_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE RESTRICT';
    PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;

  -- 4) Rewire any FKs referencing legacy `user` table to `users`
  OPEN cur;
  read_loop: LOOP
    FETCH cur INTO v_constraint_name, v_table_name, v_delete_rule, v_update_rule;
    IF done = 1 THEN LEAVE read_loop; END IF;

    -- Child/ref column lists
    SELECT GROUP_CONCAT(k.COLUMN_NAME ORDER BY k.ORDINAL_POSITION SEPARATOR '`, `') INTO @child_cols
    FROM information_schema.KEY_COLUMN_USAGE k
    WHERE k.CONSTRAINT_SCHEMA = v_db AND k.CONSTRAINT_NAME = v_constraint_name AND k.TABLE_NAME = v_table_name;

    SELECT GROUP_CONCAT(k.REFERENCED_COLUMN_NAME ORDER BY k.ORDINAL_POSITION SEPARATOR '`, `') INTO @ref_cols
    FROM information_schema.KEY_COLUMN_USAGE k
    WHERE k.CONSTRAINT_SCHEMA = v_db AND k.CONSTRAINT_NAME = v_constraint_name AND k.TABLE_NAME = v_table_name;

    -- Drop old FK
    SET @sql := CONCAT('ALTER TABLE `', v_table_name, '` DROP FOREIGN KEY `', v_constraint_name, '`');
    PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

    -- Add new FK referencing `users` with same delete/update rules
    SET @sql := CONCAT(
      'ALTER TABLE `', v_table_name, '` ADD CONSTRAINT `', v_constraint_name,
      '` FOREIGN KEY (`', @child_cols, '`) REFERENCES `users`(`', @ref_cols, '`)',
      ' ON DELETE ', v_delete_rule, ' ON UPDATE ', v_update_rule
    );
    PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END LOOP;
  CLOSE cur;

  -- 5) If legacy `user` table exists: backup and drop
  SELECT COUNT(*) INTO v_exists_user
  FROM information_schema.TABLES t
  WHERE t.TABLE_SCHEMA = v_db AND t.TABLE_NAME = 'user';

  IF v_exists_user > 0 THEN
    -- Backup
    SET @sql := 'CREATE TABLE IF NOT EXISTS `users_legacy_backup` AS SELECT * FROM `user`';
    PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

    -- Drop
    SET @sql := 'DROP TABLE `user`';
    PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;

  -- Restore FK checks
  SET FOREIGN_KEY_CHECKS = @old_fk_checks;
END
;;
delimiter ;

SET FOREIGN_KEY_CHECKS = 1;
