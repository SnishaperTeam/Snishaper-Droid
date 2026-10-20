#Requires -Version 7.0
<#
.SYNOPSIS
    Builds the CA-free gomobile AAR for SniShaper Droid.

.DESCRIPTION
    Fixes the machine environment values that are known to be wrong/stale in a
    fresh shell, runs the wrapper module self-checks, then invokes gomobile bind
    for the requested Android ABIs and prints the artifact summary.

    The script never modifies snishaper-core or SniShaper-main.

.PARAMETER Target
    gomobile -target value. Defaults to the three ABIs shipped in the AAR:
    arm64-v8a (real devices), armeabi-v7a (old devices) and x86_64 (MuMu).

.PARAMETER Output
    AAR output path. Defaults to <repo>/app/libs/snishaper-core.aar.

.PARAMETER NoTest
    Skip the host `go test ./mobile` self-check.

.EXAMPLE
    pwsh -File scripts/build-aar.ps1
#>
[CmdletBinding()]
param(
    [string]$Target = "android/arm64,android/arm,android/amd64",
    [string]$Output = "",
    [switch]$NoTest
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$gomobileDir = Join-Path $repoRoot "gomobile"
if (-not $Output) {
    $Output = Join-Path $repoRoot "app/libs/snishaper-core.aar"
}

# --- 0) environment correction (a new shell inherits the machine defaults) ---
$env:JAVA_HOME = "D:\Jetbrains IDES\Android Studio\jbr"          # jbr 25.0.3
$env:ANDROID_HOME = "D:\AndroidSDKs"
$env:ANDROID_NDK_HOME = "D:\AndroidSDKs\ndk\30.0.16248370"       # NOT the stale 27.2 path
$env:GOPROXY = "https://goproxy.cn,direct"
$env:GOSUMDB = "off"
$env:GOFLAGS = "-mod=mod"
$env:CGO_ENABLED = "1"
Remove-Item Env:GOOS -ErrorAction SilentlyContinue
Remove-Item Env:GOARCH -ErrorAction SilentlyContinue

Write-Host "[build-aar] JAVA_HOME         = $env:JAVA_HOME"
Write-Host "[build-aar] ANDROID_HOME      = $env:ANDROID_HOME"
Write-Host "[build-aar] ANDROID_NDK_HOME  = $env:ANDROID_NDK_HOME"
Write-Host "[build-aar] GOPROXY / GOSUMDB = $env:GOPROXY / $env:GOSUMDB"
Write-Host "[build-aar] target            = $Target"
Write-Host "[build-aar] output            = $Output"

# --- 1) precondition checks -------------------------------------------------
foreach ($tool in @("go", "gomobile", "gobind")) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        throw "$tool was not found in PATH"
    }
}
if (-not (Test-Path $env:ANDROID_NDK_HOME)) {
    throw "ANDROID_NDK_HOME does not exist: $env:ANDROID_NDK_HOME"
}
if (-not (Test-Path (Join-Path $env:JAVA_HOME "bin/javac.exe"))) {
    throw "javac.exe not found under JAVA_HOME: $env:JAVA_HOME"
}
if (-not (Test-Path (Join-Path $gomobileDir "go.mod"))) {
    throw "gomobile wrapper module not found at $gomobileDir"
}

$outputDir = Split-Path -Parent $Output
if (-not (Test-Path $outputDir)) {
    New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
}

Push-Location $gomobileDir
try {
    # --- 2) host self-checks ------------------------------------------------
    Write-Host "[build-aar] go build ./mobile (host sanity check)"
    & go build ./mobile
    if ($LASTEXITCODE -ne 0) { throw "host go build failed" }

    if (-not $NoTest) {
        Write-Host "[build-aar] go test ./mobile"
        & go test ./mobile
        if ($LASTEXITCODE -ne 0) { throw "go test failed" }
    }

    Write-Host "[build-aar] CA guard: go list -deps ./mobile must not contain certmanager"
    $deps = & go list -deps ./mobile
    if ($LASTEXITCODE -ne 0) { throw "go list -deps failed" }
    $caHits = @($deps | Select-String -SimpleMatch "snishaper-core/certmanager")
    if ($caHits.Count -gt 0) {
        throw "certmanager is in the mobile import graph: $($caHits -join ', ')"
    }
    Write-Host "[build-aar] CA guard OK: certmanager is not in the mobile import graph"

    # --- 3) gomobile bind ---------------------------------------------------
    # -trimpath + -ldflags "-s -w" keep the AAR under GitHub's 100 MB file
    # limit (the unstripped 3-ABI build was ~134 MB, the stripped one ~70 MB)
    # while preserving the same exported Java surface and CA-free guarantees.
    Write-Host "[build-aar] gomobile bind (this compiles three ABIs; expect a few minutes)"
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    & gomobile bind `
        "-target=$Target" `
        "-androidapi=24" `
        "-tags=with_gvisor" `
        "-javapkg=com.snishaper.core" `
        "-trimpath" `
        "-ldflags=-s -w" `
        "-o=$Output" `
        "./mobile"
    $exitCode = $LASTEXITCODE
    $stopwatch.Stop()
    if ($exitCode -ne 0) {
        throw "gomobile bind failed with exit code $exitCode"
    }
    $elapsed = $stopwatch.Elapsed
}
finally {
    Pop-Location
}

# --- 4) artifact summary ----------------------------------------------------
if (-not (Test-Path $Output)) {
    throw "gomobile reported success but the AAR does not exist: $Output"
}
$aar = Get-Item $Output
$hash = (Get-FileHash -Algorithm SHA256 -Path $Output).Hash

Add-Type -AssemblyName System.IO.Compression.FileSystem
$abis = @()
$jniLibs = @()
$zip = [System.IO.Compression.ZipFile]::OpenRead($Output)
try {
    foreach ($entry in $zip.Entries) {
        if ($entry.FullName -match '^jni/([^/]+)/') {
            if ($abis -notcontains $Matches[1]) { $abis += $Matches[1] }
            $jniLibs += [pscustomobject]@{
                Path = $entry.FullName
                Size = $entry.Length
            }
        }
    }
}
finally {
    $zip.Dispose()
}

Write-Host ""
Write-Host "[build-aar] ==== done ===="
Write-Host ("[build-aar] AAR      : {0}" -f $aar.FullName)
Write-Host ("[build-aar] size     : {0:N0} bytes ({1:N1} MB)" -f $aar.Length, ($aar.Length / 1MB))
Write-Host ("[build-aar] sha256   : {0}" -f $hash)
Write-Host ("[build-aar] elapsed  : {0:N1} s" -f $elapsed.TotalSeconds)
Write-Host ("[build-aar] ABIs     : {0}" -f ($abis -join ", "))
foreach ($lib in $jniLibs) {
    Write-Host ("[build-aar]   {0}  {1:N0} bytes" -f $lib.Path, $lib.Size)
}
