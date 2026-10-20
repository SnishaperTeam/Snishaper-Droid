# gomobile wrapper module (SniShaper Droid)

Independent Go module that binds `snishaper-core` into an Android AAR. It is
**not** part of `snishaper-core` and does not modify it.

- module: `github.com/SnishaperTeam/snishaper-droid/gomobile`
- core: `github.com/SnishaperTeam/snishaper-core v0.4.0`
- bound package: `./mobile` (exports exactly `App` and `Listener`)
- AAR output: `../app/libs/snishaper-core.aar`
- frozen Kotlin↔Go contract: `../docs/android-bridge-api.md`

## CA-free guarantee

`mobile` never imports `snishaper-core/certmanager` and never installs a
`proxy.CertGenerator`. The AAR therefore contains no CA code and no certificate
material is ever created. Rules are restricted to `tls-rf` / `transparent`
(`direct` only in the shipped built-in rules); mitm / quic / migration, rule
level `sni_fake` / `cert_verify` / ECH fields are rejected or cleared on every
entry point (startup load, import, CRUD, evolution apply). Smart routing is
forced to `mode:""` because its Cloudflare branch synthesizes MITM rules.

## Layout

| Path | Purpose |
|---|---|
| `mobile/app.go` | `App` lifecycle, proxy control, status/stats/logs, IPv6 probe, TUN control |
| `mobile/config.go` | JSON config CRUD (site groups / upstreams / DoH nodes / ECH / NAT64 / fragment / Cloudflare / import-export / auto-routing) |
| `mobile/sanitize.go` | CA-free rule policy (the only place that rewrites modes / clears CA fields) |
| `mobile/evolution.go` | evolution tester; only `tls_fragment`/`direct` results may be applied |
| `mobile/tun_android.go` | VpnService fd → sing-tun `Options.FileDescriptor` + core `singtun.Handler` + gvisor stack |
| `mobile/tun_stub.go` | non-Android stub so `go test ./...` works on the host |
| `cmd/gen-defaults` | one-off tool that converts the desktop default rules to CA-free defaults |
| `defaults/` | generated `rules.json` + `settings.json` (also copied to app assets) |

## Defaults

`defaults/` and `../app/src/main/assets/defaults/` are generated from the
desktop repository (read-only):

```powershell
cd gomobile
go run ./cmd/gen-defaults `
  -desktop-rules  D:/c++/Contributions/SniShaper-main/rules/config.json `
  -desktop-settings D:/c++/Contributions/SniShaper-main/config/settings.json `
  -out ./defaults -assets ../app/src/main/assets/defaults
```

Result: 222 site groups, 0 mitm/quic/migration, 0 `sni_fake`, 0 `ech_*`
(77 CA-dependent rules rewritten to `tls-rf`); DNS nodes / ECH profiles /
NAT64 profiles are kept.

## Build

```powershell
pwsh -File ../scripts/build-aar.ps1
# or manually:
cd gomobile
gomobile bind -target=android/arm64,android/arm,android/amd64 `
  -androidapi 24 -tags with_gvisor -javapkg com.snishaper.core `
  -trimpath -ldflags "-s -w" `
  -o ../app/libs/snishaper-core.aar ./mobile
```

`-trimpath -ldflags "-s -w"` are required to stay under GitHub's 100 MB file
limit: the 3-ABI AAR is 74.7 MB stripped (140.6 MB unstripped) and the
exported Java surface / CA-free behaviour is unchanged.

Environment requirements (the script fixes these):

- `JAVA_HOME=D:\Jetbrains IDES\Android Studio\jbr` (jbr 25.0.3)
- `ANDROID_HOME=D:\AndroidSDKs`
- `ANDROID_NDK_HOME=D:\AndroidSDKs\ndk\30.0.16248370` (the machine default 27.2 is unusable)
- `GOPROXY=https://goproxy.cn,direct`, `GOSUMDB=off`
- `go.mod` must keep `tool golang.org/x/mobile/cmd/gobind` and the pinned
  `golang.org/x/mobile v0.0.0-20260803200217-62cee1672c8e` (matches the
  installed gomobile/gobind binaries); otherwise `gomobile bind` refuses to run.

`-tags with_gvisor` is required for the TUN data plane: without it sing-tun
returns `gVisor is not included in this build, rebuild with -tags with_gvisor`.

## Self-checks

```powershell
go test ./...
go list -deps ./mobile | Select-String certmanager   # must be empty
go build -tags with_gvisor ./mobile                   # GOOS=android GOARCH=arm64
```

The tests cover: mode normalization, write-path rejection of mitm/quic/migration,
CA-field clearing, desktop `rules/config.json` import (222 groups → 0 CA modes),
import/export round-trip, smart-routing rejection, proxy lifecycle, port
fallback, and the committed defaults being CA-free.

## Notes

- `snishaper-core` generates rule IDs with `time.Now().UnixNano()`. On hosts
  with a coarse clock (observed on Windows) two consecutive adds can collide;
  the wrapper waits for the next clock tick before calling the core add methods
  (`waitForUniqueGeneratedID`).
- TUN must not call `proxyServer.SetTUNMode(true)`: the Kotlin side excludes the
  app itself from the VPN (`addDisallowedApplication`), so outbound sockets are
  not captured and no interface binding is needed (non-root Android cannot
  `SO_BINDTODEVICE` anyway).
