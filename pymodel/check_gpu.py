"""
GPU使用情况检查工具
"""
import torch
import sys
import io

# 修复Windows编码
if sys.platform == 'win32':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

print("="*70)
print("GPU 信息检查")
print("="*70)

# 检查CUDA
print(f"\n是否可用CUDA: {torch.cuda.is_available()}")

if torch.cuda.is_available():
    print(f"CUDA版本: {torch.version.cuda}")
    print(f"PyTorch版本: {torch.__version__}")
    print(f"\nGPU数量: {torch.cuda.device_count()}")
    
    for i in range(torch.cuda.device_count()):
        print(f"\nGPU {i}:")
        print(f"  名称: {torch.cuda.get_device_name(i)}")
        print(f"  总显存: {torch.cuda.get_device_properties(i).total_memory / 1024**3:.2f} GB")
        print(f"  已分配: {torch.cuda.memory_allocated(i) / 1024**3:.2f} GB")
        print(f"  已缓存: {torch.cuda.memory_reserved(i) / 1024**3:.2f} GB")
    
    # 测试GPU速度
    print("\n" + "="*70)
    print("GPU 性能测试")
    print("="*70)
    
    import time
    
    # 小批次测试
    print("\n测试配置1: BATCH_SIZE=32 (当前配置)")
    x = torch.randn(32, 3, 224, 224).cuda()
    model = torch.nn.Conv2d(3, 64, 3).cuda()
    
    # 预热
    for _ in range(10):
        _ = model(x)
    
    torch.cuda.synchronize()
    start = time.time()
    for _ in range(100):
        _ = model(x)
    torch.cuda.synchronize()
    elapsed = time.time() - start
    
    print(f"  100次前向传播耗时: {elapsed:.3f}秒")
    print(f"  速度: {100/elapsed:.1f} it/s")
    
    # 大批次测试
    print("\n测试配置2: BATCH_SIZE=64")
    x = torch.randn(64, 3, 224, 224).cuda()
    
    torch.cuda.synchronize()
    start = time.time()
    for _ in range(100):
        _ = model(x)
    torch.cuda.synchronize()
    elapsed = time.time() - start
    
    print(f"  100次前向传播耗时: {elapsed:.3f}秒")
    print(f"  速度: {100/elapsed:.1f} it/s")
    
    print("\n" + "="*70)
    print("建议")
    print("="*70)
    
    print("\nGPU利用率低的可能原因:")
    print("  1. NUM_WORKERS=0 导致数据加载慢（CPU瓶颈）")
    print("  2. BATCH_SIZE太小（32可以尝试增加到64）")
    print("  3. 图片预处理在CPU上进行")
    print("\n优化建议:")
    print("  1. 增加BATCH_SIZE到64或128（如果显存足够）")
    print("  2. 减少数据增强操作")
    print("  3. 使用更大的模型（当前MobileNetV2较小）")
    
else:
    print("\nCUDA不可用，正在使用CPU")
    print("解决方案:")
    print("  1. 检查是否安装了GPU版本的PyTorch")
    print("  2. 检查NVIDIA驱动是否正确安装")
    print("  3. 运行 nvidia-smi 查看GPU状态")

print("\n" + "="*70)

