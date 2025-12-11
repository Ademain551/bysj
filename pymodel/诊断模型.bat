@echo off
chcp 65001 >nul
echo ========================================
echo 🔍 全面诊断模型问题
echo ========================================
echo.
echo 这个脚本会：
echo   ✓ 在测试集上评估模型
echo   ✓ 统计所有识别错误
echo   ✓ 找出最容易混淆的类别
echo   ✓ 给出针对性改进建议
echo.
echo 预计耗时: 1-2 分钟
echo.
pause

python 诊断模型问题.py

pause

