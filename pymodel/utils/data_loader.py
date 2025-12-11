import os
import json
import torch
from torch.utils.data import DataLoader, Subset, WeightedRandomSampler, Dataset
from torchvision import datasets, transforms
from sklearn.model_selection import train_test_split
from PIL import Image
import config


class TransformSubset(Dataset):
    """
    自定义Subset类，支持在索引子集上动态应用transform
    
    优化说明：避免为每个数据集副本重新加载和过滤原始数据，
    而是共享同一个基础数据集，只在读取时应用不同的transform
    """
    def __init__(self, dataset, indices, transform=None):
        """
        Args:
            dataset: 基础数据集（ImageFolder）
            indices: 索引列表
            transform: 要应用的transform
        """
        self.dataset = dataset
        self.indices = indices
        self.transform = transform
    
    def __len__(self):
        return len(self.indices)
    
    def __getitem__(self, idx):
        # 获取原始索引
        original_idx = self.indices[idx]
        # 从基础数据集获取图片路径和标签
        path, label = self.dataset.samples[original_idx]
        # 加载图片
        image = Image.open(path).convert('RGB')
        # 应用transform
        if self.transform is not None:
            image = self.transform(image)
        return image, label


def get_transforms():
    """
    定义训练/验证/测试集的图像预处理
    
    增强策略（针对光照、方向、背景鲁棒性）：
    1. 光照变化：ColorJitter (brightness, contrast, saturation, hue)
    2. 方向变化：RandomRotation, RandomHorizontalFlip, RandomVerticalFlip, RandomAffine
    3. 背景变化：RandomResizedCrop, RandomPerspective, RandomErasing
    4. 其他：GaussianBlur (模拟失焦), RandomGrayscale (颜色鲁棒性)
    
    根据 config.AUGMENTATION_LEVEL 调整增强强度
    """
    aug_level = getattr(config, 'AUGMENTATION_LEVEL', 'medium')
    
    # 根据增强级别设置参数
    if aug_level == 'light':
        # 轻量增强：适合数据质量高、类别区分明显的情况
        crop_scale = (0.85, 1.0)
        rotation_deg = 15
        color_jitter = (0.2, 0.2, 0.2, 0.05)
        perspective_p = 0.0
        blur_p = 0.0
        gray_p = 0.0
        erase_p = 0.1
        affine_enabled = False
    elif aug_level == 'heavy':
        # 强力增强：最大鲁棒性，适合实际部署环境复杂的情况
        crop_scale = (0.6, 1.0)
        rotation_deg = 45
        color_jitter = (0.5, 0.5, 0.5, 0.15)
        perspective_p = 0.4
        blur_p = 0.3
        gray_p = 0.15
        erase_p = 0.4
        affine_enabled = True
    else:  # medium (默认)
        # 中等增强：平衡性能和鲁棒性
        crop_scale = (0.7, 1.0)
        rotation_deg = 30
        color_jitter = (0.4, 0.4, 0.4, 0.1)
        perspective_p = 0.3
        blur_p = 0.2
        gray_p = 0.1
        erase_p = 0.3
        affine_enabled = True
    
    # 构建训练集增强pipeline
    train_transforms_list = [
        # 1. 尺寸和裁剪增强（模拟不同拍摄距离和角度）
        transforms.RandomResizedCrop(
            config.IMAGE_SIZE,
            scale=crop_scale,
            ratio=(0.8, 1.2)
        ),
        
        # 2. 方向和几何变换（增强方向鲁棒性）
        transforms.RandomHorizontalFlip(p=0.5),
        transforms.RandomVerticalFlip(p=0.3),
        transforms.RandomRotation(
            degrees=rotation_deg,
            interpolation=transforms.InterpolationMode.BILINEAR
        ),
    ]
    
    # 仿射变换（中等和强力增强）
    if affine_enabled:
        train_transforms_list.append(
            transforms.RandomAffine(
                degrees=0,
                translate=(0.1, 0.1),
                scale=(0.9, 1.1),
                shear=10
            )
        )
    
    # 透视变换
    if perspective_p > 0:
        train_transforms_list.append(
            transforms.RandomPerspective(
                distortion_scale=0.2,
                p=perspective_p
            )
        )
    
    # 3. 光照和颜色增强（⭐ 核心：增强光照鲁棒性）
    train_transforms_list.append(
        transforms.ColorJitter(
            brightness=color_jitter[0],
            contrast=color_jitter[1],
            saturation=color_jitter[2],
            hue=color_jitter[3]
        )
    )
    
    # 4. 模糊（模拟失焦）
    if blur_p > 0:
        train_transforms_list.append(
            transforms.RandomApply([
                transforms.GaussianBlur(kernel_size=3, sigma=(0.1, 2.0))
            ], p=blur_p)
        )
    
    # 5. 灰度化（增强颜色鲁棒性）
    if gray_p > 0:
        train_transforms_list.append(
            transforms.RandomGrayscale(p=gray_p)
        )
    
    # 6. 转换为张量
    train_transforms_list.append(transforms.ToTensor())
    
    # 7. 随机擦除（⭐ 核心：模拟遮挡和背景干扰）
    if erase_p > 0:
        train_transforms_list.append(
            transforms.RandomErasing(
                p=erase_p,
                scale=(0.02, 0.15),
                ratio=(0.3, 3.3),
                value='random'
            )
        )
    
    # 8. 标准化
    train_transforms_list.append(
        transforms.Normalize(
            mean=[0.485, 0.456, 0.406],
            std=[0.229, 0.224, 0.225]
        )
    )
    
    train_transform = transforms.Compose(train_transforms_list)
    
    print(f"数据增强级别: {aug_level}")
    print(f"  - 裁剪范围: {crop_scale[0]*100:.0f}%-{crop_scale[1]*100:.0f}%")
    print(f"  - 旋转角度: ±{rotation_deg}°")
    print(f"  - 颜色抖动: 亮度±{color_jitter[0]*100:.0f}%, 对比度±{color_jitter[1]*100:.0f}%, 饱和度±{color_jitter[2]*100:.0f}%, 色调±{color_jitter[3]*100:.0f}%")
    print(f"  - 随机擦除: {erase_p*100:.0f}%概率")

    # 验证/测试集：仅基础预处理 + 中心裁剪（保证一致性）
    val_test_transform = transforms.Compose([
        transforms.Resize(int(config.IMAGE_SIZE * 1.14)),  # 先放大（256 for 224）
        transforms.CenterCrop(config.IMAGE_SIZE),          # 中心裁剪
        transforms.ToTensor(),
        transforms.Normalize(
            mean=[0.485, 0.456, 0.406],
            std=[0.229, 0.224, 0.225]
        )
    ])
    
    return train_transform, val_test_transform


