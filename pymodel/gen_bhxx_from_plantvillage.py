import os

import pymysql


DB_CONFIG = {
    "host": "localhost",
    "port": 3306,
    "user": "root",
    "password": "123456",
    "database": "bysj",
    "charset": "utf8mb4",
}


PLANTVILLAGE_DIR = r"E:\Users\LENOVO\Desktop\mtjbysj\pymodel\data\PlantVillage"


PLANT_NAME_MAP = {
    "Apple": "苹果",
    "Blueberry": "蓝莓",
    "Cherry": "樱桃",
    "Corn": "玉米",
    "Grape": "葡萄",
    "Orange": "柑橘",
    "Peach": "桃",
    "Pepper,_bell": "甜椒",
    "Potato": "马铃薯",
    "Raspberry": "树莓",
    "Soybean": "大豆",
    "Squash": "南瓜",
    "Strawberry": "草莓",
    "Tomato": "番茄",
}


YUNNAN_REGION_TEXT = (
    "云南省昆明市、曲靖市、玉溪市、保山市、昭通市、丽江市、普洱市、临沧市、"
    "楚雄彝族自治州、红河哈尼族彝族自治州、文山壮族苗族自治州、西双版纳傣族自治州、"
    "大理白族自治州、德宏傣族景颇族自治州、怒江傈僳族自治州、迪庆藏族自治州等地。"
)


YUNNAN_MONTH_RANGE = "3-10月（云南主要作物生长及病害易发期）"


