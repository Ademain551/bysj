"""
训练和测试工具函数
优化：添加异常处理、checkpoint保存、日志系统
"""
import os
import torch
import torch.nn as nn
import torch.optim as optim
from tqdm import tqdm
import numpy as np
import config
from utils.logger import TrainingLogger
from utils.misc import safe_torch_load

try:
    from torch.amp import GradScaler, autocast  # 新API，兼容未来版本
    HAS_TORCH_AMP = True
except Exception:
    HAS_TORCH_AMP = False


def train_one_epoch(model, train_loader, criterion, optimizer, scaler=None, logger=None):
    """训练一个epoch（支持AMP混合精度）
    
    Args:
        model: 模型
        train_loader: 训练数据加载器
        criterion: 损失函数
        optimizer: 优化器
        scaler: AMP梯度缩放器
        logger: 日志记录器
        
    Returns:
        epoch_loss: 平均损失
        epoch_acc: 平均准确率
    """
    model.train()  # 训练模式（启用dropout等）
    total_loss = 0.0
    total_correct = 0
    total_samples = 0

    use_amp = (scaler is not None) and (HAS_TORCH_AMP)

    # 进度条显示
    pbar = tqdm(train_loader, desc="训练中", ncols=100)
    try:
        for batch_idx, (images, labels) in enumerate(pbar):
            try:
                # 数据移到设备
                images = images.to(config.DEVICE, non_blocking=True)
                labels = labels.to(config.DEVICE, non_blocking=True)

                optimizer.zero_grad()  # 清零梯度

                if use_amp:
                    with autocast("cuda"):
                        outputs = model(images)
                        loss = criterion(outputs, labels)
                    scaler.scale(loss).backward()
                    scaler.step(optimizer)
                    scaler.update()
                else:
                    outputs = model(images)
                    loss = criterion(outputs, labels)
                    loss.backward()
                    optimizer.step()

                # 统计指标
                total_loss += loss.item() * images.size(0)
                _, predicted = torch.max(outputs.data, 1)
                total_correct += (predicted == labels).sum().item()
                total_samples += labels.size(0)

                # 更新进度条
                current_acc = total_correct / total_samples
                pbar.set_postfix(**{
                    "损失": f"{total_loss / total_samples:.4f}",
                    "准确率": f"{current_acc:.4f}"
                })
            
            except RuntimeError as e:
                if "out of memory" in str(e):
                    if logger:
                        logger.error(f"GPU内存不足在batch {batch_idx}，尝试清理缓存")
                    torch.cuda.empty_cache()
                    continue
                else:
                    raise e
                    
    except Exception as e:
        if logger:
            logger.error(f"训练epoch时发生错误: {str(e)}")
        raise e
    finally:
        pbar.close()

    epoch_loss = total_loss / total_samples if total_samples > 0 else 0.0
    epoch_acc = total_correct / total_samples if total_samples > 0 else 0.0
    return epoch_loss, epoch_acc


def validate(model, val_loader, criterion, logger=None):
    """验证模型
    
    Args:
        model: 模型
        val_loader: 验证数据加载器
        criterion: 损失函数
        logger: 日志记录器
        
    Returns:
        val_loss: 验证损失
        val_acc: 验证准确率
    """
    model.eval()  # 评估模式（关闭dropout等）
    total_loss = 0.0
    total_correct = 0
    total_samples = 0

    with torch.no_grad():  # 关闭梯度计算，加速并节省内存
        pbar = tqdm(val_loader, desc="验证中", ncols=100)
        try:
            for images, labels in pbar:
                images = images.to(config.DEVICE, non_blocking=True)
                labels = labels.to(config.DEVICE, non_blocking=True)

                outputs = model(images)
                loss = criterion(outputs, labels)

                total_loss += loss.item() * images.size(0)
                _, predicted = torch.max(outputs.data, 1)
                total_correct += (predicted == labels).sum().item()
                total_samples += labels.size(0)

                current_acc = total_correct / total_samples
                pbar.set_postfix(**{
                    "损失": f"{total_loss / total_samples:.4f}",
                    "准确率": f"{current_acc:.4f}"
                })
        except Exception as e:
            if logger:
                logger.error(f"验证时发生错误: {str(e)}")
            raise e
        finally:
            pbar.close()

    val_loss = total_loss / total_samples if total_samples > 0 else 0.0
    val_acc = total_correct / total_samples if total_samples > 0 else 0.0
    return val_loss, val_acc


