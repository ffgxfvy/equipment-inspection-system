<#
    启动后端.ps1 —— 一键启动 SpringBoot 后端

    推荐用法：双击同目录下的 start-backend.bat
    命令行用法：.\start-backend.ps1

    ⚠️ 这个脚本里有三个必须注意的点，改动时别踩：

    1) 文件必须保存为「UTF-8 带 BOM」。
       Windows PowerShell 5.1 读取无 BOM 的 .ps1 时会按系统 ANSI（GBK）解码，
       中文变乱码后破坏引号与括号配对，脚本一行都不跑就退出（窗口闪退）。

    2) 不能用 $ErrorActionPreference = "Stop" 配合原生命令的 2>&1 重定向。
       java -version 把版本信息写到 stderr，Maven 也会往 stderr 打警告。
       在 Stop 模式下，PS 5.1 会把重定向过来的 stderr 当成致命错误直接抛出，
       结果是「明明找到了 Java，脚本却立刻报失败退出」。
       所以这里用 Continue，改由显式判断 + throw 来控制流程。

    3) 捕获原生命令输出用 cmd /c "..." 2>&1 更稳，
       绕开 PowerShell 对原生命令 stderr 的特殊处理。
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
    Write-Host "   工业设备巡检管理系统 - 启动后端" -ForegroundColor Cyan
    Write-Host "=================================================" -ForegroundColor Cyan

    # ---------------- 1. Java ----------------
    Write-Host ""
    Write-Host "[1/3] 查找 Java ..." -ForegroundColor Yellow
    $javaExe = Get-JavaExe
    if (-not $javaExe) {
        throw "没有找到 Java。`n`n请安装 JDK 17 或更高版本：`n  https://adoptium.net/zh-CN/temurin/releases/?version=17`n`n安装后重新运行本脚本。"
    }
    if (-not $env:JAVA_HOME) {
        $env:JAVA_HOME = Split-Path (Split-Path $javaExe -Parent) -Parent
    }
    # 用 cmd /c 捕获，避免 PS 5.1 把 java 的 stderr 当成错误
    $javaVer = (cmd /c "`"$javaExe`" -version 2>&1" | Select-Object -First 1)
    Write-Host "    找到：$javaExe" -ForegroundColor Green
    Write-Host "    版本：$javaVer" -ForegroundColor Gray
    Write-Host "    JAVA_HOME = $env:JAVA_HOME" -ForegroundColor Gray

    # ---------------- 2. Maven ----------------
    Write-Host ""
    Write-Host "[2/3] 查找 Maven ..." -ForegroundColor Yellow
    $mvnExe = Get-MavenExe $PSScriptRoot
    if (-not $mvnExe) {
        throw "没有找到 Maven。三种解决办法（任选其一）：`n  1. 安装 IntelliJ IDEA（自带 Maven），用它打开 backend 目录`n  2. 到 backend 目录执行 .\mvnw.cmd（项目自带 Maven Wrapper）`n  3. 手动安装 Maven 并加入 PATH"
    }
    Write-Host "    找到：$mvnExe" -ForegroundColor Green

    # ---------------- 3. 启动 ----------------
    Write-Host ""
    Write-Host "[3/3] 启动服务（dev 模式：H2 内存数据库，不需要 MySQL）..." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "  +-----------------------------------------------------+" -ForegroundColor DarkGray
    Write-Host "  |  接口地址    http://localhost:8080/api" -ForegroundColor DarkGray
    Write-Host "  |  接口文档    http://localhost:8080/api/swagger-ui.html" -ForegroundColor DarkGray
    Write-Host "  |  H2 控制台   http://localhost:8080/api/h2-console" -ForegroundColor DarkGray
    Write-Host "  |  演示账号    admin / admin123      （管理员）" -ForegroundColor DarkGray
    Write-Host "  |              zhangsan / 123456     （巡检员）" -ForegroundColor DarkGray
    Write-Host "  +-----------------------------------------------------+" -ForegroundColor DarkGray
    Write-Host ""
    Write-Host "  看到 Started InspectionApplication 就是启动成功。" -ForegroundColor Gray
    Write-Host "  停止服务：按 Ctrl + C，或直接关掉这个窗口。" -ForegroundColor Gray
    Write-Host ""

    $busy = Test-PortBusy 8080
    if ($busy -ne 0) {
        throw "8080 端口已被占用（PID=$busy）。`n`n如果不是你启动的服务，先结束它：`n  taskkill /PID $busy /F"
    }

    $backendDir = Join-Path $PSScriptRoot "backend"
    if (-not (Test-Path -LiteralPath $backendDir)) { $backendDir = $PSScriptRoot }
    Set-Location $backendDir

    # 前台阻塞运行：成功时窗口不会自己关闭
    & $mvnExe spring-boot:run
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
    Write-Host "  1. 首次启动要下载依赖，网络慢时请多等几分钟" -ForegroundColor Gray
    Write-Host "  2. 端口被占用：netstat -ano | findstr :8080" -ForegroundColor DarkGray
    Write-Host "  3. 完整指引见 docs\development-guide.md" -ForegroundColor Gray
    Write-Host "  4. 想确认环境是否齐全，双击 check-env.bat" -ForegroundColor Gray
    Write-Host ""
    Read-Host "按回车键关闭窗口"
}