def _filter_bad_images(dataset):
    """过滤不合格图片：裁切/拼接/异常尺寸或损坏文件。
    修改并返回原 ImageFolder。支持使用清理缓存加速。"""
    if not getattr(config, "FILTER_BAD_IMAGES", False):
        return dataset

    patterns = [p.lower() for p in getattr(config, "BAD_IMAGE_PATTERNS", [])]
    min_size = getattr(config, "MIN_IMAGE_SIZE", 80)
    max_ratio = getattr(config, "MAX_ASPECT_RATIO", 2.5)

    # 尝试使用清理脚本生成的缓存，加速过滤
    removed_cache = set()
    cache_path = os.path.join(config.SPLITS_DIR, "removed_files.json")
    if os.path.isfile(cache_path):
        try:
            with open(cache_path, "r", encoding="utf-8") as f:
                data = json.load(f)
                removed_cache = {item.get("path") for item in data if isinstance(item, dict) and item.get("path")}
            print(f"使用清理缓存：{len(removed_cache)} 条，将跳过逐张读取")
        except Exception:
            removed_cache = set()

    new_samples = []
    removed = 0
    for path, label in dataset.samples:
        name = os.path.basename(path).lower()
        # 文件名命中裁剪/拼接关键词
        if any(k in name for k in patterns):
            removed += 1
            continue
        # 使用缓存：若命中缓存则直接移除，无需读图
        if removed_cache and path in removed_cache:
            removed += 1
            continue
        # 无缓存情况才读取尺寸与长宽比
        if not removed_cache:
            try:
                with Image.open(path) as img:
                    img = img.convert("RGB")
                    w, h = img.size
                    if min(w, h) < min_size:
                        removed += 1
                        continue
                    ratio = max(w / h, h / w)
                    if ratio > max_ratio:
                        removed += 1
                        continue
            except Exception:
                # 读图失败也剔除
                removed += 1
                continue
        new_samples.append((path, label))

    dataset.samples = new_samples
    dataset.targets = [label for _, label in new_samples]
    print(f"过滤异常图片：移除 {removed} 张，保留 {len(new_samples)} 张")
    return dataset


