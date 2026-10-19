<#
    工具探测函数集合（供启动脚本和检查脚本共用）

    ⚠️ 为什么不能直接写 Join-Path $env:JAVA_HOME ...：
    本机 JAVA_HOME 可能是空的，Join-Path 收到 null 会抛异常。
    在 $ErrorActionPreference = "Stop" 的脚本里，这会直接终止整个脚本
    —— 表现为「明明装了 Java 却说找不到，甚至什么都没做就退出」。
    所以先把候选路径里可能为空的项过滤掉，再逐个 Test-Path。
#>

function Get-JavaExe {
    $candidates = New-Object System.Collections.ArrayList
    [void]$candidates.Add("D:\java\bin\java.exe")          # 本机常见的独立安装位置
    if ($env:JAVA_HOME) {
        [void]$candidates.Add((Join-Path $env:JAVA_HOME "bin\java.exe"))
    }
    $onPath = Get-Command java -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($onPath) { [void]$candidates.Add($onPath.Source) }

    foreach ($c in $candidates) {
        if ($c -and (Test-Path -LiteralPath $c)) { return $c }
    }
    return $null
}

function Get-MavenExe([string]$projectRoot) {
    $candidates = New-Object System.Collections.ArrayList
    [void]$candidates.Add("D:\桌面\实习\.tools\apache-maven-3.9.16\bin\mvn.cmd")
    if ($projectRoot) {
        [void]$candidates.Add((Join-Path $projectRoot "backend\mvnw.cmd"))
        [void]$candidates.Add((Join-Path $projectRoot "mvnw.cmd"))
    }
    $onPath = Get-Command mvn -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($onPath) { [void]$candidates.Add($onPath.Source) }

    foreach ($c in $candidates) {
        if ($c -and (Test-Path -LiteralPath $c)) { return $c }
    }
    return $null
}

function Get-NodeExe {
    $onPath = Get-Command node -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($onPath) { return $onPath.Source }
    return $null
}

function Get-NpmExe {
    $onPath = Get-Command npm -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($onPath) { return $onPath.Source }
    return $null
}

function Test-PortBusy([int]$port) {
    $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($conn) { return $conn[0].OwningProcess }
    return 0
}
