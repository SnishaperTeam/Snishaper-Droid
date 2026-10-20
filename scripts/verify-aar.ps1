#requires -Version 7
# =============================================================================
# scripts/verify-aar.ps1  (ASCII-only on purpose: safe under Windows PowerShell
# 5.1 parsing, and requires PowerShell 7+ at runtime via #requires above.)
#
# Validate the gomobile-produced snishaper-core AAR against the Android bridge
# contract (docs/android-bridge-api.md sections 1 and 7):
#   1. AAR exists, can be unpacked; print size;
#   2. AndroidManifest.xml minSdkVersion must equal -AndroidApi (default 24);
#   3. jni ABI must contain arm64-v8a, armeabi-v7a and x86_64;
#   4. classes.jar application surface:
#        - MUST contain com/snishaper/core/mobile/App.class and Listener.class;
#        - ALLOWED in addition: com/snishaper/core/mobile/Mobile*.class
#          (gobind-generated runtime helper class, not application API),
#          go/** (gomobile runtime) and META-INF/**;
#        - anything else -> FAIL;
#        - no class path/name may contain cert / certificate / CA semantics;
#        - no certificate files (*.crt/pem/key/p12/jks) inside the AAR;
#   5. for 64-bit ABIs (arm64-v8a, x86_64) every .so PT_LOAD alignment must be
#      >= 0x4000 (16 KB page size, required by Android 15+/Play);
#      32-bit ABIs (armeabi-v7a) print 4 KB alignment info only.
#
# Exit codes: 0 = PASS; 1 = validation failure; 2 = AAR missing / bad argument;
#             3 = PowerShell < 7 (blocked by #requires) / unsupported runtime.
#
# Usage:
#   ./scripts/verify-aar.ps1
#   ./scripts/verify-aar.ps1 -AarPath D:/path/to/snishaper-core.aar
#   ./scripts/verify-aar.ps1 -RequiredAbis arm64-v8a,x86_64
#   ./scripts/verify-aar.ps1 -KeepTemp
#   ./scripts/verify-aar.ps1 -Quiet          # only failures + summary
#
# NOTE: All output strings are ASCII on purpose (see file header); Chinese
# documentation lives in docs/analysis/stage2-infra.md.
# =============================================================================
[CmdletBinding()]
param(
    [string]$AarPath = '',
    [int]$AndroidApi = 24,
    [string[]]$RequiredAbis = @('arm64-v8a', 'armeabi-v7a', 'x86_64'),
    [switch]$KeepTemp,
    [switch]$Quiet
)

# Defensive runtime check (normally #requires already blocks PS 5.1).
if ($PSVersionTable.PSVersion.Major -lt 7) {
    Write-Host ('[FAIL] PowerShell 7+ is required, current version: ' + $PSVersionTable.PSVersion.ToString()) -ForegroundColor Red
    Write-Host '       Run with: pwsh -NoProfile -File scripts/verify-aar.ps1' -ForegroundColor Red
    exit 3
}

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
if ([string]::IsNullOrWhiteSpace($AarPath)) {
    $AarPath = Join-Path $repoRoot 'app/libs/snishaper-core.aar'
}
if (-not [System.IO.Path]::IsPathRooted($AarPath)) {
    $AarPath = Join-Path $repoRoot $AarPath
}

$script:Failures = New-Object 'System.Collections.Generic.List[string]'
$script:Warnings = New-Object 'System.Collections.Generic.List[string]'
function Add-Failure([string]$Message) { $script:Failures.Add($Message); Write-Host ('  [FAIL] ' + $Message) -ForegroundColor Red }
function Add-Warning([string]$Message) { $script:Warnings.Add($Message); Write-Host ('  [WARN] ' + $Message) -ForegroundColor Yellow }
function Add-Ok([string]$Message) { if (-not $Quiet) { Write-Host ('  [ OK ] ' + $Message) -ForegroundColor Green } }

$sw = [System.Diagnostics.Stopwatch]::StartNew()
Write-Host ''
Write-Host '=== verify-aar: snishaper-core AAR validation ===' -ForegroundColor Cyan
Write-Host ('AAR       : ' + $AarPath)
Write-Host ('androidapi: ' + $AndroidApi + '   required ABIs: ' + ($RequiredAbis -join ', '))

