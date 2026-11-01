# Android 平台支持（Snishaper-Droid）

> 本文描述 Snishaper-Droid 的平台范围、功能矩阵与已知限制。桌面端见 [SniShaper Platform.md](https://github.com/SnishaperTeam/SniShaper/blob/main/docs/Platform.md)。

## 1. 版本与 ABI

| 项 | 值 |
|---|---|
| 最低系统版本 | Android 7.0 / API 24 |
| 编译 / 目标 SDK | API 36（`compileSdk = targetSdk = 36`） |
| 支持 ABI | `arm64-v8a`（推荐）、`armeabi-v7a`、`x86_64`（模拟器） |
| 测试设备 | MuMu 模拟器（Android 15，x86_64） |
| 运行形态 | 单 Activity + WebView（桌面版 React/MUI 前端）；Go 引擎进程内运行（gomobile AAR） |
| 前台服务 | 代理运行期间显示常驻通知（Android 8+ 后台限制） |

## 2. 功能矩阵

### 保留

| 能力 | 说明 |
|---|---|
| 混合端口代理 | HTTP CONNECT + SOCKS5，默认 `127.0.0.1:8080` |
| 规则引擎 | 站点分组、上游绑定、DNS 策略 |
| TLS-RF 分片 | 记录数 / 分段 / 间隔 / TLS 次版本 / OOB，全局与规则级 |
| Transparent / Direct | 纯透传与直连 |
| DoH / ECH（DoH 层） | 多节点故障转移、ECH 档案、QUIC DoH、前端 SNI |
| Cloudflare 优选池 | 测速、健康检查、失效清理、API Key |
| NAT64 | 多前缀配置与测试 |
| 规则进化测试 | 直连 / TCPing / 域前置 / TLS 分片 / ECH / QUIC 探测结果展示 |
| 配置导入导出 | 桌面格式，导入时仅做规则校验（模式白名单 + 域名去重），不再剥离 CA 字段 |
| VpnService TUN（Beta） | Kotlin 隧道 + Go `sing-tun` + gvisor 栈，无需 root |
| UI | 桌面版 React/MUI 前端（9 页）、☰ 侧边栏、6 配色 × 明暗、zh/en/ru |

### CA 功能（已恢复）

| 能力 | 说明 |
|---|---|
| CA 生成 / 重生成 / 卸载 / 导出（PEM 下载） | 通过 `certmanager`（core v0.8.4 等价接口） |
| 信任库安装与状态 | 移动版实现：root su + `certutil` 安装到系统信任库 |
| 证书列举 / 卸载 | `GetInstalledCerts` / `UninstallCert(token)` |
| 规则模式 `mitm` / `quic` / `migration` 与规则级 `cert_verify` | 与桌面版一致，RuleForm 完整支持 |
| 进化模式规则应用 | CA 路径可用后已解除白名单限制 |

> 历史说明：早期 Droid 版本曾按 CA-free 设计移除上述能力，现已全部恢复；详见 `docs/core-ca-coupling.md`（历史审计）与 `docs/EngineBridge.md`。

### 桌面专有、Android 不适用的能力

系统代理（注册表）、托盘、窗口控制、开机自启、应用内自动更新、打开日志文件、杀端口占用进程、休眠。

## 3. 权限

| 权限 | 用途 |
|---|---|
| `INTERNET` | 代理、DoH、规则/订阅更新 |
| `ACCESS_NETWORK_STATE` | IPv6 探测与网络状态 |
| `FOREGROUND_SERVICE` *（TUN/VPN 启用时）* | 代理常驻前台服务 |

## 4. 已知限制

- **TUN/VpnService** 为 Beta：数据面已实现，仍需真机/模拟器验证连通性与后台保活。
- 无全局系统代理 API：Android 端通过 VpnService 或手动 Wi-Fi 代理使用。
- CA 安装到系统信任库需要已 root 的设备（su + certutil）；未 root 设备可生成/导出证书但无法系统级安装。
- 智能分流 Smart 不可用（产品决定，见 `app.go` 的 sanitize 分支）；未命中手动规则的域名按 Direct 处理。
- 应用内更新未实现，请关注 [Releases](https://github.com/SnishaperTeam/Snishaper-Droid/releases)。

## 5. 数据目录

| 路径 | 内容 |
|---|---|
| `<filesDir>/config/settings.json` | 应用设置（端口、语言、主题、TUN、分片、CF 池） |
| `<filesDir>/rules/config.json` | 站点规则 / 上游 / DoH 节点 / ECH 档案 / NAT64 |
| `<filesDir>/certs/` | 本地 CA（`ca.crt` / `ca.key`），由 certmanager 生成 |
