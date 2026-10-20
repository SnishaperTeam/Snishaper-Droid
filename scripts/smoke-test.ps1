# ---------------------------------------------------------------------------
# scripts/smoke-test.ps1
#
# 端到端冒烟测试（骨架版，真实执行，不伪造结果）：
#   0) 先跑 mumu-status.ps1：MuMu 离线 → 明确提示“需用户启动 MuMu”并 exit 2；
#   1) gradlew assembleDebug（可 -SkipBuild）→ 失败 exit 1；
#   2) 定位 app\build\outputs\apk\debug\app-debug.apk；
#   3) adb install -r 安装到在线设备（默认第一台，可用 -Serial 指定）；
#   4) adb shell am start -W -n <Package>/<Activity> 启动；
#   5) 等待 -LaunchWaitSec 秒后抓 logcat：`logcat -d -s Snishaper:* AndroidRuntime:E`，
#      检测 FATAL EXCEPTION / CRASH；进程不存在记 WARN；
#   6) 日志保存到 build\test-logs\（gradle 构建日志、logcat、smoke-*.md 摘要）。
#
# 退出码：0 = 冒烟通过；1 = 冒烟失败（构建/安装/启动/崩溃）；2 = MuMu 离线；
#         3 = 缺少 adb/gradlew 等前置工具。
#
# 用法：
#   .\scripts\smoke-test.ps1
#   .\scripts\smoke-test.ps1 -SkipBuild
#   .\scripts\smoke-test.ps1 -Serial 127.0.0.1:16384 -Package com.snishaper.android -Activity .MainActivity
# ---------------------------------------------------------------------------
[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [string]$Serial = '',
    [string]$Package = 'com.snishaper.android',
    [string]$Activity = '.MainActivity',
    [string]$ApkPath = '',
    [int]$LaunchWaitSec = 5
)

$ErrorActionPreference = 'Continue'
$repoRoot = Split-Path $PSScriptRoot -Parent
$logDir = Join-Path $repoRoot 'build\test-logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$summaryPath = Join-Path $logDir ("smoke-" + $stamp + ".md")
$consoleLogPath = Join-Path $logDir ("smoke-" + $stamp + ".log")
$script:Transcript = New-Object 'System.Collections.Generic.List[string]'
$script:Steps = New-Object 'System.Collections.Generic.List[object]'

