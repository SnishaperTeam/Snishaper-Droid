# Android Platform Support (Snishaper-Droid)

> Platform scope, feature matrix and known limitations of Snishaper-Droid. For the desktop app see [SniShaper Platform_EN.md](https://github.com/SnishaperTeam/SniShaper/blob/main/docs/Platform_EN.md).

## 1. Versions & ABIs

| Item | Value |
|---|---|
| Minimum Android | 7.0 / API 24 |
| compileSdk / targetSdk | API 36 |
| Supported ABIs | `arm64-v8a` (recommended), `armeabi-v7a`, `x86_64` (emulators) |
| Test device | MuMu emulator (Android 15, x86_64) |
| Runtime | Single Activity + WebView (the desktop React/MUI frontend); Go engine runs in-process via the gomobile AAR |
| Foreground service | Persistent notification while the proxy runs |

## 2. Feature matrix

### Kept

| Capability | Notes |
|---|---|
| Hybrid proxy port | HTTP CONNECT + SOCKS5, default `127.0.0.1:8080` |
| Rule engine | Site groups, upstream binding, DNS policy |
| TLS-RF fragmentation | Records / segments / interval / TLS minor version / OOB, global and per-rule |
| Transparent / Direct | Raw pass-through and direct |
| DoH / ECH (DoH layer) | Failover, ECH profiles, QUIC DoH, frontend SNI |
| Cloudflare preferred-IP pool | Speed test, health check, invalidation |
| Named CDN IP pools | CRUD over named pools (`GetIPPools` / `UpdateIPPools`); rules bind `ip_pool` by name |
| Live connections | `GetActiveConnections` (protocol / host / destination / matched rule / mode / bytes / age) |
| Match explanation | `ExplainMatch` runs the real pipeline and reports mode / rule / DNS / upstream / candidates |
| Configuration profiles | Named snapshots of settings + rules: create, rename, duplicate, switch, import, export |
| Diagnostics bundle | Status + logs + redacted settings as a zip shared through the system sheet |
| Startup migration report | `GetLastMigrationReport`, surfaced on the Dashboard |
| NAT64 | Multiple prefixes with tests |
| Evolution tests | Direct / TCPing / domain-fronting / TLS-fragment / ECH / QUIC results shown |
| Import / export | Desktop format; rules are only validated on import (mode whitelist + domain de-dup), CA fields are kept |
| VpnService TUN (Beta) | Kotlin tunnel + Go `sing-tun` + gvisor stack, no root |
| UI | The desktop React/MUI frontend (11 screens), ☰ drawer, 6 color schemes × light/dark, zh/en/ru |

### CA capabilities (restored)

| Capability | Notes |
|---|---|
| CA generation / regeneration / uninstall / export (PEM download) | Via `certmanager` (core v0.8.4-equivalent interface) |
| Trust-store install & status | Mobile implementation: root su + `certutil` into the system trust store |
| Certificate listing / uninstall | `GetInstalledCerts` / `UninstallCert(token)` |
| Rule modes `mitm` / `quic` / `migration` and rule-level `cert_verify` | Same as desktop, fully supported by RuleForm |
| Evolution rule application | Allow-list restriction lifted now that the CA paths are available |

> Historical note: an early Droid build removed these capabilities under a CA-free design; they have since been fully restored. See `docs/core-ca-coupling.md` (historical audit) and `docs/EngineBridge.md`.

### Desktop-only, not applicable on Android

System proxy (registry), tray, window controls, autostart, in-app updater, open log files, kill port occupant, hibernation.

## 3. Permissions

| Permission | Purpose |
|---|---|
| `INTERNET` | Proxy, DoH, rule updates |
| `ACCESS_NETWORK_STATE` | IPv6 probe and network state |
| `FOREGROUND_SERVICE` *(when TUN/VPN is enabled)* | Keep the proxy alive in the foreground |

## 4. Known limitations

- **TUN/VpnService** is Beta: the data plane is implemented but still needs real-device/emulator verification of connectivity and background survival.
- No system-wide proxy API: use VpnService or a manual Wi-Fi proxy.
- Installing the CA into the system trust store requires a rooted device (su + certutil); non-rooted devices can generate/export the certificate but cannot install it system-wide.
- Smart routing is unavailable (product decision, see the sanitize branch in `app.go`); unmatched domains fall back to Direct.
- No in-app updater; watch [Releases](https://github.com/SnishaperTeam/Snishaper-Droid/releases).

## 5. Data directory

| Path | Content |
|---|---|
| `<filesDir>/config/settings.json` | App settings (port, language, theme, TUN, fragmentation, CF pool) |
| `<filesDir>/rules/config.json` | Site rules / upstreams / DoH nodes / ECH profiles / NAT64 |
| `<filesDir>/certs/` | Local CA (`ca.crt` / `ca.key`) created by certmanager |
