# Build Guide (Snishaper-Droid)

> Covers the gomobile AAR, Android APK/AAB builds, the MuMu smoke test, CA-free verification and troubleshooting.
> Platform scope and feature boundaries: [Platform_EN.md](Platform_EN.md).

## 1. Artifact matrix

| Artifact | Command | Notes |
|---|---|---|
| Debug APK (arm64-v8a) | `.\gradlew.bat :app:assembleDebug` | Local dev / devices; arm64 only by default |
| Debug APK (x86_64) | `.\gradlew.bat :app:assembleDebug -PdebugAbi=x86_64` | MuMu and other x86_64 emulators |
| Release APK (all ABIs) | `.\gradlew.bat :app:assembleRelease` | arm64-v8a / armeabi-v7a / x86_64 |
| Release AAB | `.\gradlew.bat :app:bundleRelease` | Store distribution |
| gomobile AAR | `pwsh -File scripts/build-aar.ps1` | Writes `app/libs/snishaper-core.aar` (not committed) |

Outputs: `app/build/outputs/apk/{debug,release}/`, `app/build/outputs/bundle/release/`.

## 2. Requirements

| Component | Version |
|---|---|
| JDK | 25 (Android Studio jbr 25 or Temurin 25) |
| Gradle | 9.8.0 (wrapper, Huawei Cloud mirror by default) |
| Android Gradle Plugin | 9.4.1 (built-in Kotlin; **do not** apply `org.jetbrains.kotlin.android`) |
| Android SDK | platform 36, build-tools 36.0.0 |
| Android NDK | **30.0.16248370** |
| Go | 1.27 |
| gomobile | `golang.org/x/mobile v0.0.0-20260803200217-62cee1672c8e` |
| Core library | `snishaper-core v0.4.0` |

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

The script validates Go/gomobile/NDK/JDK, asserts that `certmanager` is absent from the
`gomobile/mobile` dependency closure, runs `gomobile bind` (arm64-v8a + armeabi-v7a + x86_64,
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

## 5. Build the APK

```powershell
.\gradlew.bat :app:assembleDebug                          # arm64-v8a
.\gradlew.bat :app:assembleDebug -PdebugAbi=x86_64        # MuMu
.\gradlew.bat :app:assembleRelease                        # all ABIs
.\gradlew.bat :app:bundleRelease                          # AAB
```

> Dependencies are fetched from the Huawei Cloud Maven mirror configured in `settings.gradle.kts`
> (official repositories are the fallback).

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

Manual checklist: no crash / no `FATAL EXCEPTION`; engine start & stop works; the ☰ drawer toggles;
no CA/certificate entry anywhere; only TLS-RF / Transparent rule modes.

## 7. CA-free verification

```powershell
pwsh -File scripts/verify-aar.ps1
cd gomobile
go list -deps ./mobile | Select-String certmanager   # must be empty
```

The script checks: AAR presence / `minSdkVersion=24` / three ABIs / 16 KB page alignment for
arm64 & x86_64 / `classes.jar` contains only `App`, `Listener` and gobind runtime classes /
no `cert`/`ca`-named classes / no certificate files.

## 8. CI/CD

| Workflow | Trigger | Contents |
|---|---|---|
| `.github/workflows/android.yml` | push / PR / manual | JDK + SDK + NDK + Go + gcc → gomobile AAR → verify-aar → `lint test assembleRelease` → APK/AAB artifacts |
| `.github/workflows/release.yml` | `v*` tags / manual | Build and publish a GitHub Release (APK + AAB + version + changelog, stating “CA functionality removed”) |

## 9. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `gomobile bind` fails with `ANDROID_NDK_HOME` errors | The variable points to a missing NDK; set the installed version (e.g. 30.0.16248370) |
| `sum.golang.org` timeouts / checksum failures | Set `GOPROXY=https://goproxy.cn,direct` and `GOSUMDB=off` |
| `com.snishaper.core.mobile.*` unresolved | The AAR was not built / is missing; run `scripts/build-aar.ps1` first |
| “no longer required since AGP 9.0” for `org.jetbrains.kotlin.android` | AGP 9 has built-in Kotlin; remove that plugin (already handled here) |
| Slow Gradle downloads | Default is the Huawei Cloud Maven mirror; fall back to the official repositories if needed |
| MuMu `adb devices` empty | Enable ADB debugging in MuMu, or `adb connect 127.0.0.1:16384` |
| Large release APK | Release bundles three ABIs by design; publish per-ABI APKs or an AAB |
