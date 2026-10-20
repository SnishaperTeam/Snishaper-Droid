# ---------------------------------------------------------------------------
# scripts/mumu-status.ps1
#
# 检查 MuMu 模拟器（Android 15 / x86_64）是否已通过 adb 在线。
#
# 逻辑：
#   1. 定位 adb（优先 %ANDROID_HOME%\platform-tools\adb.exe，其次 D:\platform-tools\adb.exe）；
#   2. adb start-server + adb devices；
#   3. 若无在线设备，自动尝试 adb connect（默认 127.0.0.1:16384，是 MuMu 实例 0
#      在 vm_config.json 中配置的 NAT adb 端口），再查一次；
#   4. 仍无设备 → 打印“需用户启动 MuMu”+ 操作指引并 exit 2（不伪造在线）；
#   5. 在线 → 打印设备 ABI / SDK / 分辨率 / MuMu 识别信息，exit 0。
#
# 退出码：0 = 设备在线；2 = 未在线（需用户启动 MuMu）；3 = 找不到 adb。
#
# 用法：
#   .\scripts\mumu-status.ps1
#   .\scripts\mumu-status.ps1 -NoConnect        # 不自动 adb connect
#   .\scripts\mumu-status.ps1 -Ports 127.0.0.1:16384,127.0.0.1:7555
# ---------------------------------------------------------------------------
[CmdletBinding()]
param(
    [string]$Adb = '',
    [string[]]$Ports = @('127.0.0.1:16384'),
    [switch]$NoConnect,
    [switch]$Quiet
)

$ErrorActionPreference = 'Continue'

function Write-Info([string]$Message) { if (-not $Quiet) { Write-Host $Message } }
function Write-Ok([string]$Message) { if (-not $Quiet) { Write-Host ("  [ OK ] " + $Message) -ForegroundColor Green } }
function Write-Bad([string]$Message) { Write-Host ("  [FAIL] " + $Message) -ForegroundColor Red }

