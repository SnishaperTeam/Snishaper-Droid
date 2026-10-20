# Android 平台支持（Snishaper-Droid）

> 本文描述 Snishaper-Droid 的平台范围、功能矩阵与已知限制。桌面端见 [SniShaper Platform.md](https://github.com/SnishaperTeam/SniShaper/blob/main/docs/Platform.md)。

## 1. 版本与 ABI

| 项 | 值 |
|---|---|
| 最低系统版本 | Android 7.0 / API 24 |
| 编译 / 目标 SDK | API 36（`compileSdk = targetSdk = 36`） |
| 支持 ABI | `arm64-v8a`（推荐）、`armeabi-v7a`、`x86_64`（模拟器） |
| 测试设备 | MuMu 模拟器（Android 15，x86_64） |
| 运行形态 | 单 Activity + Compose UI；Go 引擎进程内运行（gomobile AAR） |
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
| 配置导入导出 | 桌面格式，导入时自动 CA-free 清洗 |
| VpnService TUN（Beta） | Kotlin 隧道 + Go `sing-tun` + gvisor 栈，无需 root |
| UI | 9 页 Material 3、☰ 侧边栏、6 配色 × 明暗、zh/en/ru |

### 移除（CA 相关）

| 能力 | 原因 |
|---|---|
| CA 生成 / 保存 / 导出 / 重生成 / 卸载、信任库安装与状态、证书列举 | 用户要求完全移除；Android/gomobile 无法完成信任链与 KeyStore 交互 |
| 规则模式 `mitm` / `quic` / `migration` | 依赖本地 TLS 终结 |
| 规则级 `cert_verify`、`sni_fake`、站点级 ECH | 仅 MITM 路径生效 |
| 智能分流 Smart 策略 | CF 分支在 core 内部合成 MITM 规则，包装层无法拦截 |
| 进化模式规则转换（除 TLS-Fragment / Direct） | 产物为 mitm/quic 规则 |

> 内置 222 条桌面规则中，77 条 CA 依赖规则（68 MITM + 9 QUIC）在生成 Android 默认规则时自动转为 `tls-rf`，CA 字段全部清空；导入桌面配置时同样执行清洗并汇报改写条数。

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
- 无 CA：MITM / QUIC / Migration / 域前置 / 站点级 ECH 不可用（这是设计决定，不是缺陷）。
- 智能分流 Smart 不可用；未命中手动规则的域名按 Direct 处理。
- 进化模式仅用于探测与结果展示，规则应用受 CA-free 白名单限制。
- 应用内更新未实现，请关注 [Releases](https://github.com/SnishaperTeam/Snishaper-Droid/releases)。

## 5. 数据目录

| 路径 | 内容 |
|---|---|
| `<filesDir>/config/settings.json` | 应用设置（端口、语言、主题、TUN、分片、CF 池） |
| `<filesDir>/rules/config.json` | 站点规则 / 上游 / DoH 节点 / ECH 档案 / NAT64 |
| 无证书目录 | 本版本不生成 `ca.crt` / `ca.key` |
