import torch
import torch.nn as nn
from torchvision import models
import config

# 兼容不同版本的 torchvision：优先使用新权重枚举，失败则回退到 pretrained=True
try:
    from torchvision.models import MobileNet_V2_Weights  # torchvision >= 0.13
    HAS_WEIGHTS_ENUM = True
except Exception:
    HAS_WEIGHTS_ENUM = False


class CropDiseaseModel(nn.Module):
    def __init__(self, num_classes):
        super(CropDiseaseModel, self).__init__()
        # 加载预训练的MobileNetV2（基于ImageNet）
        if HAS_WEIGHTS_ENUM:
            self.mobilenet = models.mobilenet_v2(weights=MobileNet_V2_Weights.DEFAULT)
        else:
            self.mobilenet = models.mobilenet_v2(pretrained=True)

        # 获取冻结策略
        freeze_strategy = getattr(config, "FREEZE_STRATEGY", "partial")
        
        if freeze_strategy == "none":
            # 策略1: 不冻结任何层（全部微调）
            print("冻结策略: 全部微调 (所有层可训练)")
            # 所有参数默认已经是可训练的
            
        elif freeze_strategy == "partial":
            # 策略2: 部分冻结（推荐）
            # 冻结早期层（低级特征），解冻后期层（高级特征）
            total_blocks = len(self.mobilenet.features)
            unfreeze_blocks = getattr(config, "UNFREEZE_LAST_BLOCKS", 10)
            freeze_blocks = total_blocks - unfreeze_blocks
            
            print(f"冻结策略: 部分冻结")
            print(f"  - MobileNetV2总共 {total_blocks} 个特征块")
            print(f"  - 冻结前 {freeze_blocks} 个块（低级特征）")
            print(f"  - 解冻后 {unfreeze_blocks} 个块（高级特征）")
            
            # 先冻结所有特征层
            for param in self.mobilenet.features.parameters():
                param.requires_grad = False
            
            # 解冻后N个块
            start_idx = max(0, total_blocks - unfreeze_blocks)
            for i in range(start_idx, total_blocks):
                for param in self.mobilenet.features[i].parameters():
                    param.requires_grad = True
                    
        elif freeze_strategy == "bn_only":
            # 策略3: 只冻结BatchNorm层
            print("冻结策略: 只冻结BatchNorm层")
            for module in self.mobilenet.modules():
                if isinstance(module, nn.BatchNorm2d):
                    module.eval()
                    for param in module.parameters():
                        param.requires_grad = False
                        
        else:  # "all" - 完全冻结
            # 策略4: 冻结所有特征层（只训练分类头）
            print("冻结策略: 完全冻结 (仅训练分类头)")
            for param in self.mobilenet.features.parameters():
                param.requires_grad = False

        # 替换最后一层全连接层为自定义类别数
        in_features = self.mobilenet.classifier[1].in_features
        dropout_rate = getattr(config, "CLASSIFIER_DROPOUT", 0.2)
        self.mobilenet.classifier = nn.Sequential(
            nn.Dropout(dropout_rate),
            nn.Linear(in_features, num_classes)
        )
        
        # 统计可训练参数
        total_params = sum(p.numel() for p in self.parameters())
        trainable_params = sum(p.numel() for p in self.parameters() if p.requires_grad)
        frozen_params = total_params - trainable_params
        
        print(f"\n参数统计:")
        print(f"  - 总参数: {total_params:,}")
        print(f"  - 可训练参数: {trainable_params:,} ({trainable_params/total_params*100:.1f}%)")
        print(f"  - 冻结参数: {frozen_params:,} ({frozen_params/total_params*100:.1f}%)")

    def forward(self, x):
        return self.mobilenet(x)


def get_model(num_classes: int | None = None):
    """创建模型并移动到指定设备
    
    Args:
        num_classes: 类别数；若为None，则回退到单模型 config.CLASS_NAMES 长度
    """
    if num_classes is None:
        num_classes = len(config.CLASS_NAMES)
    model = CropDiseaseModel(num_classes=num_classes)
    model = model.to(config.DEVICE)
    print(f"模型创建完成，类别数：{num_classes}，设备：{config.DEVICE}")
    return model
