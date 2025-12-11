import argparse
import json
import os
from pathlib import Path

import matplotlib.pyplot as plt
import torch
from PIL import Image
from torchvision import transforms

import config
from models.mobilenet_v2 import get_model
from utils.misc import safe_torch_load

# 中文字体设置，保证标题中文正常显示
plt.rcParams['font.sans-serif'] = ['SimHei']
plt.rcParams['axes.unicode_minus'] = False


def ensure_dual_class_names():
    """确保物种与病害的类别名称已加载（从JSON读取）。"""
    # Species
    if getattr(config, "SPECIES_CLASS_NAMES", None) is None:
        if os.path.isfile(config.SPECIES_CLASS_NAMES_JSON):
            with open(config.SPECIES_CLASS_NAMES_JSON, "r", encoding="utf-8") as f:
                config.SPECIES_CLASS_NAMES = json.load(f)
        else:
            # 回退：扫描数据集
            from torchvision import datasets
            ds = datasets.ImageFolder(root=config.DATA_ROOT)
            species = sorted({c.split("___")[0] if "___" in c else c for c in ds.classes})
            config.SPECIES_CLASS_NAMES = species

    # Disease
    if getattr(config, "DISEASE_CLASS_NAMES", None) is None:
        if os.path.isfile(config.DISEASE_CLASS_NAMES_JSON):
            with open(config.DISEASE_CLASS_NAMES_JSON, "r", encoding="utf-8") as f:
                config.DISEASE_CLASS_NAMES = json.load(f)
        else:
            from torchvision import datasets
            ds = datasets.ImageFolder(root=config.DATA_ROOT)
            disease = sorted({(c.split("___")[1] if "___" in c else c) for c in ds.classes})
            config.DISEASE_CLASS_NAMES = disease


def _val_transform():
    return transforms.Compose([
        transforms.Resize(int(config.IMAGE_SIZE * 1.14)),
        transforms.CenterCrop(config.IMAGE_SIZE),
        transforms.ToTensor(),
        transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
    ])


def _tta_transforms():
    base = _val_transform()
    # 定义一组轻量TTA变换
    def apply(img, ops):
        x = img
        for op in ops:
            x = op(x)
        return base(x)
    return [
        lambda img: base(img),
        lambda img: apply(img, [transforms.functional.hflip]),
        lambda img: apply(img, [transforms.functional.vflip]),
        lambda img: apply(img, [lambda x: transforms.functional.rotate(x, 15)]),
        lambda img: apply(img, [lambda x: transforms.functional.rotate(x, -15)]),
    ]


def predict_with_tta(image: Image.Image, model, class_names, top_k=5, use_tta=True):
    model.eval()
    tfs = _tta_transforms() if use_tta else [ _val_transform() ]
    with torch.no_grad():
        probs_sum = None
        for tf in tfs:
            t = tf(image).unsqueeze(0).to(config.DEVICE)
            outputs = model(t)
            probs = torch.softmax(outputs, dim=1)
            probs_sum = probs if probs_sum is None else (probs_sum + probs)
        probs_avg = probs_sum / len(tfs)
        top_probs, top_indices = torch.topk(probs_avg[0], min(top_k, len(class_names)))
        top_predictions = [(class_names[idx.item()], prob.item() * 100) for prob, idx in zip(top_probs, top_indices)]
        predicted_class, confidence = top_predictions[0]
        return image, predicted_class, confidence, top_predictions


def _resolve_image_path(args_image: str | None) -> str:
    """解析图片路径：优先命令行；否则弹出文件选择框，失败则命令行输入。"""
    if args_image:
        return str(Path(args_image))
    # 尝试文件选择框
    try:
        from tkinter import Tk, filedialog
        root = Tk()
        root.withdraw()
        image_path = filedialog.askopenfilename(
            title="选择待预测图片",
            filetypes=[("Image files", "*.jpg;*.jpeg;*.png;*.bmp;*.JPG;*.JPEG;*.PNG;*.BMP")]
        )
        if image_path:
            return str(Path(image_path))
    except Exception:
        pass
    # 兜底：命令行输入
    image_path = input("请输入待预测图片路径：").strip()
    return str(Path(image_path))


