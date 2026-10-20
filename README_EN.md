# Snishaper-Droid

[中文](README.md) | [English](README_EN.md)

[![Android](https://img.shields.io/badge/Android-7.0%2B%20(API%2024)-3DDC84?style=flat&logo=android&logoColor=white)](https://developer.android.com) [![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose%20M3-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org) [![Core](https://img.shields.io/badge/Core-snishaper--core-00ADD8?style=flat&logo=go)](https://github.com/SnishaperTeam/snishaper-core) [![License](https://img.shields.io/badge/License-AGPL--3.0-blue?style=flat&logo=open-source-initiative)](LICENSE) [![GitHub Release](https://img.shields.io/github/v/release/SnishaperTeam/Snishaper-Droid?style=flat&logo=github&label=Release)](https://github.com/SnishaperTeam/Snishaper-Droid/releases) [![GitHub Downloads](https://img.shields.io/github/downloads/SnishaperTeam/Snishaper-Droid/total?style=flat&logo=github&label=Downloads)](https://github.com/SnishaperTeam/Snishaper-Droid/releases) [![GitHub last commit](https://img.shields.io/github/last-commit/SnishaperTeam/Snishaper-Droid?style=flat&logo=git&label=Last%20Commit)](https://github.com/SnishaperTeam/Snishaper-Droid/commits/main) [![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/SnishaperTeam/Snishaper-Droid/android.yml?style=flat&logo=githubactions&label=CI)](https://github.com/SnishaperTeam/Snishaper-Droid/actions)

**Snishaper-Droid** is the **native Android client of [SniShaper](https://github.com/SnishaperTeam/SniShaper)**: the UI is built with **Kotlin + Jetpack Compose + Material 3**, and the proxy engine comes from **[snishaper-core](https://github.com/SnishaperTeam/snishaper-core)**, compiled into an AAR via **gomobile bind** and running in-process — no root required. Through **TLS fragmentation**, **encrypted DNS / ECH (DoH layer)**, **NAT64** and **VpnService global proxy**, it delivers a stable and flexible browsing experience in complex network environments.

> **⚠️ All CA functionality has been removed from this repository**: no certificate is generated, stored, installed or exported; the Android trust store / KeyStore is never touched. `certmanager` is not compiled into the AAR, the gomobile export surface contains **zero CA APIs**, and no CA entry exists in the UI. See **[CA removal](#ca-removal)**.

Android 7.0+ is supported. See **[Platform_EN.md](docs/Platform_EN.md)**.

---

## CA removal

| Desktop capability | Android |
|---|---|
| CA generation / storage / export / regeneration / uninstall, trust-store install & status, installed-certificate list | **Not present** (`certmanager` not in AAR; no Android counterpart) |
| Rule modes `mitm` / `quic` / `migration` | **Removed** (all need local TLS termination) |
| Rule-level `cert_verify`, domain fronting `sni_fake`, rule-level ECH | **Removed** (only effective on the MITM path) |
| Smart routing | **Strategy switch removed**; traffic-flow and GFWList status retained (the Cloudflare branch synthesizes MITM rules inside core) |
| Evolution rule conversion | Only **TLS-Fragment / Direct** results can be applied |
| Built-in rules (222 groups) | 77 CA-dependent rules (68 MITM + 9 QUIC) converted to `tls-rf`; CA fields stripped |
| Certificate panels / install dialogs / tutorial certificate step | **All removed** |

**Why no MITM?** MITM requires clients to trust a local root CA. Android only allows manual installation by the user — an app cannot do it silently — and the `gomobile` layer cannot interact with the KeyStore/trust chain. Per project requirements: **not implemented, not hidden, no interfaces left**.

---

## Related projects

[**SniShaper**](https://github.com/SnishaperTeam/SniShaper) is the desktop app (Windows / Linux / macOS) built on the same engine, and [**FlowWeaver**](https://github.com/SnishaperTeam/FlowWeaver) is the full-featured client branch. All three are **actively maintained**:

| | Snishaper-Droid (this repository) | [SniShaper](https://github.com/SnishaperTeam/SniShaper) | [FlowWeaver](https://github.com/SnishaperTeam/FlowWeaver) |
|---|---|---|---|
| Focus | Native Android client | Desktop GUI / CLI | Full-featured client / gateway |
| Platform | Android 7.0+ | Windows / Linux / macOS | Windows / Linux / macOS |
| Core strengths | TLS fragmentation, DoH/ECH, NAT64, VpnService TUN | ECH injection, TLS fragmentation, QUIC, session migration, TUN | Clash subscriptions, multi-protocol nodes, WireGuard, TUN gateway |

All three take their proxy engine from the standalone **[snishaper-core](https://github.com/SnishaperTeam/snishaper-core)** library; this repository keeps only the Android application layer (Compose UI, VpnService, configuration data and the CA-free trimming layer).

---

## Community

Join the QQ group **[Snishaper and FlowWeaver building](https://qm.qq.com/q/GtBOkAOiMe)** to talk directly with SniShaper, Snishaper-Droid and FlowWeaver users and developers, report issues and send suggestions.

---

## Features

- **Hybrid proxy port**: HTTP CONNECT + SOCKS5 on a single port (`127.0.0.1:8080` by default).
- **TLS-RF fragmentation**: record count / segments / interval / TLS minor-version tweak / OOB first byte, global and per-rule.
- **Transparent / Direct**: raw pass-through and direct connections.
- **Encrypted DNS**: DoH failover, ECH (DoH layer), QUIC DoH and frontend SNI.
- **Cloudflare preferred-IP pool**: auto speed-test, health check and invalidation.
- **NAT64**: multiple prefixes with connectivity tests and `IPv6 prefer / NAT64` routing.
- **Upstreams**: rules can bind static upstream IP lists.
- **Rule evolution**: direct / TCPing / TLS-fragment probing (domain-fronting / ECH / QUIC tests are shown but their rules cannot be applied on Android).
- **VpnService TUN (Beta)**: Kotlin `VpnService` → Go `sing-tun` `FileDescriptor` + core `singtun.Handler` + gvisor stack; no root.
- **Import / export**: desktop-compatible format with automatic CA-free sanitization.
- **Material 3 UI**: 9 screens in desktop order (CA entries removed), **☰** drawer persisted in DataStore, 6 color schemes × light/dark/system, zh / en / ru.
- **Foreground service**: persistent notification while the proxy runs.

---

## Quick Start

For most users, download the latest stable APK (arm64-v8a) from [Releases](https://github.com/SnishaperTeam/Snishaper-Droid/releases).

- Minimum system: **Android 7.0 (API 24)**.
- x86_64 emulators such as MuMu: build the debug APK with `-PdebugAbi=x86_64` from source.
- The first launch seeds CA-free default rules; start/stop the engine from the Dashboard.

For building from source, environment requirements and CA-free verification, see:

- **[Platform_EN.md](docs/Platform_EN.md)** — platform support, feature matrix and known limitations.
- **[build_EN.md](docs/build_EN.md)** — build guide (gomobile AAR, Gradle APK, MuMu smoke test and CI).

---

## Documentation

- **[Platform_EN.md](docs/Platform_EN.md)** — Android platform support and feature boundaries.
- **[build_EN.md](docs/build_EN.md)** — build, test and CA-free verification guide.
- **[CHANGELOG.md](CHANGELOG.md)** — release notes.
- **[SniShaper Wiki](https://github.com/SnishaperTeam/SniShaper/wiki)** — shared engine documentation (proxy modes, rule format):
  - [Core Proxy Modes](https://github.com/SnishaperTeam/SniShaper/wiki/Core-Proxy-Modes)
  - [Custom Rules Guide](https://github.com/SnishaperTeam/SniShaper/wiki/Custom-Rules-Guide)

---

## Build & Development

The UI is built with **Kotlin + Jetpack Compose + Material 3**; `gomobile/mobile` (a standalone Go module) binds **snishaper-core** into an AAR that runs in-process. The current target is **API 24 (minSdk) / 36 (compileSdk · targetSdk)**.

CA-free is enforced by four layers: **compile time** (no `certmanager` import), **instantiation time** (never inject a `CertGenerator`), **config sanitization** (startup load / import / rule CRUD / AutoRouter forced off / Evolution apply allow-list) and a **CI guard** (AAR export surface plus dependency-closure checks).

CI/CD follows the desktop release cadence:

- `.github/workflows/android.yml`: push / PR → `gomobile bind` (CA-free AAR) → contract verification → `lint test assembleRelease` → APK/AAB artifacts.
- `.github/workflows/release.yml`: `v*` tags → GitHub Release with APK + AAB + version + changelog, explicitly stating **“CA functionality removed”**.

Full build instructions live in **[build_EN.md](docs/build_EN.md)**.

---

## Acknowledgements

Inspired by and built upon these excellent open-source projects:

- [DoH-ECH-Demo](https://github.com/0xCaner/DoH-ECH-Demo)
- [lumine](https://github.com/moi-si/lumine)
- [snishaper-core](https://github.com/SnishaperTeam/snishaper-core)
- [golang.org/x/mobile](https://pkg.go.dev/golang.org/x/mobile) (gomobile)
- [Jetpack Compose](https://developer.android.com/jetpack/compose) / [Material 3](https://m3.material.io/)

## Activity & Contributors

[![GitHub contributors](https://img.shields.io/github/contributors/SnishaperTeam/Snishaper-Droid?style=flat&label=Contributors)](https://github.com/SnishaperTeam/Snishaper-Droid/graphs/contributors)
[![GitHub commit activity](https://img.shields.io/github/commit-activity/m/SnishaperTeam/Snishaper-Droid?style=flat&label=Monthly%20commits)](https://github.com/SnishaperTeam/Snishaper-Droid/graphs/contributors)
[![GitHub last commit](https://img.shields.io/github/last-commit/SnishaperTeam/Snishaper-Droid?style=flat&label=Last%20commit)](https://github.com/SnishaperTeam/Snishaper-Droid/commits/main)

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

## License

[GNU Affero General Public License v3.0](LICENSE) (AGPL-3.0)