DISEASE_INFO = {
    "Apple___Apple_scab": {
        "disease_name": "苹果黑星病",
        "distribution_area": "在世界多数温带苹果产区普遍发生，我国北方和高海拔苹果产区较为常见。",
        "distribution_time": "多在春季到初夏温凉多雨时，嫩叶和幼果期发病最重。",
        "prevention_method": "选用抗病品种，增施有机肥改善通风透光；清除落叶和病残体集中处理；萌芽前和生长期根据病情喷施保护性或内吸性杀菌剂。",
    },
    "Apple___Black_rot": {
        "disease_name": "苹果黑腐病",
        "distribution_area": "在多数苹果栽培区均有发生，温暖多雨地区较为严重。",
        "distribution_time": "一般在花后至果实成熟期逐渐加重，雨水多的年份易流行。",
        "prevention_method": "修剪并烧毁病枝、病果和枯枝；雨季前后喷施对黑腐病有效的杀菌剂；注意园内排水和通风。",
    },
    "Apple___Cedar_apple_rust": {
        "disease_name": "苹果赤锈病",
        "distribution_area": "主要发生在同时种植苹果和某些针叶树的地区，在北美、欧洲以及我国部分苹果产区有分布。",
        "distribution_time": "春季到初夏多雨时期，幼叶、嫩梢和幼果易感病。",
        "prevention_method": "避免在苹果园附近种植病源针叶树或清除病瘤；萌芽至展叶期喷施保护性杀菌剂；加强修剪和清园。",
    },
    "Cherry___Powdery_mildew": {
        "disease_name": "樱桃白粉病",
        "distribution_area": "在樱桃栽培区普遍出现，温暖干燥、昼夜温差大的地区易发生。",
        "distribution_time": "从春季展叶后开始发病，初夏到夏季高温干燥期为发病高峰。",
        "prevention_method": "合理修剪、改善通风透光；发病初期及时喷施对白粉病有效的杀菌剂；注意清除病叶病梢。",
    },
    "Corn___Cercospora_leaf_spot Gray_leaf_spot": {
        "disease_name": "玉米灰斑病",
        "distribution_area": "在世界许多玉米主产区发生，湿热气候和高密度种植地区尤为严重。",
        "distribution_time": "多在玉米中后期拔节到灌浆阶段，遇持续高温高湿时病斑迅速扩展。",
        "prevention_method": "选择抗病品种，合理密植和轮作，减少玉米残株留在田间；在病害流行前或发病初期喷施系统性杀菌剂。",
    },
    "Corn___Common_rust": {
        "disease_name": "玉米普通锈病",
        "distribution_area": "在玉米栽培区普遍分布，凉爽湿润的地区发生较重。",
        "distribution_time": "通常在玉米拔节到抽雄期开始出现病斑，凉爽多雨年份易流行。",
        "prevention_method": "选用抗锈病品种；适当早播避开发病高峰；发病初期及时喷施对锈病有效的杀菌剂。",
    },
    "Corn___Northern_Leaf_Blight": {
        "disease_name": "玉米大斑病",
        "distribution_area": "世界各地玉米产区普遍发生，我国东北、华北和西南部分地区多见。",
        "distribution_time": "多在玉米拔节后到灌浆期发生，阴雨连绵和低温高湿有利于流行。",
        "prevention_method": "推广抗病品种，实行与豆类或其他作物轮作，清除病残体；在发病初期喷施针对大斑病的杀菌剂。",
    },
    "Grape___Black_rot": {
        "disease_name": "葡萄黑腐病",
        "distribution_area": "在湿热或多雨的葡萄产区普遍发生。",
        "distribution_time": "从展叶期开始到果实成熟前均可发病，梅雨季节是发病高峰。",
        "prevention_method": "清除并深埋病果和病枝；合理修剪和整形，保持通风透光；生长期根据病情喷施保护性或内吸性杀菌剂。",
    },
    "Grape___Esca_(Black_Measles)": {
        "disease_name": "葡萄虎眼病（Esca病）",
        "distribution_area": "主要发生在温暖地区的中老龄葡萄园，在欧洲、地中海沿岸及部分葡萄主产区较为常见。",
        "distribution_time": "多在夏季高温期表现叶片虎斑症状，严重时整株衰退甚至死亡。",
        "prevention_method": "避免大伤口修剪并及时保护剪口；清除病株和严重病枝并销毁；加强栽培管理，降低植株衰弱程度。",
    },
    "Grape___Leaf_blight_(Isariopsis_Leaf_Spot)": {
        "disease_name": "葡萄叶枯病（伊萨氏叶斑病）",
        "distribution_area": "在温暖多雨的葡萄产区发生较多。",
        "distribution_time": "多在生长季中后期叶片郁闭、湿度大时发病严重。",
        "prevention_method": "合理密植与修剪，改善冠层通风；清除病叶；生长期适时喷施广谱性杀菌剂。",
    },
    "Orange___Haunglongbing_(Citrus_greening)": {
        "disease_name": "柑橘黄龙病",
        "distribution_area": "在世界许多柑橘产区均有发生，是柑橘上极具破坏性的病害，在南方柑橘区为重点防控对象。",
        "distribution_time": "全年均可见症状，通常在新梢抽发期和果实膨大期症状最明显。",
        "prevention_method": "严格检疫，使用无病苗木；综合防治传播媒介木虱，控制虫口密度；发现疑似病株及时挖除并无害化处理。",
    },
    "Peach___Bacterial_spot": {
        "disease_name": "桃细菌性斑点病（穿孔病）",
        "distribution_area": "在温暖多雨的桃产区发生较重，苗圃和幼树园易发。",
        "distribution_time": "多在春夏季多雨、湿度大时流行，叶片和果实生长期极易感病。",
        "prevention_method": "选择较抗病品种；采前后喷施含铜制剂或其他细菌性药剂；注意修剪和清园，减少带菌病残体。",
    },
    "Pepper,_bell___Bacterial_spot": {
        "disease_name": "甜椒细菌性斑点病",
        "distribution_area": "在设施栽培和露地栽培的甜椒、辣椒产区均可发生，高温多雨或频繁灌水条件下尤为严重。",
        "distribution_time": "幼苗期到结果期均可发病，雨水多或喷灌频繁时病斑迅速扩展。",
        "prevention_method": "选用健壮无病种苗；避免带菌种子并进行种子消毒；改善通风排水，控制氮肥用量；发病初期喷施含铜制剂等细菌性药剂。",
    },
    "Potato___Early_blight": {
        "disease_name": "马铃薯早疫病",
        "distribution_area": "世界各马铃薯主产区均有发生，干旱或半干旱地区叶片病斑较多。",
        "distribution_time": "多在马铃薯中后期、植株衰老时发生严重。",
        "prevention_method": "合理施肥，防止偏施氮肥和缺钾；清除病残体；在发病初期喷施保护性或内吸性杀菌剂。",
    },
    "Potato___Late_blight": {
        "disease_name": "马铃薯晚疫病",
        "distribution_area": "在凉爽多雨地区极易暴发，是马铃薯生产上的重要病害之一。",
        "distribution_time": "一般在生长后期气温较低、湿度大时迅速流行。",
        "prevention_method": "选用抗病品种，实行高垄栽培和合理密植；雨季前和发病初期及时喷施针对晚疫病的杀菌剂；收获后处理残株和病薯。",
    },
    "Squash___Powdery_mildew": {
        "disease_name": "南瓜白粉病",
        "distribution_area": "在葫芦科蔬菜产区普遍存在，温暖干燥、昼夜温差大的环境有利于发病。",
        "distribution_time": "多在生长后期叶片老化时发生严重。",
        "prevention_method": "合理密植和整枝打叶，加强通风；发病初期喷施对白粉病有效的药剂；加施有机肥提高植株抗性。",
    },
    "Strawberry___Leaf_scorch": {
        "disease_name": "草莓叶枯病（叶灼病）",
        "distribution_area": "在多数草莓栽培区发生，潮湿、通风不良的种植环境发病重。",
        "distribution_time": "多在春末夏初及秋季温和多雨时出现。",
        "prevention_method": "选用健壮苗，合理密植，铺设地膜减少雨水飞溅；清除病叶；发病初期喷施广谱性杀菌剂。",
    },
    "Tomato___Bacterial_spot": {
        "disease_name": "番茄细菌性斑点病",
        "distribution_area": "在设施和露地番茄产区均可见，温暖多雨或频繁淋水的条件下易暴发。",
        "distribution_time": "从苗期到结果期均可发病，雨季或高湿环境下病斑快速扩展。",
        "prevention_method": "选择健康种子并进行种子消毒；避免大水漫灌，减少叶面长期积水；发病初期喷施含铜制剂等细菌性药剂。",
    },
    "Tomato___Early_blight": {
        "disease_name": "番茄早疫病",
        "distribution_area": "在番茄主产区普遍存在，气候温暖且湿度较高的地区发病较重。",
        "distribution_time": "多在中后期叶片老化时严重发生，雨水频繁年份危害加重。",
        "prevention_method": "实施轮作，清除病残体；合理施肥提高抗性；在发病初期及时喷施针对早疫病的杀菌剂。",
    },
    "Tomato___Late_blight": {
        "disease_name": "番茄晚疫病",
        "distribution_area": "在凉爽多雨的番茄产区极易爆发流行。",
        "distribution_time": "多在结果期至成熟期阴雨连绵、高湿低温时发生。",
        "prevention_method": "选用抗病品种，避免低洼积水地种植；加强通风排湿；在流行前和发病初期喷施晚疫病专用杀菌剂。",
    },
    "Tomato___Leaf_Mold": {
        "disease_name": "番茄叶霉病",
        "distribution_area": "主要危害设施栽培番茄，高湿、通风不良的温室和大棚中发生严重。",
        "distribution_time": "多在春末夏初和秋季温度适宜、空气湿度长期偏高时流行。",
        "prevention_method": "合理密植和整枝，控制棚内湿度，及时通风；发病初期喷施内吸性杀菌剂；清除病叶并带出棚外处理。",
    },
    "Tomato___Septoria_leaf_spot": {
        "disease_name": "番茄尾孢叶斑病（Septoria叶斑病）",
        "distribution_area": "在多数番茄栽培区均有发生，阴雨连绵和露水重的环境利于病害发展。",
        "distribution_time": "通常在番茄生长中后期叶片郁闭、湿度大时发病。",
        "prevention_method": "合理密植与轮作，注意清除病残体；在病害初期喷施广谱性杀菌剂。",
    },
    "Tomato___Spider_mites Two-spotted_spider_mite": {
        "disease_name": "二斑叶螨为害番茄",
        "distribution_area": "在温室和露地蔬菜区普遍发生，干燥炎热环境下螨害严重。",
        "distribution_time": "多在高温干燥季节螨类快速繁殖时期造成明显危害。",
        "prevention_method": "加强水肥管理，保持适度空气湿度；及时清除杂草，减少虫源；在螨量较低时采用生物或化学杀螨剂防治。",
    },
    "Tomato___Target_Spot": {
        "disease_name": "番茄靶斑病",
        "distribution_area": "在热带和亚热带番茄产区较为常见，湿热环境利于流行。",
        "distribution_time": "多在番茄生长中后期、叶片郁闭期发病。",
        "prevention_method": "避免连作，清除病残体；改善通风透光条件；发病初期喷施针对靶斑病的杀菌剂。",
    },
    "Tomato___Tomato_Yellow_Leaf_Curl_Virus": {
        "disease_name": "番茄黄化曲叶病毒病",
        "distribution_area": "在世界多地番茄产区广泛发生，设施栽培区为重要病毒病之一。",
        "distribution_time": "从苗期到结果期均可表现症状，高温季节白粉虱密度高时易流行。",
        "prevention_method": "选用抗病或耐病品种；加强对白粉虱等传播媒介的综合防治；使用防虫网、银灰地膜等物理措施；尽量采用无毒苗移栽。",
    },
    "Tomato___Tomato_mosaic_virus": {
        "disease_name": "番茄花叶病毒病",
        "distribution_area": "在各主要番茄产区均有发生，可通过种子、苗木和机械接触传播。",
        "distribution_time": "生育期均可发病，多在温度适宜、管理粗放的条件下加重。",
        "prevention_method": "使用无病种子和健康苗木；加强温室卫生，减少机械操作传播；拔除严重病株集中处理；合理轮作，避免连作重茬。",
    },
}