if __name__ == "__main__":
    ensure_dual_class_names()

    # 命令行参数：图片路径（可选）
    parser = argparse.ArgumentParser(description="PlantVillage 双模型图像分类预测（物种+病害）")
    parser.add_argument("--image", "-i", type=str, required=False, help="待预测图片的路径（不提供则弹出选择窗口）")
    parser.add_argument("--no_tta", action="store_true", help="禁用TTA")
    parser.add_argument("--leaf_check", action="store_true", help="先做叶片存在性检查")
    parser.add_argument("--no_leaf_check", action="store_true", help="禁用叶片存在性检查（默认开启）")
    parser.add_argument("--single", type=str, choices=["species", "disease"], default=None, help="仅运行单个模型预测")
    args = parser.parse_args()
    leaf_check_enabled = (args.leaf_check or (not args.no_leaf_check))

    # 解析路径，避免反斜杠转义问题
    image_path = _resolve_image_path(args.image)
    if not os.path.isfile(image_path):
        raise FileNotFoundError(f"未找到图像文件：{image_path}")

    # 加载图像
    image = Image.open(image_path).convert("RGB")

    # 可选：叶片存在性检查
    if leaf_check_enabled and os.path.isfile(config.LEAF_PRESENCE_MODEL_PATH):
        from leaf_presence import predict_leaf_presence
        no_leaf_prob, leaf_prob = predict_leaf_presence(image)
        print(f"叶片存在性: 无叶片={no_leaf_prob*100:.2f}%, 有叶片={leaf_prob*100:.2f}%")
        if no_leaf_prob >= config.LEAF_PRESENCE_NO_LEAF_THRESHOLD:
            print("\n⚠ 判定为无叶片背景（概率高于阈值）")
            # 打印并展示
            plt.figure(figsize=(10, 8))
            plt.imshow(image)
            title = f"预测类别：Background_without_leaves（{no_leaf_prob*100:.2f}%）"
            plt.title(title, fontsize=12, pad=20)
            plt.axis("off")
            plt.tight_layout()
            plt.show()
            raise SystemExit(0)
        # 新增：有叶片概率低于阈值，也判定为未知/非叶片
        if leaf_prob < config.LEAF_PRESENCE_LEAF_MIN:
            print("\n⚠ 叶片概率低于阈值，判定为未知/非叶片")
            plt.figure(figsize=(10, 8))
            plt.imshow(image)
            title = f"预测类别：Unknown / 非叶片（leaf_prob={leaf_prob*100:.2f}%）"
            plt.title(title, fontsize=12, pad=20)
            plt.axis("off")
            plt.tight_layout()
            plt.show()
            raise SystemExit(0)

    # 加载两个模型
    # species
    species_class_names = config.SPECIES_CLASS_NAMES
    disease_class_names = config.DISEASE_CLASS_NAMES

    species_model = get_model(num_classes=len(species_class_names))
    disease_model = get_model(num_classes=len(disease_class_names))

    species_weights_ok = os.path.isfile(config.SPECIES_MODEL_SAVE_PATH)
    disease_weights_ok = os.path.isfile(config.DISEASE_MODEL_SAVE_PATH)

    if not species_weights_ok and not disease_weights_ok:
        raise FileNotFoundError(
            "未找到任何模型权重：\n"
            f"  - 物种权重: {config.SPECIES_MODEL_SAVE_PATH}\n"
            f"  - 病害权重: {config.DISEASE_MODEL_SAVE_PATH}\n"
            "请先分别运行：train.py --task species 与 train.py --task disease"
        )

    if species_weights_ok:
        species_state = safe_torch_load(config.SPECIES_MODEL_SAVE_PATH, map_location=config.DEVICE)
        species_model.load_state_dict(species_state)
    else:
        print(f"[提示] 未找到物种模型权重，将跳过物种预测：{config.SPECIES_MODEL_SAVE_PATH}")

    if disease_weights_ok:
        disease_state = safe_torch_load(config.DISEASE_MODEL_SAVE_PATH, map_location=config.DEVICE)
        disease_model.load_state_dict(disease_state)
    else:
        print(f"[提示] 未找到病害模型权重，将跳过病害预测：{config.DISEASE_MODEL_SAVE_PATH}")

    # 预测（支持TTA）
    use_tta = (not args.no_tta)
    if args.single == "species":
        if not species_weights_ok:
            raise FileNotFoundError(f"未找到物种模型权重：{config.SPECIES_MODEL_SAVE_PATH}。请先运行 train.py --task species 训练模型。")
        _, sp1, sp1_conf, sp_top = predict_with_tta(image, species_model, species_class_names, top_k=5, use_tta=use_tta)
        print("\n" + "="*60)
        print("物种预测（Top-5）：")
        print("="*60)
        for i, (cls, conf) in enumerate(sp_top, 1):
            marker = "★" if i == 1 else " "
            print(f"{marker} {i}. {cls:<45} {conf:6.2f}%")
        print("="*60)
        plt.figure(figsize=(10, 8))
        plt.imshow(image)
        title = f"物种：{sp1}（{sp1_conf:.2f}%）"
        plt.title(title, fontsize=12, pad=20)
        plt.axis("off")
        plt.tight_layout()
        plt.show()
        raise SystemExit(0)

    if args.single == "disease":
        if not disease_weights_ok:
            raise FileNotFoundError(f"未找到病害模型权重：{config.DISEASE_MODEL_SAVE_PATH}。请先运行 train.py --task disease 训练模型。")
        _, dz1, dz1_conf, dz_top = predict_with_tta(image, disease_model, disease_class_names, top_k=5, use_tta=use_tta)
        print("\n" + "="*60)
        print("病害预测（Top-5）：")
        print("="*60)
        for i, (cls, conf) in enumerate(dz_top, 1):
            marker = "★" if i == 1 else " "
            print(f"{marker} {i}. {cls:<45} {conf:6.2f}%")
        print("="*60)
        plt.figure(figsize=(10, 8))
        plt.imshow(image)
        title = f"病害：{dz1}（{dz1_conf:.2f}%）"
        plt.title(title, fontsize=12, pad=20)
        plt.axis("off")
        plt.tight_layout()
        plt.show()
        raise SystemExit(0)

    # 双模型（可降级为单个可用模型）
    sp1 = sp1_conf = sp_top = None
    dz1 = dz1_conf = dz_top = None
    if species_weights_ok:
        _, sp1, sp1_conf, sp_top = predict_with_tta(image, species_model, species_class_names, top_k=5, use_tta=use_tta)
    if disease_weights_ok:
        _, dz1, dz1_conf, dz_top = predict_with_tta(image, disease_model, disease_class_names, top_k=5, use_tta=use_tta)

    if sp_top is not None:
        print("\n" + "="*60)
        print("物种预测（Top-5）：")
        print("="*60)
        for i, (cls, conf) in enumerate(sp_top, 1):
            marker = "★" if i == 1 else " "
            print(f"{marker} {i}. {cls:<45} {conf:6.2f}%")
        print("="*60)
    if dz_top is not None:
        print("病害预测（Top-5）：")
        print("="*60)
        for i, (cls, conf) in enumerate(dz_top, 1):
            marker = "★" if i == 1 else " "
            print(f"{marker} {i}. {cls:<45} {conf:6.2f}%")
        print("="*60)

    # 显示合并结果
    plt.figure(figsize=(10, 8))
    plt.imshow(image)
    # 组合标题（根据可用预测）
    title_parts = []
    if sp1 is not None:
        title_parts.append(f"物种：{sp1}（{sp1_conf:.2f}%）")
    if dz1 is not None:
        title_parts.append(f"病害：{dz1}（{dz1_conf:.2f}%）")
    title = "\n".join(title_parts) if title_parts else "未进行任何预测"
    plt.title(title, fontsize=12, pad=20)
    plt.axis("off")
    plt.tight_layout()
    plt.show()
