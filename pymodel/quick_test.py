"""
快速测试脚本 - 批量测试多张图片，对比修复前后的差异

使用方法：
    python quick_test.py --folder "测试图片文件夹路径"
    
或者测试单张图片：
    python quick_test.py --image "测试图片.jpg"
"""

import os
import torch
from torchvision import transforms
from PIL import Image
import argparse
from pathlib import Path
from models.mobilenet_v2 import get_model
import config
import json
from utils.misc import safe_torch_load

# 确保类别名已加载
def ensure_class_names():
    if config.CLASS_NAMES is None:
        json_path = os.path.join(config.SPLITS_DIR, "class_names.json")
        if os.path.isfile(json_path):
            with open(json_path, "r", encoding="utf-8") as f:
                config.CLASS_NAMES = json.load(f)
        else:
            from torchvision import datasets
            full_dataset = datasets.ImageFolder(root=config.DATA_ROOT)
            config.CLASS_NAMES = full_dataset.classes


def get_old_transform():
    """旧的预处理方式（错误的）"""
    return transforms.Compose([
        transforms.Resize((config.IMAGE_SIZE, config.IMAGE_SIZE)),
        transforms.ToTensor(),
        transforms.Normalize(
            mean=[0.485, 0.456, 0.406],
            std=[0.229, 0.224, 0.225]
        )
    ])


def get_new_transform():
    """新的预处理方式（正确的）"""
    return transforms.Compose([
        transforms.Resize(int(config.IMAGE_SIZE * 1.14)),  # 256
        transforms.CenterCrop(config.IMAGE_SIZE),           # 224
        transforms.ToTensor(),
        transforms.Normalize(
            mean=[0.485, 0.456, 0.406],
            std=[0.229, 0.224, 0.225]
        )
    ])


def predict_with_transform(image_path, model, transform, top_k=3):
    """使用指定的transform进行预测"""
    ensure_class_names()
    
    # 加载并预处理图像
    image = Image.open(image_path).convert("RGB")
    image_tensor = transform(image).unsqueeze(0).to(config.DEVICE)
    
    # 预测
    model.eval()
    with torch.no_grad():
        outputs = model(image_tensor)
        probabilities = torch.nn.functional.softmax(outputs, dim=1)
        top_probs, top_indices = torch.topk(probabilities[0], min(top_k, len(config.CLASS_NAMES)))
        
        results = []
        for prob, idx in zip(top_probs, top_indices):
            results.append({
                'class': config.CLASS_NAMES[idx.item()],
                'confidence': prob.item() * 100
            })
    
    return results


def compare_predictions(image_path, model):
    """对比新旧预处理方式的预测结果"""
    old_transform = get_old_transform()
    new_transform = get_new_transform()
    
    old_results = predict_with_transform(image_path, model, old_transform, top_k=3)
    new_results = predict_with_transform(image_path, model, new_transform, top_k=3)
    
    return {
        'image': os.path.basename(image_path),
        'old_method': old_results,
        'new_method': new_results
    }


def print_comparison(result):
    """打印对比结果"""
    print("\n" + "="*80)
    print(f"📷 图片：{result['image']}")
    print("="*80)
    
    print("\n❌ 旧方法（Resize(224,224) - 错误）:")
    for i, pred in enumerate(result['old_method'], 1):
        marker = "★" if i == 1 else " "
        warning = " ⚠️ 可能错误" if pred['class'] == "Background_without_leaves" else ""
        print(f"{marker} {i}. {pred['class']:<40} {pred['confidence']:6.2f}%{warning}")
    
    print("\n✅ 新方法（Resize(256) + CenterCrop(224) - 正确）:")
    for i, pred in enumerate(result['new_method'], 1):
        marker = "★" if i == 1 else " "
        warning = " ⚠️ 可能错误" if pred['class'] == "Background_without_leaves" else ""
        print(f"{marker} {i}. {pred['class']:<40} {pred['confidence']:6.2f}%{warning}")
    
    # 分析差异
    old_top1 = result['old_method'][0]
    new_top1 = result['new_method'][0]
    
    if old_top1['class'] != new_top1['class']:
        print(f"\n🔄 预测发生变化：{old_top1['class']} → {new_top1['class']}")
        conf_diff = new_top1['confidence'] - old_top1['confidence']
        print(f"   置信度变化：{old_top1['confidence']:.2f}% → {new_top1['confidence']:.2f}% ({conf_diff:+.2f}%)")
    else:
        print(f"\n✔️ 预测一致：{old_top1['class']}")
        conf_diff = new_top1['confidence'] - old_top1['confidence']
        if abs(conf_diff) > 1:
            print(f"   置信度变化：{old_top1['confidence']:.2f}% → {new_top1['confidence']:.2f}% ({conf_diff:+.2f}%)")