function Resolve-AdbExe {
    param([string]$Explicit)
    if (-not [string]::IsNullOrWhiteSpace($Explicit)) {
        if (Test-Path -LiteralPath $Explicit) { return (Get-Item -LiteralPath $Explicit).FullName }
        return $null
    }
    $candidates = New-Object 'System.Collections.Generic.List[string]'
    if ($env:ANDROID_HOME) { $candidates.Add((Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe')) }
    if ($env:ANDROID_SDK_ROOT) { $candidates.Add((Join-Path $env:ANDROID_SDK_ROOT 'platform-tools\adb.exe')) }
    $candidates.Add('D:\platform-tools\adb.exe')
    foreach ($c in $candidates) {
        if (Test-Path -LiteralPath $c) { return (Get-Item -LiteralPath $c).FullName }
    }
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    return $null
}

function Get-OnlineDevices {
    param([string]$AdbExe)
    $lines = & $AdbExe devices 2>&1
    $result = @()
    foreach ($line in $lines) {
        if ($line -match '^(\S+)\s+device\b') { $result += $Matches[1] }
    }
    return @($result)
}

# ---------------- 定位 adb ----------------
$adbExe = Resolve-AdbExe -Explicit $Adb
if (-not $adbExe) {
    Write-Host '[FAIL] 找不到 adb：请安装 platform-tools，或设置 ANDROID_HOME，或用 -Adb 指定路径。' -ForegroundColor Red
    exit 3
}
Write-Info ''
Write-Info '=== MuMu / adb 状态检查 ===' 
Write-Info ("adb : " + $adbExe)

& $adbExe start-server 2>&1 | Out-Null
$devices = Get-OnlineDevices -AdbExe $adbExe
Write-Info ("adb devices : " + $(if ($devices.Count -eq 0) { '(空)' } else { ($devices -join ', ') }))

# ---------------- 尝试连接 MuMu ----------------
$connectAttempts = @()
if ($devices.Count -eq 0 -and -not $NoConnect) {
    foreach ($port in $Ports) {
        Write-Info ("尝试 adb connect " + $port + " ...")
        $out = (& $adbExe connect $port 2>&1) -join ' '
        $connectAttempts += ("adb connect $port => $out")
        Write-Info ("  " + $out)
        $devices = Get-OnlineDevices -AdbExe $adbExe
        if ($devices.Count -gt 0) { break }
    }
    Write-Info ("adb devices（connect 后）: " + $(if ($devices.Count -eq 0) { '(空)' } else { ($devices -join ', ') }))
}

# ---------------- 未在线 → 明确提示，exit 2 ----------------
if ($devices.Count -eq 0) {
    Write-Host ''
    Write-Host '  [FAIL] MuMu 未在线：adb 设备列表为空。' -ForegroundColor Red
    Write-Host '  >>> 需用户启动 MuMu（本脚本不伪造在线状态） <<<' -ForegroundColor Yellow
    Write-Host ''
    Write-Host '  操作步骤：'
    Write-Host '    1) 打开 MuMu 模拟器（GUI 双击“MuMu模拟器”），或执行：'
    Write-Host '       "D:\Program Files\Netease\MuMu\nx_main\MuMuManager.exe" control -v 0 launch'
    Write-Host '    2) 等 Android 桌面出现后连接 adb（二选一）：'
    Write-Host '       & "D:\Program Files\Netease\MuMu\nx_main\MuMuManager.exe" adb -v 0 -c connect'
    Write-Host ('       & "' + $adbExe + '" connect 127.0.0.1:16384')
    Write-Host '    3) 复查：'
    Write-Host '       .\scripts\mumu-status.ps1'
    Write-Host '  若仍连不上：MuMu 设置中确认 ADB 调试/root 已开启（实例配置 shell_config.json adb_debug.mode）。'
    Write-Host '  环境细节见 docs/analysis/env-report.md §1.4/§6。'
    exit 2
}

# ---------------- 在线 → 打印设备信息 ----------------
$serial = $devices[0]
Write-Ok ("设备在线: " + $serial)
if ($devices.Count -gt 1) { Write-Info ("  注意：共 " + $devices.Count + " 台在线设备，后续步骤默认使用第一台；可用 -Serial 指定。") }

function Get-DeviceProp {
    param([string]$Name)
    $v = (& $adbExe -s $serial shell getprop $Name 2>&1) -join ''
    return $v.Trim()
}
function Get-DeviceShell {
    param([string]$Command)
    $v = (& $adbExe -s $serial shell $Command 2>&1) -join ' '
    return $v.Trim()
}

$abi = Get-DeviceProp 'ro.product.cpu.abi'
$sdk = Get-DeviceProp 'ro.build.version.sdk'
$release = Get-DeviceProp 'ro.build.version.release'
$model = Get-DeviceProp 'ro.product.model'
$manufacturer = Get-DeviceProp 'ro.product.manufacturer'
$wmSize = Get-DeviceShell 'wm size'
$wmDensity = Get-DeviceShell 'wm density'

Write-Info ''
Write-Info '  设备信息：'
Write-Info ("    serial        : " + $serial)
Write-Info ("    manufacturer  : " + $manufacturer)
Write-Info ("    model         : " + $model)
Write-Info ("    Android       : " + $release + " (API " + $sdk + ")")
Write-Info ("    ABI           : " + $abi)
Write-Info ("    分辨率/密度   : " + $wmSize + " / " + $wmDensity)

$isMuMu = ($serial -match '127\.0\.0\.1:') -or ($model -match 'MuMu')
Write-Info ("    MuMu 识别     : " + $(if ($isMuMu) { '是（模拟器）' } else { '否/未知' }))
if ($abi -match 'x86_64') {
    Write-Info '    提示：MuMu 为 x86_64，AAR 必须包含 x86_64 ABI（scripts/verify-aar.ps1 会校验）。'
}
if ($isMuMu) { Write-Ok 'MuMu 已就绪，可执行 scripts/smoke-test.ps1' }
elseif ($devices.Count -gt 0) { Write-Ok '已有 adb 设备在线（非 MuMu 或无法识别型号），可继续 smoke test。' }
exit 0