def save_checkpoint(model, optimizer, scheduler, epoch, best_val_acc, history, 
                   checkpoint_path, logger=None):
    """保存训练checkpoint
    
    Args:
        model: 模型
        optimizer: 优化器
        scheduler: 学习率调度器
        epoch: 当前epoch
        best_val_acc: 最佳验证准确率
        history: 训练历史
        checkpoint_path: checkpoint保存路径
        logger: 日志记录器
    """
    try:
        checkpoint = {
            'epoch': epoch,
            'model_state_dict': model.state_dict(),
            'optimizer_state_dict': optimizer.state_dict(),
            'scheduler_state_dict': scheduler.state_dict() if scheduler else None,
            'best_val_acc': best_val_acc,
            'history': history,
            'config': {
                'batch_size': config.BATCH_SIZE,
                'learning_rate': config.LEARNING_RATE,
                'num_classes': len(config.CLASS_NAMES) if config.CLASS_NAMES else 0
            }
        }
        
        # 确保目录存在
        os.makedirs(os.path.dirname(checkpoint_path), exist_ok=True)
        
        torch.save(checkpoint, checkpoint_path)
        
        if logger:
            logger.info(f"Checkpoint已保存: {checkpoint_path}")
    except Exception as e:
        if logger:
            logger.error(f"保存checkpoint失败: {str(e)}")
        # 不抛出异常，避免中断训练


def load_checkpoint(model, checkpoint_path, optimizer=None, scheduler=None, logger=None):
    """加载训练checkpoint
    
    Args:
        model: 模型
        checkpoint_path: checkpoint路径
        optimizer: 优化器（可选）
        scheduler: 学习率调度器（可选）
        logger: 日志记录器
        
    Returns:
        start_epoch: 开始epoch
        best_val_acc: 最佳验证准确率
        history: 训练历史
    """
    try:
        # weights_only=False 允许加载优化器等完整状态
        checkpoint = safe_torch_load(
            checkpoint_path,
            map_location=config.DEVICE,
        )
        
        model.load_state_dict(checkpoint['model_state_dict'])
        
        if optimizer and 'optimizer_state_dict' in checkpoint:
            optimizer.load_state_dict(checkpoint['optimizer_state_dict'])
        
        if scheduler and 'scheduler_state_dict' in checkpoint and checkpoint['scheduler_state_dict']:
            scheduler.load_state_dict(checkpoint['scheduler_state_dict'])
        
        start_epoch = checkpoint.get('epoch', 0) + 1
        best_val_acc = checkpoint.get('best_val_acc', 0.0)
        history = checkpoint.get('history', {
            "train_loss": [], "train_acc": [],
            "val_loss": [], "val_acc": []
        })
        
        if logger:
            logger.info(f"成功加载checkpoint: {checkpoint_path}")
            logger.info(f"从Epoch {start_epoch}继续训练，最佳验证准确率: {best_val_acc:.4f}")
        
        return start_epoch, best_val_acc, history
        
    except FileNotFoundError:
        if logger:
            logger.warning(f"未找到checkpoint: {checkpoint_path}，从头开始训练")
        return 0, 0.0, {"train_loss": [], "train_acc": [], "val_loss": [], "val_acc": []}
    except Exception as e:
        if logger:
            logger.error(f"加载checkpoint失败: {str(e)}，从头开始训练")
        return 0, 0.0, {"train_loss": [], "train_acc": [], "val_loss": [], "val_acc": []}