# ---------------- 0. existence ----------------
if (-not (Test-Path -LiteralPath $AarPath -PathType Leaf)) {
    Write-Host ('[FAIL] AAR not found: ' + $AarPath) -ForegroundColor Red
    Write-Host '       Build it first: pwsh -NoProfile -File scripts/build-aar.ps1' -ForegroundColor Red
    exit 2
}
$aarItem = Get-Item -LiteralPath $AarPath
$aarSizeMb = [math]::Round($aarItem.Length / 1MB, 2)
Write-Host ('size      : {0:N0} bytes ({1} MB)   mtime: {2}' -f $aarItem.Length, $aarSizeMb, $aarItem.LastWriteTime)
if ($aarItem.Length -lt 1MB) { Add-Warning 'AAR is smaller than 1 MB; it may only contain Java code.' }

# ---------------- 1. extract ----------------
# Type normally exists on PS7 / .NET; Add-Type is only a fallback for old hosts.
if (-not ('System.IO.Compression.ZipFile' -as [type])) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
}
$tmp = Join-Path $env:TEMP ('snishaper-aar-verify-' + [guid]::NewGuid().ToString('N'))
[System.IO.Compression.ZipFile]::ExtractToDirectory($aarItem.FullName, $tmp)
if (-not $Quiet) { Write-Host ('unpacked  : ' + $tmp) }

