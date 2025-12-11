# PowerShell 脚本 - 诊断模型问题
# 使用方法：在 PowerShell 中运行
#   .\诊断模型.ps1

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "🔍 全面诊断模型问题" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "这个脚本会："
Write-Host "  ✓ 在测试集上评估模型"
Write-Host "  ✓ 统计所有识别错误"
Write-Host "  ✓ 找出最容易混淆的类别"
Write-Host "  ✓ 给出针对性改进建议"
Write-Host ""
Write-Host "预计耗时: 1-2 分钟" -ForegroundColor Yellow
Write-Host ""
Write-Host "按任意键继续..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")

# 检查是否在conda环境中
if ($env:CONDA_DEFAULT_ENV) {
    Write-Host "✓ 检测到 Conda 环境: $env:CONDA_DEFAULT_ENV" -ForegroundColor Green
} else {
    Write-Host "⚠ 未检测到 Conda 环境，如果运行失败，请先激活环境：" -ForegroundColor Yellow
    Write-Host "  conda activate bysj601" -ForegroundColor Yellow
    Write-Host ""
}

# 运行诊断脚本
python 诊断模型问题.py

Write-Host ""
Write-Host "按任意键退出..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")

