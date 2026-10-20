# ---------------------------------------------------------------------------
# scripts/dev-env.ps1
#
# 用法（在当前 PowerShell 会话中生效，需 dot-source）：
#     . .\scripts\dev-env.ps1                 # 设置环境并打印版本矩阵
#     . .\scripts\dev-env.ps1 -Quiet          # 只设置环境，不打印
#     . .\scripts\dev-env.ps1 -WithGradle     # 额外调用 gradlew --version（较慢）
#
# 直接执行（非 dot-source）只影响该进程，不影响父 shell。
# 子进程方式调用（推荐给脚本/CI 风格的检查）：
#     powershell -NoProfile -File .\scripts\dev-env.ps1
#
# 背景（详见 docs/analysis/env-report.md）：
#   * 机器默认 java 是 Oracle JDK 27；Android Studio 自带 jbr 25.0.3，AGP 9.4.1
#     已用 jbr 25 做全量构建验证。
#   * 机器 ANDROID_NDK_HOME 指向不存在的 NDK 27.2.12479018，必须覆盖为 r30。
#   * proxy.golang.org / sum.golang.org 不可达，Go 依赖走 goproxy.cn 且关闭 sumdb。
# ---------------------------------------------------------------------------
[CmdletBinding()]
param(
    [switch]$Quiet,
    [switch]$WithGradle,
    [switch]$NoPath
)

$ErrorActionPreference = 'Continue'
$repoRoot = Split-Path $PSScriptRoot -Parent

# ---------------- 固定路径（如换机器，只改这里） ----------------
$JbrPath   = 'D:\Jetbrains IDES\Android Studio\jbr'
$SdkPath   = 'D:\AndroidSDKs'
$NdkPath   = Join-Path $SdkPath 'ndk\30.0.16248370'
$GoBinPath = Join-Path $env:USERPROFILE 'go\bin'

# ---------------- 环境变量 ----------------
$env:JAVA_HOME        = $JbrPath
$env:ANDROID_HOME     = $SdkPath
$env:ANDROID_SDK_ROOT = $SdkPath
$env:ANDROID_NDK_HOME = $NdkPath          # 关键：覆盖机器上指向不存在 27.2 的错误值
$env:ANDROID_NDK_ROOT = $NdkPath
$env:GOPROXY          = 'https://goproxy.cn,direct'
$env:GOSUMDB          = 'off'             # sum.golang.org 不可达；generate go.sum 时必须
$env:GOFLAGS          = ''
$env:GOTOOLCHAIN      = 'auto'

