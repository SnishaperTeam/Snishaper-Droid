#Requires -Version 7.0
<#
.SYNOPSIS
    Builds a signed release APK (and optionally an AAB) for Snishaper-Droid.

.DESCRIPTION
    End-to-end local release pipeline:

      1. corrects the known-stale environment values of a fresh shell;
      2. builds the gomobile AAR when app/libs/snishaper-core.aar is missing;
      3. builds the React bundle into app/src/main/assets/frontend when missing;
      4. runs `:app:assembleRelease` (and `:app:bundleRelease` with -Bundle);
      5. verifies the APK signature with apksigner and fails when unsigned;
      6. copies the artifacts to build/dist with a versioned name plus SHA256SUMS.txt.

    Signing material comes from keystore.properties (see scripts/init-keystore.ps1)
    or the SNISHAPER_KEYSTORE* environment variables; without it Gradle emits an
    unsigned APK and this script stops with an explicit error.

.PARAMETER Bundle
    Also build the release AAB.

.PARAMETER SkipAar
    Do not rebuild a missing AAR (fails instead when it is absent).

.PARAMETER SkipFrontend
    Do not rebuild a missing frontend bundle (fails instead when it is absent).

.EXAMPLE
    pwsh -File scripts/build-apk.ps1 -Bundle
#>
[CmdletBinding()]
param(
    [switch]$Bundle,
    [switch]$SkipAar,
    [switch]$SkipFrontend
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$appDir = Join-Path $repoRoot "app"
$aarPath = Join-Path $appDir "libs/snishaper-core.aar"
$frontendIndex = Join-Path $appDir "src/main/assets/frontend/index.html"
$distDir = Join-Path $repoRoot "build/dist"

$env:JAVA_HOME = "D:\Jetbrains IDES\Android Studio\jbr"
$env:ANDROID_HOME = "D:\AndroidSDKs"
$env:ANDROID_NDK_HOME = "D:\AndroidSDKs\ndk\30.0.16248370"

if (-not (Test-Path $aarPath)) {
    if ($SkipAar) { throw "snishaper-core.aar is missing: $aarPath" }
    Write-Host "[build-apk] AAR missing, building it first"
    & pwsh -File (Join-Path $PSScriptRoot "build-aar.ps1")
    if ($LASTEXITCODE -ne 0) { throw "build-aar.ps1 failed" }
}

if (-not (Test-Path $frontendIndex)) {
    if ($SkipFrontend) { throw "frontend bundle is missing: $frontendIndex" }
    Write-Host "[build-apk] frontend bundle missing, building it first"
    & pwsh -File (Join-Path $PSScriptRoot "build-frontend.ps1")
    if ($LASTEXITCODE -ne 0) { throw "build-frontend.ps1 failed" }
}

$versionName = (Select-String -Path (Join-Path $appDir "build.gradle.kts") -Pattern 'versionName\s*=\s*"([^"]+)"').Matches[0].Groups[1].Value
Write-Host "[build-apk] version = $versionName"

Push-Location $repoRoot
try {
    $tasks = if ($Bundle) { @(":app:assembleRelease", ":app:bundleRelease") } else { @(":app:assembleRelease") }
    Write-Host "[build-apk] gradlew $($tasks -join ' ')"
    & .\gradlew.bat --no-daemon --stacktrace @tasks
    if ($LASTEXITCODE -ne 0) { throw "gradle build failed with exit code $LASTEXITCODE" }
}
finally {
    Pop-Location
}

$apk = Get-ChildItem -Path (Join-Path $appDir "build/outputs/apk/release") -Filter "*.apk" |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $apk) { throw "no APK produced under app/build/outputs/apk/release" }
if ($apk.Name -match "unsigned") {
    throw "release APK is unsigned; create keystore.properties with scripts/init-keystore.ps1"
}

$buildTools = Get-ChildItem -Path (Join-Path $env:ANDROID_HOME "build-tools") -Directory |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
$apksigner = Join-Path $buildTools.FullName "apksigner.bat"
if (-not (Test-Path $apksigner)) { throw "apksigner not found under $($buildTools.FullName)" }

Write-Host "[build-apk] verifying signature"
& $apksigner verify --verbose --print-certs $apk.FullName
if ($LASTEXITCODE -ne 0) { throw "apksigner verify failed: the APK is not correctly signed" }

if (-not (Test-Path $distDir)) { New-Item -ItemType Directory -Force -Path $distDir | Out-Null }
$apkTarget = Join-Path $distDir ("snishaper-droid-{0}.apk" -f $versionName)
Copy-Item -LiteralPath $apk.FullName -Destination $apkTarget -Force
$artifacts = @($apkTarget)

if ($Bundle) {
    $aab = Get-ChildItem -Path (Join-Path $appDir "build/outputs/bundle/release") -Filter "*.aab" |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($aab) {
        $aabTarget = Join-Path $distDir ("snishaper-droid-{0}.aab" -f $versionName)
        Copy-Item -LiteralPath $aab.FullName -Destination $aabTarget -Force
        $artifacts += $aabTarget
    }
}

$sums = foreach ($file in $artifacts) {
    $hash = (Get-FileHash -Algorithm SHA256 -Path $file).Hash.ToLowerInvariant()
    "{0}  {1}" -f $hash, (Split-Path -Leaf $file)
}
$sums | Set-Content -LiteralPath (Join-Path $distDir "SHA256SUMS.txt") -Encoding utf8

Write-Host ""
Write-Host "[build-apk] ==== done ===="
foreach ($file in $artifacts) {
    $item = Get-Item $file
    Write-Host ("[build-apk] {0}  {1:N1} MB" -f $item.FullName, ($item.Length / 1MB))
}
Write-Host ("[build-apk] checksums : {0}" -f (Join-Path $distDir "SHA256SUMS.txt"))