def _limit_train_indices(train_indices, targets, max_per_class):
    """将训练索引按类别限制为最多 max_per_class。保持类均衡。
    返回新的索引列表（顺序稳定）。"""
    if not max_per_class or max_per_class <= 0:
        return train_indices
    counts = {}
    limited = []
    for i in train_indices:
        y = targets[i]
        c = counts.get(y, 0)
        if c < max_per_class:
            limited.append(i)
            counts[y] = c + 1
    print(f"限制训练样本：每类最多 {max_per_class}，原 {len(train_indices)} -> 现 {len(limited)}")
    return limited


def _get_dataset_fingerprint(dataset):
    """
    生成数据集指纹，用于检测数据集变化
    
    返回：包含样本数、类别数、文件路径哈希的字典
    """
    import hashlib
    # 使用文件路径列表生成简单哈希
    paths = sorted([p for p, _ in dataset.samples])
    path_str = ''.join(paths[:100])  # 只使用前100个路径避免太慢
    fingerprint = {
        'num_samples': len(dataset.samples),
        'num_classes': len(dataset.classes),
        'path_hash': hashlib.md5(path_str.encode()).hexdigest(),
        'classes': dataset.classes
    }
    return fingerprint


def _extract_base_image_name(filename):
    """
    提取图像的基础名称，去除增强后缀
    
    例如:
    - image (1).JPG -> image (1)
    - image (1)_rot_191.JPG -> image (1)
    - image (1)_comp.JPG -> image (1)
    - image (1)_comp_rot_172.JPG -> image (1)
    """
    import re
    # 去除扩展名
    base = os.path.splitext(filename)[0]
    # 去除增强后缀: _rot_数字, _comp, _comp_rot_数字等
    # 匹配模式: _rot_数字, _comp, _comp_comp等
    base = re.sub(r'_comp(_comp)?(_rot_\d+)?$', '', base)
    base = re.sub(r'_rot_\d+$', '', base)
    return base