try {
    # ---------------- 2. minSdk ----------------
    Write-Host ''
    Write-Host '[1/5] AndroidManifest.xml minSdkVersion' -ForegroundColor Cyan
    $manifestPath = Join-Path $tmp 'AndroidManifest.xml'
    if (-not (Test-Path -LiteralPath $manifestPath)) {
        Add-Failure 'AndroidManifest.xml is missing at AAR root'
    } else {
        $manifestText = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8
        if ($manifestText -match 'minSdkVersion\s*=\s*"(\d+)"') {
            $minSdk = [int]$Matches[1]
            if ($minSdk -eq $AndroidApi) { Add-Ok ('minSdkVersion = ' + $minSdk) }
            else { Add-Failure ('minSdkVersion = ' + $minSdk + ', expected ' + $AndroidApi + ' (gomobile -androidapi ' + $AndroidApi + ')') }
        } else {
            Add-Failure 'minSdkVersion attribute not found in AndroidManifest.xml'
        }
    }

    # ---------------- 3. jni ABIs ----------------
    Write-Host ''
    Write-Host '[2/5] jni ABI and .so files' -ForegroundColor Cyan
    $jniDir = Join-Path $tmp 'jni'
    $abis = @()
    if (-not (Test-Path -LiteralPath $jniDir)) {
        Add-Failure 'jni/ directory is missing (AAR has no native libraries)'
    } else {
        $abis = @(Get-ChildItem -LiteralPath $jniDir -Directory | Select-Object -ExpandProperty Name)
        Write-Host ('  ABIs found: ' + ($abis -join ', '))
        foreach ($abi in $RequiredAbis) {
            if ($abis -contains $abi) { Add-Ok ('ABI present: ' + $abi) }
            else { Add-Failure ('required ABI missing: ' + $abi + ' (add it to gomobile -target)') }
        }
        $extra = @($abis | Where-Object { $RequiredAbis -notcontains $_ })
        if ($extra.Count -gt 0) { Add-Warning ('extra ABI(s) found (allowed): ' + ($extra -join ', ')) }

        foreach ($abi in $abis) {
            $abiDir = Join-Path $jniDir $abi
            $soList = @(Get-ChildItem -LiteralPath $abiDir -File -Filter '*.so')
            if ($soList.Count -eq 0) { Add-Failure ('no .so under ABI ' + $abi); continue }
            foreach ($so in $soList) {
                Write-Host ('  ' + $abi + '/' + $so.Name + ' : ' + ('{0:N0}' -f $so.Length) + ' bytes')
            }
            if (-not ($soList | Where-Object { $_.Name -eq 'libgojni.so' })) {
                Add-Warning ('ABI ' + $abi + ' has no libgojni.so (gomobile bind normally produces libgojni.so)')
            }
        }
    }

    # ---------------- 4. 16 KB page alignment ----------------
    Write-Host ''
    Write-Host '[3/5] .so 16 KB page alignment (64-bit ABIs require PT_LOAD Align >= 0x4000)' -ForegroundColor Cyan
    function Get-ElfProgramHeaderInfo {
        param([string]$Path)
        $bytes = [System.IO.File]::ReadAllBytes($Path)
        if ($bytes.Length -lt 64) { throw 'file too small to be a valid ELF' }
        if (-not ($bytes[0] -eq 0x7F -and $bytes[1] -eq 0x45 -and $bytes[2] -eq 0x4C -and $bytes[3] -eq 0x46)) {
            throw 'not an ELF file (bad magic)'
        }
        $elfClass = $bytes[4]      # 1 = ELF32, 2 = ELF64
        $elfData = $bytes[5]       # 1 = little endian, 2 = big endian
        if ($elfData -ne 1) { throw 'only little-endian ELF is supported (all Android ABIs are LE)' }
        $is64 = ($elfClass -eq 2)
        if ($is64) {
            $phoff = [BitConverter]::ToUInt64($bytes, 0x20)
            $phentsize = [BitConverter]::ToUInt16($bytes, 0x36)
            $phnum = [BitConverter]::ToUInt16($bytes, 0x38)
            $pTypeOffset = 0
            $pAlignOffset = 0x30
        } else {
            $phoff = [BitConverter]::ToUInt32($bytes, 0x1C)
            $phentsize = [BitConverter]::ToUInt16($bytes, 0x2A)
            $phnum = [BitConverter]::ToUInt16($bytes, 0x2C)
            $pTypeOffset = 0
            $pAlignOffset = 0x1C
        }
        $aligns = New-Object 'System.Collections.Generic.List[uint64]'
        for ($i = 0; $i -lt $phnum; $i++) {
            $base = [int]$phoff + ($i * $phentsize)
            if (($base + $phentsize) -gt $bytes.Length) { break }
            $pType = [BitConverter]::ToUInt32($bytes, $base + $pTypeOffset)
            if ($pType -ne 1) { continue }  # PT_LOAD
            $align = if ($is64) { [BitConverter]::ToUInt64($bytes, $base + $pAlignOffset) } else { [uint64][BitConverter]::ToUInt32($bytes, $base + $pAlignOffset) }
            $aligns.Add($align)
        }
        $maxAlign = [uint64]0
        if ($aligns.Count -gt 0) { $maxAlign = [uint64](($aligns | Measure-Object -Maximum).Maximum) }
        return [pscustomobject]@{ Is64 = $is64; Aligns = $aligns; MaxAlign = $maxAlign }
    }

    if (Test-Path -LiteralPath $jniDir) {
        foreach ($abi in $abis) {
            foreach ($so in @(Get-ChildItem -LiteralPath (Join-Path $jniDir $abi) -File -Filter '*.so')) {
                try {
                    $info = Get-ElfProgramHeaderInfo -Path $so.FullName
                    $maxAlignHex = ('0x' + [Convert]::ToString([uint64]$info.MaxAlign, 16))
                    if ($info.Is64) {
                        if ($info.MaxAlign -ge 0x4000) {
                            Add-Ok ($abi + '/' + $so.Name + ' PT_LOAD Align = ' + $maxAlignHex + ' (16 KB aligned)')
                        } else {
                            Add-Failure ($abi + '/' + $so.Name + ' PT_LOAD Align = ' + $maxAlignHex + ' < 0x4000; Android 15+/Play 16 KB page requirement not met')
                        }
                    } else {
                        Write-Host ('  [INFO] ' + $abi + '/' + $so.Name + ' is 32-bit ELF, PT_LOAD Align = ' + $maxAlignHex + ' (no 16 KB requirement for 32-bit)')
                        if ($info.MaxAlign -lt 0x1000) { Add-Failure ($abi + '/' + $so.Name + ' unusual alignment: ' + $maxAlignHex) }
                    }
                } catch {
                    Add-Failure ($abi + '/' + $so.Name + ' ELF parse failed: ' + $_.Exception.Message)
                }
            }
        }
    }

    # ---------------- 5. classes.jar surface / CA-free ----------------
    Write-Host ''
    Write-Host '[4/5] classes.jar application surface and CA-free scan' -ForegroundColor Cyan
    $classesJar = Join-Path $tmp 'classes.jar'
    $expectedClasses = @(
        'com/snishaper/core/mobile/App.class',
        'com/snishaper/core/mobile/Listener.class'
    )
    if (-not (Test-Path -LiteralPath $classesJar)) {
        Add-Failure 'classes.jar is missing'
    } else {
        $zip = [System.IO.Compression.ZipFile]::OpenRead($classesJar)
        try { $entries = @($zip.Entries | ForEach-Object { $_.FullName }) }
        finally { $zip.Dispose() }
        $classEntries = @($entries | Where-Object { $_ -match '\.class$' })
        Write-Host ('  classes.jar entries: ' + $entries.Count + ' (classes: ' + $classEntries.Count + ')')

        # Required application API classes.
        foreach ($expected in $expectedClasses) {
            if ($classEntries -contains $expected) { Add-Ok ('present: ' + $expected) }
            else { Add-Failure ('contract class missing: ' + $expected + ' (gomobile -javapkg com.snishaper.core + Go package mobile)') }
        }

        # Allowed class patterns:
        #   - application API: App / Listener (incl. inner classes)
        #   - gobind runtime helper: Mobile / Mobile$* (generated because the Go
        #     package is named "mobile"; not part of the application API)
        #   - gomobile runtime: go/**
        #   - META-INF/**
        $appClassRegex = '^com/snishaper/core/mobile/(App|Listener)(\$[^/]*)?\.class$'
        $gobindRuntimeRegex = '^com/snishaper/core/mobile/Mobile(\$[^/]*)?\.class$'
        $unexpected = @($classEntries | Where-Object {
                $_ -notmatch '^go/' -and
                $_ -notmatch '^META-INF/' -and
                $_ -notmatch $appClassRegex -and
                $_ -notmatch $gobindRuntimeRegex
            })
        if ($unexpected.Count -eq 0) {
            Add-Ok 'application classes limited to App/Listener; gobind runtime Mobile*/go/** allowed'
        } else {
            foreach ($u in $unexpected) { Add-Failure ('unexpected application class: ' + $u) }
        }

        # CA-free scan (class path / simple name).
        $caHits = @()
        foreach ($entry in $classEntries) {
            $simple = [System.IO.Path]::GetFileNameWithoutExtension($entry).ToLowerInvariant()
            $pathLower = $entry.ToLowerInvariant()
            $isHit = $false
            if ($pathLower -match '/cert' -or $pathLower -match 'certmanager') { $isHit = $true }
            if ($simple -match '^cert') { $isHit = $true }
            if ($simple -match '^ca($|[_\$0-9])' -or $simple -match 'certificate') { $isHit = $true }
            if ($isHit) { $caHits += $entry }
        }
        if ($caHits.Count -eq 0) {
            Add-Ok 'no cert / ca named classes found (CA-free contract satisfied)'
        } else {
            foreach ($h in $caHits) { Add-Failure ('CA-related class found: ' + $h) }
        }

        # No certificate files inside the AAR.
        $certFiles = @($entries | Where-Object { $_ -match '\.(crt|pem|key|p12|jks)$' })
        if ($certFiles.Count -gt 0) {
            foreach ($f in $certFiles) { Add-Failure ('certificate file found inside AAR: ' + $f) }
        } else {
            Add-Ok 'no certificate files inside AAR (*.crt/pem/key/p12/jks)'
        }
    }

    # ---------------- 6. summary ----------------
    Write-Host ''
    Write-Host '[5/5] summary' -ForegroundColor Cyan
    $elapsed = [math]::Round($sw.Elapsed.TotalSeconds, 1)
    Write-Host ('  AAR size: ' + $aarSizeMb + ' MB   elapsed: ' + $elapsed + ' s')
    if ($script:Failures.Count -eq 0) {
        Write-Host ''
        Write-Host ('PASS  AAR validation passed (' + $elapsed + ' s, ' + $aarSizeMb + ' MB)') -ForegroundColor Green
        if ($script:Warnings.Count -gt 0) {
            Write-Host ('WARN  ' + $script:Warnings.Count + ' warning(s):') -ForegroundColor Yellow
            foreach ($w in $script:Warnings) { Write-Host ('  - ' + $w) -ForegroundColor Yellow }
        }
        exit 0
    } else {
        Write-Host ''
        Write-Host ('FAIL  AAR validation failed: ' + $script:Failures.Count + ' item(s)') -ForegroundColor Red
        foreach ($f in $script:Failures) { Write-Host ('  - ' + $f) -ForegroundColor Red }
        exit 1
    }
} finally {
    if (-not $KeepTemp) {
        Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue
    } else {
        Write-Host ('[keep] unpacked directory kept: ' + $tmp) -ForegroundColor Yellow
    }
}
