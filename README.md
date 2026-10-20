# Snishaper-Droid

[中文](README.md) | [English](README_EN.md)

[![Android](https://img.shields.io/badge/Android-7.0%2B%20(API%2024)-3DDC84?style=flat&logo=android&logoColor=white)](https://developer.android.com) [![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose%20M3-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org) [![Core](https://img.shields.io/badge/核心库-snishaper--core-00ADD8?style=flat&logo=go)](https://github.com/SnishaperTeam/snishaper-core) [![License](https://img.shields.io/badge/许可证-AGPL--3.0-blue?style=flat&logo=open-source-initiative)](LICENSE) [![GitHub Release](https://img.shields.io/github/v/release/SnishaperTeam/Snishaper-Droid?style=flat&logo=github&label=版本)](https://github.com/SnishaperTeam/Snishaper-Droid/releases) [![GitHub Downloads](https://img.shields.io/github/downloads/SnishaperTeam/Snishaper-Droid/total?style=flat&logo=github&label=下载量)](https://github.com/SnishaperTeam/Snishaper-Droid/releases) [![GitHub last commit](https://img.shields.io/github/last-commit/SnishaperTeam/Snishaper-Droid?style=flat&logo=git&label=最后提交)](https://github.com/SnishaperTeam/Snishaper-Droid/commits/main) [![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/SnishaperTeam/Snishaper-Droid/android.yml?style=flat&logo=githubactions&label=持续集成)](https://github.com/SnishaperTeam/Snishaper-Droid/actions)

**Snishaper-Droid** 是 [SniShaper](https://github.com/SnishaperTeam/SniShaper) 的 **Android 原生客户端**：前端使用 **Kotlin + Jetpack Compose + Material 3**，代理引擎来自 **[snishaper-core](https://github.com/SnishaperTeam/snishaper-core)**，通过 **gomobile bind** 编译为 AAR 在进程内运行，无需 root。通过 **TLS 分片**、**加密 DNS / ECH（DoH 层）**、**NAT64** 与 **VpnService 全局代理**，在复杂网络环境下提供稳定而灵活的网络访问体验。

> **⚠️ 本仓库已完整移除 CA 功能**：不生成 / 保存 / 安装 / 导出任何证书，不操作 Android 系统信任库或 KeyStore；`certmanager` 不会编译进 AAR，gomobile 导出面中 **0 个 CA API**，UI 中不存在任何 CA 入口。详见 **[CA 功能移除](#ca-功能移除)**。

本项目提供 Android 7.0+ 支持。详见 **[Platform.md](docs/Platform.md)**。

---

## CA 功能移除

| 桌面端能力 | Android 处置 |
|---|---|
| CA 生成 / 落盘 / 导出 / 重生成 / 卸载，系统信任库安装与状态、已装证书列举 | **不存在**（`certmanager` 不进 AAR，无对等能力） |
| 规则模式 `mitm` / `quic` / `migration` | **删除**（均依赖本地 TLS 终结） |
| 规则级 `cert_verify`、域前置 `sni_fake`、站点级 ECH | **删除**（仅 MITM 路径生效） |
| 智能分流 Smart | **移除策略开关**，保留 Traffic Flow 与 GFWList 状态（CF 分支会在 core 内部合成 MITM 规则） |
| 进化模式规则转换 | 仅 **TLS-Fragment / Direct** 结果可应用 |
| 内置规则（222 组） | 77 条 CA 依赖规则（68 MITM + 9 QUIC）自动转为 `tls-rf`，CA 字段清洗 |
| 证书管理面板 / 安装弹窗 / 引导证书步骤 | **全部删除** |

**为什么没有 MITM？** MITM 需要客户端信任一张本地根证书；Android 系统信任库只能由用户手动安装、应用无法静默完成，`gomobile` 层也无法进行 KeyStore / 信任链交互。按项目要求：**不实现、不隐藏、不留接口**。

---

## 姊妹项目

[**SniShaper**](https://github.com/SnishaperTeam/SniShaper) 是同一引擎的桌面端（Windows / Linux / macOS），[**FlowWeaver**](https://github.com/SnishaperTeam/FlowWeaver) 是全功能客户端分支。三者**并行维护**：

| | Snishaper-Droid（本仓库） | [SniShaper](https://github.com/SnishaperTeam/SniShaper) | [FlowWeaver](https://github.com/SnishaperTeam/FlowWeaver) |
|---|---|---|---|
| 定位 | Android 原生客户端 | 桌面端 GUI / CLI | 全功能客户端 / 网关 |
| 平台 | Android 7.0+ | Windows / Linux / macOS | Windows / Linux / macOS |
| 核心能力 | TLS 分片、DoH/ECH、NAT64、VpnService TUN | ECH 注入、TLS 分片、QUIC、会话迁移、TUN | Clash 订阅、全协议节点、WireGuard、TUN 网关 |

三者的代理引擎均来自独立库 **[snishaper-core](https://github.com/SnishaperTeam/snishaper-core)**：代理服务、规则引擎、DoH 解析、TLS 分片与 TUN 数据面由该库提供，本仓库只保留 Android 应用层（Compose UI、VpnService、配置数据与 CA-free 裁剪层）。

---

## 交流群组

欢迎加入 QQ 群 **[Snishaper and FlowWeaver building](https://qm.qq.com/q/GtBOkAOiMe)**，与 SniShaper、Snishaper-Droid 与 FlowWeaver 的用户和开发者直接交流、反馈问题与提交建议。

---

## 特性

- **混合端口代理**：单端口同时提供 HTTP CONNECT 与 SOCKS5（默认 `127.0.0.1:8080`）。
- **TLS-RF 分片**：记录数 / 分段数 / 间隔 / TLS 次版本修改 / OOB 首字节，支持全局与规则级参数。
- **Transparent / Direct**：纯透传与直连，未命中规则时按默认策略处理。
- **加密 DNS**：DoH 多节点故障转移、ECH（DoH 层）、QUIC DoH 与前端 SNI。
- **Cloudflare IP 优选池**：自动测速、健康检查与失效清理。
- **NAT64 支持**：多套 NAT64 前缀配置与连通性测试，`IPv6 优先 / NAT64` 分流。
- **上游与静态 IP**：规则可绑定静态上游 IP 列表。
- **进化模式**：直连 / TCPing / TLS 分片等探测（域前置 / ECH / QUIC 测试仅展示，Android 不可应用其规则）。
- **VpnService TUN（Beta）**：Kotlin `VpnService` 建隧道 → Go `sing-tun` `FileDescriptor` + core `singtun.Handler` + gvisor 栈，无需 root。
- **配置导入 / 导出**：兼容桌面格式，导入时自动 CA-free 清洗并汇报改写条数。
- **Material 3 UI**：9 个页面与桌面版导航一致（CA 项删除）；顶部 **☰** 控制侧边栏，可见性持久化；6 套配色 × 明暗 / light·dark·system；简体中文 / English / Русский。
- **前台服务**：代理运行期间常驻通知，满足 Android 后台限制。

---

## 快速开始

对于一般用户，我们建议直接在 [Releases](https://github.com/SnishaperTeam/Snishaper-Droid/releases) 下载最新的 Android 稳定版本 APK（arm64-v8a）并安装。

- 最低系统：**Android 7.0（API 24）**。
- MuMu 等 x86_64 模拟器：请使用源码构建 `-PdebugAbi=x86_64` 的调试包。
- 首次启动会自动播种 CA-free 默认规则；可在 Dashboard 启动/停止代理引擎。

源码构建、环境要求与 CA-free 校验方式见以下文档：

- **[Platform.md](docs/Platform.md)** — 平台支持、功能矩阵与已知限制。
- **[build.md](docs/build.md)** — 构建指南（gomobile AAR、Gradle APK、MuMu 冒烟测试与 CI）。

---

## 文档

- **[Platform.md](docs/Platform.md)** — Android 平台支持与功能边界。
- **[build.md](docs/build.md)** — 构建、测试与 CA-free 校验指南。
- **[CHANGELOG.md](CHANGELOG.md)** — 版本变更记录。
- **[SniShaper Wiki](https://github.com/SnishaperTeam/SniShaper/wiki)** — 核心引擎的通用文档（代理模式、规则格式等，桌面端与 Android 共用同一引擎）：
  - [核心模式介绍](https://github.com/SnishaperTeam/SniShaper/wiki/Core-Proxy-Modes)
  - [规则自定义指南](https://github.com/SnishaperTeam/SniShaper/wiki/Custom-Rules-Guide)

---

## 构建与开发

本项目前端基于 **Kotlin + Jetpack Compose + Material 3**，通过 `gomobile/mobile`（独立 Go module）将 **snishaper-core** 绑定为 AAR 在进程内运行。当前支持 Android 7.0+，构建目标为 **API 24（minSdk）/ 36（compileSdk · targetSdk）**。

CA-free 由四道保障共同实现：**编译期**（不 import `certmanager`）、**实例化期**（永不注入 `CertGenerator`）、**配置清洗**（启动加载 / 导入 / 规则 CRUD / AutoRouter 强制关闭 / Evolution Apply 白名单）、**CI 守卫**（AAR 导出面与依赖闭包校验）。

CI/CD 与桌面端发布节奏对齐：

- `.github/workflows/android.yml`：push / PR → `gomobile bind`（CA-free AAR）→ 契约校验 → `lint test assembleRelease` → 上传 APK/AAB artifact。
- `.github/workflows/release.yml`：`v*` tag → GitHub Release（APK + AAB + 版本号 + changelog，发布说明标注 **“已移除 CA 功能”**）。

完整的构建指南详见 **[build.md](docs/build.md)**。

---

## 致谢

本项目受益于以下优秀开源项目的启发：

- [DoH-ECH-Demo](https://github.com/0xCaner/DoH-ECH-Demo)
- [lumine](https://github.com/moi-si/lumine)
- [snishaper-core](https://github.com/SnishaperTeam/snishaper-core)
- [golang.org/x/mobile](https://pkg.go.dev/golang.org/x/mobile)（gomobile）
- [Jetpack Compose](https://developer.android.com/jetpack/compose) / [Material 3](https://m3.material.io/)

## 项目活跃度与贡献者

### 活跃度徽章

[![GitHub contributors](https://img.shields.io/github/contributors/SnishaperTeam/Snishaper-Droid?style=flat&label=总贡献者)](https://github.com/SnishaperTeam/Snishaper-Droid/graphs/contributors)
[![GitHub commit activity](https://img.shields.io/github/commit-activity/m/SnishaperTeam/Snishaper-Droid?style=flat&label=月均提交)](https://github.com/SnishaperTeam/Snishaper-Droid/graphs/contributors)
[![GitHub last commit](https://img.shields.io/github/last-commit/SnishaperTeam/Snishaper-Droid?style=flat&label=最近提交)](https://github.com/SnishaperTeam/Snishaper-Droid/commits/main)

### 贡献者图谱

<div align="center">
<a href="https://github.com/SnishaperTeam/Snishaper-Droid/graphs/contributors" target="_blank">
<img src="https://contrib.rocks/image?repo=SnishaperTeam/Snishaper-Droid" alt="Contributors" />
</a>
</div>

## Star History

<a href="https://www.star-history.com/?repos=snishaperteam%2Fsnishaper-droid&type=timeline&logscale=&releases=&legend=bottom-right">
<picture>
<source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=snishaperteam/snishaper-droid&type=timeline&theme=dark&logscale&legend=bottom-right" />
<source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=snishaperteam/snishaper-droid&type=timeline&theme=light&logscale&legend=bottom-right" />
<img alt="Star History Chart" src="https://api.star-history.com/chart?repos=snishaperteam/snishaper-droid&type=timeline&logscale&legend=bottom-right" />
</picture>
</a>

---

## 许可

[GNU Affero General Public License v3.0](LICENSE)（AGPL-3.0）
