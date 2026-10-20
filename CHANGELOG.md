# Changelog

All notable changes to Snishaper-Droid are documented in this file.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the
project adheres to the desktop SniShaper release cadence (`alpha` / `beta` / `rc` / `stable`).

## [Unreleased]

### Added

- Material 3 Compose UI with the 9 desktop screens (Dashboard, Proxies, Rules, Routing,
  DNS, Evolution, Logs, Settings, About), ☰ modal navigation drawer persisted in DataStore,
  6 desktop color schemes × light/dark/system, and zh/en/ru resources.
- `SnishaperEngine` Kotlin singleton bridging the gomobile AAR: lifecycle, status/stats,
  ring-buffer logs, route events, configuration CRUD, import/export, evolution, VpnService TUN.
- Independent `gomobile/` Go module producing the CA-free `snishaper-core.aar`
  (arm64-v8a + armeabi-v7a + x86_64, `-androidapi 24`, `-tags with_gvisor`, stripped).
- Rule sanitization for every config entry point (startup load / import / CRUD /
  AutoRouter forced to `mode:""` / evolution apply allow-list).
- CA-free default rule set generated from the desktop rules (222 site groups;
  77 CA-dependent rules converted from `mitm`/`quic` to `tls-rf`).
- Build/test scripts: `build-aar.ps1`, `verify-aar.ps1`, `dev-env.ps1`,
  `mumu-status.ps1`, `smoke-test.ps1`.
- Android CI and release workflows with AAR build, contract verification,
  `lint/test/assembleRelease` and APK/AAB artifacts.

### Removed

- **All CA functionality**: certificate generation/storage/export/regeneration/uninstall,
  Android trust-store and KeyStore interaction, the `certmanager` package from the AAR,
  and every CA API / UI entry.
- Rule modes `mitm`, `quic` and `migration`, rule-level `cert_verify`, `sni_fake` and
  rule-level ECH.
- Smart routing (Auto Routing) strategy controls; the Routing page keeps the traffic-flow
  and GFWList status views only.
- Desktop-only integrations that have no Android counterpart (system proxy registry,
  tray, window controls, autostart, in-app updater, kill-port).

### Notes

- Core engine pinned to `snishaper-core v0.4.0`; `minSdk = 24`, `targetSdk = compileSdk = 36`.
- Version aligned with the desktop app: `1.29.x`.
