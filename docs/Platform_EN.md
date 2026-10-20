# Android Platform Support (Snishaper-Droid)

> Platform scope, feature matrix and known limitations of Snishaper-Droid. For the desktop app see [SniShaper Platform_EN.md](https://github.com/SnishaperTeam/SniShaper/blob/main/docs/Platform_EN.md).

## 1. Versions & ABIs

| Item | Value |
|---|---|
| Minimum Android | 7.0 / API 24 |
| compileSdk / targetSdk | API 36 |
| Supported ABIs | `arm64-v8a` (recommended), `armeabi-v7a`, `x86_64` (emulators) |
| Test device | MuMu emulator (Android 15, x86_64) |
| Runtime | Single Activity + Compose UI; Go engine runs in-process via the gomobile AAR |
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
| NAT64 | Multiple prefixes with tests |
| Evolution tests | Direct / TCPing / domain-fronting / TLS-fragment / ECH / QUIC results shown |
| Import / export | Desktop format with automatic CA-free sanitization |
| VpnService TUN (Beta) | Kotlin tunnel + Go `sing-tun` + gvisor stack, no root |
| UI | 9 Material 3 screens, ☰ drawer, 6 color schemes × light/dark, zh/en/ru |

### Removed (CA-related)

| Capability | Reason |
|---|---|
| CA generation / storage / export / regeneration / uninstall, trust-store install & status, certificate listing | Requirement: remove completely; Android/gomobile cannot do trust-chain or KeyStore interaction |
| Rule modes `mitm` / `quic` / `migration` | Depend on local TLS termination |
| Rule-level `cert_verify`, `sni_fake`, rule-level ECH | Only effective on the MITM path |
| Smart routing strategy | The Cloudflare branch synthesizes MITM rules inside core |
| Evolution conversion except TLS-Fragment / Direct | Generated rules are mitm/quic |

> 77 of the 222 built-in desktop rules (68 MITM + 9 QUIC) were converted to `tls-rf` when generating the Android defaults, with all CA fields stripped. Imported desktop configs receive the same sanitization and report the rewritten count.

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
- No CA: MITM / QUIC / Migration / domain fronting / rule-level ECH are unavailable by design.
- Smart routing is unavailable; unmatched domains fall back to Direct.
- Evolution is limited to probing and result display; applying rules is restricted by the CA-free allow-list.
- No in-app updater; watch [Releases](https://github.com/SnishaperTeam/Snishaper-Droid/releases).

## 5. Data directory

| Path | Content |
|---|---|
| `<filesDir>/config/settings.json` | App settings (port, language, theme, TUN, fragmentation, CF pool) |
| `<filesDir>/rules/config.json` | Site rules / upstreams / DoH nodes / ECH profiles / NAT64 |
| No certificate directory | This build never creates `ca.crt` / `ca.key` |
