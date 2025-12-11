import os
import torch

# 数据集路径（与本文件位置无关，稳健）
DATA_ROOT = os.path.join(os.path.dirname(__file__), "data", "PlantVillage")  # PlantVillage根目录（包含类别子文件夹）
SPLITS_DIR = os.path.join(os.path.dirname(__file__), "data", "splits")       # 数据集划分与类别名保存路径（自动创建）

# 可选：排除不参与训练的类别名
# 建议排除Background_without_leaves，因为它会干扰植物病害分类
EXCLUDE_CLASSES = ["Background_without_leaves"]  # 🔥 排除无叶片背景类，专注于植物病害识别

# 训练参数
BATCH_SIZE = 16                 # 优化：增大批次减少数据加载次数，提升GPU利用率
IMAGE_SIZE = 224                 # MobileNetV2 默认输入尺寸
EPOCHS = 30                      # 增加训练轮次，配合早停机制
LEARNING_RATE = 1e-4
WEIGHT_DECAY = 1e-5
NUM_WORKERS = 0                  # 修复Windows共享内存错误(error 1455)，必须设为0

# 设备配置
DEVICE = torch.device("cuda" if torch.cuda.is_available() else "cpu")

# 模型保存路径（固定到当前模块所在目录，避免工作目录差异）
# 兼容旧单模型路径，同时新增物种/病害双模型路径
MODEL_SAVE_PATH = os.path.join(os.path.dirname(__file__), "saved_models", "best_model.pth")  # 旧路径（向后兼容）
SPECIES_MODEL_SAVE_PATH = os.path.join(os.path.dirname(__file__), "saved_models", "best_species_model.pth")
DISEASE_MODEL_SAVE_PATH = os.path.join(os.path.dirname(__file__), "saved_models", "best_disease_model.pth")
os.makedirs(os.path.join(os.path.dirname(__file__), "saved_models"), exist_ok=True)

# 叶片存在性模型配置（用于两阶段推理）
LEAF_PRESENCE_MODEL_PATH = os.path.join(os.path.dirname(__file__), "saved_models", "leaf_presence.pth")
LEAF_PRESENCE_THRESHOLD = 0.90  # 置信度阈值，≥此值判定为“无叶片背景”（校准推荐）
# 更稳健的双阈值策略：显著无叶片阈值 + 最低叶片置信
LEAF_PRESENCE_NO_LEAF_THRESHOLD = 0.90  # ≥此值强判为“无叶片背景”（校准推荐）
LEAF_PRESENCE_LEAF_MIN = 0.20          # 叶片最低置信（低于此值才判为未知）

# 训练优化配置
# 冻结策略选择：
# - "none": 全部微调（所有层可训练，最慢但效果最好）
# - "partial": 部分冻结（推荐，冻结早期层，解冻后期层）
# - "bn_only": 只冻结BatchNorm层（适合小数据集）
# - "all": 完全冻结（只训练分类头，最快但效果可能不佳）
FREEZE_STRATEGY = "partial"      # 🔥 使用部分冻结策略
UNFREEZE_LAST_BLOCKS = 10        # 🔥 解冻后10个块（约50%参数），增强特征学习能力
CLASSIFIER_DROPOUT = 0.3         # 分类头Dropout率（防止过拟合）

EARLY_STOP_PATIENCE = 7          # 优化：增加早停耐心值，避免过早终止训练
LABEL_SMOOTHING = 0.1            # 标签平滑以提升泛化
USE_AMP = True                   # GPU上启用混合精度训练以加速
HEAD_LR_MULTIPLIER = 3.0         # 分类头学习率相对基础LR的倍率
FEATURE_LR_MULTIPLIER = 0.5      # 解冻特征层学习率倍率（较小更稳）
USE_BALANCED_SAMPLER = True      # 训练集使用类别均衡采样以提升小类精度
TRAIN_MAX_PER_CLASS = 0          # 🔥 修复：使用全部训练数据（设为0表示不限制）
LR_SCHEDULER_PATIENCE = 3        # 优化：学习率调度器耐心值，应小于早停耐心值

# 数据质量过滤（删除裁切/拼接/异常尺寸图片）
FILTER_BAD_IMAGES = True
PERSISTENT_WORKERS = False       # Windows上不支持，设为False
BAD_IMAGE_PATTERNS = [
    "crop", "cropped", "cut", "patch", "tile", "mosaic", "mixup",
    "stitch", "concat", "裁剪", "裁切", "拼接"
]
MIN_IMAGE_SIZE = 80              # 最小边长度阈值（像素），过小视为裁切
MAX_ASPECT_RATIO = 2.5           # 最大长宽比（>2.5视为异常裁切/拼接）

# 数据增强配置（增强鲁棒性）
# 可选值: 'light', 'medium', 'heavy'
# - light: 基础增强（翻转、轻微旋转、轻微颜色变化）
# - medium: 中等增强（默认，平衡性能和鲁棒性）
# - heavy: 强力增强（最大鲁棒性，但可能导致过度增强）
AUGMENTATION_LEVEL = 'medium'     # 🔥 使用中等增强，避免过度增强影响准确性

# 类别名称（运行时在 data_loader 中动态填充/或从 JSON 加载）
CLASS_NAMES = None                 # 旧字段（向后兼容单模型）
SPECIES_CLASS_NAMES = None         # 物种类别名列表
DISEASE_CLASS_NAMES = None         # 病害类别名列表

# 类名文件
SPECIES_CLASS_NAMES_JSON = os.path.join(SPLITS_DIR, "species_class_names.json")
DISEASE_CLASS_NAMES_JSON = os.path.join(SPLITS_DIR, "disease_class_names.json")
