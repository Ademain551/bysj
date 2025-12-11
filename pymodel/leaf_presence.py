"""
叶片存在性二分类模型与训练脚本
- 负类: Background_without_leaves
- 正类: 其他所有类别（含健康与病害）

输出:
- 权重保存到 config.LEAF_PRESENCE_MODEL_PATH
- 使用与验证一致的预处理，适合与现有分类模型级联
"""
import os
import random
import json
from typing import Tuple, List, Optional

import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import Dataset, DataLoader
from torchvision import models, transforms
from PIL import Image, ImageFile

# 限制底层线程，避免Windows上OpenMP/MKL冲突导致崩溃
os.environ.setdefault('OMP_NUM_THREADS', '1')
os.environ.setdefault('MKL_NUM_THREADS', '1')
torch.set_num_threads(1)
torch.set_num_interop_threads(1)
ImageFile.LOAD_TRUNCATED_IMAGES = True

import config
from utils.misc import safe_torch_load

# 固定随机种子，保证可复现
random.seed(42)
torch.manual_seed(42)


class BinaryImageDataset(Dataset):
    """从PlantVillage构建二分类数据集: 有叶片(1) vs 无叶片(0)。"""
    def __init__(self, roots: List[str], transform=None):
        self.samples = []  # (path, label)
        self.transform = transform
        for root in roots:
            for rel_path, label in self._scan_dir(root):
                self.samples.append((rel_path, label))

    def _scan_dir(self, root: str):
        bg_dir = os.path.join(root, 'Background_without_leaves')
        # 负类: 背景
        if os.path.isdir(bg_dir):
            for fname in os.listdir(bg_dir):
                fpath = os.path.join(bg_dir, fname)
                if self._is_image(fpath):
                    yield fpath, 0
        # 正类: 其他所有目录
        for cname in os.listdir(root):
            cpath = os.path.join(root, cname)
            if not os.path.isdir(cpath):
                continue
            if cname == 'Background_without_leaves':
                continue
            for fname in os.listdir(cpath):
                fpath = os.path.join(cpath, fname)
                if self._is_image(fpath):
                    yield fpath, 1

    def _is_image(self, path: str):
        ext = os.path.splitext(path)[1].lower()
        return ext in {'.jpg', '.jpeg', '.png', '.bmp'}

    def __len__(self):
        return len(self.samples)

    def __getitem__(self, idx):
        path, label = self.samples[idx]
        with Image.open(path) as img:
            img = img.convert('RGB')
        if self.transform:
            img = self.transform(img)
        return img, torch.tensor(label, dtype=torch.long)


def get_transforms() -> Tuple[transforms.Compose, transforms.Compose]:
    """与主任务一致的预处理，但训练阶段增加轻微增强。"""
    # 训练增强(轻-中)：裁剪/旋转/颜色/模糊
    train_tf = transforms.Compose([
        transforms.RandomResizedCrop(config.IMAGE_SIZE, scale=(0.75, 1.0), ratio=(0.8, 1.2)),
        transforms.RandomHorizontalFlip(0.5),
        transforms.RandomRotation(20),
        transforms.ColorJitter(0.3, 0.3, 0.3, 0.1),
        transforms.ToTensor(),
        transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
    ])
    # 验证/测试与主任务一致
    val_tf = transforms.Compose([
        transforms.Resize(int(config.IMAGE_SIZE * 1.14)),
        transforms.CenterCrop(config.IMAGE_SIZE),
        transforms.ToTensor(),
        transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
    ])
    return train_tf, val_tf


def _inference_transform() -> transforms.Compose:
    """推理阶段与主任务一致的预处理。"""

    return transforms.Compose([
        transforms.Resize(int(config.IMAGE_SIZE * 1.14)),
        transforms.CenterCrop(config.IMAGE_SIZE),
        transforms.ToTensor(),
        transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
    ])


def split_dataset(dataset: BinaryImageDataset, val_ratio=0.2):
    indices = list(range(len(dataset)))
    random.shuffle(indices)
    val_size = int(len(indices) * val_ratio)
    val_idx = indices[:val_size]
    train_idx = indices[val_size:]
    return train_idx, val_idx