def test_folder(folder_path, model, max_images=10):
    """批量测试文件夹中的图片"""
    folder = Path(folder_path)
    image_extensions = {'.jpg', '.jpeg', '.png', '.bmp', '.JPG', '.JPEG', '.PNG', '.BMP'}
    
    # 查找所有图片
    images = [f for f in folder.glob('*') if f.suffix in image_extensions]
    
    if not images:
        print(f"⚠️ 文件夹中没有找到图片：{folder_path}")
        return
    
    # 限制测试数量
    images = images[:max_images]
    print(f"\n🔍 找到 {len(images)} 张图片，开始测试...\n")
    
    # 统计
    changed_count = 0
    improved_count = 0
    
    for image_path in images:
        result = compare_predictions(str(image_path), model)
        print_comparison(result)
        
        old_top1 = result['old_method'][0]
        new_top1 = result['new_method'][0]
        
        if old_top1['class'] != new_top1['class']:
            changed_count += 1
            # 如果旧方法预测为Background_without_leaves，新方法不是，则认为是改进
            if old_top1['class'] == "Background_without_leaves" and new_top1['class'] != "Background_without_leaves":
                improved_count += 1
    
    # 打印总结
    print("\n" + "="*80)
    print("📊 测试总结")
    print("="*80)
    print(f"总测试图片数：{len(images)}")
    print(f"预测发生变化：{changed_count} 张 ({changed_count/len(images)*100:.1f}%)")
    print(f"从Background修正：{improved_count} 张")
    print("\n💡 结论：")
    if changed_count > 0:
        print("  修复预处理方式后，预测结果有明显变化！")
        if improved_count > 0:
            print(f"  其中 {improved_count} 张从'无叶片背景'修正为其他类别，这很可能是改进！")
    else:
        print("  预测结果没有变化，说明当前测试图片不受预处理方式影响。")
    print("="*80)


def test_single_image(image_path, model):
    """测试单张图片"""
    if not os.path.isfile(image_path):
        print(f"❌ 图片不存在：{image_path}")
        return
    
    result = compare_predictions(image_path, model)
    print_comparison(result)


if __name__ == "__main__":
    # 命令行参数
    parser = argparse.ArgumentParser(description="快速测试脚本 - 对比新旧预处理方式")
    parser.add_argument("--folder", "-f", type=str, help="测试图片文件夹路径")
    parser.add_argument("--image", "-i", type=str, help="测试单张图片路径")
    parser.add_argument("--max", "-m", type=int, default=10, help="最大测试图片数（默认10）")
    args = parser.parse_args()
    
    # 确保类别名已加载
    ensure_class_names()
    
    # 加载模型
    print("正在加载模型...")
    model = get_model()
    if not os.path.isfile(config.MODEL_SAVE_PATH):
        print(f"❌ 未找到模型文件：{config.MODEL_SAVE_PATH}")
        print("   请先运行 train.py 训练模型")
        exit(1)
    
    state_dict = safe_torch_load(
        config.MODEL_SAVE_PATH,
        map_location=config.DEVICE,
    )
    model.load_state_dict(state_dict)
    print("✅ 模型加载完成\n")
    
    # 执行测试
    if args.folder:
        test_folder(args.folder, model, max_images=args.max)
    elif args.image:
        test_single_image(args.image, model)
    else:
        print("❌ 请指定测试图片或文件夹：")
        print("   python quick_test.py --folder '图片文件夹'")
        print("   python quick_test.py --image '图片.jpg'")

