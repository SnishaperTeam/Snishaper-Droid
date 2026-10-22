#Requires -Version 7.0
<#
.SYNOPSIS
    Builds the reused desktop React frontend into the Android assets folder.

.DESCRIPTION
    Builds the reused desktop React frontend into the Android assets folder.

    The Android client reuses the desktop frontend (React 19 + MUI 9) verbatim.
    This script installs the frontend dependencies (once) and runs `vite build`
    so that the bundle lands in app/src/main/assets/frontend/, where the
    WebView loads it from file:///android_asset/frontend/index.html.

    The frontend BUILD PROJECT lives in frontend-android/ (a sibling of app/),
    NOT in app/src/main/assets/frontend/. This matters: AGP packages everything
    under app/src/main/assets/ verbatim into the APK, so keeping node_modules
    and the TypeScript sources there would bloat the APK with the whole npm
    dependency tree. vite.config.ts sets build.outDir to
    ../app/src/main/assets/frontend, so only the built bundle is emitted into
    the assets folder.

    frontend-android/src is the ported copy of SniShaper-main/frontend/src with
    the CA surface removed.

.PARAMETER SkipInstall
    Skip `npm install` (useful when node_modules is already present).

.PARAMETER SkipBuild
    Only install dependencies; do not run vite build.

.EXAMPLE
    pwsh -File scripts/build-frontend.ps1
#>
[CmdletBinding()]
param(
    [switch]$SkipInstall,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
# The build project (sources + node_modules). The build OUTPUT goes to
# $assetsDir below, see vite.config.ts (build.outDir).
$frontendDir = Join-Path $repoRoot "frontend-android"
$assetsDir = Join-Path $repoRoot "app/src/main/assets/frontend"

if (-not (Test-Path $frontendDir)) {
    throw "frontend source not found: $frontendDir"
}

# node / npm are required. Prefer the ones already on PATH.
if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    throw "node was not found in PATH (Node 18+ required to build the frontend)"
}
if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
    throw "npm was not found in PATH"
}

Write-Host "[build-frontend] node  = $(node --version)"
Write-Host "[build-frontend] npm   = $(npm --version)"
Write-Host "[build-frontend] dir   = $frontendDir"
Write-Host "[build-frontend] out   = $assetsDir"

Push-Location $frontendDir
try {
    if (-not $SkipInstall) {
        if (Test-Path "node_modules") {
            Write-Host "[build-frontend] node_modules present; refreshing with npm ci/install"
        }
        Write-Host "[build-frontend] npm install ..."
        # `npm install` (not ci) because the ported package.json may differ from
        # the desktop lockfile; CI may use `npm ci` when the lockfile is kept.
        & npm install --no-audit --no-fund
        if ($LASTEXITCODE -ne 0) { throw "npm install failed with exit code $LASTEXITCODE" }
    }

    if ($SkipBuild) { return }

    Write-Host "[build-frontend] vite build ..."
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    & npx vite build
    if ($LASTEXITCODE -ne 0) { throw "vite build failed with exit code $LASTEXITCODE" }
    $stopwatch.Stop()
}
finally {
    Pop-Location
}

# --- verify the bundle -------------------------------------------------------
$indexHtml = Join-Path $assetsDir "index.html"
if (-not (Test-Path $indexHtml)) {
    throw "vite build succeeded but index.html is missing: $indexHtml"
}

# The WebView loads the page over file://, so every asset URL must be relative.
$html = Get-Content -LiteralPath $indexHtml -Raw
if ($html -match 'src="/' -or $html -match 'href="/') {
    throw "index.html references absolute paths; vite base must be './' for file:// loading"
}

$builtAssetsDir = Join-Path $assetsDir "assets"
if (-not (Test-Path $builtAssetsDir)) {
    throw "frontend assets directory is missing: $builtAssetsDir"
}

# Guard: the sources must never leak into the APK assets (node_modules in
# particular would bloat the APK by hundreds of megabytes).
$leaked = Get-ChildItem -LiteralPath $assetsDir -Recurse -Directory |
    Where-Object { $_.Name -in @('node_modules', 'src') }
if ($leaked) {
    throw ("frontend sources leaked into the APK assets: " +
        (($leaked | ForEach-Object { $_.FullName }) -join ', '))
}

# CA guard: the shipped bundle must not contain any CA entry point.
$caPatterns = @(
    "InstallCA", "ExportCert", "RegenerateCert", "GetCACertPEM", "GetCACertPath",
    "GetCAInstallStatus", "GetInstalledCerts", "OpenCertDir", "OpenCAFile", "UninstallCert"
)
$caHits = New-Object 'System.Collections.Generic.List[string]'
foreach ($file in Get-ChildItem -Recurse -File $builtAssetsDir -Include *.js) {
    $content = Get-Content -LiteralPath $file.FullName -Raw
    foreach ($pattern in $caPatterns) {
        if ($content -match [regex]::Escape($pattern)) {
            $caHits.Add("$($file.Name): $pattern")
        }
    }
}
if ($caHits.Count -gt 0) {
    throw "CA API names found in the built frontend bundle: $($caHits -join ', ')"
}

$totalSize = (Get-ChildItem -Recurse -File $builtAssetsDir | Measure-Object -Property Length -Sum).Sum
Write-Host ""
Write-Host "[build-frontend] ==== done ===="
Write-Host ("[build-frontend] index.html : {0}" -f $indexHtml)
Write-Host ("[build-frontend] assets      : {0:N0} bytes ({1:N2} MB)" -f $totalSize, ($totalSize / 1MB))
Write-Host "[build-frontend] CA guard OK : no CA API names in the bundle"
