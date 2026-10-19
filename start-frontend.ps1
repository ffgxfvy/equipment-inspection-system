<#
    启动前端.ps1 —— 一键启动 Vue 前端

    推荐用法：双击同目录下的 start-frontend.bat
    前提：后端已经启动（先运行 start-backend.bat）

    ⚠️ 本文件必须保存为「UTF-8 带 BOM」，否则 Windows PowerShell 5.1 会按 GBK
    解码中文，导致解析失败、窗口闪退。
#>
$ErrorActionPreference = "Continue"

try {
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    chcp 65001 > $null
} catch { }

try {
    . (Join-Path $PSScriptRoot "_common.ps1")

    Write-Host ""
    Write-Host "=================================================" -ForegroundColor Cyan
    Write-Host "   工业设备巡检管理系统 - 启动前端" -ForegroundColor Cyan
    Write-Host "=================================================" -ForegroundColor Cyan

    Write-Host ""
    Write-Host "[1/2] 查找 Node.js ..." -ForegroundColor Yellow
    $nodeExe = Get-NodeExe
    if (-not $nodeExe) {
        throw "没有找到 Node.js。`n`n请安装 Node 18 或更高版本：`n  https://nodejs.org/zh-cn/download`n`n安装后需要重新打开一个窗口，让 PATH 生效。"
    }
    Write-Host "    找到：$nodeExe" -ForegroundColor Green
    Write-Host "    版本：Node $(& $nodeExe -v)" -ForegroundColor Gray

    $frontendDir = Join-Path $PSScriptRoot "frontend"
    if (-not (Test-Path -LiteralPath $frontendDir)) { $frontendDir = $PSScriptRoot }
    Set-Location $frontendDir

    Write-Host ""
    if (-not (Test-Path -LiteralPath "node_modules")) {
        Write-Host "[2/2] 首次运行，安装依赖（约 200 MB，需要几分钟）..." -ForegroundColor Yellow
        Write-Host "      使用国内镜像 registry.npmmirror.com" -ForegroundColor Gray
        npm install --registry=https://registry.npmmirror.com --no-fund --no-audit
    } else {
        Write-Host "[2/2] 依赖已存在，跳过安装" -ForegroundColor Green
        Write-Host "      （如需重装，删掉 frontend\node_modules 后重新运行）" -ForegroundColor Gray
    }

    Write-Host ""
    Write-Host "  +-----------------------------------------------------+" -ForegroundColor DarkGray
    Write-Host "  |  前端地址   http://localhost:5173" -ForegroundColor DarkGray
    Write-Host "  |  演示账号   admin / admin123" -ForegroundColor DarkGray
    Write-Host "  +-----------------------------------------------------+" -ForegroundColor DarkGray
    Write-Host ""
    Write-Host "  注意：请确保后端已经在运行，否则登录会提示网络异常。" -ForegroundColor Yellow
    Write-Host "  停止服务：按 Ctrl + C，或直接关掉这个窗口。" -ForegroundColor Gray
    Write-Host ""

    npm run dev
}
catch {
    Write-Host ""
    Write-Host "=================================================" -ForegroundColor Red
    Write-Host "   启动失败" -ForegroundColor Red
    Write-Host "=================================================" -ForegroundColor Red
    Write-Host ""
    Write-Host $_.Exception.Message -ForegroundColor Yellow
    Write-Host ""
    Write-Host "--------------- 排错提示 ---------------" -ForegroundColor Gray
    Write-Host "  1. 5173 端口被占用？关掉占用它的程序后重试" -ForegroundColor Gray
    Write-Host "  2. npm install 报网络错误？多试几次，或换镜像源" -ForegroundColor Gray
    Write-Host "  3. 完整指引见 docs\development-guide.md" -ForegroundColor Gray
    Write-Host ""
    Read-Host "按回车键关闭窗口"
}
