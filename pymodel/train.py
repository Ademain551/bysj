"""
主训练脚本
优化：添加异常处理、命令行参数、日志系统
"""
import argparse
import sys
import traceback
from utils.data_loader import get_data_loaders
from models.mobilenet_v2 import get_model
from utils.train_utils import train_model
from utils.visualize import plot_training_curve
from utils.misc import set_seed
from utils.logger import setup_simple_logger
import config


def parse_args():
    """解析命令行参数"""
    parser = argparse.ArgumentParser(description="植物病害分类模型训练")
    parser.add_argument(
        "--task",
        type=str,
        default="disease",
        choices=["species", "disease"],
        help="训练任务类型：species=物种识别，disease=病害识别"
    )
    parser.add_argument(
        "--device",
        type=str,
        default=None,
        help="计算设备，如 cuda / cuda:0 / cpu（不指定则按环境自动选择）"
    )
    parser.add_argument(
        "--resume", 
        action="store_true", 
        help="从上次checkpoint恢复训练"
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=42,
        help="随机种子 (默认: 42)"
    )
    return parser.parse_args()


def main():
    """主训练流程"""
    # 解析命令行参数
    args = parse_args()
    
    # 设置简单日志（用于主脚本）
    logger = setup_simple_logger("TrainScript")
    
    try:
        logger.info("="*60)
        logger.info("植物病害分类模型训练程序")
        logger.info("="*60)
        
        # 可选：覆盖设备选择
        if args.device is not None:
            import torch as _torch
            if args.device.startswith("cuda") and not _torch.cuda.is_available():
                raise RuntimeError("请求使用CUDA，但当前环境未检测到可用GPU。")
            config.DEVICE = _torch.device(args.device)
        logger.info(f"使用设备: {config.DEVICE}")

        # 训练性能优化（在GPU上启用cudnn benchmark）
        try:
            import torch as _torch
            if config.DEVICE.type == "cuda":
                _torch.backends.cudnn.benchmark = True
        except Exception:
            pass

        # 0. 设定随机种子，保证结果可复现
        logger.info(f"设置随机种子: {args.seed}")
        set_seed(args.seed)

        # 1. 加载数据
        logger.info(f"正在加载数据集（task={args.task}）...")
        train_loader, val_loader, test_loader = get_data_loaders(task=args.task)
        logger.info(f"数据加载完成 - 训练批次: {len(train_loader)}, "
                   f"验证批次: {len(val_loader)}, 测试批次: {len(test_loader)}")

        # 2. 创建模型
        logger.info("正在创建模型...")
        # 为向后兼容，设置运行期的 CLASS_NAMES 和 保存路径
        if args.task == "species":
            # 使用species类名与保存路径
            if getattr(config, "SPECIES_CLASS_NAMES", None) is None:
                raise RuntimeError("SPECIES_CLASS_NAMES 未初始化，请检查数据加载流程")
            config.CLASS_NAMES = config.SPECIES_CLASS_NAMES
            config.MODEL_SAVE_PATH = getattr(config, "SPECIES_MODEL_SAVE_PATH")
        else:
            if getattr(config, "DISEASE_CLASS_NAMES", None) is None:
                raise RuntimeError("DISEASE_CLASS_NAMES 未初始化，请检查数据加载流程")
            config.CLASS_NAMES = config.DISEASE_CLASS_NAMES
            config.MODEL_SAVE_PATH = getattr(config, "DISEASE_MODEL_SAVE_PATH")

        model = get_model(num_classes=len(config.CLASS_NAMES))

        # 3. 训练模型
        logger.info("开始训练模型...")
        history = train_model(
            model, 
            train_loader, 
            val_loader,
            resume_from_checkpoint=args.resume
        )

        # 4. 可视化训练结果
        logger.info("正在生成训练曲线...")
        plot_training_curve(history)
        
        logger.info("="*60)
        logger.info("训练流程全部完成！")
        logger.info(f"最佳模型已保存至: {config.MODEL_SAVE_PATH}")
        logger.info("可以运行 test.py 进行测试，或运行 predict.py 进行预测")
        logger.info("="*60)
        
        return 0  # 成功退出
        
    except KeyboardInterrupt:
        logger.warning("\n训练被用户中断")
        logger.info("提示: 使用 --resume 参数可以恢复训练")
        return 130  # SIGINT退出码
        
    except FileNotFoundError as e:
        logger.error(f"文件未找到错误: {str(e)}")
        logger.error("请检查数据集路径配置是否正确")
        return 1
        
    except RuntimeError as e:
        if "out of memory" in str(e):
            logger.error("GPU内存不足！")
            logger.error("建议: 1) 减小BATCH_SIZE 2) 使用更小的模型 3) 使用CPU训练")
        else:
            logger.error(f"运行时错误: {str(e)}")
        logger.error(traceback.format_exc())
        return 1
        
    except Exception as e:
        logger.error(f"发生未预期的错误: {type(e).__name__}")
        logger.error(f"错误详情: {str(e)}")
        logger.error(traceback.format_exc())
        return 1


if __name__ == "__main__":
    sys.exit(main())
