package mobile

import (
	"fmt"
	"net"
	"os"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/SnishaperTeam/snishaper-core/evolution"
	corelog "github.com/SnishaperTeam/snishaper-core/log"
	"github.com/SnishaperTeam/snishaper-core/proxy"
)

const (
	// engineVersion is shown on the About / Settings pages.
	engineVersion = "1.29.0"
	// coreVersion is the pinned snishaper-core release (see go.mod).
	coreVersion = "v0.4.0"

	proxyModeRule        = ""
	proxyModeTransparent = "transparent"

	defaultListenPort = 8080
	maxPortScan       = 100
)

// App is the only gomobile exported type besides Listener. It owns the engine:
// RuleManager + ProxyServer + AutoRouter (always Mode "") + evolution.Tester.
//
// Android never installs a CertGenerator: the MITM/QUIC code paths of the core
// stay unreachable and no certificate material is ever produced.
type App struct {
	dataDir      string
	settingsPath string
	rulesPath    string

	opMu sync.Mutex

	mu      sync.Mutex
	started bool
	rules   *proxy.RuleManager
	server  *proxy.ProxyServer
	tun     tunHandle
	lastErr string

	ipv6Available bool
	ipv6CheckedAt time.Time

	listenerMu sync.RWMutex
	listener   Listener

	logs *ringLog

	evoAutoRouter *proxy.AutoRouter
	tester        *evolution.Tester
	evoPollStop   chan struct{}
	evoRunning    bool
	lastTaskJSON  string
}

// NewApp creates the controller. dataDir must be an absolute app private
// directory (Android Context.filesDir). Config files live under
// <dataDir>/config/settings.json and <dataDir>/rules/config.json.
func NewApp(dataDir string) (app *App, err error) {
	defer recoverError(&err)

	dataDir = strings.TrimSpace(dataDir)
	if dataDir == "" {
		return nil, fmt.Errorf("dataDir is empty")
	}
	abs, err := filepath.Abs(dataDir)
	if err != nil {
		return nil, fmt.Errorf("resolve dataDir: %w", err)
	}
	instance := &App{
		dataDir:      abs,
		settingsPath: filepath.Join(abs, "config", "settings.json"),
		rulesPath:    filepath.Join(abs, "rules", "config.json"),
		logs:         newRingLog(ringLogCapacity),
	}
	for _, dir := range []string{filepath.Dir(instance.settingsPath), filepath.Dir(instance.rulesPath)} {
		if err := os.MkdirAll(dir, 0o755); err != nil {
			return nil, fmt.Errorf("create engine directory %s: %w", dir, err)
		}
	}
	return instance, nil
}

// SetListener installs the async event listener. It may be called repeatedly;
// nil clears it. Kotlin must keep a strong reference to the implementation.
func (a *App) SetListener(l Listener) {
	a.listenerMu.Lock()
	a.listener = l
	a.listenerMu.Unlock()
}

