"""
全面诊断模型问题 - 找出所有识别错误的模式

功能：
1. 在测试集上评估模型
2. 统计所有错误分类的模式
3. 找出最容易混淆的类别对
4. 分析问题的严重程度
"""

import torch
import numpy as np
from collections import defaultdict
from utils.data_loader import get_data_loaders
from models.mobilenet_v2 import get_model
from utils.train_utils import test_model
from utils.misc import safe_torch_load
import config
from tqdm import tqdm


def analyze_predictions_detailed(model, test_loader):
    """详细分析预测结果"""
    model.eval()
    
    # 统计变量
    correct = 0
    total = 0
    confusion_pairs = defaultdict(int)  # {(true_class, pred_class): count}
    class_correct = defaultdict(int)
    class_total = defaultdict(int)
    class_confidences = defaultdict(list)
    
    print("\n正在分析测试集预测结果...")
    
    with torch.no_grad():
        for images, labels in tqdm(test_loader, desc="分析中"):
            images = images.to(config.DEVICE)
            labels = labels.to(config.DEVICE)
            
            outputs = model(images)
            probabilities = torch.nn.functional.softmax(outputs, dim=1)
            _, predicted = torch.max(outputs, 1)
            
            # 统计
            total += labels.size(0)
            correct += (predicted == labels).sum().item()
            
            # 详细统计每个样本
            for true_label, pred_label, probs in zip(labels.cpu().numpy(), 
                                                       predicted.cpu().numpy(), 
                                                       probabilities.cpu().numpy()):
                class_total[true_label] += 1
                confidence = probs[pred_label] * 100
                class_confidences[true_label].append(confidence)
                
                if true_label == pred_label:
                    class_correct[true_label] += 1
                else:
                    # 记录混淆对
                    confusion_pairs[(true_label, pred_label)] += 1
    
    accuracy = correct / total
    
    return {
        'accuracy': accuracy,
        'confusion_pairs': confusion_pairs,
        'class_correct': class_correct,
        'class_total': class_total,
        'class_confidences': class_confidences
    }


