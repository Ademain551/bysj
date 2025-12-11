@echo off
chcp 65001 >nul
echo ========================================
echo 对比测试：新旧预处理方式
echo ========================================
echo.
echo 这个脚本会：
echo   1. 使用旧方法（Resize 224x224）预测
echo   2. 使用新方法（Resize 256 + CenterCrop 224）预测
echo   3. 对比两种方法的差异
echo.
echo 帮助你验证修复是否有效！
echo.

set /p IMAGE_PATH="请输入图片路径: "

if "%IMAGE_PATH%"=="" (
    echo 错误：必须提供图片路径
    pause
    exit /b
)

python quick_test.py --image "%IMAGE_PATH%"

pause