def train_model(model, train_loader, val_loader, resume_from_checkpoint=False):
    """完整训练流程（AMP、标签平滑、分组学习率、早停、checkpoint）
    
    Args:
        model: 模型
        train_loader: 训练数据加载器
        val_loader: 验证数据加载器
        resume_from_checkpoint: 是否从checkpoint恢复训练
        
    Returns:
        history: 训练历史记录
    """
    # 初始化日志记录器
    logger = TrainingLogger()
    
    try:
        logger.info("="*60)
        logger.info("开始训练流程")
        logger.info(f"设备: {config.DEVICE}")
        logger.info(f"批次大小: {config.BATCH_SIZE}")
        logger.info(f"总轮次: {config.EPOCHS}")
        logger.info(f"学习率: {config.LEARNING_RATE}")
        logger.info(f"类别数: {len(config.CLASS_NAMES)}")
        logger.info("="*60)
        
        # 区分分类头与特征层参数，设置分组学习率
        head_params = []
        feature_params = []
        for name, p in model.named_parameters():
            if not p.requires_grad:
                continue
            if "mobilenet.classifier" in name:
                head_params.append(p)
            else:
                feature_params.append(p)

        logger.info(f"可训练参数: 特征层 {len(feature_params)}, 分类头 {len(head_params)}")

        # 定义损失函数（标签平滑提升泛化）
        criterion = nn.CrossEntropyLoss(label_smoothing=getattr(config, "LABEL_SMOOTHING", 0.0))

        # 优化器（分类头较大学习率，特征层较小）
        param_groups = []
        if feature_params:
            param_groups.append({
                "params": feature_params,
                "lr": config.LEARNING_RATE * getattr(config, "FEATURE_LR_MULTIPLIER", 0.5)
            })
        if head_params:
            param_groups.append({
                "params": head_params,
                "lr": config.LEARNING_RATE * getattr(config, "HEAD_LR_MULTIPLIER", 3.0)
            })
        optimizer = optim.Adam(param_groups, weight_decay=config.WEIGHT_DECAY)

        # AMP混合精度（仅在CUDA可用且启用时使用）
        use_amp = (config.DEVICE.type == "cuda") and getattr(config, "USE_AMP", False) and HAS_TORCH_AMP
        scaler = GradScaler("cuda") if use_amp else None
        if use_amp:
            logger.info("启用混合精度训练(AMP)")

        # 学习率调度器（动态调整学习率）
        scheduler = optim.lr_scheduler.ReduceLROnPlateau(
            optimizer, mode='min', factor=0.5, 
            patience=getattr(config, "LR_SCHEDULER_PATIENCE", 3)
            # verbose参数已废弃，使用get_last_lr()替代
        )

        # checkpoint路径
        checkpoint_dir = os.path.dirname(config.MODEL_SAVE_PATH)
        checkpoint_path = os.path.join(checkpoint_dir, "last_checkpoint.pth")

        # 尝试恢复checkpoint
        start_epoch = 0
        best_val_acc = 0.0
        history = {"train_loss": [], "train_acc": [], "val_loss": [], "val_acc": []}
        epochs_no_improve = 0
        
        if resume_from_checkpoint and os.path.exists(checkpoint_path):
            start_epoch, best_val_acc, history = load_checkpoint(
                model, checkpoint_path, optimizer, scheduler, logger
            )

        # 训练循环
        for epoch in range(start_epoch, config.EPOCHS):
            logger.info("")
            logger.info(f"Epoch {epoch + 1}/{config.EPOCHS}")
            logger.info("-" * 60)

            # 训练
            train_loss, train_acc = train_one_epoch(
                model, train_loader, criterion, optimizer, scaler=scaler, logger=logger
            )
            
            # 验证
            val_loss, val_acc = validate(model, val_loader, criterion, logger=logger)

            # 记录指标
            history["train_loss"].append(train_loss)
            history["train_acc"].append(train_acc)
            history["val_loss"].append(val_loss)
            history["val_acc"].append(val_acc)

            # 学习率调度
            old_lr = optimizer.param_groups[0]['lr']
            scheduler.step(val_loss)
            current_lr = optimizer.param_groups[0]['lr']
            
            # 检查学习率是否被调整
            if current_lr < old_lr:
                logger.info(f"学习率已降低: {old_lr:.6f} → {current_lr:.6f}")

            # 记录到日志
            logger.log_epoch(epoch + 1, train_loss, train_acc, val_loss, val_acc, current_lr)

            # 保存checkpoint（每个epoch）
            save_checkpoint(
                model, optimizer, scheduler, epoch, best_val_acc, history,
                checkpoint_path, logger
            )

            # 保存最佳模型（基于验证集准确率）
            if val_acc > best_val_acc:
                best_val_acc = val_acc
                torch.save(model.state_dict(), config.MODEL_SAVE_PATH)
                logger.log_best_model(epoch + 1, best_val_acc)
                epochs_no_improve = 0
            else:
                epochs_no_improve += 1
                logger.info(f"验证准确率无提升：连续 {epochs_no_improve}/{config.EARLY_STOP_PATIENCE} 次")
                
                # 早停检查
                if epochs_no_improve >= getattr(config, "EARLY_STOP_PATIENCE", 0):
                    logger.log_early_stop(epoch + 1, epochs_no_improve)
                    break

        logger.log_training_complete(best_val_acc, epoch + 1)
        
        return history

    except KeyboardInterrupt:
        logger.warning("训练被用户中断(Ctrl+C)")
        logger.info("正在保存当前进度...")
        # 保存中断时的checkpoint
        save_checkpoint(
            model, optimizer, scheduler, epoch, best_val_acc, history,
            checkpoint_path, logger
        )
        logger.info("进度已保存，可使用resume_from_checkpoint=True恢复训练")
        raise
        
    except Exception as e:
        logger.log_exception(e)
        raise e


