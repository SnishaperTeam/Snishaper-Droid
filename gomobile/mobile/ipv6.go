package mobile

import (
	"net"
	"time"
)

// ipv6ProbeTargets are literal IPv6 endpoints; dialing them does not involve
// DNS, so a success means the device has working IPv6 egress.
var ipv6ProbeTargets = []string{
	"[2606:4700:4700::1111]:443",
	"[2001:4860:4860::8888]:443",
}

// refreshIPv6 probes IPv6 connectivity and caches the result. Concurrent
// callers (Start warm-up + bridge GetIPv6Available) share one in-flight
// probe instead of racing duplicates.
func (a *App) refreshIPv6() bool {
	a.mu.Lock()
	if a.ipv6Probing {
		a.mu.Unlock()
		return a.ipv6Available
	}
	a.ipv6Probing = true
	a.mu.Unlock()

	available := probeIPv6()
	a.mu.Lock()
	a.ipv6Available = available
	a.ipv6CheckedAt = time.Now()
	a.ipv6Probing = false
	a.mu.Unlock()
	a.dispatchLog("[ipv6] egress available: " + boolText(available))
	a.pushState()
	return available
}

func probeIPv6() bool {
	results := make(chan bool, len(ipv6ProbeTargets))
	for _, target := range ipv6ProbeTargets {
		go func(target string) {
			conn, err := net.DialTimeout("tcp6", target, 2*time.Second)
			if err != nil {
				results <- false
				return
			}
			_ = conn.Close()
			results <- true
		}(target)
	}
	for i := 0; i < len(ipv6ProbeTargets); i++ {
		if <-results {
			return true
		}
	}
	return false
}

func boolText(value bool) string {
	if value {
		return "true"
	}
	return "false"
}
