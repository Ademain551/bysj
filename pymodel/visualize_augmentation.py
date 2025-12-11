"""
可视化数据增强效果
展示原始图像和增强后的图像对比
"""
import os
import sys
import io
import torch
import matplotlib.pyplot as plt
from PIL import Image
import config
from utils.data_loader import get_transforms

# Windows编码修复
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

def visualize_augmentations(image_path, num_augmentations=8):
    """
    可视化数据增强效果
    
    Args:
        image_path: 图像路径
        num_augmentations: 显示的增强版本数量
    """
    # 加载原始图像
    original_image = Image.open(image_path).convert('RGB')
    
    # 获取训练集的transform
    train_transform, _ = get_transforms()
    
    # 创建图形
    fig, axes = plt.subplots(3, 3, figsize=(15, 15))
    fig.suptitle(f'数据增强效果展示 (级别: {config.AUGMENTATION_LEVEL})', 
                 fontsize=16, fontweight='bold')
    
    # 显示原始图像
    axes[0, 0].imshow(original_image)
    axes[0, 0].set_title('原始图像', fontsize=12, fontweight='bold')
    axes[0, 0].axis('off')
    
    # 显示增强后的图像
    for i in range(1, 9):
        row = i // 3
        col = i % 3
        
        # 应用增强
        augmented = train_transform(original_image)
        
        # 反标准化以便显示
        mean = torch.tensor([0.485, 0.456, 0.406]).view(3, 1, 1)
        std = torch.tensor([0.229, 0.224, 0.225]).view(3, 1, 1)
        augmented = augmented * std + mean
        augmented = torch.clamp(augmented, 0, 1)
        
        # 转换为numpy并显示
        augmented_np = augmented.permute(1, 2, 0).numpy()
        axes[row, col].imshow(augmented_np)
        axes[row, col].set_title(f'增强版本 {i}', fontsize=10)
        axes[row, col].axis('off')
    
    plt.tight_layout()
    
    # 保存图像
    output_dir = os.path.join(config.SPLITS_DIR, 'augmentation_examples')
    os.makedirs(output_dir, exist_ok=True)
    output_path = os.path.join(output_dir, f'augmentation_{config.AUGMENTATION_LEVEL}.png')
    plt.savefig(output_path, dpi=150, bbox_inches='tight')
    print(f"\n✅ 增强效果图已保存: {output_path}")
    
    plt.show()

def compare_augmentation_levels(image_path):
    """
    对比不同增强级别的效果
    
    Args:
        image_path: 图像路径
    """
    original_image = Image.open(image_path).convert('RGB')
    
    levels = ['light', 'medium', 'heavy']
    fig, axes = plt.subplots(len(levels), 4, figsize=(16, 12))
    fig.suptitle('不同增强级别对比', fontsize=16, fontweight='bold')
    
    for level_idx, level in enumerate(levels):
        # 临时修改配置
        original_level = config.AUGMENTATION_LEVEL
        config.AUGMENTATION_LEVEL = level
        
        # 获取对应级别的transform
        train_transform, _ = get_transforms()
        
        # 恢复原配置
        config.AUGMENTATION_LEVEL = original_level
        
        # 第一列显示级别名称和原图
        if level_idx == 0:
            axes[level_idx, 0].imshow(original_image)
            axes[level_idx, 0].set_title('原始图像', fontsize=10, fontweight='bold')
        else:
            axes[level_idx, 0].imshow(original_image)
            axes[level_idx, 0].set_title(f'{level.upper()}', fontsize=10, fontweight='bold')
        axes[level_idx, 0].axis('off')
        
        # 显示3个增强样本
        for sample_idx in range(1, 4):
            augmented = train_transform(original_image)
            
            # 反标准化
            mean = torch.tensor([0.485, 0.456, 0.406]).view(3, 1, 1)
            std = torch.tensor([0.229, 0.224, 0.225]).view(3, 1, 1)
            augmented = augmented * std + mean
            augmented = torch.clamp(augmented, 0, 1)
            
            augmented_np = augmented.permute(1, 2, 0).numpy()
            axes[level_idx, sample_idx].imshow(augmented_np)
            axes[level_idx, sample_idx].set_title(f'样本 {sample_idx}', fontsize=9)
            axes[level_idx, sample_idx].axis('off')
    
    plt.tight_layout()
    
    # 保存对比图
    output_dir = os.path.join(config.SPLITS_DIR, 'augmentation_examples')
    os.makedirs(output_dir, exist_ok=True)
    output_path = os.path.join(output_dir, 'augmentation_comparison.png')
    plt.savefig(output_path, dpi=150, bbox_inches='tight')
    print(f"✅ 增强级别对比图已保存: {output_path}")
    
    plt.show()

def find_sample_image():
    """查找一张示例图像"""
    for class_name in os.listdir(config.DATA_ROOT):
        class_path = os.path.join(config.DATA_ROOT, class_name)
        if os.path.isdir(class_path):
            files = [f for f in os.listdir(class_path) 
                    if f.lower().endswith(('.jpg', '.jpeg', '.png'))]
            if files:
                # 找一张原始图像（不是增强版本）
                for f in files:
                    if '_rot' not in f.lower() and '_comp' not in f.lower():
                        return os.path.join(class_path, f)
                # 如果没有原始图像，返回第一张
                return os.path.join(class_path, files[0])
    return None

if __name__ == "__main__":
    print("=" * 70)
    print("数据增强可视化工具")
    print("=" * 70)
    
    # 查找示例图像
    sample_image = find_sample_image()
    
    if sample_image is None:
        print("❌ 未找到示例图像")
        sys.exit(1)
    
    print(f"\n使用示例图像: {sample_image}")
    print(f"当前增强级别: {config.AUGMENTATION_LEVEL}")
    
    print("\n正在生成增强效果展示...")
    visualize_augmentations(sample_image)
    
    print("\n正在生成增强级别对比...")
    compare_augmentation_levels(sample_image)
    
    print("\n" + "=" * 70)
    print("✅ 可视化完成！")
    print("=" * 70)