def test_model(model, test_loader):
    """测试模型在测试集上的性能
    
    Args:
        model: 模型
        test_loader: 测试数据加载器
        
    Returns:
        test_acc: 测试准确率
        all_preds: 所有预测结果
        all_labels: 所有真实标签
    """
    from utils.logger import setup_simple_logger
    logger = setup_simple_logger("TestModel")
    
    try:
        logger.info(f"加载最佳模型: {config.MODEL_SAVE_PATH}")
        
        # 使用 map_location 保证在 CPU/GPU 环境都能加载
        if not os.path.exists(config.MODEL_SAVE_PATH):
            raise FileNotFoundError(f"未找到模型文件: {config.MODEL_SAVE_PATH}")
        
        # weights_only=False 允许加载完整模型状态    
        state_dict = safe_torch_load(
            config.MODEL_SAVE_PATH,
            map_location=config.DEVICE,
        )
        model.load_state_dict(state_dict)
        model.eval()

        total_correct = 0
        total_samples = 0
        all_preds = []
        all_labels = []

        with torch.no_grad():
            pbar = tqdm(test_loader, desc="测试中", ncols=100)
            for images, labels in pbar:
                images = images.to(config.DEVICE, non_blocking=True)
                labels = labels.to(config.DEVICE, non_blocking=True)

                outputs = model(images)
                _, predicted = torch.max(outputs.data, 1)

                total_correct += (predicted == labels).sum().item()
                total_samples += labels.size(0)

                # 收集所有预测和标签（用于后续混淆矩阵）
                all_preds.extend(predicted.cpu().numpy())
                all_labels.extend(labels.cpu().numpy())

                pbar.set_postfix(**{"准确率": f"{total_correct / total_samples:.4f}"})
            
            pbar.close()

        test_acc = total_correct / total_samples
        logger.info(f"测试集准确率: {test_acc:.4f}")
        
        return test_acc, np.array(all_preds), np.array(all_labels)
        
    except Exception as e:
        logger.error(f"测试过程中发生错误: {str(e)}")
        raise e
