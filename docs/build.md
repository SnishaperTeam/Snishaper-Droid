# 构建指南（Snishaper-Droid）

> 本文覆盖 gomobile AAR、Android APK/AAB 构建、MuMu 冒烟测试、CA-free 校验与常见问题。
> 平台支持与功能边界见 [Platform.md](Platform.md)。

## 1. 产物矩阵

| 产物 | 命令 | 说明 |
|---|---|---|
| Debug APK（arm64-v8a） | `.\gradlew.bat :app:assembleDebug` | 本地开发 / 真机，默认只打 arm64 |
| Debug APK（x86_64） | `.\gradlew.bat :app:assembleDebug -PdebugAbi=x86_64` | MuMu 等 x86_64 模拟器 |
| Release APK（全 ABI） | `.\gradlew.bat :app:assembleRelease` | arm64-v8a / armeabi-v7a / x86_64 |
| Release AAB | `.\gradlew.bat :app:bundleRelease` | 应用商店分发 |
| gomobile AAR | `pwsh -File scripts/build-aar.ps1` | 输出 `app/libs/snishaper-core.aar`（不入库） |

产物路径：`app/build/outputs/apk/{debug,release}/`、`app/build/outputs/bundle/release/`。

## 2. 环境要求

| 组件 | 版本 |
|---|---|
| JDK | 25（Android Studio jbr 25 或 Temurin 25） |
| Gradle | 9.8.0（wrapper，默认华为云镜像） |
| Android Gradle Plugin | 9.4.1（AGP 9 内置 Kotlin，**不要**应用 `org.jetbrains.kotlin.android`） |
| Android SDK | platform 36、build-tools 36.0.0 |
| Android NDK | **30.0.16248370**（gomobile cgo 交叉编译） |
| Go | 1.27 |
| gomobile | `golang.org/x/mobile v0.0.0-20260803200217-62cee1672c8e` |
| 核心库 | `snishaper-core v0.4.0` |

## 3. 环境变量

```powershell
$env:JAVA_HOME        = "D:\Jetbrains IDES\Android Studio\jbr"
$env:ANDROID_HOME     = "D:\AndroidSDKs"
$env:ANDROID_NDK_HOME = "D:\AndroidSDKs\ndk\30.0.16248370"   # 必须指向实际安装的 NDK
$env:GOPROXY          = "https://goproxy.cn,direct"
$env:GOSUMDB          = "off"
```

也可以直接 dot-source 环境脚本，其中同时打印版本矩阵：

```powershell
. .\scripts\dev-env.ps1
```

## 4. 构建 gomobile AAR

```powershell
pwsh -File scripts/build-aar.ps1
```

脚本行为：

1. 校验 `go` / `gomobile` / NDK / JDK；
2. 检查 `gomobile/mobile` 的依赖闭包中**不含 `certmanager`**（CA-free 守卫）；
3. 执行 `gomobile bind`（arm64-v8a + armeabi-v7a + x86_64，`-androidapi 24`、`-tags with_gvisor`、`-trimpath -ldflags "-s -w"`）；
4. 输出体积 / sha256 摘要到控制台。

等价的单条命令：

```powershell
cd gomobile
gomobile bind -target=android/arm64,android/arm,android/amd64 `
  -androidapi 24 -tags with_gvisor -trimpath -ldflags "-s -w" `
  -javapkg com.snishaper.core -o ../app/libs/snishaper-core.aar ./mobile
```

> AAR 约 **74.7 MB**（stripped，三 ABI），属于构建产物，**不提交 Git**；CI 会重新构建并作为 artifact 上传。

## 5. 构建 APK

```powershell
# 默认 debug：arm64-v8a
.\gradlew.bat :app:assembleDebug

# MuMu（x86_64）
.\gradlew.bat :app:assembleDebug -PdebugAbi=x86_64

# Release：全 ABI + AAB
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:bundleRelease
```

> 首次构建会从华为云 Maven 镜像下载依赖（`settings.gradle.kts` 中配置，官方源兜底），耗时取决于网络。

## 6. MuMu 冒烟测试

```powershell
# 1) 确认设备在线（MuMu 需开启 ADB 调试）
adb devices
# 空列表时：启动 MuMu，然后
adb connect 127.0.0.1:16384

# 2) 安装
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 3) 启动
adb shell am start -n com.snishaper.android/.MainActivity

# 4) 日志（无 FATAL EXCEPTION 即通过启动检查）
adb logcat -s Snishaper:* AndroidRuntime:E
```

一键脚本（MuMu 离线时明确提示并 exit 2，不伪造结果）：

```powershell
pwsh -File scripts/mumu-status.ps1
pwsh -File scripts/smoke-test.ps1
```

人工检查项：

- [ ] App 启动无崩溃 / 无 `FATAL EXCEPTION`
- [ ] Dashboard 可启动 / 停止代理引擎
- [ ] 顶部 ☰ 可开关侧边栏，9 项顺序与桌面一致
- [ ] 全 App 无任何 CA / 证书入口
- [ ] 规则编辑仅 TLS-RF / Transparent 模式

## 7. CA-free 校验

```powershell
pwsh -File scripts/verify-aar.ps1

cd gomobile
go list -deps ./mobile | Select-String certmanager   # 应无输出
```

校验内容：AAR 存在 / `minSdkVersion=24` / 三个 ABI / arm64 与 x86_64 的 16 KB 页对齐 / `classes.jar` 仅含 `App`、`Listener` 与 gobind 运行时类 / 无 `cert` 或 `ca` 命名类 / 无证书文件。

## 8. CI/CD

| Workflow | 触发 | 内容 |
|---|---|---|
| `.github/workflows/android.yml` | push / PR / 手动 | JDK + Android SDK + NDK + Go + gcc → gomobile AAR → verify-aar → `lint test assembleRelease` → APK/AAB artifacts |
| `.github/workflows/release.yml` | `v*` tag / 手动 | 构建并创建 GitHub Release（APK + AAB + 版本号 + changelog，标注“已移除 CA 功能”） |

## 9. 常见问题

| 现象 | 原因与处理 |
|---|---|
| `gomobile bind` 报 `ANDROID_NDK_HOME` 相关错误 | 环境变量指向不存在的 NDK 版本；改为实际安装版本（如 30.0.16248370） |
| `sum.golang.org` 超时 / 模块校验失败 | 设置 `GOPROXY=https://goproxy.cn,direct` 与 `GOSUMDB=off` |
| 编译报找不到 `com.snishaper.core.mobile.*` | AAR 尚未生成或 `app/libs/snishaper-core.aar` 缺失；先执行 `scripts/build-aar.ps1` |
| 应用 `org.jetbrains.kotlin.android` 时报 “no longer required since AGP 9.0” | AGP 9 已内置 Kotlin，删除该插件（本仓库已处理） |
| Gradle 下载慢 / 超时 | 仓库默认使用华为云 Maven 镜像；如镜像不可用可临时切回官方源 |
| MuMu `adb devices` 为空 | 在 MuMu 设置中开启 ADB 调试，或 `adb connect 127.0.0.1:16384` |
| Release 包体积大 | Release 包含三个 ABI，属预期；可只发布需要的 ABI 或使用 AAB |
