//go:build !android

package mobile

import "fmt"

// newPlatformTUN is a stub outside Android so `go test ./...` and desktop
// tooling can build the wrapper module without the NDK/sing-tun TUN path.
func newPlatformTUN(cfg tunConfig) (tunHandle, error) {
	_ = cfg
	return nil, fmt.Errorf("TUN is only supported on Android builds")
}
