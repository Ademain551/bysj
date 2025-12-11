import os
import math
import random
import argparse
from typing import List, Tuple

from PIL import Image, ImageFile

ImageFile.LOAD_TRUNCATED_IMAGES = True

SUPPORTED_EXTS = {'.jpg', '.jpeg', '.png', '.bmp'}
AUGMENT_TAGS = ('_rot_', '_crop_', '_comp_')

BASE_DIR = r"e:\\Users\\LENOVO\\Desktop\\mtjbysj\\pymodel\\data\\PlantVillage"
SKIP_FOLDER_NAME = 'Background_without_leaves'

# 全局参数（由命令行赋值）
ARGS = None


def is_image_file(filename: str) -> bool:
    ext = os.path.splitext(filename)[1].lower()
    return ext in SUPPORTED_EXTS


def is_augmented_file(filename: str) -> bool:
    name = os.path.basename(filename)
    return any(tag in name for tag in AUGMENT_TAGS)


def list_original_images(folder: str) -> List[str]:
    files = []
    for fn in os.listdir(folder):
        if not is_image_file(fn):
            continue
        if is_augmented_file(fn):
            continue
        files.append(os.path.join(folder, fn))
    return files


def save_rotated_image(img_path: str, angle: int) -> None:
    try:
        with Image.open(img_path) as im:
            rotated = im.rotate(angle, expand=True, resample=Image.BICUBIC)
            base, ext = os.path.splitext(img_path)
            out_path = f"{base}_rot_{angle}{ext}"
            rotated.save(out_path)
            print(f"[旋转] {img_path} -> {out_path}（{angle}°）")
    except Exception as e:
        print(f"[旋转][错误] {img_path}: {e}")


def rotate_random_subset(folder: str, image_paths: List[str]) -> None:
    if not image_paths:
        return
    n = len(image_paths)
    ratio = getattr(ARGS, 'rotate_ratio', 1/3)
    k = max(1, math.floor(n * ratio)) if n >= 3 else 1
    subset = random.sample(image_paths, k)
    for img_path in subset:
        angle = random.randint(0, 359)
        save_rotated_image(img_path, angle)


def grid_sizes(total: int, parts: int) -> List[int]:
    base = total // parts
    rem = total % parts
    sizes = [base] * parts
    for i in range(rem):
        sizes[i] += 1
    return sizes


def crop_into_parts(img: Image.Image, n_parts: int) -> Tuple[List[Image.Image], dict]:
    W, H = img.size
    parts = []
    layout = {}

    if n_parts == 4:
        rows, cols = 2, 2
        widths = grid_sizes(W, cols)
        heights = grid_sizes(H, rows)
        y = 0
        for r in range(rows):
            x = 0
            for c in range(cols):
                w = widths[c]
                h = heights[r]
                box = (x, y, x + w, y + h)
                parts.append(img.crop(box))
                x += w
            y += h
        layout = {'type': 'grid', 'rows': rows, 'cols': cols, 'widths': widths, 'heights': heights}

    elif n_parts == 5:
        orient = random.choice(['vertical', 'horizontal'])
        if orient == 'vertical':
            widths = grid_sizes(W, 5)
            x = 0
            for w in widths:
                box = (x, 0, x + w, H)
                parts.append(img.crop(box))
                x += w
            layout = {'type': 'stripes', 'orient': 'vertical', 'sizes': widths, 'W': W, 'H': H}
        else:
            heights = grid_sizes(H, 5)
            y = 0
            for h in heights:
                box = (0, y, W, y + h)
                parts.append(img.crop(box))
                y += h
            layout = {'type': 'stripes', 'orient': 'horizontal', 'sizes': heights, 'W': W, 'H': H}

    elif n_parts == 6:
        if random.choice([True, False]):
            rows, cols = 2, 3
        else:
            rows, cols = 3, 2
        widths = grid_sizes(W, cols)
        heights = grid_sizes(H, rows)
        y = 0
        for r in range(rows):
            x = 0
            for c in range(cols):
                w = widths[c]
                h = heights[r]
                box = (x, y, x + w, y + h)
                parts.append(img.crop(box))
                x += w
            y += h
        layout = {'type': 'grid', 'rows': rows, 'cols': cols, 'widths': widths, 'heights': heights}

    else:
        raise ValueError("n_parts must be in [4, 5, 6]")

    return parts, layout