def _subsample_binary(full: BinaryImageDataset, max_pos: int = 6000, max_neg: int = 1500):
    """对子采样以提升稳定性：限制正负样本数量并打乱。"""
    pos_paths = [s for s in full.samples if s[1] == 1]
    neg_paths = [s for s in full.samples if s[1] == 0]
    random.shuffle(pos_paths)
    random.shuffle(neg_paths)
    pos_keep = pos_paths[:max_pos] if max_pos > 0 else pos_paths
    neg_keep = neg_paths[:min(len(neg_paths), max_neg)] if max_neg > 0 else neg_paths
    kept = pos_keep + neg_keep
    random.shuffle(kept)
    full.samples = kept
    print(f"[SUBSAMPLE] 正类保留={len(pos_keep)} 负类保留={len(neg_keep)} 总计={len(kept)}")


class LeafPresenceModel(nn.Module):
    def __init__(self):
        super().__init__()
        # 使用ImageNet预训练的MobileNetV2
        try:
            from torchvision.models import MobileNet_V2_Weights
            self.backbone = models.mobilenet_v2(weights=MobileNet_V2_Weights.DEFAULT)
        except Exception:
            self.backbone = models.mobilenet_v2(pretrained=True)
        in_features = self.backbone.classifier[1].in_features
        self.backbone.classifier = nn.Sequential(
            nn.Dropout(0.3),
            nn.Linear(in_features, 2)
        )

    def forward(self, x):
        return self.backbone(x)


def train_leaf_presence(batch_size: int = 64, lr: float = 1e-4, epochs: int = 10):
    print('构建叶片存在性数据集...')
    train_tf, val_tf = get_transforms()
    full = BinaryImageDataset([config.DATA_ROOT], transform=None)

    # 统计类别
    labels = [lbl for _, lbl in full.samples]
    pos = sum(1 for l in labels if l == 1)
    neg = sum(1 for l in labels if l == 0)
    print(f'样本统计: 有叶片={pos}, 无叶片={neg}, 总计={len(labels)}')

    # 子采样提升稳定性（限制最大样本量）
    _subsample_binary(full, max_pos=6000, max_neg=1500)

    print('[STEP] 完成子采样，开始切分...')
    # 切分
    train_idx, val_idx = split_dataset(full, val_ratio=0.2)
    print(f'[STEP] 切分完成: train={len(train_idx)} val={len(val_idx)}')

    # 包装为带transform的数据集
    class _Subset(Dataset):
        def __init__(self, base, indices, tf):
            self.base = base
            self.indices = indices
            self.tf = tf
        def __len__(self):
            return len(self.indices)
        def __getitem__(self, i):
            path, label = self.base.samples[self.indices[i]]
            try:
                with Image.open(path) as img:
                    img = img.convert('RGB')
            except Exception:
                # 若图像损坏，返回纯黑图以不中断训练
                img = Image.new('RGB', (config.IMAGE_SIZE, config.IMAGE_SIZE), (0, 0, 0))
            img = self.tf(img)
            return img, torch.tensor(label, dtype=torch.long)

    train_ds = _Subset(full, train_idx, train_tf)
    val_ds = _Subset(full, val_idx, val_tf)

    # 计算类别权重（避免使用采样器，降低不稳定性）
    counts = {0: 0, 1: 0}
    for _, lbl in [full.samples[i] for i in train_idx]:
        counts[lbl] += 1
    w_neg = 1.0 / max(counts[0], 1)
    w_pos = 1.0 / max(counts[1], 1)
    class_weights = torch.tensor([w_neg, w_pos], dtype=torch.float32, device=config.DEVICE)
    print(f'[STEP] 类别计数: neg={counts[0]} pos={counts[1]} -> weights={w_neg:.6f},{w_pos:.6f}')

    # Windows上统一单线程，禁用pin_memory
    workers = 0
    train_loader = DataLoader(train_ds, batch_size=batch_size, shuffle=True, num_workers=workers, pin_memory=False)
    val_loader = DataLoader(val_ds, batch_size=batch_size, shuffle=False, num_workers=workers, pin_memory=False)
    print('[STEP] DataLoader 构建完成')

    # 模型与优化
    model = LeafPresenceModel().to(config.DEVICE)
    criterion = nn.CrossEntropyLoss(weight=class_weights, label_smoothing=0.05)
    optimizer = optim.Adam(model.parameters(), lr=lr, weight_decay=config.WEIGHT_DECAY)
    print('[STEP] 模型与优化器就绪，开始训练...')

    best_val_acc = 0.0
    for epoch in range(epochs):
        model.train()
        total, correct, total_loss = 0, 0, 0.0
        for imgs, labels in train_loader:
            imgs = imgs.to(config.DEVICE)
            labels = labels.to(config.DEVICE)
            optimizer.zero_grad()
            outputs = model(imgs)
            loss = criterion(outputs, labels)
            loss.backward()
            optimizer.step()
            total_loss += loss.item() * imgs.size(0)
            preds = outputs.argmax(1)
            correct += (preds == labels).sum().item()
            total += labels.size(0)
        train_acc = correct / max(total, 1)
        train_loss = total_loss / max(total, 1)

        # 验证
        model.eval()
        with torch.no_grad():
            v_total, v_correct = 0, 0
            for imgs, labels in val_loader:
                imgs = imgs.to(config.DEVICE)
                labels = labels.to(config.DEVICE)
                outputs = model(imgs)
                preds = outputs.argmax(1)
                v_correct += (preds == labels).sum().item()
                v_total += labels.size(0)
            val_acc = v_correct / max(v_total, 1)

        print(f'Epoch {epoch+1}/{epochs} - loss {train_loss:.4f} acc {train_acc:.4f} val_acc {val_acc:.4f}')

        if val_acc > best_val_acc:
            best_val_acc = val_acc
            torch.save(model.state_dict(), config.LEAF_PRESENCE_MODEL_PATH)
            print(f'✓ 已保存最优模型到 {config.LEAF_PRESENCE_MODEL_PATH} (val_acc={val_acc:.4f})')

    print('训练完成。最佳验证准确率:', best_val_acc)


