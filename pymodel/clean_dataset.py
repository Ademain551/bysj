import os
import json
import argparse
from PIL import Image
from tqdm import tqdm
import config

IMAGE_EXTS = (".jpg", ".jpeg", ".png", ".bmp", ".JPG", ".JPEG", ".PNG", ".BMP")


def _is_bad_image(path: str, patterns, min_size: int, max_ratio: float):
    """返回不合格原因字符串；若合格返回 None。"""
    name = os.path.basename(path).lower()
    if any(k in name for k in patterns):
        return "pattern"
    try:
        with Image.open(path) as img:
            img = img.convert("RGB")
            w, h = img.size
            if min(w, h) < min_size:
                return "too_small"
            ratio = max(w / h, h / w)
            if ratio > max_ratio:
                return "bad_ratio"
    except Exception:
        return "read_error"
    return None


def clean_dataset(dry_run: bool = False):
    root = config.DATA_ROOT
    patterns = [p.lower() for p in getattr(config, "BAD_IMAGE_PATTERNS", [])]
    min_size = getattr(config, "MIN_IMAGE_SIZE", 80)
    max_ratio = getattr(config, "MAX_ASPECT_RATIO", 2.5)

    removed = []
    total = 0

    print(f"扫描目录：{root}")
    files = []
    for dirpath, _, filenames in os.walk(root):
        for fname in filenames:
            if fname.lower().endswith(IMAGE_EXTS):
                files.append(os.path.join(dirpath, fname))
    print(f"发现图片文件：{len(files)}")

    for fpath in tqdm(files, desc="清理中"):
        total += 1
        reason = _is_bad_image(fpath, patterns, min_size, max_ratio)
        if reason:
            removed.append({"path": fpath, "reason": reason})
            if not dry_run:
                try:
                    os.remove(fpath)
                except Exception as e:
                    removed[-1]["error"] = str(e)

    # 记录到 splits/removed_files.json
    os.makedirs(config.SPLITS_DIR, exist_ok=True)
    out_path = os.path.join(config.SPLITS_DIR, "removed_files.json")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(removed, f, ensure_ascii=False, indent=2)

    print(f"总计扫描：{total}，移除：{len(removed)}，保留：{total - len(removed)}")
    print(f"移除文件清单已写入：{out_path}")
    if dry_run:
        print("注意：dry-run 模式未实际删除文件。")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="清理裁切/拼接/异常图片（直接删除）")
    parser.add_argument("--dry-run", action="store_true", help="试运行，仅统计不删除")
    args = parser.parse_args()
    clean_dataset(dry_run=args.dry_run)