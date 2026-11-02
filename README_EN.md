# Snishaper-Droid

[中文](README.md) | [English](README_EN.md)

[![Android](https://img.shields.io/badge/Android-7.0%2B%20(API%2024)-3DDC84?style=flat&logo=android&logoColor=white)](https://developer.android.com) [![Kotlin](https://img.shields.io/badge/Kotlin-WebView%20Host-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org) [![Core](https://img.shields.io/badge/Core-snishaper--core-00ADD8?style=flat&logo=go)](https://github.com/SnishaperTeam/snishaper-core) [![License](https://img.shields.io/badge/License-AGPL--3.0-blue?style=flat&logo=open-source-initiative)](LICENSE) [![GitHub Release](https://img.shields.io/github/v/release/SnishaperTeam/Snishaper-Droid?style=flat&logo=github&label=Release)](https://github.com/SnishaperTeam/Snishaper-Droid/releases) [![GitHub Downloads](https://img.shields.io/github/downloads/SnishaperTeam/Snishaper-Droid/total?style=flat&logo=github&label=Downloads)](https://github.com/SnishaperTeam/Snishaper-Droid/releases) [![GitHub last commit](https://img.shields.io/github/last-commit/SnishaperTeam/Snishaper-Droid?style=flat&logo=git&label=Last%20Commit)](https://github.com/SnishaperTeam/Snishaper-Droid/commits/main) [![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/SnishaperTeam/Snishaper-Droid/android.yml?style=flat&logo=githubactions&label=CI)](https://github.com/SnishaperTeam/Snishaper-Droid/actions)

**Snishaper-Droid** is the **Android client of [SniShaper](https://github.com/SnishaperTeam/SniShaper)**: it reuses the desktop **React 19 + MUI** frontend verbatim (bundled into the APK and rendered in a WebView; Kotlin is only the host and bridge), and the proxy engine comes from **[snishaper-core](https://github.com/SnishaperTeam/snishaper-core)**, compiled into an AAR via **gomobile bind** and running in-process — no root required. Through **TLS fragmentation**, **encrypted DNS / ECH (DoH layer)**, **NAT64** and **VpnService global proxy**, it delivers a stable and flexible browsing experience in complex network environments.

> **Certificates**: the root CA is generated and exported by `certmanager` and can be written into the Android system trust store through `su` + `certutil` (requires a rooted device). Without root the PEM can only be exported and installed by the user as a user certificate. Rule modes `mitm` / `quic` / `migration` and rule-level `cert_verify` rely on that certificate and only work while it is trusted. See **[CA support](#ca-support)**.

Android 7.0+ is supported. See **[Platform_EN.md](docs/Platform_EN.md)**.

---

## CA support

| Capability | Android status |
|---|---|
| Root CA generation / reset / PEM export | Available (`certmanager` is compiled into the AAR; Settings → Certificates) |
| Install into the system trust store | Needs root: `su` + `certutil`; without root, install the PEM manually as a user certificate |
| List / remove installed certificates | Available (reads the system trust store; removal also needs root) |
| Rule modes `mitm` / `quic` / `migration` | Retained; decryption only works when the certificate is trusted by the target app |
| Rule-level `cert_verify`, `sni_fake`, rule-level ECH | Retained; same MITM dependency |
| CA-dependent built-in rules | Sanitized on import and degrade to `tls-rf` when no certificate is trusted |

**Why does installing a certificate need root?** Since Android 7.0 user certificates only apply to apps that opt in,
and the system store lives in `/system/etc/security/cacerts`, which requires elevation. The app never installs
silently: the UI states up front that a superuser prompt will appear.

---

## Related projects

[**SniShaper**](https://github.com/SnishaperTeam/SniShaper) is the desktop app (Windows / Linux / macOS) built on the same engine, and [**FlowWeaver**](https://github.com/SnishaperTeam/FlowWeaver) is the full-featured client branch. All three are **actively maintained**:

| | Snishaper-Droid (this repository) | [SniShaper](https://github.com/SnishaperTeam/SniShaper) | [FlowWeaver](https://github.com/SnishaperTeam/FlowWeaver) |
|---|---|---|---|
| Focus | Native Android client | Desktop GUI / CLI | Full-featured client / gateway |
| Platform | Android 7.0+ | Windows / Linux / macOS | Windows / Linux / macOS |
| Core strengths | TLS fragmentation, DoH/ECH, NAT64, VpnService TUN | ECH injection, TLS fragmentation, QUIC, session migration, TUN | Clash subscriptions, multi-protocol nodes, WireGuard, TUN gateway |

All three take their proxy engine from the standalone **[snishaper-core](https://github.com/SnishaperTeam/snishaper-core)** library; this repository keeps only the Android application layer (WebView host, VpnService and configuration data).

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
- **Import / export**: desktop-compatible format, sanitized on import for Android limits.
- **React + MUI UI**: the same frontend bundle as the desktop build (9 screens; desktop-only tray, autostart, system-proxy, update-channel and NIC-picker controls removed), **☰** drawer with persisted visibility, 6 color schemes × light/dark/system, zh / en / ru.
- **Foreground service**: persistent notification while the proxy runs.

---

## Quick Start

For most users, download the latest stable APK (arm64-v8a) from [Releases](https://github.com/SnishaperTeam/Snishaper-Droid/releases).

- Minimum system: **Android 7.0 (API 24)**.
- x86_64 emulators such as MuMu: build the debug APK with `-PdebugAbi=x86_64` from source.
- The first launch seeds the default rule set; start/stop the engine from the Dashboard.

For building from source, environment requirements and signing setup, see:

- **[Platform_EN.md](docs/Platform_EN.md)** — platform support, feature matrix and known limitations.
- **[build_EN.md](docs/build_EN.md)** — build guide (gomobile AAR, Gradle APK, MuMu smoke test and CI).

---

## Documentation

- **[Platform_EN.md](docs/Platform_EN.md)** — Android platform support and feature boundaries.
- **[build_EN.md](docs/build_EN.md)** — build, sign, test and AAR verification guide.
- **[CHANGELOG.md](CHANGELOG.md)** — release notes.
- **[SniShaper Wiki](https://github.com/SnishaperTeam/SniShaper/wiki)** — shared engine documentation (proxy modes, rule format):
  - [Core Proxy Modes](https://github.com/SnishaperTeam/SniShaper/wiki/Core-Proxy-Modes)
  - [Custom Rules Guide](https://github.com/SnishaperTeam/SniShaper/wiki/Custom-Rules-Guide)

---

## Build & Development

The UI reuses the desktop **React 19 + MUI** frontend verbatim (a Kotlin WebView host plus the Wails bridge); `gomobile/mobile` (a standalone Go module) binds **snishaper-core** into an AAR that runs in-process. The current target is **API 24 (minSdk) / 36 (compileSdk · targetSdk)**.

Release packages are signed with the repository keystore (`keystore/release.jks`, v2 + v3 + v4 schemes); one local command builds and verifies everything: `pwsh -File scripts/build-apk.ps1 -Bundle`. CI imports the same key from Secrets, gates on `apksigner verify` and ships `SHA256SUMS.txt` alongside the artifacts. R8 stays off (`isMinifyEnabled = false`) so release and debug behave identically on the Go side.

CI/CD follows the desktop release cadence:

- `.github/workflows/android.yml`: push / PR → `gomobile bind` → AAR contract verification → `lint test assembleDebug` (single ABI for fast feedback).
- `.github/workflows/release.yml`: manual `workflow_dispatch` (confirming version + channel) → the bot commits the bump → import the signing key → `assembleRelease bundleRelease` → `apksigner verify` → publish a GitHub Release with the signed APK + AAB + AAR + `SHA256SUMS.txt` + changelog.

Full build instructions live in **[build_EN.md](docs/build_EN.md)**.

---

## Acknowledgements

Inspired by and built upon these excellent open-source projects:

- [DoH-ECH-Demo](https://github.com/0xCaner/DoH-ECH-Demo)
- [lumine](https://github.com/moi-si/lumine)
- [snishaper-core](https://github.com/SnishaperTeam/snishaper-core)
- [golang.org/x/mobile](https://pkg.go.dev/golang.org/x/mobile) (gomobile)
- [React](https://react.dev/) / [MUI](https://mui.com/) (the desktop frontend, reused verbatim)

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
