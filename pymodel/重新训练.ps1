# PowerShell 脚本 - 重新训练模型
# 使用方法：在 PowerShell 中运行
#   .\重新训练.ps1

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "重新训练模型（使用新配置）" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "当前配置变更：" -ForegroundColor Green
Write-Host "  ✓ 排除 Background_without_leaves 类别"
Write-Host "  ✓ 数据增强从 heavy 降为 medium"
Write-Host ""
Write-Host "准备工作：" -ForegroundColor Yellow
Write-Host "  1. 删除旧的数据划分文件"
Write-Host "  2. 保留训练日志和模型文件作为备份"
Write-Host ""
Write-Host "按任意键继续..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")

# 检查conda环境
if ($env:CONDA_DEFAULT_ENV) {
    Write-Host "`n✓ 检测到 Conda 环境: $env:CONDA_DEFAULT_ENV" -ForegroundColor Green
} else {
    Write-Host "`n⚠ 警告：未检测到 Conda 环境" -ForegroundColor Yellow
    Write-Host "如果运行失败，请先激活环境：conda activate bysj601" -ForegroundColor Yellow
}

# 备份当前最佳模型
Write-Host "`n[1/3] 备份当前最佳模型..." -ForegroundColor Cyan
if (Test-Path "saved_models\best_model.pth") {
    Copy-Item "saved_models\best_model.pth" "saved_models\best_model_backup.pth" -Force
    Write-Host "✓ 已备份到 best_model_backup.pth" -ForegroundColor Green
} else {
    Write-Host "! 未找到现有模型，跳过备份" -ForegroundColor Yellow
}

# 删除数据划分文件
Write-Host "`n[2/3] 删除数据划分文件（强制重新划分）..." -ForegroundColor Cyan
$filesToDelete = @(
    "data\splits\indices.json",
    "data\splits\dataset_fingerprint.json",
    "data\splits\class_names.json"
)

foreach ($file in $filesToDelete) {
    if (Test-Path $file) {
        Remove-Item $file -Force
        Write-Host "✓ 已删除 $(Split-Path $file -Leaf)" -ForegroundColor Green
    }
}

# 开始训练
Write-Host "`n[3/3] 开始训练新模型..." -ForegroundColor Cyan
Write-Host ""
python train.py

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "训练完成！" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "按任意键退出..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")