YUNNAN_DISEASE_DIST = {
    "Apple___Apple_scab": {
        "area": "主要发生在云南省苹果种植较集中的冷凉高海拔地区，如昭通市、丽江市、迪庆州等地。",
        "time": "3-6月（苹果展叶至幼果期，多雨潮湿时发病重）",
    },
    "Apple___Black_rot": {
        "area": "云南省苹果园分布区，尤以昭通市、丽江市等老果园较多地区发病偏重。",
        "time": "5-9月（雨水较多、气温偏高阶段病斑和果腐发生明显）",
    },
    "Apple___Cedar_apple_rust": {
        "area": "在云南省苹果与针叶树混栽或相邻分布的果园区域，如昭通市、丽江市部分山区果园。",
        "time": "3-5月（苹果展叶至幼果期，遇连续降雨时易发病）",
    },
    "Cherry___Powdery_mildew": {
        "area": "云南省樱桃栽培区，如昆明市周边、曲靖市、昭通市等地的樱桃园。",
        "time": "3-6月（樱桃展叶至结果期，晴暖干燥、昼夜温差大时易流行）",
    },
    "Corn___Cercospora_leaf_spot Gray_leaf_spot": {
        "area": "云南省玉米主产区的中低海拔地区，如曲靖市、昆明市、红河州、文山州等地的玉米田。",
        "time": "6-9月（玉米拔节以后至灌浆期，高温高湿或降雨多时易暴发）",
    },
    "Corn___Common_rust": {
        "area": "在云南省各玉米产区普遍发生，以气候偏凉爽、湿度大的高原玉米区较为多见，如昭通市、曲靖市等地。",
        "time": "6-9月（玉米拔节至抽雄期为主要发病阶段）",
    },
    "Corn___Northern_Leaf_Blight": {
        "area": "云南省中高海拔玉米种植区，如昭通市、曲靖市、昆明市部分山区等地。",
        "time": "7-9月（玉米大喇叭口期至灌浆期，阴雨连绵时病斑扩展快）",
    },
    "Grape___Black_rot": {
        "area": "云南省葡萄主产区，如昆明市、玉溪市、红河州、大理州等地的葡萄园。",
        "time": "4-7月（展叶后至坐果、果实膨大期，连续降雨时易流行）",
    },
    "Grape___Esca_(Black_Measles)": {
        "area": "云南省气候较温暖、葡萄栽培历史较长的老龄葡萄园，如红河州、玉溪市、大理州部分地区。",
        "time": "6-9月（夏季高温期叶片症状明显，植株衰退加重）",
    },
    "Grape___Leaf_blight_(Isariopsis_Leaf_Spot)": {
        "area": "云南省葡萄栽培偏湿润地区，如红河州、玉溪市、大理州部分山地果园。",
        "time": "6-9月（雨水多、叶片郁闭、湿度大的时期易发病）",
    },
    "Orange___Haunglongbing_(Citrus_greening)": {
        "area": "主要分布在云南省南部和东南部柑橘主产区，如红河州、文山州、玉溪市、普洱市、西双版纳州等地。",
        "time": "全年均可见症状，以3-12月新梢生长及结果期为明显。",
    },
    "Peach___Bacterial_spot": {
        "area": "云南省桃树种植区，如昆明市、玉溪市、曲靖市、大理州等地的桃园和苗圃。",
        "time": "3-6月（桃树发芽展叶至幼果期，多雨高湿年份病斑多）",
    },
    "Pepper,_bell___Bacterial_spot": {
        "area": "云南省设施和露地甜椒、辣椒产区，如昆明市、曲靖市、玉溪市、红河州、文山州等地。",
        "time": "4-9月（辣椒生长期，多雨或喷灌频繁时叶片和果实易感病）",
    },
    "Potato___Early_blight": {
        "area": "云南省冷凉山区马铃薯主产区，如昭通市、曲靖市、昆明市、大理州、丽江市等地。",
        "time": "4-7月（马铃薯现蕾到中后期，植株逐渐衰老时发病增多）",
    },
    "Potato___Late_blight": {
        "area": "云南省高海拔、多雾多雨的马铃薯种植区，如昭通市、曲靖市、大理州、丽江市等地。",
        "time": "6-8月（持续低温高湿或降雨频繁时易暴发流行）",
    },
    "Squash___Powdery_mildew": {
        "area": "云南省葫芦科蔬菜集中种植区，如昆明市、玉溪市、曲靖市、红河州等地。",
        "time": "5-9月（南瓜生长中后期、天气干燥少雨且昼夜温差大时发病较重）",
    },
    "Strawberry___Leaf_scorch": {
        "area": "云南省草莓设施栽培区和冷凉高原种植区，如昆明市周边、曲靖市、大理州等地。",
        "time": "2-5月及9-11月（春季和秋季气温适宜且湿度偏高时易发生）",
    },
    "Tomato___Bacterial_spot": {
        "area": "云南省番茄设施和露地栽培区，如昆明市、玉溪市、曲靖市、红河州、文山州等地。",
        "time": "3-7月（番茄生长期，雨水多或叶面长时间潮湿时发病明显）",
    },
    "Tomato___Early_blight": {
        "area": "在云南省番茄主产区普遍发生，如昆明市、玉溪市、曲靖市、红河州等地。",
        "time": "4-7月（番茄中后期叶片老化、气温较高时病害加重）",
    },
    "Tomato___Late_blight": {
        "area": "云南省凉爽多雨或高海拔番茄种植区，如昆明市部分高海拔乡镇、曲靖市、大理州等地。",
        "time": "6-10月（阴雨连绵、空气湿度大的时期易暴发流行）",
    },
    "Tomato___Leaf_Mold": {
        "area": "主要危害云南省设施番茄种植区，如昆明市郊区、玉溪市、红河州等大棚种植区域。",
        "time": "3-6月及9-11月（棚内湿度长期偏高、通风不良时多见）",
    },
    "Tomato___Septoria_leaf_spot": {
        "area": "云南省番茄栽培区中叶片郁闭、湿度较大的地块，如昆明市、玉溪市、曲靖市等地蔬菜基地。",
        "time": "4-7月（番茄生长中后期、雨水较多年份易发生）",
    },
    "Tomato___Spider_mites Two-spotted_spider_mite": {
        "area": "云南省番茄及蔬菜主产区的设施栽培地，如昆明市、玉溪市、曲靖市等地。",
        "time": "5-9月（高温干燥季节螨类快速繁殖期）",
    },
    "Tomato___Target_Spot": {
        "area": "云南省南部及中部温暖湿润番茄种植区，如红河州、文山州、玉溪市、普洱市等地。",
        "time": "5-9月（叶片郁闭、降雨频繁时病斑扩展快）",
    },
    "Tomato___Tomato_Yellow_Leaf_Curl_Virus": {
        "area": "云南省中低海拔番茄主产区，特别是白粉虱危害较重的地区，如红河州、文山州、玉溪市、西双版纳州等地。",
        "time": "3-7月（番茄生长前中期，温度较高且白粉虱密度大时易流行）",
    },
    "Tomato___Tomato_mosaic_virus": {
        "area": "云南省各番茄产区的育苗和生产地，如昆明市、玉溪市、曲靖市、红河州等地。",
        "time": "2-10月（番茄育苗至结果期均可见，以温度适宜、管理粗放时较多）",
    },
}