_LEAF_MODEL: Optional[LeafPresenceModel] = None
_LEAF_MODEL_MTIME: Optional[float] = None
_LEAF_VAL_TRANSFORM = _inference_transform()


def _get_leaf_model() -> Optional[LeafPresenceModel]:
    """惰性加载并缓存叶片存在性模型，必要时自动热更新。"""

    global _LEAF_MODEL, _LEAF_MODEL_MTIME

    if not os.path.isfile(config.LEAF_PRESENCE_MODEL_PATH):
        _LEAF_MODEL = None
        _LEAF_MODEL_MTIME = None
        return None

    mtime = os.path.getmtime(config.LEAF_PRESENCE_MODEL_PATH)
    if _LEAF_MODEL is None or _LEAF_MODEL_MTIME != mtime:
        state = safe_torch_load(
            config.LEAF_PRESENCE_MODEL_PATH,
            map_location=config.DEVICE,
        )
        model = LeafPresenceModel().to(config.DEVICE)
        model.load_state_dict(state)
        model.eval()
        _LEAF_MODEL = model
        _LEAF_MODEL_MTIME = mtime

    return _LEAF_MODEL


def predict_leaf_presence(image: Image.Image) -> Tuple[float, float]:
    """返回 (no_leaf_prob, leaf_prob)。若模型不存在则返回(0.0, 1.0)。"""

    model = _get_leaf_model()
    if model is None:
        return 0.0, 1.0

    img_t = _LEAF_VAL_TRANSFORM(image).unsqueeze(0).to(config.DEVICE)
    with torch.no_grad():
        logits = model(img_t)
        probs = torch.softmax(logits, dim=1)[0]
        return float(probs[0].item()), float(probs[1].item())