function Log {
    param([string]$Message, [string]$Color = 'Gray')
    Write-Host $Message -ForegroundColor $Color
    $script:Transcript.Add($Message)
}
function Add-StepResult {
    param([string]$Name, [string]$Status, [string]$Detail = '')
    $script:Steps.Add([pscustomobject]@{ Name = $Name; Status = $Status; Detail = $Detail })
}
function Save-Summary {
    param([string]$Result, [string]$Reason = '')
    $lines = New-Object 'System.Collections.Generic.List[string]'
    $lines.Add('# Snishaper-Droid smoke test')
    $lines.Add('')
    $lines.Add('- 时间: ' + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'))
    $lines.Add('- 结果: **' + $Result + '**')
    if ($Reason) { $lines.Add('- 原因: ' + $Reason) }
    $lines.Add('- Package: `' + $Package + '`  Activity: `' + $Activity + '`')
    $lines.Add('- 设备 serial: `' + $script:Serial + '`')
    $lines.Add('')
    $lines.Add('| 步骤 | 状态 | 说明 |')
    $lines.Add('|---|---|---|')
    foreach ($s in $script:Steps) { $lines.Add('| ' + $s.Name + ' | ' + $s.Status + ' | ' + ($s.Detail -replace '\|', '\|') + ' |') }
    $lines.Add('')
    $lines.Add('## 控制台输出（截断）')
    $lines.Add('```')
    $lines.Add(($script:Transcript -join "`n"))
    $lines.Add('```')
    $lines | Out-File -LiteralPath $summaryPath -Encoding utf8
    $script:Transcript | Out-File -LiteralPath $consoleLogPath -Encoding utf8
    Write-Host ''
    Write-Host ("摘要: " + $summaryPath)
    Write-Host ("控制台日志: " + $consoleLogPath)
}

function Resolve-AdbExe {
    $candidates = New-Object 'System.Collections.Generic.List[string]'
    if ($env:ANDROID_HOME) { $candidates.Add((Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe')) }
    if ($env:ANDROID_SDK_ROOT) { $candidates.Add((Join-Path $env:ANDROID_SDK_ROOT 'platform-tools\adb.exe')) }
    $candidates.Add('D:\platform-tools\adb.exe')
    foreach ($c in $candidates) { if (Test-Path -LiteralPath $c) { return (Get-Item -LiteralPath $c).FullName } }
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    return $null
}

function Get-FirstOnlineSerial {
    param([string]$AdbExe)
    $lines = & $AdbExe devices 2>&1
    foreach ($line in $lines) { if ($line -match '^(\S+)\s+device\b') { return $Matches[1] } }
    return ''
}

Log '=== Snishaper-Droid smoke test ===' 'Cyan'
Log ("repo: " + $repoRoot)

# ---------------- 0. MuMu / adb 在线检查 ----------------
Add-StepResult -Name '0. MuMu/adb 在线' -Status 'RUN'
$mumuScript = Join-Path $PSScriptRoot 'mumu-status.ps1'
if (-not (Test-Path -LiteralPath $mumuScript)) {
    Log '[FAIL] 缺少 scripts/mumu-status.ps1' 'Red'
    Add-StepResult -Name '0. MuMu/adb 在线' -Status 'FAIL' -Detail 'missing mumu-status.ps1'
    Save-Summary -Result 'FAIL' -Reason '缺少 mumu-status.ps1'
    exit 3
}
$mumuOut = & $mumuScript 2>&1
$mumuCode = $LASTEXITCODE
foreach ($line in $mumuOut) { Log ([string]$line) }
if ($mumuCode -eq 2) {
    Add-StepResult -Name '0. MuMu/adb 在线' -Status 'BLOCKED' -Detail 'MuMu 未启动（需用户启动 MuMu）'
    Save-Summary -Result 'BLOCKED' -Reason 'MuMu 未在线：需用户启动 MuMu 后重跑（未伪造结果）'
    Log ''
    Log '>>> 阻塞：需用户启动 MuMu，再重跑 scripts/smoke-test.ps1 <<<' 'Yellow'
    exit 2
}
if ($mumuCode -ne 0) {
    Add-StepResult -Name '0. MuMu/adb 在线' -Status 'FAIL' -Detail ("mumu-status exit " + $mumuCode)
    Save-Summary -Result 'FAIL' -Reason ('mumu-status.ps1 返回 ' + $mumuCode)
    exit $mumuCode
}

$adbExe = Resolve-AdbExe
if (-not $adbExe) {
    Log '[FAIL] 找不到 adb' 'Red'
    Add-StepResult -Name '0. MuMu/adb 在线' -Status 'FAIL' -Detail 'adb not found'
    Save-Summary -Result 'FAIL' -Reason '找不到 adb'
    exit 3
}
if ([string]::IsNullOrWhiteSpace($Serial)) { $Serial = Get-FirstOnlineSerial -AdbExe $adbExe }
if ([string]::IsNullOrWhiteSpace($Serial)) {
    Log '[FAIL] 没有在线 adb 设备' 'Red'
    Add-StepResult -Name '0. MuMu/adb 在线' -Status 'FAIL' -Detail 'no online device after mumu-status'
    Save-Summary -Result 'FAIL' -Reason '没有在线 adb 设备'
    exit 2
}
$script:Serial = $Serial
Add-StepResult -Name '0. MuMu/adb 在线' -Status 'PASS' -Detail ('serial=' + $Serial)
Log ("使用设备: " + $Serial) 'Green'

# ---------------- 1. assembleDebug ----------------
if (-not $SkipBuild) {
    Add-StepResult -Name '1. gradlew assembleDebug' -Status 'RUN'
    $gradlew = Join-Path $repoRoot 'gradlew.bat'
    if (-not (Test-Path -LiteralPath $gradlew)) {
        Log ('[FAIL] 缺少 gradle wrapper: ' + $gradlew) 'Red'
        Add-StepResult -Name '1. gradlew assembleDebug' -Status 'FAIL' -Detail 'gradlew.bat missing'
        Save-Summary -Result 'FAIL' -Reason '缺少 gradle wrapper'
        exit 3
    }
    if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'D:\Jetbrains IDES\Android Studio\jbr' }
    Log '[1/5] gradlew assembleDebug ...' 'Cyan'
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $buildOut = & $gradlew assembleDebug --console=plain 2>&1
    $buildCode = $LASTEXITCODE
    $sw.Stop()
    $gradleLog = Join-Path $logDir ("gradle-assembleDebug-" + $stamp + ".log")
    $buildOut | Out-File -LiteralPath $gradleLog -Encoding utf8
    foreach ($line in ($buildOut | Select-Object -Last 8)) { Log ([string]$line) }
    Log ("gradle log: " + $gradleLog)
    if ($buildCode -ne 0) {
        Log ('[FAIL] assembleDebug 失败（exit ' + $buildCode + '，' + [math]::Round($sw.Elapsed.TotalSeconds,1) + 's）') 'Red'
        Add-StepResult -Name '1. gradlew assembleDebug' -Status 'FAIL' -Detail ('exit=' + $buildCode)
        Save-Summary -Result 'FAIL' -Reason 'assembleDebug 失败，详见 ' + $gradleLog
        exit 1
    }
    Add-StepResult -Name '1. gradlew assembleDebug' -Status 'PASS' -Detail ([math]::Round($sw.Elapsed.TotalSeconds,1).ToString() + 's')
    Log ('[OK] assembleDebug 成功（' + [math]::Round($sw.Elapsed.TotalSeconds,1) + 's）') 'Green'
} else {
    Add-StepResult -Name '1. gradlew assembleDebug' -Status 'SKIP' -Detail '-SkipBuild'
}

# ---------------- 2. 定位 APK ----------------
Add-StepResult -Name '2. 定位 APK' -Status 'RUN'
if ([string]::IsNullOrWhiteSpace($ApkPath)) {
    $ApkPath = Join-Path $repoRoot 'app\build\outputs\apk\debug\app-debug.apk'
}
if (-not (Test-Path -LiteralPath $ApkPath)) {
    Log ('[FAIL] APK 不存在: ' + $ApkPath) 'Red'
    Add-StepResult -Name '2. 定位 APK' -Status 'FAIL' -Detail 'APK missing'
    Save-Summary -Result 'FAIL' -Reason 'APK 不存在: ' + $ApkPath
    exit 1
}
$apkItem = Get-Item -LiteralPath $ApkPath
Add-StepResult -Name '2. 定位 APK' -Status 'PASS' -Detail ($apkItem.FullName + ' (' + [math]::Round($apkItem.Length/1MB,2) + ' MB)')
Log ('[OK] APK: ' + $apkItem.FullName + ' (' + [math]::Round($apkItem.Length/1MB,2) + ' MB)') 'Green'

# ---------------- 3. adb install ----------------
Add-StepResult -Name '3. adb install' -Status 'RUN'
Log '[3/5] adb install -r ...' 'Cyan'
$installOut = (& $adbExe -s $Serial install -r $ApkPath 2>&1) -join ' '
Log ('  ' + $installOut)
if ($LASTEXITCODE -ne 0 -or $installOut -notmatch 'Success') {
    Add-StepResult -Name '3. adb install' -Status 'FAIL' -Detail (($installOut -replace '\s+',' ').Trim())
    Save-Summary -Result 'FAIL' -Reason 'adb install 失败（可能 ABI 不匹配：MuMu 需要 x86_64 AAR）'
    exit 1
}
Add-StepResult -Name '3. adb install' -Status 'PASS' -Detail 'Success'
Log '[OK] 安装成功' 'Green'

# ---------------- 4. am start ----------------
Add-StepResult -Name '4. am start' -Status 'RUN'
$component = $Package + '/' + $Activity
Log ('[4/5] am start -W -n ' + $component) 'Cyan'
$startOut = (& $adbExe -s $Serial shell am start -W -n $component 2>&1) -join "`n"
Log $startOut
$startBad = ($startOut -match 'Error type|does not exist|Unable to find|Activity class') -or ($LASTEXITCODE -ne 0)
if ($startBad) {
    Add-StepResult -Name '4. am start' -Status 'FAIL' -Detail 'am start 报错'
    Save-Summary -Result 'FAIL' -Reason 'Activity 启动失败: ' + $component
    exit 1
}
Add-StepResult -Name '4. am start' -Status 'PASS' -Detail $component
Log '[OK] Activity 启动命令已执行' 'Green'

# ---------------- 5. logcat 崩溃检测 ----------------
Add-StepResult -Name '5. logcat 崩溃检测' -Status 'RUN'
Log ('[5/5] 等待 ' + $LaunchWaitSec + 's 后抓取 logcat ...') 'Cyan'
Start-Sleep -Seconds $LaunchWaitSec
$logcatPath = Join-Path $logDir ("logcat-" + $stamp + ".txt")
$logcatFullPath = Join-Path $logDir ("logcat-full-" + $stamp + ".txt")
& $adbExe -s $Serial logcat -d -s 'Snishaper:*' 'AndroidRuntime:E' 2>&1 | Out-File -LiteralPath $logcatPath -Encoding utf8
& $adbExe -s $Serial logcat -d -t 300 2>&1 | Out-File -LiteralPath $logcatFullPath -Encoding utf8
$logcatText = Get-Content -LiteralPath $logcatPath -Raw -ErrorAction SilentlyContinue
$crashHit = ($logcatText -match 'FATAL EXCEPTION') -or ($logcatText -match 'AndroidRuntime.*FATAL') -or ($logcatText -match '\bCRASH\b')
Log ("logcat: " + $logcatPath + "；全文: " + $logcatFullPath)

$pidOut = (& $adbExe -s $Serial shell pidof $Package 2>&1) -join ''
if ([string]::IsNullOrWhiteSpace($pidOut)) {
    Log '[WARN] 进程未存活（pidof 为空），可能启动后退出/崩溃，请查看 logcat' 'Yellow'
} else {
    Log ('[OK] 进程存活 pid=' + $pidOut.Trim()) 'Green'
}

if ($crashHit) {
    Log '[FAIL] logcat 中检测到 FATAL EXCEPTION / CRASH：' 'Red'
    $logcatText -split "`n" | Select-String 'FATAL EXCEPTION|AndroidRuntime: FATAL|CRASH' | Select-Object -First 10 | ForEach-Object { Log ('  ' + $_.Line) 'Red' }
    Add-StepResult -Name '5. logcat 崩溃检测' -Status 'FAIL' -Detail 'FATAL/CRASH found'
    Save-Summary -Result 'FAIL' -Reason 'logcat 检测到崩溃，详见 ' + $logcatPath
    exit 1
}
Add-StepResult -Name '5. logcat 崩溃检测' -Status 'PASS' -Detail 'no FATAL/CRASH'
Log ''
Log 'SMOKE TEST PASS（未检测到 FATAL EXCEPTION/CRASH）' 'Green'
Save-Summary -Result 'PASS'
exit 0
