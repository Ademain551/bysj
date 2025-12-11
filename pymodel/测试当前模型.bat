@echo off
chcp 65001 >nul
echo ========================================
echo 测试当前模型（使用修复后的预测脚本）
echo ========================================
echo.
echo 已修复的问题：
echo   ✓ 预处理方式已与训练时保持一致
echo   ✓ 显示 Top-5 预测结果
echo   ✓ 自动警告可疑的预测
echo.
echo 使用方法：
echo   1. 将测试图片拖拽到窗口中
echo   2. 或者直接回车使用文件选择对话框
echo.

set /p IMAGE_PATH="请输入图片路径（或直接回车使用选择框）: "

if "%IMAGE_PATH%"=="" (
    echo 将打开文件选择对话框...
    python predict.py --leaf_check
) else (
    python predict.py --leaf_check --image "%IMAGE_PATH%"
)

pause

