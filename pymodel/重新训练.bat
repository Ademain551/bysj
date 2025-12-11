@echo off
chcp 65001 >nul
echo ========================================
echo 重新训练模型（使用新配置）
echo ========================================
echo.
echo 当前配置变更：
echo   ✓ 排除 Background_without_leaves 类别
echo   ✓ 数据增强从 heavy 降为 medium
echo.
echo 准备工作：
echo   1. 删除旧的数据划分文件
echo   2. 保留训练日志和模型文件作为备份
echo.
pause

echo.
echo [1/3] 备份当前最佳模型...
if exist "saved_models\best_model.pth" (
    copy "saved_models\best_model.pth" "saved_models\best_model_backup.pth" >nul
    echo ✓ 已备份到 best_model_backup.pth
) else (
    echo ! 未找到现有模型，跳过备份
)

echo.
echo [2/3] 删除数据划分文件（强制重新划分）...
if exist "data\splits\indices.json" (
    del /F /Q "data\splits\indices.json"
    echo ✓ 已删除 indices.json
)
if exist "data\splits\dataset_fingerprint.json" (
    del /F /Q "data\splits\dataset_fingerprint.json"
    echo ✓ 已删除 dataset_fingerprint.json
)
if exist "data\splits\class_names.json" (
    del /F /Q "data\splits\class_names.json"
    echo ✓ 已删除 class_names.json
)

echo.
echo [3/3] 开始训练新模型...
echo.
python train.py

echo.
echo ========================================
echo 训练完成！
echo ========================================
pause

