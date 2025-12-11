"""
模型测试脚本
优化：添加异常处理和日志
"""
import sys
import traceback
from utils.data_loader import get_data_loaders
from models.mobilenet_v2 import get_model
from utils.train_utils import test_model
from utils.visualize import plot_confusion_matrix
from utils.logger import setup_simple_logger


def main():
    """主测试流程"""
    logger = setup_simple_logger("TestScript")
    
    try:
        logger.info("="*60)
        logger.info("植物病害分类模型测试程序")
        logger.info("="*60)
        
        # 1. 加载数据（仅需测试集）
        logger.info("正在加载测试集...")
        _, _, test_loader = get_data_loaders()
        logger.info(f"测试集加载完成，批次数: {len(test_loader)}")

        # 2. 创建模型
        logger.info("正在创建模型...")
        model = get_model()

        # 3. 测试模型
        logger.info("开始测试模型...")
        test_acc, all_preds, all_labels = test_model(model, test_loader)

        # 4. 绘制混淆矩阵
        logger.info("正在生成混淆矩阵...")
        plot_confusion_matrix(all_preds, all_labels)
        
        logger.info("="*60)
        logger.info(f"测试完成！最终准确率: {test_acc:.4f}")
        logger.info("="*60)
        
        return 0
        
    except FileNotFoundError as e:
        logger.error(f"文件未找到: {str(e)}")
        logger.error("请先运行 train.py 训练模型")
        return 1
        
    except Exception as e:
        logger.error(f"测试过程中发生错误: {type(e).__name__}")
        logger.error(f"错误详情: {str(e)}")
        logger.error(traceback.format_exc())
        return 1


if __name__ == "__main__":
    sys.exit(main())