def compose_from_parts(parts: List[Image.Image], layout: dict, mode: str) -> Image.Image:
    if layout['type'] == 'grid':
        rows = layout['rows']
        cols = layout['cols']
        widths = layout['widths']
        heights = layout['heights']
        W = sum(widths)
        H = sum(heights)
        canvas = Image.new(mode, (W, H))
        order = parts.copy()
        random.shuffle(order)
        idx = 0
        y = 0
        for r in range(rows):
            x = 0
            for c in range(cols):
                p = order[idx]
                idx += 1
                canvas.paste(p, (x, y))
                x += widths[c]
            y += heights[r]
        return canvas

    elif layout['type'] == 'stripes':
        W = layout['W']
        H = layout['H']
        orient = layout['orient']
        sizes = layout['sizes']
        canvas = Image.new(mode, (W, H))
        order = parts.copy()
        random.shuffle(order)
        if orient == 'vertical':
            x = 0
            for i, w in enumerate(sizes):
                p = order[i]
                canvas.paste(p, (x, 0))
                x += w
        else:
            y = 0
            for i, h in enumerate(sizes):
                p = order[i]
                canvas.paste(p, (0, y))
                y += h
        return canvas

    else:
        raise ValueError("Unknown layout type")


def process_image(img_path: str) -> List[str]:
    base, ext = os.path.splitext(img_path)
    saved_part_paths: List[str] = []
    try:
        comp_path = f"{base}_comp{ext}"
        crop1_path = f"{base}_crop_1{ext}"
        # 续跑：如果已存在目标文件且不覆盖，则跳过
        if getattr(ARGS, 'resume', True) and os.path.exists(comp_path):
            print(f"[跳过] 已存在拼接图：{comp_path}")
            return []
        with Image.open(img_path) as im:
            im.load()
            n_min = getattr(ARGS, 'min_parts', 4)
            n_max = getattr(ARGS, 'max_parts', 6)
            n_parts = random.randint(n_min, n_max)
            parts, layout = crop_into_parts(im, n_parts)
            # 保存裁切块
            for i, part in enumerate(parts, start=1):
                out_path = f"{base}_crop_{i}{ext}"
                if os.path.exists(out_path) and not getattr(ARGS, 'overwrite', False):
                    # 不覆盖则跳过已有裁切块
                    continue
                part.save(out_path)
                saved_part_paths.append(out_path)
            print(f"[裁切] {img_path} -> {len(parts)} 个裁切块（保存 {len(saved_part_paths)}）")
            # 单图拼接（随机顺序）
            if os.path.exists(comp_path) and not getattr(ARGS, 'overwrite', False):
                print(f"[跳过] 已存在拼接图：{comp_path}")
            else:
                collage = compose_from_parts(parts, layout, im.mode)
                collage.save(comp_path)
                print(f"[拼接] {img_path} -> {comp_path}")
    except Exception as e:
        print(f"[裁切/拼接][错误] {img_path}: {e}")
    return saved_part_paths