# ---------------- PATH（去重后前置） ----------------
function Add-PathPrefix {
    param([string]$Dir)
    if ([string]::IsNullOrWhiteSpace($Dir)) { return }
    if (-not (Test-Path -LiteralPath $Dir)) { return }
    $parts = @($env:PATH -split ';' | Where-Object { $_ -ne '' })
    $exists = $parts | Where-Object { $_.TrimEnd('\') -ieq $Dir.TrimEnd('\') }
    if (-not $exists) {
        $env:PATH = ($Dir + ';' + ($parts -join ';'))
    }
}

if (-not $NoPath) {
    Add-PathPrefix (Join-Path $JbrPath 'bin')
    Add-PathPrefix (Join-Path $SdkPath 'platform-tools')
    Add-PathPrefix (Join-Path $SdkPath 'cmdline-tools\latest\bin')  # 本机缺失时静默跳过
    Add-PathPrefix $GoBinPath
}

# ---------------- 工具函数 ----------------
function Resolve-Executable {
    param([string]$Name)
    if ([string]::IsNullOrWhiteSpace($Name)) { return $null }
    if ([System.IO.Path]::IsPathRooted($Name) -or $Name -match '[\\/]') {
        if (Test-Path -LiteralPath $Name) { return (Get-Item -LiteralPath $Name).FullName }
        return $null
    }
    $cmd = Get-Command $Name -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    return $null
}

function Get-FirstLine {
    param([string]$Exe, [string[]]$ArgList = @('-version'))
    $resolved = Resolve-Executable $Exe
    if (-not $resolved) { return "(missing: $Exe)" }
    try { return ((& $resolved @ArgList 2>&1 | Select-Object -First 1) -as [string]) }
    catch { return "(error: $($_.Exception.Message))" }
}

function Get-NdkVersion {
    param([string]$Path)
    $props = Join-Path $Path 'source.properties'
    if (Test-Path -LiteralPath $props) {
        $rev = (Get-Content -LiteralPath $props | Select-String '^Pkg\.Revision\s*=\s*(.+)$').Matches
        if ($rev.Count -gt 0) { return $rev[0].Groups[1].Value.Trim() }
    }
    return '(missing)'
}

function Get-PlatformList {
    param([string]$Sdk)
    $p = Join-Path $Sdk 'platforms'
    if (-not (Test-Path -LiteralPath $p)) { return @() }
    return @(Get-ChildItem -LiteralPath $p -Directory | Select-Object -ExpandProperty Name)
}

function Get-BuildToolsList {
    param([string]$Sdk)
    $p = Join-Path $Sdk 'build-tools'
    if (-not (Test-Path -LiteralPath $p)) { return @() }
    return @(Get-ChildItem -LiteralPath $p -Directory | Select-Object -ExpandProperty Name)
}

function Get-WrapperInfo {
    param([string]$Root)
    $p = Join-Path $Root 'gradle\wrapper\gradle-wrapper.properties'
    if (-not (Test-Path -LiteralPath $p)) { return '(missing gradle-wrapper.properties)' }
    $line = Get-Content -LiteralPath $p | Where-Object { $_ -match '^\s*distributionUrl\s*=' } | Select-Object -First 1
    if (-not $line) { return '(missing distributionUrl)' }
    $url = ($line -replace '^\s*distributionUrl\s*=\s*', '').Trim()
    if ($url -match 'gradle-([0-9][0-9.]*)-(bin|all)\.zip') { return ("Gradle " + $Matches[1] + "  (" + $url + ")") }
    return $url
}

function Get-GomobileInfo {
    param([string]$GoBin)
    $exe = Join-Path $GoBin 'gomobile.exe'
    if (-not (Test-Path -LiteralPath $exe)) { return '(missing gomobile.exe)' }
    $goExe = Resolve-Executable 'go'
    if (-not $goExe) { return 'gomobile.exe (go not found, cannot read metadata)' }
    $meta = & $goExe version -m $exe 2>&1
    $modLine = $meta | Select-String 'golang.org/x/mobile\s+v' | Select-Object -First 1
    if ($modLine) { return ('gomobile.exe (' + ($modLine.Line.Trim() -replace '\s+h1:.*$', '') + ')') }
    return 'gomobile.exe (metadata unavailable)'
}

# ---------------- 版本矩阵 ----------------
if (-not $Quiet) {
    Write-Host ''
    Write-Host '============ Snishaper-Droid dev environment ============' -ForegroundColor Cyan
    Write-Host ("JAVA_HOME            : " + $env:JAVA_HOME)
    Write-Host ("  java               : " + (Get-FirstLine (Join-Path $JbrPath 'bin\java.exe')))
    Write-Host ("  javac              : " + (Get-FirstLine (Join-Path $JbrPath 'bin\javac.exe')))
    Write-Host ("ANDROID_HOME         : " + $env:ANDROID_HOME)
    Write-Host ("ANDROID_NDK_HOME     : " + $env:ANDROID_NDK_HOME)
    Write-Host ("  NDK version        : " + (Get-NdkVersion $NdkPath))
    $ndkClang = Join-Path $NdkPath 'toolchains\llvm\prebuilt\windows-x86_64\bin\clang.exe'
    Write-Host ("  NDK clang          : " + (Get-FirstLine $ndkClang @('--version')))
    Write-Host ("  platforms          : " + ((Get-PlatformList $SdkPath) -join ', '))
    Write-Host ("  build-tools        : " + ((Get-BuildToolsList $SdkPath) -join ', '))
    $cmdlineTools = Join-Path $SdkPath 'cmdline-tools'
    Write-Host ("  cmdline-tools      : " + $(if (Test-Path -LiteralPath $cmdlineTools) { 'present' } else { 'MISSING（无 sdkmanager，见 env-report）' }))
    $adb = Join-Path $SdkPath 'platform-tools\adb.exe'
    if (-not (Test-Path -LiteralPath $adb)) { $adb = 'D:\platform-tools\adb.exe' }
    Write-Host ("  adb                : " + (Get-FirstLine $adb @('version')))
    Write-Host ''
    Write-Host ("Go                   : " + (Get-FirstLine 'go' @('version')))
    Write-Host ("  GOPROXY            : " + $env:GOPROXY)
    Write-Host ("  GOSUMDB            : " + $env:GOSUMDB)
    $goExe = Resolve-Executable 'go'
    if ($goExe) {
        Write-Host ("  GOMODCACHE         : " + ((& $goExe env GOMODCACHE) -as [string]))
    }
    Write-Host ("  gcc                : " + (Get-FirstLine 'gcc' @('--version')))
    Write-Host ("  gomobile           : " + (Get-GomobileInfo $GoBinPath))
    Write-Host ''
    Write-Host ("Gradle wrapper       : " + (Get-WrapperInfo $repoRoot))
    Write-Host ("repo root            : " + $repoRoot)
    Write-Host '=========================================================' -ForegroundColor Cyan
}

if ($WithGradle) {
    $gradlew = Join-Path $repoRoot 'gradlew.bat'
    if (Test-Path -LiteralPath $gradlew) {
        Write-Host ''
        Write-Host '[dev-env] gradlew --version ...' -ForegroundColor Yellow
        & $gradlew --version 2>&1 | Select-Object -First 12
    } else {
        Write-Warning "gradlew.bat not found at $gradlew"
    }
}

if (-not $Quiet) { Write-Host '' }