// Start initializes the engine (load + sanitize configs, assemble rule manager,
// proxy server and routers) without listening on any port. Idempotent.
func (a *App) Start() (err error) {
	defer recoverError(&err)

	a.opMu.Lock()
	defer a.opMu.Unlock()

	a.mu.Lock()
	if a.started {
		a.mu.Unlock()
		return nil
	}
	a.mu.Unlock()

	rm := proxy.NewRuleManager(a.settingsPath, a.rulesPath, proxy.WithLogger(corelog.Func(a.logf)))
	if err := rm.LoadConfig(); err != nil {
		a.setLastError(err.Error())
		return fmt.Errorf("load config: %w", err)
	}
	if err := a.sanitizeConfigLocked(rm); err != nil {
		a.setLastError(err.Error())
		return fmt.Errorf("sanitize config: %w", err)
	}
	rm.SetRouteEventCallback(func(domain, mode string) {
		a.dispatchRoute(domain, mode)
	})
	rm.SetOnConfigSaved(func() {
		a.pushState()
	})

	srv := proxy.NewProxyServer("127.0.0.1:"+rm.GetListenPort(),
		proxy.WithRuleManager(rm),
		proxy.WithLogger(corelog.Func(a.logf)),
	)
	srv.SetLogCallback(a.appendLog)
	srv.UpdateCloudflareIPPool(rm.GetCloudflareConfig().PreferredIPs)

	// Mode is forced to "" by sanitizeConfigLocked, so the auto router can only
	// ever return "direct"; it is kept for the read-only GFWList status page.
	rm.InitAutoRouter(srv.GetDoHResolver())

	// Separate router for the evolution tester (never registered on the rule
	// manager). Mode "" means it is only used for Cloudflare classification.
	evoRouter := proxy.NewAutoRouter(proxy.AutoRoutingConfig{Mode: ""}, srv.GetDoHResolver(), proxy.WithLogger(corelog.Func(a.logf)))

	a.mu.Lock()
	a.rules = rm
	a.server = srv
	a.evoAutoRouter = evoRouter
	a.started = true
	a.lastErr = ""
	a.mu.Unlock()

	a.dispatchLog(fmt.Sprintf("[engine] started (core %s, %d site groups, %d dns nodes)", coreVersion, len(rm.GetSiteGroups()), len(rm.GetDNSNodes())))
	a.pushState()
	return nil
}

// Stop stops the proxy, the TUN data plane and the evolution tester, persists
// the current config and releases resources. Idempotent.
func (a *App) Stop() (err error) {
	defer recoverError(&err)

	a.opMu.Lock()
	defer a.opMu.Unlock()

	a.mu.Lock()
	tun := a.tun
	a.tun = nil
	srv := a.server
	rm := a.rules
	evoRouter := a.evoAutoRouter
	tester := a.tester
	a.tester = nil
	a.evoRunning = false
	pollStop := a.evoPollStop
	a.evoPollStop = nil
	a.mu.Unlock()

	if pollStop != nil {
		close(pollStop)
	}
	if tester != nil {
		tester.StopTest()
	}
	if tun != nil {
		if stopErr := tun.Stop(); stopErr != nil {
			err = stopErr
		}
	}
	if srv != nil {
		if stopErr := srv.Stop(); stopErr != nil && err == nil {
			err = stopErr
		}
	}
	if evoRouter != nil {
		evoRouter.Stop()
	}
	if rm != nil {
		if saveErr := rm.SaveConfig(); saveErr != nil && err == nil {
			err = saveErr
		}
		if autoRouter := rm.GetAutoRouter(); autoRouter != nil {
			autoRouter.Stop()
		}
	}

	a.mu.Lock()
	a.started = false
	a.server = nil
	a.rules = nil
	a.evoAutoRouter = nil
	a.mu.Unlock()

	a.dispatchLog("[engine] stopped")
	a.pushState()
	if err != nil {
		return fmt.Errorf("stop engine: %w", err)
	}
	return nil
}

// IsRunning reports whether the proxy listener is running.
func (a *App) IsRunning() bool { return a.IsProxyRunning() }

// IsProxyRunning reports whether the proxy listener is running.
func (a *App) IsProxyRunning() bool {
	a.mu.Lock()
	defer a.mu.Unlock()
	return a.server != nil && a.server.IsRunning()
}

// StartProxy starts the local mixed HTTP/SOCKS5 proxy. It reuses the persisted
// port and falls back to the next free port (proxy.EnsurePortAvailable relies on
// desktop process tools, so the wrapper uses a net.Listen probe instead).
func (a *App) StartProxy() (err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	return a.startProxyLocked()
}

