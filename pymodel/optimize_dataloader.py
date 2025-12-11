"""
优化数据加载 - 缓存预处理后的数据
"""
import os
import torch
from torchvision import transforms
from PIL import Image
import pickle
from tqdm import tqdm
import config

def preprocess_and_cache():
    """预处理所有图片并缓存到磁盘"""
    print("="*70)
    print("预处理数据集并缓存")
    print("="*70)
    
    cache_dir = "data/preprocessed_cache"
    os.makedirs(cache_dir, exist_ok=True)
    
    # 定义transform
    transform = transforms.Compose([
        transforms.Resize((config.IMAGE_SIZE, config.IMAGE_SIZE)),
        transforms.ToTensor(),
        transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
    ])
    
    from utils.data_loader import get_data_loaders
    
    print("\n正在加载数据集...")
    train_loader, val_loader, test_loader = get_data_loaders()
    
    print("\n预处理训练集...")
    cache_dataset(train_loader, os.path.join(cache_dir, "train.pkl"))
    
    print("\n预处理验证集...")
    cache_dataset(val_loader, os.path.join(cache_dir, "val.pkl"))
    
    print("\n预处理测试集...")
    cache_dataset(test_loader, os.path.join(cache_dir, "test.pkl"))
    
    print("\n✓ 完成！缓存已保存到:", cache_dir)

def cache_dataset(loader, cache_path):
    """缓存数据集"""
    data = []
    for images, labels in tqdm(loader):
        for i in range(len(images)):
            data.append((images[i].cpu(), labels[i].item()))
    
    with open(cache_path, 'wb') as f:
        pickle.dump(data, f)
    
    print(f"  保存了 {len(data)} 个样本")

if __name__ == "__main__":
    preprocess_and_cache()

