"""
日志工具模块：提供统一的日志记录、指标追踪和可视化功能
"""
import os
import json
import logging
from datetime import datetime
from typing import Dict, Any, Optional
import config


class TrainingLogger:
    """训练日志记录器：记录训练过程、保存指标、生成日志文件"""
    
    def __init__(self, log_dir: Optional[str] = None, experiment_name: Optional[str] = None):
        """
        初始化日志记录器
        
        Args:
            log_dir: 日志保存目录，默认为 saved_models/logs
            experiment_name: 实验名称，默认使用时间戳
        """
        # 设置日志目录
        if log_dir is None:
            log_dir = os.path.join(os.path.dirname(config.MODEL_SAVE_PATH), "logs")
        os.makedirs(log_dir, exist_ok=True)
        self.log_dir = log_dir
        
        # 设置实验名称
        if experiment_name is None:
            experiment_name = datetime.now().strftime("%Y%m%d_%H%M%S")
        self.experiment_name = experiment_name
        
        # 创建实验专属目录
        self.exp_dir = os.path.join(log_dir, experiment_name)
        os.makedirs(self.exp_dir, exist_ok=True)
        
        # 设置文件日志
        self.log_file = os.path.join(self.exp_dir, "training.log")
        self.metrics_file = os.path.join(self.exp_dir, "metrics.json")
        self.config_file = os.path.join(self.exp_dir, "config.json")
        
        # 配置Python logging
        self._setup_logging()
        
        # 指标存储
        self.metrics_history = {
            "train_loss": [],
            "train_acc": [],
            "val_loss": [],
            "val_acc": [],
            "learning_rates": [],
            "epochs": []
        }
        
        # 保存配置
        self._save_config()
        
        self.info(f"日志初始化完成，实验名称：{experiment_name}")
        self.info(f"日志目录：{self.exp_dir}")
    
    def _setup_logging(self):
        """配置日志系统"""
        # 创建logger
        self.logger = logging.getLogger(f"TrainingLogger_{self.experiment_name}")
        self.logger.setLevel(logging.INFO)
        
        # 清除已有的handlers
        self.logger.handlers = []
        
        # 文件handler
        file_handler = logging.FileHandler(self.log_file, encoding='utf-8')
        file_handler.setLevel(logging.INFO)
        
        # 控制台handler
        console_handler = logging.StreamHandler()
        console_handler.setLevel(logging.INFO)
        
        # 格式化
        formatter = logging.Formatter(
            '%(asctime)s - %(levelname)s - %(message)s',
            datefmt='%Y-%m-%d %H:%M:%S'
        )
        file_handler.setFormatter(formatter)
        console_handler.setFormatter(formatter)
        
        # 添加handlers
        self.logger.addHandler(file_handler)
        self.logger.addHandler(console_handler)
    
    def _save_config(self):
        """保存训练配置"""
        config_dict = {
            "data_root": config.DATA_ROOT,
            "batch_size": config.BATCH_SIZE,
            "image_size": config.IMAGE_SIZE,
            "epochs": config.EPOCHS,
            "learning_rate": config.LEARNING_RATE,
            "weight_decay": config.WEIGHT_DECAY,
            "device": str(config.DEVICE),
            "unfreeze_last_blocks": getattr(config, "UNFREEZE_LAST_BLOCKS", 0),
            "early_stop_patience": getattr(config, "EARLY_STOP_PATIENCE", 0),
            "label_smoothing": getattr(config, "LABEL_SMOOTHING", 0.0),
            "use_amp": getattr(config, "USE_AMP", False),
            "use_balanced_sampler": getattr(config, "USE_BALANCED_SAMPLER", False),
            "train_max_per_class": getattr(config, "TRAIN_MAX_PER_CLASS", 0),
            "num_classes": len(config.CLASS_NAMES) if config.CLASS_NAMES else 0,
            "experiment_name": self.experiment_name,
            "timestamp": datetime.now().isoformat()
        }
        
        with open(self.config_file, 'w', encoding='utf-8') as f:
            json.dump(config_dict, f, indent=2, ensure_ascii=False)
    
    def info(self, message: str):
        """记录INFO级别日志"""
        self.logger.info(message)
    
    def warning(self, message: str):
        """记录WARNING级别日志"""
        self.logger.warning(message)
    
    def error(self, message: str):
        """记录ERROR级别日志"""
        self.logger.error(message)
    
    def log_epoch(self, epoch: int, train_loss: float, train_acc: float, 
                  val_loss: float, val_acc: float, lr: float):
        """
        记录每个epoch的指标
        
        Args:
            epoch: 当前epoch编号
            train_loss: 训练损失
            train_acc: 训练准确率
            val_loss: 验证损失
            val_acc: 验证准确率
            lr: 当前学习率
        """
        # 添加到历史记录
        self.metrics_history["epochs"].append(epoch)
        self.metrics_history["train_loss"].append(train_loss)
        self.metrics_history["train_acc"].append(train_acc)
        self.metrics_history["val_loss"].append(val_loss)
        self.metrics_history["val_acc"].append(val_acc)
        self.metrics_history["learning_rates"].append(lr)
        
        # 记录日志
        self.info(f"Epoch {epoch}/{config.EPOCHS} - "
                 f"Train Loss: {train_loss:.4f}, Train Acc: {train_acc:.4f} | "
                 f"Val Loss: {val_loss:.4f}, Val Acc: {val_acc:.4f} | "
                 f"LR: {lr:.6f}")
        
        # 保存指标到文件
        self._save_metrics()
    
    def _save_metrics(self):
        """保存指标到JSON文件"""
        with open(self.metrics_file, 'w', encoding='utf-8') as f:
            json.dump(self.metrics_history, f, indent=2, ensure_ascii=False)
    
    def log_best_model(self, epoch: int, val_acc: float):
        """记录最佳模型保存信息"""
        self.info(f"💾 保存最佳模型 - Epoch {epoch}, 验证准确率: {val_acc:.4f}")
    
    def log_early_stop(self, epoch: int, patience: int):
        """记录早停信息"""
        self.warning(f"⚠️ 早停触发 - Epoch {epoch}, 连续 {patience} 次无提升")
    
    def log_training_complete(self, best_val_acc: float, total_epochs: int):
        """记录训练完成信息"""
        self.info("="*60)
        self.info(f"✅ 训练完成！")
        self.info(f"总训练轮次: {total_epochs}")
        self.info(f"最佳验证准确率: {best_val_acc:.4f}")
        self.info(f"模型保存路径: {config.MODEL_SAVE_PATH}")
        self.info(f"日志保存路径: {self.exp_dir}")
        self.info("="*60)
    
    def log_exception(self, exception: Exception):
        """记录异常信息"""
        self.error(f"❌ 训练过程中发生异常: {type(exception).__name__}")
        self.error(f"异常详情: {str(exception)}")
        import traceback
        self.error(f"堆栈追踪:\n{traceback.format_exc()}")
    
    def get_metrics_history(self) -> Dict[str, Any]:
        """获取指标历史记录"""
        return self.metrics_history.copy()
    
    def get_log_dir(self) -> str:
        """获取日志目录路径"""
        return self.exp_dir


def setup_simple_logger(name: str = "PlantDisease") -> logging.Logger:
    """
    创建简单的日志记录器（用于非训练脚本）
    
    Args:
        name: logger名称
        
    Returns:
        配置好的logger实例
    """
    logger = logging.getLogger(name)
    logger.setLevel(logging.INFO)
    
    # 清除已有handlers
    logger.handlers = []
    
    # 控制台handler
    handler = logging.StreamHandler()
    handler.setLevel(logging.INFO)
    
    # 格式化
    formatter = logging.Formatter(
        '%(asctime)s - %(name)s - %(levelname)s - %(message)s',
        datefmt='%Y-%m-%d %H:%M:%S'
    )
    handler.setFormatter(formatter)
    
    logger.addHandler(handler)
    
    return logger

