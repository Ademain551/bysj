"""
可视化工具模块
优化：字体兼容性、混淆矩阵显示优化
"""
import os
import matplotlib.pyplot as plt
import seaborn as sns
from sklearn.metrics import confusion_matrix
import numpy as np
import config
from utils.logger import setup_simple_logger

# 初始化日志
logger = setup_simple_logger("Visualize")

# 优化：跨平台中文字体设置
def setup_chinese_font():
    """设置中文字体，支持多平台"""
    import platform
    system = platform.system()
    
    try:
        if system == "Windows":
            plt.rcParams['font.sans-serif'] = ['SimHei', 'Microsoft YaHei']
        elif system == "Darwin":  # macOS
            plt.rcParams['font.sans-serif'] = ['Arial Unicode MS', 'Heiti TC']
        else:  # Linux
            plt.rcParams['font.sans-serif'] = ['WenQuanYi Micro Hei', 'Noto Sans CJK']
        
        plt.rcParams['axes.unicode_minus'] = False
        logger.info(f"已配置中文字体 ({system})")
    except Exception as e:
        logger.warning(f"中文字体配置失败: {e}，将使用默认字体")
        # 回退到英文
        plt.rcParams['font.sans-serif'] = ['DejaVu Sans']

# 初始化字体
setup_chinese_font()


def _to_chinese_labels(class_names):
    """将英文类别名转换为中文别名（尽量覆盖 PlantVillage 39 类）。"""
    crop_map = {
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
        "Background_without_leaves": "背景（无叶片）",
    }
    disease_map = {
        "Apple_scab": "黑星病",
        "Black_rot": "黑腐病",
        "Cedar_apple_rust": "雪松锈病",
        "healthy": "健康",
        "Powdery_mildew": "白粉病",
        "Cercospora_leaf_spot Gray_leaf_spot": "灰斑病",
        "Common_rust": "普通锈病",
        "Northern_Leaf_Blight": "北方叶枯病",
        "Esca_(Black_Measles)": "黑死病（Esca）",
        "Leaf_blight_(Isariopsis_Leaf_Spot)": "叶枯病（Isariopsis）",
        "Haunglongbing_(Citrus_greening)": "黄龙病（绿化病）",
        "Bacterial_spot": "细菌性斑点病",
        "Early_blight": "早疫病",
        "Late_blight": "晚疫病",
        "Leaf_scorch": "叶灼病",
        "Leaf_Mold": "叶霉病",
        "Septoria_leaf_spot": "黑斑病（Septoria）",
        "Spider_mites Two-spotted_spider_mite": "二斑叶螨",
        "Target_Spot": "靶斑病",
        "Tomato_Yellow_Leaf_Curl_Virus": "黄化卷叶病毒",
        "Tomato_mosaic_virus": "花叶病毒",
    }

    zh_labels = []
    for name in class_names:
        # 背景类直接映射
        if name == "Background_without_leaves":
            zh_labels.append(crop_map[name])
            continue
        # 拆分 "作物___病害"
        parts = name.split("___")
        if len(parts) == 2:
            crop, disease = parts
            crop_zh = crop_map.get(crop, crop)
            disease_zh = disease_map.get(disease, disease.replace("_", " "))
            zh_labels.append(f"{crop_zh} {disease_zh}")
        else:
            # 兜底：替换下划线
            zh_labels.append(name.replace("_", " "))
    return zh_labels