def compose_global_mosaic(part_paths: List[str], folder: str) -> None:
    if not part_paths:
        return
    # 采样以控制规模
    max_parts = getattr(ARGS, 'mosaic_max_parts', 1024)
    if len(part_paths) > max_parts:
        part_paths = random.sample(part_paths, max_parts)
    # 目标画布最大边长限制
    max_size = getattr(ARGS, 'mosaic_max_size', 4096)
    n = len(part_paths)
    cols = max(1, math.ceil(math.sqrt(n)))
    rows = math.ceil(n / cols)
    # 瓦片尺寸自适应，保证不超过 max_size
    tile_w = max(16, min(128, max_size // cols))
    tile_h = tile_w
    canvas_w = cols * tile_w
    canvas_h = rows * tile_h
    canvas = Image.new('RGB', (canvas_w, canvas_h))

    order = part_paths.copy()
    random.shuffle(order)

    idx = 0
    for r in range(rows):
        for c in range(cols):
            if idx >= n:
                break
            path = order[idx]
            idx += 1
            try:
                with Image.open(path) as im:
                    im = im.convert('RGB')
                    im = im.resize((tile_w, tile_h), resample=Image.BICUBIC)
                    canvas.paste(im, (c * tile_w, r * tile_h))
            except Exception as e:
                print(f"[全局拼接][错误] {path}: {e}")

    out_name = f"{os.path.basename(folder)}_comp_all.jpg"
    out_path = os.path.join(folder, out_name)
    if os.path.exists(out_path) and not getattr(ARGS, 'overwrite', False):
        print(f"[跳过] 已存在全局拼接：{out_path}")
        return
    if os.path.exists(out_path):
        out_path = os.path.join(folder, f"{os.path.basename(folder)}_comp_all_{random.randint(0,9999)}.jpg")
    canvas.save(out_path)
    print(f"[全局拼接] {folder} -> {out_path}（{n} 块，{cols}×{rows}，单块{tile_w}×{tile_h}）")


def process_folder(folder: str) -> None:
    print(f"\n[目录] 处理 {folder}")
    images = list_original_images(folder)
    if not images:
        print("[目录] 未发现原始图片，跳过")
        return
    # 1/3随机旋转（可调）
    rotate_random_subset(folder, images)
    # 裁切并单图拼接，同时收集所有裁切块供全局拼图
    all_parts: List[str] = []
    for img_path in images:
        parts = process_image(img_path)
        all_parts.extend(parts)
    # 全局拼接（将该子文件夹的所有裁切块随机组合成一张图）
    compose_global_mosaic(all_parts, folder)


def get_args():
    parser = argparse.ArgumentParser(description="PlantVillage 数据增强：旋转、裁切与拼接")
    parser.add_argument('--base_dir', type=str, default=BASE_DIR, help='待处理的根目录（包含类别子文件夹）')
    parser.add_argument('--skip_folder', type=str, default=SKIP_FOLDER_NAME, help='需要跳过的子文件夹名')
    parser.add_argument('--rotate_ratio', type=float, default=1/3, help='随机旋转的比例（0-1）')
    parser.add_argument('--min_parts', type=int, default=4, help='每张图裁切最少块数')
    parser.add_argument('--max_parts', type=int, default=6, help='每张图裁切最多块数')
    parser.add_argument('--mosaic_max_parts', type=int, default=1024, help='全局拼接最多使用的裁切块数量')
    parser.add_argument('--mosaic_max_size', type=int, default=4096, help='全局拼接最大画布边长')
    parser.add_argument('--overwrite', action='store_true', help='覆盖已存在的输出文件')
    parser.add_argument('--resume', action='store_true', help='续跑：跳过已存在拼接图的图片')
    parser.add_argument('--seed', type=int, default=42, help='随机种子')
    return parser.parse_args()


def main():
    global ARGS
    ARGS = get_args()
    random.seed(ARGS.seed)
    base_dir = ARGS.base_dir
    skip_name = ARGS.skip_folder
    if not os.path.isdir(base_dir):
        print(f"[错误] 基础目录不存在: {base_dir}")
        return
    count = 0
    for entry in os.listdir(base_dir):
        folder = os.path.join(base_dir, entry)
        if not os.path.isdir(folder):
            continue
        if entry == skip_name:
            print(f"[跳过] {folder}")
            continue
        process_folder(folder)
        count += 1
    print(f"\n[完成] 共处理 {count} 个子文件夹。")


if __name__ == '__main__':
    random.seed(42)
    main()