def _load_or_create_splits(full_dataset):
    """
    加载已有的数据划分，或创建新划分
    
    核心修复：按原始图像分组，避免同一图像的不同增强版本出现在不同数据集
    
    返回：train_indices, val_indices, test_indices
    """
    indices_file = os.path.join(config.SPLITS_DIR, "indices.json")
    fingerprint_file = os.path.join(config.SPLITS_DIR, "dataset_fingerprint.json")
    
    # 计算当前数据集指纹
    current_fingerprint = _get_dataset_fingerprint(full_dataset)
    
    # 尝试加载已有划分
    if os.path.exists(indices_file) and os.path.exists(fingerprint_file):
        try:
            with open(fingerprint_file, 'r', encoding='utf-8') as f:
                saved_fingerprint = json.load(f)
            
            # 检查数据集是否发生变化
            if (saved_fingerprint['num_samples'] == current_fingerprint['num_samples'] and
                saved_fingerprint['num_classes'] == current_fingerprint['num_classes'] and
                saved_fingerprint.get('split_version', 1) == 2):  # 版本2: 按图像分组
                
                with open(indices_file, 'r', encoding='utf-8') as f:
                    splits = json.load(f)
                
                print("✓ 使用已保存的数据划分（数据集未变化，版本2）")
                return splits['train'], splits['val'], splits['test']
            else:
                print("⚠ 数据集已变化或使用旧版本划分，将重新划分")
                if saved_fingerprint.get('split_version', 1) < 2:
                    print("  升级到版本2: 按原始图像分组，避免数据泄露")
        except Exception as e:
            print(f"⚠ 加载已有划分失败: {e}，将重新划分")
    
    # 创建新划分 - 按原始图像分组
    print("正在创建新的数据划分（按原始图像分组）...")
    
    # 第一步：将样本按原始图像分组
    from collections import defaultdict
    image_groups = defaultdict(list)  # {(class_idx, base_name): [indices]}
    
    for idx, (path, label) in enumerate(full_dataset.samples):
        filename = os.path.basename(path)
        base_name = _extract_base_image_name(filename)
        # 使用(类别, 基础名)作为分组键，确保不同类别的同名图像不会混淆
        group_key = (label, base_name)
        image_groups[group_key].append(idx)
    
    print(f"  原始样本数: {len(full_dataset.samples)}")
    print(f"  原始图像组数: {len(image_groups)}")
    print(f"  平均每组增强版本数: {len(full_dataset.samples) / len(image_groups):.1f}")
    
    # 第二步：按组划分，而非按样本划分
    group_keys = list(image_groups.keys())
    group_labels = [key[0] for key in group_keys]  # 提取类别标签用于分层
    
    # 划分图像组（70% 训练，20% 验证，10% 测试）
    train_groups, temp_groups, train_labels, temp_labels = train_test_split(
        group_keys,
        group_labels,
        test_size=0.3,
        random_state=42,
        stratify=group_labels
    )
    
    val_groups, test_groups, val_labels, test_labels = train_test_split(
        temp_groups,
        temp_labels,
        test_size=1/3,  # 30%的1/3 = 10%
        random_state=42,
        stratify=temp_labels
    )
    
    # 第三步：将组转换为样本索引
    train_indices = []
    for group_key in train_groups:
        train_indices.extend(image_groups[group_key])
    
    val_indices = []
    for group_key in val_groups:
        val_indices.extend(image_groups[group_key])
    
    test_indices = []
    for group_key in test_groups:
        test_indices.extend(image_groups[group_key])
    
    # 排序索引（保持一致性）
    train_indices.sort()
    val_indices.sort()
    test_indices.sort()
    
    # 保存划分和指纹
    current_fingerprint['split_version'] = 2  # 标记为版本2
    current_fingerprint['num_image_groups'] = len(image_groups)
    
    with open(indices_file, 'w', encoding='utf-8') as f:
        json.dump({
            'train': train_indices,
            'val': val_indices,
            'test': test_indices,
            'split_version': 2,
            'note': '版本2: 按原始图像分组，同一图像的所有增强版本在同一数据集'
        }, f, indent=2)
    
    with open(fingerprint_file, 'w', encoding='utf-8') as f:
        json.dump(current_fingerprint, f, indent=2, ensure_ascii=False)
    
    print(f"✓ 数据划分完成并保存（版本2）")
    print(f"  训练集: {len(train_indices)} 样本 ({len(train_groups)} 图像组)")
    print(f"  验证集: {len(val_indices)} 样本 ({len(val_groups)} 图像组)")
    print(f"  测试集: {len(test_indices)} 样本 ({len(test_groups)} 图像组)")
    
    return train_indices, val_indices, test_indices


