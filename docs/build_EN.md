# Build Guide (Snishaper-Droid)

> Covers the gomobile AAR, Android APK/AAB builds and signing, the MuMu smoke test, AAR verification and troubleshooting.
> Platform scope and feature boundaries: [Platform_EN.md](Platform_EN.md).

## 1. Artifact matrix

| Artifact | Command | Notes |
|---|---|---|
| Debug APK (arm64-v8a) | `.\gradlew.bat :app:assembleDebug` | Local dev / devices; arm64 only by default |
| Debug APK (x86_64) | `.\gradlew.bat :app:assembleDebug -PdebugAbi=x86_64` | MuMu and other x86_64 emulators |
| Signed release APK + AAB | `pwsh -File scripts/build-apk.ps1 -Bundle` | Verifies the signature, writes to `build/dist/` |
| Release APK (all ABIs, manual) | `.\gradlew.bat :app:assembleRelease` | arm64-v8a / armeabi-v7a / x86_64 |
| Release AAB | `.\gradlew.bat :app:bundleRelease` | Store distribution |
| gomobile AAR | `pwsh -File scripts/build-aar.ps1` | Writes `app/libs/snishaper-core.aar` (not committed) |
| Signing status | `.\gradlew.bat :app:signingInfo` | Prints the resolved keystore/alias or the missing pieces |

Outputs: `app/build/outputs/apk/{debug,release}/`, `app/build/outputs/bundle/release/`.
`scripts/build-apk.ps1` additionally copies the artifacts to `build/dist/` with `SHA256SUMS.txt`.

## 2. Requirements

| Component | Version |
|---|---|
| JDK | 25 (Android Studio jbr or Temurin 25) |
| Gradle | 9.8.2 (wrapper, Huawei Cloud mirror by default) |
| Android Gradle Plugin | 9.5.0-rc01 (built-in Kotlin; **do not** apply `org.jetbrains.kotlin.android`) |
| Android SDK | compileSdk 37 (minor 2), build-tools 37.0.0, targetSdk 36 |
| Android NDK | **30.0.16248370** |
| Go | 1.27 |
| gomobile | `golang.org/x/mobile v0.0.0-20260803200217-62cee1672c8e` |
| Core library | `snishaper-core v0.8.4` |

## 3. Environment variables

```powershell
$env:JAVA_HOME        = "D:\Jetbrains IDES\Android Studio\jbr"
$env:ANDROID_HOME     = "D:\AndroidSDKs"
$env:ANDROID_NDK_HOME = "D:\AndroidSDKs\ndk\30.0.16248370"
$env:GOPROXY          = "https://goproxy.cn,direct"
$env:GOSUMDB          = "off"
# or: . .\scripts\dev-env.ps1
```

## 4. Build the gomobile AAR

```powershell
pwsh -File scripts/build-aar.ps1
```

The script validates Go/gomobile/NDK/JDK, asserts that `certmanager` **is present** in the
`gomobile/mobile` dependency closure (CA-support check), runs `gomobile bind` (arm64-v8a + armeabi-v7a + x86_64,
`-androidapi 24`, `-tags with_gvisor`, `-trimpath -ldflags "-s -w"`) and prints size/sha256.

Equivalent single command:

```powershell
cd gomobile
gomobile bind -target=android/arm64,android/arm,android/amd64 `
  -androidapi 24 -tags with_gvisor -trimpath -ldflags "-s -w" `
  -javapkg com.snishaper.core -o ../app/libs/snishaper-core.aar ./mobile
```

> The AAR is ~**74.7 MB** (stripped, three ABIs) and is a build artifact — it is **not committed**;
> CI rebuilds it and uploads it as an artifact.

## 5. Build and sign the APK / AAB

### 5.1 One-time keystore setup

```powershell
pwsh -File scripts/init-keystore.ps1
```

Two files are created, **both git-ignored**:

| File | Contents |
|---|---|
| `keystore/release.jks` | PKCS12 keystore, RSA 4096, 30 years, alias `snishaper` |
| `keystore.properties` | `storeFile` / `storePassword` / `keyAlias` / `keyPassword` |

The script prints the generated password and the certificate SHA-256. **Back both files up offline**: Android
only accepts updates signed by the same key, so a lost keystore makes every published version unupgradable.

### 5.2 Build

```powershell
pwsh -File scripts/build-apk.ps1           # signed release APK
pwsh -File scripts/build-apk.ps1 -Bundle   # + AAB
```

The script fills in a missing AAR and frontend bundle, runs `assembleRelease` (plus `bundleRelease`), verifies the
APK with `apksigner` (fails when unsigned), copies the artifacts to
`build/dist/snishaper-droid-<version>.{apk,aab}` and writes `SHA256SUMS.txt`.

Gradle works directly too (signing is picked up whenever `keystore.properties` exists):

