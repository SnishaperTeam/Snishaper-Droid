# AGENTS.md

本文件面向在本仓库（Snishaper-Droid，SniShaper 的 Android 客户端）工作的编码代理。架构事实以 `README.md` 与 `docs/` 为准，本文件只写会改变代理行为的规则。

## 1. 架构事实（不可偏离）

- **UI**：直接复用桌面版 **React 19 + MUI** 前端 bundle，打进 APK 由 **Kotlin WebView 宿主** 渲染，经 **Wails 桥接** 调用原生能力。不要另起一套原生 UI，不要在 WebView 之外重写前端页面。
- **代理引擎**：来自独立库 **snishaper-core**，经 `gomobile/mobile`（独立 Go module）通过 **gomobile bind** 编译为 AAR，在进程内运行。本仓库只保留 Android 应用层：WebView 宿主、Wails 桥接、VpnService、配置与档案数据。
- **VpnService TUN（Beta）**：Kotlin `VpnService` 建隧道 → Go `sing-tun` `FileDescriptor` + core `singtun.Handler` + gvisor 栈。此链路涉及三层，改动必须三层同步。
- **证书**：`certmanager` 已编入 AAR；写入系统信任库需要 root（`su` + `certutil`），无 root 仅能导出 PEM 由用户手动安装。规则模式 `mitm` / `quic` / `migration` 与规则级 `cert_verify` / `sni_fake` / 规则级 ECH 依赖 MITM 路径，缺证书时必须优雅降级，不得崩溃。
- **构建目标**：minSdk 24 / compileSdk 36 / targetSdk 36；release 签名走 `keystore/release.jks`（v2 + v3 + v4），CI 用 `apksigner verify` 把关；**R8 关闭**（`isMinifyEnabled = false`），release 与 debug 的 Go 侧行为必须一致。
- **ABI**：release 为 `arm64-v8a`；x86_64 仅用于调试（`-PdebugAbi=x86_64`）。

## 2. Go / gomobile 导出规则

`gomobile bind` 仅支持基本类型、字符串、切片、映射、导出结构体（字段均为支持类型）、接口（方法参数与返回值均为支持类型）及满足条件的函数类型。泛型函数不可导出。

- 导出层只做最小化适配封装，代理 / 规则 / DoH / TLS 分片 / TUN 数据面的实现留在 `snishaper-core`，不得在本仓库复制一份。
- 不导出含 channel、complex、未导出字段的结构体，或含泛型签名的方法。
- 改动 Go 导出签名后，必须重新运行 `gomobile bind` 并通过 **AAR 契约校验**（与 `android.yml` 中一致），否则不得提交。
- 新增导出符号必须同步更新 Wails 桥接的 Kotlin 侧调用，保证同一提交可构建。

## 3. WebView + Wails 桥接约束

- WebView **只做 UI 渲染**；代理转发、规则匹配、TLS 分片、DoH 解析、TUN 数据面一律不得在 WebView 内实现。
- 前端 bundle 与桌面版共享，Android 端只通过 Wails 桥接暴露**平台差异化的最小 API 集合**（例如 VpnService 授权、证书导出、诊断包分享）。新增桥接方法必须在桌面端前端可安全降级，不得破坏桌面端。
- 桌面端独有的托盘 / 自启 / 系统代理 / 更新通道 / 网卡选择等控件已在 Android 端移除，不得在 Android bundle 中恢复。
- 与证书、VpnService 权限相关的桥接调用必须返回明确的结果对象（成功 / 失败原因），不得静默忽略，以便前端展示提示。

## 4. 提交规则（强制）

**每一次代码改动都必须提交，不得留存在工作区。** 提交即交付。

### 4.1 提交格式

提交信息 **全英文**，使用 Conventional Commits，并遵循仓库的 git 提交模板（含模板中约定的 `Co-Authored-By` 等 trailer，按模板保留，不额外增删）：

```
<type>(<scope>): <imperative subject>

<body: what changed and why>
```

允许类型：`feat`、`fix`、`refactor`、`perf`、`test`、`build`、`chore`、`docs`。

**规则**：

- subject 祈使语气、小写、不加句号；scope 推荐（`webview`、`bridge`、`vpnservice`、`gomobile`、`cert`、`ui`、`ci`）。
- body 必填，说明改动内容和原因；不罗列测试命令或门禁输出。
- 按仓库 git 提交模板填写，模板已有的 trailer 保留；模板未要求的额外 AI 署名不添加。
- 禁止 `git add -A`，只添加明确的变更文件；**禁止提交** AAR、`.so`、APK、AAB、`keystore/` 下的私钥等构建或签名产物。
- 每个逻辑变更一个提交，无关修复不混在同一提交。
- 改动 Go 导出签名、Wails 桥接契约或 TUN 三层链路时，必须在同一提交内完成所有关联侧的适配，保证提交可构建。
- 提交前运行仓库约定的校验（`scripts/build-apk.ps1` 或等价 CI 步骤），不得跳过。

## 5. 议题完成后的回复

完成某个 issue 后，**必须在对应 issue 评论区回复**，并 **@ 发起该议题的作者**。

- 语言跟随议题：议题是中文就用中文回，英文就用英文；不要一律套英文。
- 写成人话，不要模板腔。把做了什么、对应哪些提交说清楚就行，别复读流程、别自我表扬、别堆 bullet 清单式道歉。
- 通常包含：@作者、改了什么、commit 范围或 subject（方便对方查）。格式不必固定，自然表达即可。
- 议题没完全解决就直说剩下什么、为什么，不要写"已完成"糊弄。
- 一次回复只对应一个议题；多个议题分别回复，分别 @ 各自的作者。

## 6. 文档原则

**不写无用文档。** `README.md`、`docs/Platform.md`、`docs/build.md` 已覆盖定位、平台边界与构建，本文件不重复。

**允许**：跨层接口的契约说明（仅当从代码无法直接推断时）、环境搭建中非显而易见的前提、代理容易踩坑且会改变实现的约束。

**禁止**：复述 README 或 `docs/` 已有内容、罗列目录树以外的架构图、写"如何运行"若 `docs/build.md` 已覆盖、添加与当前任务无关的路线图或未来计划。