def load_dataset(task: str = "disease"):
    """加载数据集并划分为训练/验证/测试集（7:2:1）
    
    task:
        - "species": 训练物种识别模型（标签=植物物种名）
        - "disease": 训练病害识别模型（标签=病害名，包括 healthy）
    
    修复：
    1. 基于文件路径划分而非索引，避免数据泄露
    2. 添加数据集指纹检查，自动检测数据集变化
    3. 优化为只加载一次数据集，避免重复I/O
    """
    # 创建划分记录文件夹
    os.makedirs(config.SPLITS_DIR, exist_ok=True)

    # ===== 第一步：加载并过滤数据集（只执行一次）=====
    print("正在加载数据集...")
    full_dataset = datasets.ImageFolder(
        root=config.DATA_ROOT,
        transform=None  # 暂不指定transform，后续通过wrapper添加
    )
    
    print(f"原始数据集: {len(full_dataset)} 个样本，{len(full_dataset.classes)} 个类别")
    
    # 过滤类别和异常图片（只执行一次）
    full_dataset = _filter_classes(full_dataset)
    full_dataset = _filter_bad_images(full_dataset)
    
    # 基于原始类别（例如 "Apple___Apple_scab"），构建 task 对应的新类别与标签
    # 先读取原始映射
    orig_classes = full_dataset.classes
    # 构建映射函数
    def split_class_name(name: str):
        parts = name.split("___")
        if len(parts) == 1:
            return parts[0], parts[0]
        return parts[0], parts[1]

    if task == "species":
        # 物种标签：去重后的物种名
        species_names = sorted({split_class_name(c)[0] for c in orig_classes})
        species_to_idx = {c: i for i, c in enumerate(species_names)}
        # 重映射 samples 到 species 标签
        new_samples = []
        for path, label in full_dataset.samples:
            cls_name = orig_classes[label]
            species, _ = split_class_name(cls_name)
            new_samples.append((path, species_to_idx[species]))
        full_dataset.samples = new_samples
        full_dataset.targets = [y for _, y in new_samples]
        full_dataset.classes = species_names
        full_dataset.class_to_idx = species_to_idx

        # 保存与填充配置
        config.SPECIES_CLASS_NAMES = species_names
        os.makedirs(config.SPLITS_DIR, exist_ok=True)
        with open(getattr(config, "SPECIES_CLASS_NAMES_JSON"), "w", encoding="utf-8") as f:
            json.dump(config.SPECIES_CLASS_NAMES, f, ensure_ascii=False)
        print(f"过滤后: {len(full_dataset)} 个样本，{len(config.SPECIES_CLASS_NAMES)} 个物种类别")
        print(f"物种示例：{config.SPECIES_CLASS_NAMES[:5]}...")

    else:  # disease（默认）
        disease_names = sorted({split_class_name(c)[1] if "___" in c else c for c in orig_classes})
        disease_to_idx = {c: i for i, c in enumerate(disease_names)}
        new_samples = []
        for path, label in full_dataset.samples:
            cls_name = orig_classes[label]
            _, disease = split_class_name(cls_name)
            new_samples.append((path, disease_to_idx[disease]))
        full_dataset.samples = new_samples
        full_dataset.targets = [y for _, y in new_samples]
        full_dataset.classes = disease_names
        full_dataset.class_to_idx = disease_to_idx

        # 保存与填充配置
        config.DISEASE_CLASS_NAMES = disease_names
        os.makedirs(config.SPLITS_DIR, exist_ok=True)
        with open(getattr(config, "DISEASE_CLASS_NAMES_JSON"), "w", encoding="utf-8") as f:
            json.dump(config.DISEASE_CLASS_NAMES, f, ensure_ascii=False)
        print(f"过滤后: {len(full_dataset)} 个样本，{len(config.DISEASE_CLASS_NAMES)} 个病害类别")
        print(f"病害示例：{config.DISEASE_CLASS_NAMES[:5]}...")

    # ===== 第二步：划分数据集（修复：基于路径而非索引）=====
    train_indices, val_indices, test_indices = _load_or_create_splits(full_dataset)

    # ===== 第三步：限制训练集每类样本数（可选）=====
    original_train_size = len(train_indices)
    train_indices = _limit_train_indices(
        train_indices,
        full_dataset.targets,
        getattr(config, "TRAIN_MAX_PER_CLASS", 0)
    )
    
    # 如果限制了样本，保存限制后的索引（覆盖原文件）
    if len(train_indices) < original_train_size:
        indices_file = os.path.join(config.SPLITS_DIR, "indices.json")
        with open(indices_file, 'w', encoding='utf-8') as f:
            json.dump({
                'train': train_indices,
                'val': val_indices,
                'test': test_indices,
                'note': f'训练集已限制为每类最多{config.TRAIN_MAX_PER_CLASS}个样本'
            }, f, indent=2)

    # ===== 第四步：创建数据集（使用TransformSubset避免重复加载）=====
    train_transform, val_test_transform = get_transforms()
    
    train_dataset = TransformSubset(full_dataset, train_indices, train_transform)
    val_dataset = TransformSubset(full_dataset, val_indices, val_test_transform)
    test_dataset = TransformSubset(full_dataset, test_indices, val_test_transform)

    print(f"\n最终数据集大小：")
    print(f"  训练集：{len(train_dataset)} 样本")
    print(f"  验证集：{len(val_dataset)} 样本")
    print(f"  测试集：{len(test_dataset)} 样本")
    
    return train_dataset, val_dataset, test_dataset


