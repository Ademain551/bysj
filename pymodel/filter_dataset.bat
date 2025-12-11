@echo off
chcp 65001 >nul
echo ======================================================================
echo 数据集筛选工具 - 试运行模式
echo ======================================================================
echo.
echo 扫描数据集，识别需要删除的图片...
echo.

C:\Users\LENOVO\anaconda3\envs\bysj601\python.exe filter_dataset.py --dry-run

echo.
pause

