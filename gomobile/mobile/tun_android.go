//go:build android

package mobile

import (
	"context"
	"fmt"
	"net/netip"
	"sync"
	"time"

	"github.com/SnishaperTeam/snishaper-core/singtun"
	"github.com/sagernet/sing-tun"
	"github.com/sagernet/sing/common/control"
)

// The fake-ip ranges must match the addresses the core singtun.Handler and the
// Kotlin VpnService.Builder use (see singtun/fakeip.go in snishaper-core).
const (
	tunFakeIPv4Prefix = "198.18.0.0/16"
	tunFakeIPv6Prefix = "fd65:198:18::/64"
	tunGateway4       = "198.18.0.1"
	tunGateway6       = "fd65:198:18::1"
	tunDNS4           = "198.18.0.2"
	tunDNS6           = "fd65:198:18::2"
)

// androidTUN owns the sing-tun device + gvisor stack built on top of the
// VpnService file descriptor. The core singtun.Handler is reused unchanged, so
// no code from snishaper-core is modified or duplicated.
type androidTUN struct {
	mu      sync.Mutex
	device  tun.Tun
	stack   tun.Stack
	handler *singtun.Handler
	running bool
	message string
}

// newPlatformTUN builds the TUN data plane from an Android VpnService fd.
// The fd ownership moves to the returned handle: Stop() closes it.
func newPlatformTUN(cfg tunConfig) (tunHandle, error) {
	if cfg.Fd <= 0 {
		return nil, fmt.Errorf("invalid TUN file descriptor %d", cfg.Fd)
	}
	mtu := cfg.MTU
	if mtu < 576 || mtu > 9000 {
		mtu = 1500
	}
	logf := cfg.Logf
	if logf == nil {
		logf = func(string) {}
	}

	options := tun.Options{
		FileDescriptor: int(cfg.Fd),
		MTU:            uint32(mtu),
		Inet4Address:   []netip.Prefix{netip.MustParsePrefix(tunFakeIPv4Prefix)},
		Inet4Gateway:   netip.MustParseAddr(tunGateway4),
		Inet6Address:   []netip.Prefix{netip.MustParsePrefix(tunFakeIPv6Prefix)},
		Inet6Gateway:   netip.MustParseAddr(tunGateway6),
		DNSAddress: []netip.Addr{
			netip.MustParseAddr(tunDNS4),
			netip.MustParseAddr(tunDNS6),
		},
		// VpnService owns the interface addresses and routes; sing-tun must not
		// touch netlink on Android (no CAP_NET_ADMIN for apps).
		AutoRoute:                 false,
		StrictRoute:               false,
		EXP_DisableDNSHijack:      false,
		EXP_ExternalConfiguration: true,
		Logger:                    &singTunLogger{logf: logf},
	}
	finder := control.NewDefaultInterfaceFinder()
	if err := finder.Update(); err != nil {
		logf("[tun] interface finder update failed: " + err.Error())
	}
	options.InterfaceFinder = finder

	device, err := tun.New(options)
	if err != nil {
		return nil, fmt.Errorf("create tun device from fd %d: %w", cfg.Fd, err)
	}
	handler := singtun.NewHandler(cfg.ProxyAddr, cfg.Resolver, logf)
	stack, err := tun.NewStack("gvisor", tun.StackOptions{
		Context:    context.Background(),
		Tun:        device,
		TunOptions: options,
		Handler:    handler,
		UDPTimeout: 60 * time.Second,
		Logger:     &singTunLogger{logf: logf},
	})
	if err != nil {
		_ = device.Close()
		handler.Close()
		return nil, fmt.Errorf("create gvisor stack: %w", err)
	}

	if err := device.Start(); err != nil {
		_ = stack.Close()
		_ = device.Close()
		handler.Close()
		return nil, fmt.Errorf("start tun device: %w", err)
	}
	if err := stack.Start(); err != nil {
		_ = device.Close()
		handler.Close()
		return nil, fmt.Errorf("start gvisor stack: %w", err)
	}
	logf("[tun] started without netiface binding (VpnService excludes this app)")
	return &androidTUN{device: device, stack: stack, handler: handler, running: true}, nil
}

func (t *androidTUN) Running() bool {
	t.mu.Lock()
	defer t.mu.Unlock()
	return t.running
}

func (t *androidTUN) Message() string {
	t.mu.Lock()
	defer t.mu.Unlock()
	return t.message
}

func (t *androidTUN) Stop() error {
	t.mu.Lock()
	if !t.running {
		t.mu.Unlock()
		return nil
	}
	t.running = false
	device := t.device
	stack := t.stack
	handler := t.handler
	t.mu.Unlock()

	var firstErr error
	if stack != nil {
		if err := stack.Close(); err != nil && firstErr == nil {
			firstErr = err
		}
	}
	if device != nil {
		// Closing the tun file releases the VpnService fd (owns it).
		if err := device.Close(); err != nil && firstErr == nil {
			firstErr = err
		}
	}
	if handler != nil {
		handler.Close()
	}
	if firstErr != nil {
		return fmt.Errorf("close tun: %w", firstErr)
	}
	return nil
}

// singTunLogger adapts the mobile log callback to github.com/sagernet/sing
// logger.Logger. Fatal/Panic only log: a library must never kill the app.
type singTunLogger struct {
	logf func(string)
}

func (l *singTunLogger) log(level string, args ...any) {
	if l == nil || l.logf == nil {
		return
	}
	l.logf("[tun:" + level + "] " + fmt.Sprint(args...))
}

func (l *singTunLogger) Trace(args ...any) { l.log("trace", args...) }
func (l *singTunLogger) Debug(args ...any) { l.log("debug", args...) }
func (l *singTunLogger) Info(args ...any)  { l.log("info", args...) }
func (l *singTunLogger) Warn(args ...any)  { l.log("warn", args...) }
func (l *singTunLogger) Error(args ...any) { l.log("error", args...) }
func (l *singTunLogger) Fatal(args ...any) { l.log("fatal", args...) }
func (l *singTunLogger) Panic(args ...any) { l.log("panic", args...) }