func (a *App) startProxyLocked() error {
	a.mu.Lock()
	rm := a.rules
	srv := a.server
	a.mu.Unlock()
	if rm == nil || srv == nil {
		return fmt.Errorf("engine is not started")
	}
	if srv.IsRunning() {
		return nil
	}
	addr := a.ensurePortLocked(rm)
	if err := srv.SetListenAddr(addr); err != nil {
		return fmt.Errorf("set listen address: %w", err)
	}
	srv.UpdateCloudflareIPPool(rm.GetCloudflareConfig().PreferredIPs)
	if err := srv.Start(); err != nil {
		a.setLastError(err.Error())
		return fmt.Errorf("start proxy: %w", err)
	}
	if err := waitForListen(addr, 3*time.Second); err != nil {
		_ = srv.Stop()
		a.setLastError(err.Error())
		return err
	}
	a.dispatchLog("[proxy] listening on " + addr)
	a.pushState()
	return nil
}

// StopProxy stops the local proxy listener. Idempotent.
func (a *App) StopProxy() (err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	return a.stopProxyLocked()
}

func (a *App) stopProxyLocked() error {
	a.mu.Lock()
	srv := a.server
	a.mu.Unlock()
	if srv == nil {
		return nil
	}
	if !srv.IsRunning() {
		return nil
	}
	if err := srv.Stop(); err != nil {
		a.setLastError(err.Error())
		return fmt.Errorf("stop proxy: %w", err)
	}
	a.dispatchLog("[proxy] stopped")
	a.pushState()
	return nil
}

// GetListenAddr returns the current local listen address, e.g. 127.0.0.1:8080.
func (a *App) GetListenAddr() string {
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.server == nil {
		return ""
	}
	return a.server.GetListenAddr()
}

// SetListenPort validates and persists the proxy port. If the proxy is running
// it is restarted on the new port.
func (a *App) SetListenPort(port int32) (err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()

	if port < 1 || port > 65535 {
		return fmt.Errorf("port %d is out of range 1..65535", port)
	}
	a.mu.Lock()
	rm := a.rules
	srv := a.server
	a.mu.Unlock()
	if rm == nil || srv == nil {
		return fmt.Errorf("engine is not started")
	}
	wasRunning := srv.IsRunning()
	if wasRunning {
		if err := a.stopProxyLocked(); err != nil {
			return err
		}
	}
	portStr := strconv.Itoa(int(port))
	rm.SetListenPort(portStr)
	if err := srv.SetListenAddr("127.0.0.1:" + portStr); err != nil {
		return err
	}
	if err := rm.SaveConfig(); err != nil {
		return err
	}
	if wasRunning {
		if err := a.startProxyLocked(); err != nil {
			return err
		}
	}
	a.pushState()
	return nil
}

// GetProxyMode returns the proxy runtime mode ("" = per-rule mode).
func (a *App) GetProxyMode() string {
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.server == nil {
		return ""
	}
	return a.server.GetMode()
}

// SetProxyMode sets the proxy runtime mode. Android only allows "" (rule mode)
// and "transparent" (global transparent).
func (a *App) SetProxyMode(mode string) (err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	value := strings.ToLower(strings.TrimSpace(mode))
	switch value {
	case proxyModeRule, proxyModeTransparent:
	default:
		return fmt.Errorf("unsupported proxy mode %q: Android only allows \"\" and \"transparent\"", mode)
	}
	a.mu.Lock()
	srv := a.server
	a.mu.Unlock()
	if srv == nil {
		return fmt.Errorf("engine is not started")
	}
	if err := srv.SetMode(value); err != nil {
		return err
	}
	a.pushState()
	return nil
}

// CheckPortAvailable reports whether a local TCP port can be bound.
func (a *App) CheckPortAvailable(port int32) bool {
	if port < 1 || port > 65535 {
		return false
	}
	return a.checkPortFree(int(port))
}

// GetStatusJSON returns the runtime status snapshot (see docs/android-bridge-api.md §5).
func (a *App) GetStatusJSON() (out string) {
	defer recoverString(&out)
	return marshalJSON(a.statusPayload())
}

