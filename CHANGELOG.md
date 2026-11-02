# Changelog

All notable changes to Snishaper-Droid are documented in this file.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the
project adheres to the desktop SniShaper release cadence (`alpha` / `beta` / `rc` / `stable`).

## [Unreleased]

### Added

- Signed release pipeline: `signingConfigs` reads `keystore.properties` or the
  `SNISHAPER_KEYSTORE*` environment variables, signs with v2 + v3 + v4, and the new
  `scripts/init-keystore.ps1` generates the PKCS12 keystore (RSA 4096, 30 years).
- `scripts/build-apk.ps1`: end-to-end local release build (missing AAR / frontend bundle →
  `assembleRelease`/`bundleRelease` → `apksigner verify` → `build/dist/` with `SHA256SUMS.txt`).
- `:app:signingInfo` task reporting the resolved keystore, alias and schemes.
- Release workflow imports the keystore from repository secrets, gates on `apksigner verify`
  and publishes `SHA256SUMS.txt` alongside the APK/AAB/AAR.
- `GetBuildInfo` bridge method; the About page now shows app version, core version, release
  channel, Go runtime and build platform.
- Dashboard traffic counters (down / up) from the engine stats payload.
- Configurable TUN MTU in Settings, applied by `TunVpnService` when the tunnel starts.

### Changed

- Frontend de-desktop pass: Settings keeps only port, language, appearance, theme, rule
  auto-update, TUN MTU, certificates, IPv6 and the Cloudflare IP pool.
- Dashboard drops the outbound-NIC picker and the port-occupant kill dialog.
- About drops the in-app updater (7z/exe download, update channel, pending update) and links
  to GitHub Releases instead.
- Logs page streams the engine ring buffer continuously; the capture start/stop switch is gone.
- Routing page is read-only status plus live flow (the desktop smart-routing switch cannot be
  applied on Android and always failed).
- `GetBuildInfo` wired into the bridge; the unused upstream / fragment / auto-routing-config /
  migration-enabled / import / export-cert bridge branches removed.
- Documents (README, build guides, changelog) realigned with the restored CA surface.

### Removed

- Desktop-only controls and their bindings: tray, autostart (and its three sub-toggles),
  system proxy, update channel, download source + speed measurement, NIC selection and
  exclusion lists, TUN stack / adapter name / route exclude, log file list.
- Unused frontend modules (`components/ui/Autocomplete.tsx`, `lib/useClickOutside.ts`),
  dead i18n keys across zh/en/ru, and the unused `onCancel` prop of `RuleForm`.

### Notes

- Core engine pinned to `snishaper-core v0.8.4`; `minSdk = 24`, `compileSdk = 37.2`,
  `targetSdk = 36`.
- Version aligned with the desktop app: `1.30.x`.