def ensure_bhxx_table(cursor) -> None:
    create_sql = """
    CREATE TABLE IF NOT EXISTS `bhxx` (
      `id` bigint NOT NULL AUTO_INCREMENT,
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
    """
    cursor.execute(create_sql)


def clear_bhxx(cursor) -> None:
    cursor.execute("DELETE FROM `bhxx`")


def _category_type(dirname: str) -> str:
    if dirname == "Background_without_leaves":
        return "background"
    if "___healthy" in dirname:
        return "healthy"
    if "___" in dirname:
        return "disease"
    return "other"


def _normalize_plant_name(raw: str) -> str:
    if raw in PLANT_NAME_MAP:
        return PLANT_NAME_MAP[raw]
    return raw.replace("_", " ").replace(",", "，")


def build_records():
    records = []
    if not os.path.isdir(PLANTVILLAGE_DIR):
        print(f"目录不存在: {PLANTVILLAGE_DIR}")
        return records

    for name in os.listdir(PLANTVILLAGE_DIR):
        path = os.path.join(PLANTVILLAGE_DIR, name)
        if not os.path.isdir(path):
            continue

        ctype = _category_type(name)

        if ctype == "background":
            records.append(
                {
                    "plant_name": "无植物",
                    "disease_name": "背景无叶片",
                    "distribution_area": "无",
                    "distribution_time": "无",
                    "prevention_method": "无",
                }
            )
            continue

        if ctype == "healthy":
            plant_raw = name.split("___", 1)[0]
            plant_name = _normalize_plant_name(plant_raw)
            records.append(
                {
                    "plant_name": plant_name,
                    "disease_name": "无",
                    "distribution_area": "无",
                    "distribution_time": "无",
                    "prevention_method": "无",
                }
            )
            continue

        if ctype == "disease":
            plant_raw, disease_raw = name.split("___", 1)
            plant_name = _normalize_plant_name(plant_raw)
            info = DISEASE_INFO.get(name)
            yn = YUNNAN_DISEASE_DIST.get(name)

            if info is None:
                disease_name = disease_raw.replace("_", " ")
                if yn is not None:
                    distribution_area = yn["area"]
                    distribution_time = yn["time"]
                else:
                    distribution_area = YUNNAN_REGION_TEXT
                    distribution_time = YUNNAN_MONTH_RANGE
                control_text = (
                    f"病害发生前（防）：加强田间清洁，合理轮作与施肥，选择健康种苗，减少有利于{disease_name} 发生的条件。"
                    f" 病害发生后（治）：发病后应及时向当地植保部门或农业技术人员咨询，根据病情选择对{disease_name} 有效的药剂进行防治，并注意轮换用药和安全间隔期。"
                )
                records.append(
                    {
                        "plant_name": plant_name,
                        "disease_name": disease_name,
                        "distribution_area": distribution_area,
                        "distribution_time": distribution_time,
                        "prevention_method": control_text,
                    }
                )
            else:
                disease_name = info.get("disease_name") or disease_raw.replace("_", " ")
                if yn is not None:
                    distribution_area = yn["area"]
                    distribution_time = yn["time"]
                else:
                    distribution_area = YUNNAN_REGION_TEXT
                    distribution_time = YUNNAN_MONTH_RANGE
                base = info.get("prevention_method") or "无"
                control_text = (
                    f"病害发生前（防）：{base} "
                    f"病害发生后（治）：发病后应根据田间实际病情，结合当地植保部门或农业技术人员建议，选择对{disease_name} 有效的药剂进行防治，并注意轮换用药和安全间隔期。"
                )
                records.append(
                    {
                        "plant_name": plant_name,
                        "disease_name": disease_name,
                        "distribution_area": distribution_area,
                        "distribution_time": distribution_time,
                        "prevention_method": control_text,
                    }
                )

    return records


def insert_records(cursor, records) -> None:
    if not records:
        return

    sql = """
    INSERT INTO `bhxx` (`植物名称`, `病害名称`, `分布区域`, `分布时间`, `防治方法`)
    VALUES (%s, %s, %s, %s, %s)
    """

    for rec in records:
        cursor.execute(
            sql,
            (
                rec["plant_name"],
                rec["disease_name"],
                rec["distribution_area"],
                rec["distribution_time"],
                rec["prevention_method"],
            ),
        )


def main() -> None:
    records = build_records()
    print(f"从 PlantVillage 目录生成记录数: {len(records)}")

    connection = pymysql.connect(**DB_CONFIG)
    try:
        cursor = connection.cursor()
        ensure_bhxx_table(cursor)
        clear_bhxx(cursor)
        insert_records(cursor, records)
        connection.commit()
        print("已清空并重新填充 bhxx 表")
    finally:
        try:
            cursor.close()
        except Exception:
            pass
        connection.close()


if __name__ == "__main__":
    main()