// GetStatsJSON returns the traffic counters.
func (a *App) GetStatsJSON() (out string) {
	defer recoverString(&out)
	var down, up, etc int64
	a.mu.Lock()
	srv := a.server
	a.mu.Unlock()
	if srv != nil {
		down, up, etc = srv.GetStats()
	}
	return marshalJSON(statsPayload{Down: down, Up: up, Etc: etc})
}

// GetTUNStatusJSON returns the TUN status snapshot.
func (a *App) GetTUNStatusJSON() (out string) {
	defer recoverString(&out)
	a.mu.Lock()
	tun := a.tun
	a.mu.Unlock()
	payload := tunStatusPayload{Supported: true}
	if tun != nil {
		payload.Running = tun.Running()
		payload.Enabled = tun.Running()
		payload.Message = tun.Message()
	}
	return marshalJSON(payload)
}

// GetRecentLogs returns up to maxLines buffered log lines, newest last.
func (a *App) GetRecentLogs(maxLines int32) (out string) {
	defer recoverString(&out)
	if a.logs == nil {
		return ""
	}
	return a.logs.recent(int(maxLines))
}

// ClearLogs drops the in-memory log buffer.
func (a *App) ClearLogs() {
	if a.logs != nil {
		a.logs.clear()
	}
}

// GetIPv6Available returns the cached IPv6 egress probe result.
func (a *App) GetIPv6Available() bool {
	a.mu.Lock()
	if !a.ipv6CheckedAt.IsZero() && time.Since(a.ipv6CheckedAt) < 30*time.Second {
		value := a.ipv6Available
		a.mu.Unlock()
		return value
	}
	a.mu.Unlock()
	return a.refreshIPv6()
}

// RefreshIPv6Check runs the IPv6 egress probe immediately.
func (a *App) RefreshIPv6Check() bool {
	return a.refreshIPv6()
}

// GetBuildInfoJSON returns version information for the About page.
func (a *App) GetBuildInfoJSON() (out string) {
	defer recoverString(&out)
	return marshalJSON(buildInfoPayload{
		Version:     engineVersion,
		Channel:     "stable",
		CoreVersion: coreVersion,
		GoVersion:   runtime.Version(),
		Platform:    runtime.GOOS + "/" + runtime.GOARCH,
	})
}

// StartTUN attaches the Android VpnService file descriptor and starts the TUN
// data plane. Ownership of fd moves to Go (os.NewFile); StopTUN closes it.
// The proxy must be running because TUN traffic is forwarded to it.
func (a *App) StartTUN(fd int32, mtu int32) (err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()

	a.mu.Lock()
	existing := a.tun
	srv := a.server
	a.mu.Unlock()
	if existing != nil && existing.Running() {
		return fmt.Errorf("TUN is already running")
	}
	if srv == nil {
		return fmt.Errorf("engine is not started")
	}
	if !srv.IsRunning() {
		return fmt.Errorf("start the proxy before starting TUN")
	}
	if fd <= 0 {
		return fmt.Errorf("invalid TUN file descriptor %d", fd)
	}
	if mtu <= 0 {
		mtu = 1500
	}
	mtu = clampInt32(mtu, 576, 9000)

	a.dispatchLog(fmt.Sprintf("[tun] starting TUN (fd=%d mtu=%d, proxy=%s)", fd, mtu, srv.GetListenAddr()))
	handle, err := newPlatformTUN(tunConfig{
		Fd:        fd,
		MTU:       mtu,
		ProxyAddr: srv.GetListenAddr(),
		Resolver:  srv.GetDoHResolver(),
		Logf:      a.appendLog,
	})
	if err != nil {
		a.setLastError(err.Error())
		return fmt.Errorf("start TUN: %w", err)
	}
	a.mu.Lock()
	a.tun = handle
	a.mu.Unlock()
	a.dispatchLog("[tun] TUN started")
	a.pushState()
	return nil
}

