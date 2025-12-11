"""
验证数据划分修复是否有效
"""
import os
import sys
import io
import json
import re
from collections import defaultdict
import config

# Windows编码修复
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

def extract_base_image_name(filename):
    """提取图像的基础名称"""
    base = os.path.splitext(filename)[0]
    base = re.sub(r'_comp(_comp)?(_rot_\d+)?$', '', base)
    base = re.sub(r'_rot_\d+$', '', base)
    return base

def verify_no_leakage():
    """验证训练/验证/测试集之间没有同一原始图像的泄露"""
    print("=" * 70)
    print("数据划分泄露验证")
    print("=" * 70)
    
    # 加载数据划分
    indices_file = os.path.join(config.SPLITS_DIR, "indices.json")
    if not os.path.exists(indices_file):
        print("❌ 未找到数据划分文件，请先运行训练")
        return
    
    with open(indices_file, 'r', encoding='utf-8') as f:
        splits = json.load(f)
    
    split_version = splits.get('split_version', 1)
    print(f"\n数据划分版本: {split_version}")
    if split_version == 2:
        print("✅ 使用版本2（按原始图像分组）")
    else:
        print("⚠️  使用旧版本，可能存在数据泄露")
    
    # 加载数据集路径
    from torchvision import datasets
    full_dataset = datasets.ImageFolder(root=config.DATA_ROOT, transform=None)
    
    # 构建索引到路径的映射
    idx_to_path = {i: path for i, (path, _) in enumerate(full_dataset.samples)}
    
    # 提取每个数据集的基础图像名称
    def get_base_names(indices):
        base_names = set()
        for idx in indices:
            if idx in idx_to_path:
                path = idx_to_path[idx]
                filename = os.path.basename(path)
                class_name = os.path.basename(os.path.dirname(path))
                base_name = extract_base_image_name(filename)
                # 使用(类别, 基础名)作为唯一标识
                base_names.add((class_name, base_name))
        return base_names
    
    print("\n正在分析数据集...")
    train_bases = get_base_names(splits['train'])
    val_bases = get_base_names(splits['val'])
    test_bases = get_base_names(splits['test'])
    
    print(f"\n训练集: {len(splits['train'])} 样本, {len(train_bases)} 个原始图像")
    print(f"验证集: {len(splits['val'])} 样本, {len(val_bases)} 个原始图像")
    print(f"测试集: {len(splits['test'])} 样本, {len(test_bases)} 个原始图像")
    
    # 检查泄露
    print("\n" + "=" * 70)
    print("泄露检查结果")
    print("=" * 70)
    
    train_val_leak = train_bases & val_bases
    train_test_leak = train_bases & test_bases
    val_test_leak = val_bases & test_bases
    
    total_leak = len(train_val_leak) + len(train_test_leak) + len(val_test_leak)
    
    if total_leak == 0:
        print("\n✅ 完美！没有发现数据泄露")
        print("   同一原始图像的所有增强版本都在同一个数据集中")
    else:
        print(f"\n❌ 发现数据泄露!")
        if train_val_leak:
            print(f"   训练-验证泄露: {len(train_val_leak)} 个原始图像")
            print(f"   示例: {list(train_val_leak)[:3]}")
        if train_test_leak:
            print(f"   训练-测试泄露: {len(train_test_leak)} 个原始图像")
            print(f"   示例: {list(train_test_leak)[:3]}")
        if val_test_leak:
            print(f"   验证-测试泄露: {len(val_test_leak)} 个原始图像")
            print(f"   示例: {list(val_test_leak)[:3]}")
    
    # 统计增强比例
    print("\n" + "=" * 70)
    print("数据增强统计")
    print("=" * 70)
    
    total_samples = len(splits['train']) + len(splits['val']) + len(splits['test'])
    total_originals = len(train_bases) + len(val_bases) + len(test_bases)
    augmentation_ratio = total_samples / total_originals if total_originals > 0 else 0
    
    print(f"\n总样本数: {total_samples}")
    print(f"原始图像数: {total_originals}")
    print(f"平均增强倍数: {augmentation_ratio:.2f}x")
    
    # 预测准确率影响
    print("\n" + "=" * 70)
    print("对验证准确率的影响分析")
    print("=" * 70)
    
    if total_leak == 0:
        print("\n✅ 修复后，验证准确率应该会下降到更真实的水平")
        print("   预期变化:")
        print("   - 旧版本（有泄露）: 98.5-99.0%")
        print("   - 新版本（无泄露）: 96.0-98.0%")
        print("\n   如果准确率仍然很高（>98%），说明:")
        print("   1. 模型确实很好 ✅")
        print("   2. 数据集质量高 ✅")
        print("   3. 迁移学习效果好 ✅")
    else:
        print("\n⚠️  仍存在数据泄露，需要重新划分数据集")
        print("   建议: 删除 saved_models/splits/ 目录，重新运行训练")
    
    print("\n" + "=" * 70)

if __name__ == "__main__":
    verify_no_leakage()

