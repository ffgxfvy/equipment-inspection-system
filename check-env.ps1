<#
    检查环境.ps1 —— 检查运行本项目所需的工具是否齐全

    推荐用法：双击同目录下的 check-env.bat

    只做检测、不修改任何东西，用于排查「为什么启动不了」。
#>
try {
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    chcp 65001 > $null
} catch { }

. (Join-Path $PSScriptRoot "_common.ps1")

Write-Host ""
Write-Host "=================================================" -ForegroundColor Cyan
Write-Host "   环境检查" -ForegroundColor Cyan
Write-Host "=================================================" -ForegroundColor Cyan

$allOk = $true

function Show-Tool {
    param([string]$Title, [string]$Exe, [string[]]$ArgList, [string]$Hint)
    Write-Host ""
    Write-Host "[$Title]" -ForegroundColor Yellow
    if ($Exe) {
        Write-Host "  OK   $Exe" -ForegroundColor Green
        try {
            $v = & $Exe @ArgList 2>&1 | Select-Object -First 1
            if ($v) { Write-Host "       $v" -ForegroundColor Gray }
        } catch {
            Write-Host "       存在但执行失败" -ForegroundColor Red
            $script:allOk = $false
        }
    } else {
        Write-Host "  未找到" -ForegroundColor Red
        Write-Host "  $Hint" -ForegroundColor Gray
        $script:allOk = $false
    }
}

Show-Tool "Java（需要 17 或更高）" (Get-JavaExe) @("-version") `
    "请安装 JDK 17：https://adoptium.net/zh-CN/temurin/releases/?version=17"

Show-Tool "Maven（构建后端）" (Get-MavenExe $PSScriptRoot) @("-v") `
    "可安装 IDEA（自带 Maven），或使用 backend\mvnw.cmd"

Show-Tool "Node.js（需要 18 或更高）" (Get-NodeExe) @("-v") `
    "请安装 Node：https://nodejs.org/zh-cn/download"

Show-Tool "npm" (Get-NpmExe) @("-v") `
    "通常随 Node 一起安装"

Write-Host ""
Write-Host "[端口 8080（后端用）]" -ForegroundColor Yellow
$busy8080 = Test-PortBusy 8080
if ($busy8080 -ne 0) {
    Write-Host "  被占用（PID=$busy8080）" -ForegroundColor Yellow
    Write-Host "  如果不是你启动的服务，先结束它：taskkill /PID $busy8080 /F" -ForegroundColor Gray
} else {
    Write-Host "  空闲" -ForegroundColor Green
}

Write-Host ""
Write-Host "[端口 5173（前端用）]" -ForegroundColor Yellow
$busy5173 = Test-PortBusy 5173
if ($busy5173 -ne 0) {
    Write-Host "  被占用（PID=$busy5173）" -ForegroundColor Yellow
} else {
    Write-Host "  空闲" -ForegroundColor Green
}

Write-Host ""
if ($allOk) {
    Write-Host "  环境检查通过，可以启动项目了" -ForegroundColor Green
    Write-Host "  下一步：双击 start-backend.bat，再双击 start-frontend.bat" -ForegroundColor Gray
} else {
    Write-Host "  有工具缺失，请按上面的提示安装后重试" -ForegroundColor Red
}
Write-Host ""
Read-Host "按回车键关闭窗口"