// StopTUN stops the TUN data plane and closes the VpnService file descriptor.
func (a *App) StopTUN() (err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	a.mu.Lock()
	handle := a.tun
	a.tun = nil
	a.mu.Unlock()
	if handle == nil {
		return nil
	}
	if stopErr := handle.Stop(); stopErr != nil {
		a.setLastError(stopErr.Error())
		err = fmt.Errorf("stop TUN: %w", stopErr)
	}
	a.dispatchLog("[tun] TUN stopped")
	a.pushState()
	return err
}

// --- internal helpers -------------------------------------------------------

func (a *App) sanitizeConfigLocked(rm *proxy.RuleManager) error {
	if rm == nil {
		return fmt.Errorf("rule manager is nil")
	}
	if rm.GetAutoRoutingConfig().Mode != "" {
		if err := rm.UpdateAutoRoutingConfig(proxy.AutoRoutingConfig{}); err != nil {
			return err
		}
		a.dispatchLog("[sanitize] smart routing disabled: the Cloudflare branch needs MITM+CA")
	}
	if rm.GetMigrationEnabled() {
		if err := rm.SetMigrationEnabled(false); err != nil {
			return err
		}
		a.dispatchLog("[sanitize] migration service disabled on Android")
	}
	if rm.GetMigrationServer() != "" {
		if err := rm.SetMigrationServer(""); err != nil {
			return err
		}
	}
	rewritten, err := a.enforceRulesLocked(rm)
	if err != nil {
		return err
	}
	if rewritten > 0 {
		a.dispatchLog(fmt.Sprintf("[sanitize] neutralized %d legacy rule(s) to the CA-free policy", rewritten))
	}
	if !isNumericPort(rm.GetListenPort()) {
		rm.SetListenPort(strconv.Itoa(defaultListenPort))
		if err := rm.SaveConfig(); err != nil {
			return err
		}
	}
	return nil
}

func (a *App) ensurePortLocked(rm *proxy.RuleManager) string {
	start := defaultListenPort
	if value := strings.TrimSpace(rm.GetListenPort()); isNumericPort(value) {
		if port, err := strconv.Atoi(value); err == nil && port > 0 {
			start = port
		}
	}
	for i := 0; i < maxPortScan; i++ {
		port := start + i
		if port > 65535 {
			port = 1024 + (port - 65535)
		}
		if !a.checkPortFree(port) {
			continue
		}
		if port != start {
			rm.SetListenPort(strconv.Itoa(port))
			if saveErr := rm.SaveConfig(); saveErr != nil {
				a.dispatchLog("[proxy] could not persist fallback port: " + saveErr.Error())
			}
			a.dispatchLog(fmt.Sprintf("[proxy] port %d is busy, switched to %d", start, port))
		}
		return fmt.Sprintf("127.0.0.1:%d", port)
	}
	return fmt.Sprintf("127.0.0.1:%d", start)
}

func (a *App) checkPortFree(port int) bool {
	listener, err := net.Listen("tcp", fmt.Sprintf("127.0.0.1:%d", port))
	if err != nil {
		return false
	}
	_ = listener.Close()
	return true
}

func (a *App) setLastError(message string) {
	a.mu.Lock()
	a.lastErr = strings.TrimSpace(message)
	a.mu.Unlock()
}