def get_data_loaders(task: str = "disease"):
    """创建DataLoader
    
    task: "species" 或 "disease"
    """
    train_dataset, val_dataset, test_dataset = load_dataset(task=task)

    # 类别均衡采样：提升小类精度并稳定训练
    sampler = None
    if getattr(config, "USE_BALANCED_SAMPLER", False):
        # TransformSubset已有dataset和indices属性
        base_dataset = train_dataset.dataset  # ImageFolder after filtering
        indices = train_dataset.indices
        labels = [base_dataset.targets[i] for i in indices]
        # 统计每类样本数
        class_counts = {}
        for y in labels:
            class_counts[y] = class_counts.get(y, 0) + 1
        # 权重=1/类计数
        weights = [1.0 / class_counts[y] for y in labels]
        sampler = WeightedRandomSampler(weights=weights, num_samples=len(indices), replacement=True)
        print(f"使用类别均衡采样器，类别数：{len(class_counts)}")

    train_loader = DataLoader(
        train_dataset,
        batch_size=config.BATCH_SIZE,
        shuffle=(sampler is None),
        num_workers=config.NUM_WORKERS,
        pin_memory=True if config.DEVICE.type == "cuda" else False,  # 加速GPU加载
        sampler=sampler
    )
    val_loader = DataLoader(
        val_dataset,
        batch_size=config.BATCH_SIZE,
        shuffle=False,
        num_workers=config.NUM_WORKERS,
        pin_memory=True if config.DEVICE.type == "cuda" else False
    )
    test_loader = DataLoader(
        test_dataset,
        batch_size=config.BATCH_SIZE,
        shuffle=False,
        num_workers=config.NUM_WORKERS,
        pin_memory=True if config.DEVICE.type == "cuda" else False
    )
    return train_loader, val_loader, test_loader


def _filter_classes(full_dataset):
    """根据 config.EXCLUDE_CLASSES 过滤数据集，并重映射标签为从0开始的连续索引。"""
    if not getattr(config, "EXCLUDE_CLASSES", None):
        return full_dataset

    orig_classes = full_dataset.classes
    orig_class_to_idx = full_dataset.class_to_idx
    idx_to_class = {v: k for k, v in orig_class_to_idx.items()}

    allowed_classes = [c for c in orig_classes if c not in config.EXCLUDE_CLASSES]
    new_class_to_idx = {c: i for i, c in enumerate(allowed_classes)}

    # 重建 samples 与 targets
    new_samples = []
    for path, label in full_dataset.samples:
        cls_name = idx_to_class[label]
        if cls_name in new_class_to_idx:
            new_label = new_class_to_idx[cls_name]
            new_samples.append((path, new_label))
    full_dataset.samples = new_samples
    full_dataset.targets = [label for _, label in new_samples]

    # 更新类映射
    full_dataset.classes = allowed_classes
    full_dataset.class_to_idx = new_class_to_idx
    return full_dataset