def print_diagnosis(results):
    """打印诊断报告"""
    print("\n" + "="*80)
    print("📊 模型诊断报告")
    print("="*80)
    
    # 1. 总体准确率
    print(f"\n【总体性能】")
    print(f"测试集准确率: {results['accuracy']*100:.2f}%")
    
    # 2. 最严重的混淆对（Top 20）
    print(f"\n【最严重的识别错误】（Top 20）")
    print("-"*80)
    confusion_pairs = results['confusion_pairs']
    sorted_pairs = sorted(confusion_pairs.items(), key=lambda x: x[1], reverse=True)
    
    if not sorted_pairs:
        print("✅ 太好了！测试集上没有错误！")
    else:
        print(f"{'序号':<4} {'真实类别':<30} {'错误预测为':<30} {'错误次数':<8}")
        print("-"*80)
        for i, ((true_idx, pred_idx), count) in enumerate(sorted_pairs[:20], 1):
            true_class = config.CLASS_NAMES[true_idx]
            pred_class = config.CLASS_NAMES[pred_idx]
            print(f"{i:<4} {true_class:<30} {pred_class:<30} {count:<8}")
    
    # 3. 识别最差的类别（Top 10）
    print(f"\n【识别最差的类别】（Top 10）")
    print("-"*80)
    class_correct = results['class_correct']
    class_total = results['class_total']
    
    class_accuracies = []
    for class_idx in range(len(config.CLASS_NAMES)):
        if class_idx in class_total and class_total[class_idx] > 0:
            acc = class_correct.get(class_idx, 0) / class_total[class_idx]
            class_accuracies.append((class_idx, acc, class_total[class_idx]))
    
    sorted_classes = sorted(class_accuracies, key=lambda x: x[1])
    
    print(f"{'序号':<4} {'类别':<40} {'准确率':<12} {'样本数':<8}")
    print("-"*80)
    for i, (class_idx, acc, total) in enumerate(sorted_classes[:10], 1):
        class_name = config.CLASS_NAMES[class_idx]
        marker = "⚠️" if acc < 0.9 else ""
        print(f"{i:<4} {class_name:<40} {acc*100:>6.2f}% {marker:<5} {total:<8}")
    
    # 4. 识别最好的类别（Top 5）
    print(f"\n【识别最好的类别】（Top 5）")
    print("-"*80)
    print(f"{'序号':<4} {'类别':<40} {'准确率':<12} {'样本数':<8}")
    print("-"*80)
    for i, (class_idx, acc, total) in enumerate(sorted_classes[-5:][::-1], 1):
        class_name = config.CLASS_NAMES[class_idx]
        print(f"{i:<4} {class_name:<40} {acc*100:>6.2f}% ✅    {total:<8}")
    
    # 5. 分析具体问题
    print(f"\n【问题分析】")
    print("-"*80)
    
    # 统计涉及Background_without_leaves的错误
    bg_errors = 0
    bg_as_pred = 0
    bg_as_true = 0
    
    bg_idx = None
    if "Background_without_leaves" in config.CLASS_NAMES:
        bg_idx = config.CLASS_NAMES.index("Background_without_leaves")
        
        for (true_idx, pred_idx), count in confusion_pairs.items():
            if true_idx == bg_idx:
                bg_as_true += count
            if pred_idx == bg_idx:
                bg_as_pred += count
            if true_idx == bg_idx or pred_idx == bg_idx:
                bg_errors += count
        
        print(f"✓ Background_without_leaves 相关错误:")
        print(f"  - 被误识别为其他类别: {bg_as_true} 次")
        print(f"  - 其他类别被误识别为它: {bg_as_pred} 次")
        print(f"  - 总共涉及错误: {bg_errors} 次")
        
        if bg_as_pred > 10:
            print(f"  ⚠️ 警告：有 {bg_as_pred} 次将植物叶片误识别为'无叶片'，建议排除此类别！")
    
    # 统计同植物不同病害的混淆
    plant_confusion = defaultdict(int)
    for (true_idx, pred_idx), count in confusion_pairs.items():
        true_name = config.CLASS_NAMES[true_idx]
        pred_name = config.CLASS_NAMES[pred_idx]
        
        # 提取植物名（___前的部分）
        true_plant = true_name.split('___')[0] if '___' in true_name else true_name
        pred_plant = pred_name.split('___')[0] if '___' in pred_name else pred_name
        
        if true_plant == pred_plant:
            plant_confusion[true_plant] += count
    
    if plant_confusion:
        print(f"\n✓ 同一植物不同病害的混淆:")
        sorted_plants = sorted(plant_confusion.items(), key=lambda x: x[1], reverse=True)
        for plant, count in sorted_plants[:5]:
            print(f"  - {plant}: {count} 次")
        print(f"  💡 这是正常现象，同种植物的不同病害视觉相似度高")
    
    # 6. 总结和建议
    print(f"\n【建议】")
    print("-"*80)
    
    total_errors = sum(confusion_pairs.values())
    
    if results['accuracy'] >= 0.98:
        print("✅ 模型整体表现优秀（准确率≥98%）")
        if bg_as_pred > 5:
            print(f"⚠️ 但仍有 {bg_as_pred} 次误判为Background，建议:")
            print("   1. 排除 Background_without_leaves 类别")
            print("   2. 重新训练模型")
    elif results['accuracy'] >= 0.95:
        print("⚠️ 模型表现良好但有改进空间（准确率95%-98%）")
        print("   建议:")
        print("   1. 排除 Background_without_leaves 类别")
        print("   2. 降低数据增强强度（heavy→medium）")
        print("   3. 增加训练轮次（30→50）")
        print("   4. 考虑使用 TTA (Test Time Augmentation)")
    else:
        print("❌ 模型表现不佳（准确率<95%）")
        print("   强烈建议:")
        print("   1. 排除 Background_without_leaves 类别")
        print("   2. 降低数据增强强度（heavy→light/medium）")
        print("   3. 使用更多训练轮次")
        print("   4. 考虑解冻更多层（FREEZE_STRATEGY='none'）")
        print("   5. 检查数据质量")
    
    print("\n" + "="*80)
    print(f"总计发现 {total_errors} 个分类错误")
    print("="*80)


def main():
    """主函数"""
    print("="*80)
    print("🔍 模型问题全面诊断")
    print("="*80)
    print("\n这个脚本会：")
    print("  1. 在测试集上运行模型")
    print("  2. 统计所有识别错误")
    print("  3. 找出最容易混淆的类别")
    print("  4. 给出针对性的改进建议")
    print("\n预计耗时: 1-2分钟")
    
    # 加载类别名
    import json
    import os
    json_path = os.path.join(config.SPLITS_DIR, "class_names.json")
    if os.path.isfile(json_path):
        with open(json_path, "r", encoding="utf-8") as f:
            config.CLASS_NAMES = json.load(f)
    else:
        print("❌ 未找到类别名称文件，请先运行 train.py")
        return
    
    # 加载数据
    print("\n正在加载测试集...")
    _, _, test_loader = get_data_loaders()
    print(f"✓ 测试集加载完成: {len(test_loader.dataset)} 个样本")
    
    # 加载模型
    print("正在加载模型...")
    model = get_model()
    if not os.path.isfile(config.MODEL_SAVE_PATH):
        print(f"❌ 未找到模型文件: {config.MODEL_SAVE_PATH}")
        return
    
    state_dict = safe_torch_load(
        config.MODEL_SAVE_PATH,
        map_location=config.DEVICE,
    )
    model.load_state_dict(state_dict)
    print("✓ 模型加载完成")
    
    # 分析
    results = analyze_predictions_detailed(model, test_loader)
    
    # 打印报告
    print_diagnosis(results)


if __name__ == "__main__":
    main()