func (a *App) statusPayload() statusPayload {
	a.mu.Lock()
	defer a.mu.Unlock()

	payload := statusPayload{ListenPort: defaultListenPort}
	if a.server != nil {
		payload.ProxyRunning = a.server.IsRunning()
		payload.ProxyMode = a.server.GetMode()
		payload.ListenAddr = a.server.GetListenAddr()
		if _, port, err := net.SplitHostPort(payload.ListenAddr); err == nil {
			if value, convErr := strconv.Atoi(port); convErr == nil {
				payload.ListenPort = int32(value)
			}
		}
	}
	if a.rules != nil {
		if value, err := strconv.Atoi(strings.TrimSpace(a.rules.GetListenPort())); err == nil {
			payload.ListenPort = int32(value)
		}
		if payload.ListenAddr == "" {
			payload.ListenAddr = "127.0.0.1:" + strings.TrimSpace(a.rules.GetListenPort())
		}
	}
	if a.tun != nil {
		payload.TUNRunning = a.tun.Running()
		payload.TUNMessage = a.tun.Message()
	}
	payload.TUNSupported = true
	payload.IPv6Available = a.ipv6Available
	payload.LastError = a.lastErr
	return payload
}

func (a *App) appendLog(line string) {
	a.dispatchLog(strings.TrimRight(line, "\r\n"))
}

func (a *App) logf(format string, args ...any) {
	a.dispatchLog(fmt.Sprintf(format, args...))
}

func (a *App) dispatchLog(line string) {
	if a.logs != nil {
		a.logs.add(line)
	}
	a.listenerMu.RLock()
	listener := a.listener
	a.listenerMu.RUnlock()
	if listener == nil {
		return
	}
	defer func() { _ = recover() }()
	listener.OnLog(line)
}

func (a *App) dispatchRoute(domain string, mode string) {
	a.listenerMu.RLock()
	listener := a.listener
	a.listenerMu.RUnlock()
	if listener == nil {
		return
	}
	defer func() { _ = recover() }()
	listener.OnRouteEvent(domain, mode)
}

func (a *App) dispatchState() {
	a.listenerMu.RLock()
	listener := a.listener
	a.listenerMu.RUnlock()
	if listener == nil {
		return
	}
	state := a.GetStatusJSON()
	defer func() { _ = recover() }()
	listener.OnStateChanged(state)
}

// pushState dispatches a status snapshot to the listener.
func (a *App) pushState() {
	a.dispatchState()
}

func (a *App) pushEvolution(taskJSON string) {
	a.mu.Lock()
	a.lastTaskJSON = taskJSON
	a.mu.Unlock()
	a.listenerMu.RLock()
	listener := a.listener
	a.listenerMu.RUnlock()
	if listener == nil {
		return
	}
	defer func() { _ = recover() }()
	listener.OnEvolutionProgress(taskJSON)
}

func waitForListen(addr string, timeout time.Duration) error {
	deadline := time.Now().Add(timeout)
	for time.Now().Before(deadline) {
		conn, err := net.DialTimeout("tcp", addr, 250*time.Millisecond)
		if err == nil {
			_ = conn.Close()
			return nil
		}
		time.Sleep(50 * time.Millisecond)
	}
	return fmt.Errorf("proxy started but is not listening on %s", addr)
}

// JSON payloads (camelCase tags match the frozen contract).
type statusPayload struct {
	ProxyRunning  bool   `json:"proxyRunning"`
	TUNRunning    bool   `json:"tunRunning"`
	TUNSupported  bool   `json:"tunSupported"`
	TUNMessage    string `json:"tunMessage"`
	ListenAddr    string `json:"listenAddr"`
	ListenPort    int32  `json:"listenPort"`
	ProxyMode     string `json:"proxyMode"`
	IPv6Available bool   `json:"ipv6Available"`
	LastError     string `json:"lastError"`
}

type statsPayload struct {
	Down int64 `json:"down"`
	Up   int64 `json:"up"`
	Etc  int64 `json:"etc"`
}

type tunStatusPayload struct {
	Supported bool   `json:"supported"`
	Running   bool   `json:"running"`
	Enabled   bool   `json:"enabled"`
	Message   string `json:"message"`
}

type buildInfoPayload struct {
	Version     string `json:"version"`
	Channel     string `json:"channel"`
	CoreVersion string `json:"coreVersion"`
	GoVersion   string `json:"goVersion"`
	Platform    string `json:"platform"`
}
