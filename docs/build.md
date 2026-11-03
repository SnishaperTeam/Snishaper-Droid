# 构建指南（Snishaper-Droid）

> 本文覆盖 gomobile AAR、Android APK/AAB 构建与签名、MuMu 冒烟测试、AAR 校验与常见问题。
> 平台支持与功能边界见 [Platform.md](Platform.md)。

## 1. 产物矩阵

| 产物 | 命令 | 说明 |
|---|---|---|
| Debug APK（arm64-v8a） | `.\gradlew.bat :app:assembleDebug` | 本地开发 / 真机，默认只打 arm64 |
| Debug APK（x86_64） | `.\gradlew.bat :app:assembleDebug -PdebugAbi=x86_64` | MuMu 等 x86_64 模拟器 |
| Release APK + AAB（已签名） | `pwsh -File scripts/build-apk.ps1 -Bundle` | 校验签名并输出到 `build/dist/` |
| Release APK（全 ABI，手动） | `.\gradlew.bat :app:assembleRelease` | arm64-v8a / armeabi-v7a / x86_64 |
| Release AAB | `.\gradlew.bat :app:bundleRelease` | 应用商店分发 |
| gomobile AAR | `pwsh -File scripts/build-aar.ps1` | 输出 `app/libs/snishaper-core.aar`（不入库） |
| 签名状态 | `.\gradlew.bat :app:signingInfo` | 打印解析到的密钥库 / 别名，或提示缺失 |

产物路径：`app/build/outputs/apk/{debug,release}/`、`app/build/outputs/bundle/release/`；
`scripts/build-apk.ps1` 另外复制一份到 `build/dist/` 并生成 `SHA256SUMS.txt`。

## 2. 环境要求

| 组件 | 版本 |
|---|---|
| JDK | 25（Android Studio jbr 或 Temurin 25） |
| Gradle | 9.8.2（wrapper，默认华为云镜像） |
| Android Gradle Plugin | 9.5.0-rc01（AGP 9 内置 Kotlin，**不要**应用 `org.jetbrains.kotlin.android`） |
| Android SDK | compileSdk 37（minor 2）、build-tools 37.0.0、targetSdk 36 |
| Android NDK | **30.0.16248370**（gomobile cgo 交叉编译） |
| Go | 1.27 |
| gomobile | `golang.org/x/mobile v0.0.0-20260803200217-62cee1672c8e` |
| 核心库 | `snishaper-core v0.8.4` |

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
2. 检查 `gomobile/mobile` 的依赖闭包中**包含 `certmanager`**（CA 支持校验：缺失则说明 CA 功能被误删）；
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

## 5. 构建并签名 APK / AAB

### 5.1 一次性准备密钥

```powershell
pwsh -File scripts/init-keystore.ps1
```

脚本生成两样东西，**都被 `.gitignore` 忽略**：

| 文件 | 内容 |
|---|---|
| `keystore/release.jks` | PKCS12 密钥库，RSA 4096，有效期 30 年，别名 `snishaper` |
| `keystore.properties` | `storeFile` / `storePassword` / `keyAlias` / `keyPassword` |

控制台会打印随机生成的密码与证书 SHA-256。**这两个文件必须离线备份**：Android 只允许签名一致的 APK 覆盖安装，密钥丢失后已发布版本将无法升级。

### 5.2 构建

```powershell
pwsh -File scripts/build-apk.ps1           # signed release APK
pwsh -File scripts/build-apk.ps1 -Bundle   # + AAB
```

脚本依次执行：补齐缺失的 AAR → 补齐缺失的前端 bundle → `assembleRelease`（可选 `bundleRelease`）→
`apksigner verify`（未签名直接失败）→ 复制到 `build/dist/snishaper-droid-<version>.{apk,aab}` → 写 `SHA256SUMS.txt`。

也可以直接调用 Gradle（`keystore.properties` 存在时自动签名）：

```powershell
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:bundleRelease
.\gradlew.bat :app:signingInfo      # 确认签名配置已被读取
```

### 5.3 签名方案

`app/build.gradle.kts` 的 release 签名配置固定启用 **v2 + v3 + v4**：

| 方案 | 状态 | 原因 |
|---|---|---|
| v1（JAR） | 关闭 | `minSdk 24` 起系统只认 v2+，v1 徒增体积 |
| v2 | 启用 | Android 7.0+ 主流校验 |
| v3 | 启用 | 支持密钥轮换 |
| v4 | 启用 | 生成 `.idsig`，增量安装用 |

### 5.4 签名材料来源优先级

