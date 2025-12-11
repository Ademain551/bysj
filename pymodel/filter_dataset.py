"""
数据集筛选工具 - 删除拼接和无效图片，保留旋转和正常图片
"""
import os
import sys
from PIL import Image
from tqdm import tqdm
import json
from datetime import datetime

# Windows控制台编码修复
if sys.platform == 'win32':
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

# 图片扩展名
IMAGE_EXTS = ('.jpg', '.jpeg', '.png', '.bmp', '.JPG', '.JPEG', '.PNG', '.BMP')

# 删除规则：包含这些关键词的文件名
BAD_KEYWORDS = [
    'crop', 'cropped', 'cut', 'patch', 'tile', 
    'mosaic', 'mixup', 'stitch', 'concat', 'collage',
    '裁剪', '裁切', '拼接', 'segment'
]

# 保留规则：包含这些关键词的文件名
KEEP_KEYWORDS = [
    'rotate', 'rotated', 'rotation', 'flip', 'flipped',
    '旋转', '翻转', 'normal', 'original'
]


def should_remove(filename, check_quality=True, img_path=None):
    """判断是否应该删除文件"""
    name_lower = filename.lower()
    
    # 检查保留关键词
    for keyword in KEEP_KEYWORDS:
        if keyword.lower() in name_lower:
            return False, f"保留（包含'{keyword}'）"
    
    # 检查删除关键词
    for keyword in BAD_KEYWORDS:
        if keyword.lower() in name_lower:
            return True, f"删除（包含'{keyword}'）"
    
    # 检查图片质量
    if check_quality and img_path:
        try:
            with Image.open(img_path) as img:
                img = img.convert('RGB')
                w, h = img.size
                
                # 尺寸过小
                if min(w, h) < 80:
                    return True, f"删除（尺寸过小: {w}x{h}）"
                
                # 长宽比异常
                ratio = max(w/h, h/w)
                if ratio > 2.5:
                    return True, f"删除（长宽比异常: {ratio:.1f}）"
        except:
            return True, "删除（读取失败）"
    
    return False, "保留（正常）"


def scan_dataset(data_root='data/PlantVillage', check_quality=True):
    """扫描数据集"""
    print("="*70)
    print("🔍 扫描数据集")
    print("="*70)
    
    stats = {'total': 0, 'remove': [], 'keep': []}
    
    if not os.path.exists(data_root):
        print(f"❌ 数据集路径不存在: {data_root}")
        return None
    
    classes = [d for d in os.listdir(data_root) 
               if os.path.isdir(os.path.join(data_root, d))]
    
    print(f"发现 {len(classes)} 个类别")
    print()
    
    for cls in tqdm(classes, desc="扫描进度"):
        cls_path = os.path.join(data_root, cls)
        images = [f for f in os.listdir(cls_path) if f.lower().endswith(IMAGE_EXTS)]
        
        for img in images:
            stats['total'] += 1
            img_path = os.path.join(cls_path, img)
            
            remove, reason = should_remove(img, check_quality, img_path)
            
            if remove:
                stats['remove'].append({
                    'path': img_path,
                    'class': cls,
                    'file': img,
                    'reason': reason
                })
            else:
                stats['keep'].append(img_path)
    
    return stats


def print_stats(stats):
    """打印统计信息"""
    print("\n" + "="*70)
    print("📊 统计结果")
    print("="*70)
    
    total = stats['total']
    remove_count = len(stats['remove'])
    keep_count = len(stats['keep'])
    
    print(f"\n总图片数: {total:,}")
    print(f"需删除: {remove_count:,} ({remove_count/total*100:.1f}%)")
    print(f"保留: {keep_count:,} ({keep_count/total*100:.1f}%)")
    
    # 统计删除原因
    reasons = {}
    for item in stats['remove']:
        reason = item['reason']
        reasons[reason] = reasons.get(reason, 0) + 1
    
    if reasons:
        print(f"\n删除原因分布:")
        for reason, count in sorted(reasons.items(), key=lambda x: x[1], reverse=True):
            print(f"  {reason:40s}: {count:6,} 张")
    
    # 按类别统计
    by_class = {}
    for item in stats['remove']:
        cls = item['class']
        by_class[cls] = by_class.get(cls, 0) + 1
    
    if by_class:
        print(f"\n删除最多的类别（前10）:")
        for cls, count in sorted(by_class.items(), key=lambda x: x[1], reverse=True)[:10]:
            print(f"  {cls[:50]:50s}: {count:5,} 张")


def delete_files(stats):
    """删除文件"""
    remove_list = stats['remove']
    
    if not remove_list:
        print("\n✓ 没有需要删除的文件")
        return
    
    print("\n" + "="*70)
    print("🗑️  开始删除")
    print("="*70)
    
    # 备份删除列表
    backup_dir = "data/removed_backup"
    os.makedirs(backup_dir, exist_ok=True)
    
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_file = os.path.join(backup_dir, f"removed_{timestamp}.json")
    
    with open(backup_file, 'w', encoding='utf-8') as f:
        json.dump(remove_list, f, indent=2, ensure_ascii=False)
    
    print(f"✓ 删除列表已备份: {backup_file}")
    
    # 删除文件
    success = 0
    failed = 0
    
    for item in tqdm(remove_list, desc="删除进度"):
        try:
            if os.path.exists(item['path']):
                os.remove(item['path'])
                success += 1
        except Exception as e:
            failed += 1
            print(f"\n⚠️ 删除失败: {item['path']}")
    
    print(f"\n✓ 删除完成: 成功 {success:,} 张, 失败 {failed:,} 张")


def main():
    import argparse
    
    parser = argparse.ArgumentParser(description="筛选数据集")
    parser.add_argument('--data-root', default='data/PlantVillage', help='数据集路径')
    parser.add_argument('--dry-run', action='store_true', help='试运行（不删除）')
    parser.add_argument('--no-quality-check', action='store_true', help='不检查质量')
    
    args = parser.parse_args()
    
    print("="*70)
    print("🎯 数据集筛选工具")
    print("="*70)
    print(f"\n配置:")
    print(f"  数据集: {args.data_root}")
    print(f"  检查质量: {'否' if args.no_quality_check else '是'}")
    print(f"  模式: {'试运行' if args.dry_run else '正式删除'}")
    print()
    
    # 扫描
    stats = scan_dataset(args.data_root, not args.no_quality_check)
    if not stats:
        return
    
    # 打印统计
    print_stats(stats)
    
    # 删除
    if not args.dry_run:
        print("\n" + "="*70)
        confirm = input("\n⚠️  确认删除？(输入 yes): ").strip().lower()
        
        if confirm == 'yes':
            delete_files(stats)
            print("\n✅ 完成！建议下一步:")
            print("  1. 删除旧划分: rd /s /q data\\splits")
            print("  2. 重新训练: python train.py")
        else:
            print("\n❌ 已取消")
    else:
        print("\n" + "="*70)
        print("ℹ️  试运行模式 - 未删除任何文件")
        print("\n正式删除请运行: python filter_dataset.py")


if __name__ == "__main__":
    main()