def calibrate_thresholds(batch_size: int = 64, max_samples: int = 5000):
    """基于PlantVillage数据集对无叶片阈值进行校准，输出推荐阈值与指标。
    - 正类(1): 有叶片; 负类(0): 无叶片
    - 使用验证同款预处理(Resize+CenterCrop+Normalize)
    """
    from tqdm import tqdm
    # 加载模型
    if not os.path.isfile(config.LEAF_PRESENCE_MODEL_PATH):
        print(f"❌ 未找到模型: {config.LEAF_PRESENCE_MODEL_PATH}，请先训练")
        return
    model = LeafPresenceModel().to(config.DEVICE)
    state = safe_torch_load(
        config.LEAF_PRESENCE_MODEL_PATH,
        map_location=config.DEVICE,
    )
    model.load_state_dict(state)
    model.eval()

    # 数据集（验证/测试同款预处理）
    _, val_tf = get_transforms()
    full = BinaryImageDataset([config.DATA_ROOT], transform=val_tf)
    # 子采样避免过长评估
    if max_samples and len(full) > max_samples:
        idx = list(range(len(full)))
        random.shuffle(idx)
        keep = set(idx[:max_samples])
        full.samples = [full.samples[i] for i in keep]
        print(f"[CAL] 子采样至 {len(full.samples)} 样本用于校准")

    # DataLoader
    loader = DataLoader(full, batch_size=batch_size, shuffle=False, num_workers=0, pin_memory=False)

    # 收集概率
    bg_probs, leaf_probs = [], []
    with torch.no_grad():
        for imgs, labels in tqdm(loader, desc="校准采样", ncols=100):
            imgs = imgs.to(config.DEVICE)
            logits = model(imgs)
            probs = torch.softmax(logits, dim=1)
            no_leaf_batch = probs[:, 0].cpu().tolist()
            for p, lbl in zip(no_leaf_batch, labels.tolist()):
                if lbl == 0:
                    bg_probs.append(p)
                else:
                    leaf_probs.append(p)

    if not bg_probs or not leaf_probs:
        print("❌ 校准数据不足: 背景或叶片样本为空")
        return

    bg_probs.sort()
    leaf_probs.sort()

    # 评估不同阈值的表现，选择Youden J最大点
    def metrics_at(t: float):
        # TPR: 背景被判为无叶片的比例\nTNR: 叶片未被判为无叶片的比例
        tpr = (len([p for p in bg_probs if p >= t]) / len(bg_probs))
        tnr = (len([p for p in leaf_probs if p < t]) / len(leaf_probs))
        fpr = 1 - tnr
        fnr = 1 - tpr
        return tpr, tnr, fpr, fnr, (tpr + tnr - 1)

    candidates = [i / 100.0 for i in range(50, 100)]  # 0.50~0.99
    best_t, best_j, best_stats = None, -1.0, None
    for t in candidates:
        tpr, tnr, fpr, fnr, j = metrics_at(t)
        if j > best_j:
            best_j, best_t, best_stats = j, t, (tpr, tnr, fpr, fnr)

    # 建议严格阈值：背景概率的98百分位，尽量拦截纯背景
    def percentile(arr, q):
        k = max(0, min(len(arr) - 1, int(round(q * (len(arr) - 1)))))
        return arr[k]
    strict_t = percentile(bg_probs, 0.98)

    print("\n=== 阈值校准报告 ===")
    print(f"样本: 背景={len(bg_probs)} 叶片={len(leaf_probs)}")
    print(f"推荐阈值(Youden): t={best_t:.2f} \n  TPR(背景召回)={best_stats[0]:.3f} TNR(叶片特异)={best_stats[1]:.3f} \n  FPR={best_stats[2]:.3f} FNR={best_stats[3]:.3f} J={best_j:.3f}")
    print(f"严格阈值(98分位): t_strict={strict_t:.2f}")
    print("配置建议:")
    print(f"  LEAF_PRESENCE_THRESHOLD = {best_t:.2f}")
    print(f"  LEAF_PRESENCE_NO_LEAF_THRESHOLD = {max(strict_t, best_t):.2f}")
    print("  解释: 普通阈值用于一般背景过滤，严格阈值用于强背景拦截(两阶段)。")

if __name__ == '__main__':
    # 允许通过命令行训练或校准
    import argparse
    parser = argparse.ArgumentParser(description='叶片存在性模型：训练/校准')
    parser.add_argument('--epochs', type=int, default=10)
    parser.add_argument('--batch', type=int, default=64)
    parser.add_argument('--lr', type=float, default=1e-4)
    parser.add_argument('--calibrate', action='store_true', help='基于数据集校准无叶片阈值')
    args = parser.parse_args()
    if args.calibrate:
        calibrate_thresholds(batch_size=args.batch)
    else:
        train_leaf_presence(batch_size=args.batch, lr=args.lr, epochs=args.epochs)