def plot_training_curve(history, save_path=None):
    """绘制训练/验证损失和准确率曲线
    
    Args:
        history: 训练历史字典
        save_path: 保存路径，默认为saved_models/training_curve.png
    """
    try:
        if save_path is None:
            save_path = os.path.join(os.path.dirname(config.MODEL_SAVE_PATH), "training_curve.png")
        
        plt.figure(figsize=(14, 5))

        # 损失曲线
        plt.subplot(1, 2, 1)
        epochs = range(1, len(history["train_loss"]) + 1)
        plt.plot(epochs, history["train_loss"], 'b-o', label="训练损失", linewidth=2, markersize=4)
        plt.plot(epochs, history["val_loss"], 'r-s', label="验证损失", linewidth=2, markersize=4)
        plt.title("损失曲线", fontsize=14, fontweight='bold')
        plt.xlabel("轮次", fontsize=12)
        plt.ylabel("损失", fontsize=12)
        plt.legend(fontsize=10)
        plt.grid(True, alpha=0.3)

        # 准确率曲线
        plt.subplot(1, 2, 2)
        plt.plot(epochs, history["train_acc"], 'b-o', label="训练准确率", linewidth=2, markersize=4)
        plt.plot(epochs, history["val_acc"], 'r-s', label="验证准确率", linewidth=2, markersize=4)
        plt.title("准确率曲线", fontsize=14, fontweight='bold')
        plt.xlabel("轮次", fontsize=12)
        plt.ylabel("准确率", fontsize=12)
        plt.legend(fontsize=10)
        plt.grid(True, alpha=0.3)

        plt.tight_layout()
        plt.savefig(save_path, dpi=150, bbox_inches='tight')
        logger.info(f"训练曲线已保存至: {save_path}")
        plt.show()
        
    except Exception as e:
        logger.error(f"绘制训练曲线失败: {str(e)}")
        raise e


def plot_confusion_matrix(all_preds, all_labels, save_path=None, max_display_classes=20):
    """绘制混淆矩阵（中文标签）
    
    优化：当类别过多时，自动调整显示方式
    
    Args:
        all_preds: 预测标签数组
        all_labels: 真实标签数组
        save_path: 保存路径，默认为saved_models/confusion_matrix.png
        max_display_classes: 最大显示类别数，超过则不显示注释
    """
    try:
        if save_path is None:
            save_path = os.path.join(os.path.dirname(config.MODEL_SAVE_PATH), "confusion_matrix.png")
        
        cm = confusion_matrix(all_labels, all_preds)
        labels_zh = _to_chinese_labels(config.CLASS_NAMES)
        
        num_classes = len(config.CLASS_NAMES)
        logger.info(f"混淆矩阵类别数: {num_classes}")
        
        # 优化：根据类别数调整图像大小和显示方式
        if num_classes <= max_display_classes:
            # 类别较少，显示完整注释
            figsize = (max(12, num_classes * 0.6), max(10, num_classes * 0.5))
            plt.figure(figsize=figsize)
            
            # 字体大小随类别数调整
            font_scale = max(0.6, 1.2 - num_classes * 0.02)
            sns.set(font_scale=font_scale)
            
            ax = sns.heatmap(
                cm,
                annot=True,
                fmt="d",
                cmap="Blues",
                xticklabels=labels_zh,
                yticklabels=labels_zh,
                cbar_kws={'label': '样本数量'}
            )
            
            plt.xticks(rotation=45, ha='right', fontsize=max(8, 12 - num_classes * 0.1))
            plt.yticks(rotation=0, fontsize=max(8, 12 - num_classes * 0.1))
        else:
            # 类别过多，不显示注释，只显示热力图
            figsize = (16, 14)
            plt.figure(figsize=figsize)
            logger.warning(f"类别数({num_classes})超过{max_display_classes}，混淆矩阵将不显示数值注释")
            
            # 归一化显示（更清晰）
            cm_normalized = cm.astype('float') / cm.sum(axis=1)[:, np.newaxis]
            
            ax = sns.heatmap(
                cm_normalized,
                annot=False,  # 不显示数值
                cmap="Blues",
                xticklabels=labels_zh,
                yticklabels=labels_zh,
                cbar_kws={'label': '归一化比例'},
                vmin=0,
                vmax=1
            )
            
            plt.xticks(rotation=90, ha='right', fontsize=7)
            plt.yticks(rotation=0, fontsize=7)
        
        plt.title("混淆矩阵", fontsize=16, fontweight='bold', pad=20)
        plt.xlabel("预测标签", fontsize=13)
        plt.ylabel("真实标签", fontsize=13)
        
        plt.tight_layout()
        plt.savefig(save_path, dpi=150, bbox_inches='tight')
        logger.info(f"混淆矩阵已保存至: {save_path}")
        plt.show()
        
        # 重置seaborn设置
        sns.reset_defaults()
        
    except Exception as e:
        logger.error(f"绘制混淆矩阵失败: {str(e)}")
        raise e
