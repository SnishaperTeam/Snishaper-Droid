package mobile

import "github.com/SnishaperTeam/snishaper-core/dohresolver"

// tunHandle is the platform TUN data plane used by App.StartTUN / StopTUN.
type tunHandle interface {
	Running() bool
	Message() string
	Stop() error
}

// tunConfig carries everything the platform TUN implementation needs. The
// Android implementation feeds the VpnService fd into sing-tun and reuses the
// core exported singtun.Handler; other platforms return a stub error.
type tunConfig struct {
	Fd        int32
	MTU       int32
	ProxyAddr string
	Resolver  *dohresolver.FailoverResolver
	Logf      func(string)
}