```powershell
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:bundleRelease
.\gradlew.bat :app:signingInfo      # confirm the signing config was resolved
```

### 5.3 Signature schemes

The release config enables **v2 + v3 + v4**:

| Scheme | State | Why |
|---|---|---|
| v1 (JAR) | off | `minSdk 24` validates v2+ only; v1 just adds size |
| v2 | on | Primary check on Android 7.0+ |
| v3 | on | Supports key rotation |
| v4 | on | Emits `.idsig` for incremental install |

### 5.4 Signing material precedence

| Order | Source | Usage |
|---|---|---|
| 1 | `keystore.properties` (repository root) | Local development |
| 2 | `SNISHAPER_KEYSTORE` / `SNISHAPER_KEYSTORE_PASSWORD` / `SNISHAPER_KEY_ALIAS` / `SNISHAPER_KEY_PASSWORD` | CI / no repository file |
| 3 | none | Release emits `app-release-unsigned.apk`; `build-apk.ps1` aborts |

`SNISHAPER_KEYSTORE` accepts an absolute path or a path relative to the repository root.

### 5.5 CI signing secrets

The GitHub release workflow reads:

| Secret | Contents |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 keystore/release.jks` (PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("keystore/release.jks"))`) |
| `ANDROID_KEYSTORE_PASSWORD` | `storePassword` |
| `ANDROID_KEY_ALIAS` | `keyAlias` |
| `ANDROID_KEY_PASSWORD` | `keyPassword` |

With all four present the pipeline imports the keystore, signs, and gates on `apksigner verify`. With any of them
missing it prints `::warning::` and produces an unsigned package — confirm the verification line in the log before
publishing.

## 6. MuMu smoke test

```powershell
adb devices
adb connect 127.0.0.1:16384          # if the list is empty after starting MuMu
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.snishaper.android/.MainActivity
adb logcat -s Snishaper:* AndroidRuntime:E
```

Helper scripts (they exit with code 2 and an explicit message when MuMu is offline — results are never faked):

```powershell
pwsh -File scripts/mumu-status.ps1
pwsh -File scripts/smoke-test.ps1
```

Manual checklist: no crash / no `FATAL EXCEPTION`; the engine starts and stops; TUN triggers the system VPN
consent; the CA status card renders on the Dashboard; the ☰ drawer toggles; Settings only lists port, language,
appearance, theme, rule auto-update, TUN MTU, certificates, IPv6 and the CF IP pool and named CDN pools; the Logs page
streams the engine ring buffer with pause/clear/export/diagnostics; connections, profiles, match explanation and the
migration report all render.

## 7. AAR verification

```powershell
python3 scripts/verify_aar.py
cd gomobile
go list -deps ./mobile | Select-String certmanager   # must have output (CA support in the graph)
```

The script checks: AAR presence / `minSdkVersion=24` / three ABIs / 16 KB page alignment for
arm64 & x86_64 / `classes.jar` contains only `App`, `Listener` and gobind runtime classes /
no certificate files embedded.

## 8. CI/CD

| Workflow | Trigger | Contents |
|---|---|---|
| `.github/workflows/android.yml` | push / PR / manual | JDK + SDK + NDK + Go + gcc → gomobile AAR → verify_aar → `lint test assembleDebug` → APK/AAR artifacts |
| `.github/workflows/release.yml` | manual dispatch (confirm version + channel; bot commits the bump and tags) | Import keystore → `signingInfo lint test assembleRelease bundleRelease` → `apksigner verify` → GitHub Release (signed APK + AAB + AAR + `SHA256SUMS.txt`) |

## 9. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `gomobile bind` fails with `ANDROID_NDK_HOME` errors | The variable points to a missing NDK; set the installed version (e.g. 30.0.16248370) |
| `sum.golang.org` timeouts / checksum failures | Set `GOPROXY=https://goproxy.cn,direct` and `GOSUMDB=off` |
| `com.snishaper.core.mobile.*` unresolved | The AAR was not built / is missing; run `scripts/build-aar.ps1` first |
| Release emits `app-release-unsigned.apk` | No signing material found; run `.\gradlew.bat :app:signingInfo`, then `scripts/init-keystore.ps1` |
| `apksigner` reports `DOES NOT VERIFY` | Keystore differs from the installed build; uninstall the old app or restore the original `keystore/release.jks` |
| “no longer required since AGP 9.0” for `org.jetbrains.kotlin.android` | AGP 9 has built-in Kotlin; remove that plugin (already handled here) |
| Slow Gradle downloads | Default is the Huawei Cloud Maven mirror; fall back to the official repositories if needed |
| MuMu `adb devices` empty | Enable ADB debugging in MuMu, or `adb connect 127.0.0.1:16384` |
| Large release APK | Three ABIs of uncompressed `.so` (16 KB alignment) by design; ship an AAB or per-ABI APKs |