| 顺序 | 来源 | 用法 |
|---|---|---|
| 1 | `keystore.properties`（仓库根目录） | 本地开发 |
| 2 | 环境变量 `SNISHAPER_KEYSTORE` / `SNISHAPER_KEYSTORE_PASSWORD` / `SNISHAPER_KEY_ALIAS` / `SNISHAPER_KEY_PASSWORD` | CI / 无仓库文件场景 |
| 3 | 都没有 | release 产出 `app-release-unsigned.apk`，`build-apk.ps1` 会直接报错退出 |

`SNISHAPER_KEYSTORE` 支持绝对路径或相对仓库根目录的路径。

### 5.5 CI 签名密钥

GitHub Release 工作流从仓库 Secrets 读取：

| Secret | 内容 |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 keystore/release.jks`（PowerShell：`[Convert]::ToBase64String([IO.File]::ReadAllBytes("keystore/release.jks"))`） |
| `ANDROID_KEYSTORE_PASSWORD` | `storePassword` |
| `ANDROID_KEY_ALIAS` | `keyAlias` |
| `ANDROID_KEY_PASSWORD` | `keyPassword` |

四个都齐全时流水线导入密钥并签名，再用 `apksigner verify` 把关；缺任意一个会打印 `::warning::` 并产出未签名包（发版前务必确认日志中已出现签名校验通过）。

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
- [ ] Dashboard 可启动 / 停止代理引擎，TUN 可拉起系统 VPN 授权，CA 状态卡正常显示
- [ ] 顶部 ☰ 可开关侧边栏，11 项顺序与桌面一致
- [ ] 实时连接、配置档案、规则解释、多 CDN 优选池、诊断包可用
- [ ] 设置页只有端口、语言、外观、主题、自动更新规则、TUN MTU、证书、IPv6、CF IP 池
- [ ] 规则编辑支持 mitm / tls-rf / quic / transparent / migration 全部模式
- [ ] 日志页常驻显示引擎环形缓冲，暂停 / 清空 / 导出可用

## 7. AAR 校验

```powershell
python3 scripts/verify_aar.py

cd gomobile
go list -deps ./mobile | Select-String certmanager   # 应有输出（CA 支持已在依赖图中）
```

校验内容：AAR 存在 / `minSdkVersion=24` / 三个 ABI / arm64 与 x86_64 的 16 KB 页对齐 / `classes.jar` 仅含 `App`、`Listener` 与 gobind 运行时类 / 无证书文件内嵌。

## 8. CI/CD

| Workflow | 触发 | 内容 |
|---|---|---|
| `.github/workflows/android.yml` | push / PR / 手动 | JDK + Android SDK + NDK + Go + gcc → gomobile AAR → verify_aar → `lint test assembleDebug` → APK/AAR artifacts |
| `.github/workflows/release.yml` | 手动 dispatch（确认版本 + 渠道，bot 提交版本号并打 tag） | 导入签名密钥 → `signingInfo lint test assembleRelease bundleRelease` → `apksigner verify` → 发布 GitHub Release（签名 APK + AAB + AAR + `SHA256SUMS.txt`） |

## 9. 常见问题

| 现象 | 原因与处理 |
|---|---|
| `gomobile bind` 报 `ANDROID_NDK_HOME` 相关错误 | 环境变量指向不存在的 NDK 版本；改为实际安装版本（如 30.0.16248370） |
| `sum.golang.org` 超时 / 模块校验失败 | 设置 `GOPROXY=https://goproxy.cn,direct` 与 `GOSUMDB=off` |
| 编译报找不到 `com.snishaper.core.mobile.*` | AAR 尚未生成或 `app/libs/snishaper-core.aar` 缺失；先执行 `scripts/build-aar.ps1` |
| release 产出 `app-release-unsigned.apk` | 没有读到签名材料；`.\gradlew.bat :app:signingInfo` 查看缺失项，执行 `scripts/init-keystore.ps1` |
| `apksigner` 报 `DOES NOT VERIFY` | 密钥库与旧包不一致（换过密钥）；卸载旧版后重装，或恢复原 `keystore/release.jks` |
| 应用 `org.jetbrains.kotlin.android` 时报 “no longer required since AGP 9.0” | AGP 9 已内置 Kotlin，删除该插件（本仓库已处理） |
| Gradle 下载慢 / 超时 | 仓库默认使用华为云 Maven 镜像；如镜像不可用可临时切回官方源 |
| MuMu `adb devices` 为空 | 在 MuMu 设置中开启 ADB 调试，或 `adb connect 127.0.0.1:16384` |
| Release 包体积大 | 三个 ABI 的 `.so` 未压缩存放（16 KB 对齐要求）属预期；分发用 AAB 或按 ABI 拆分 |
