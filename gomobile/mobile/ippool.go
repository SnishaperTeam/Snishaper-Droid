package mobile

import (
	"encoding/json"
	"fmt"
	"time"

	"github.com/SnishaperTeam/snishaper-core/cfpool"
	"github.com/SnishaperTeam/snishaper-core/proxy"
)

// GetIPPoolsJSON returns every named pool except the legacy Cloudflare pool,
// which keeps its own Get/UpdateCloudflareConfig* surface.
func (a *App) GetIPPoolsJSON() (out string) {
	defer recoverString(&out)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, _, err := a.engineLocked()
	if err != nil {
		return "[]"
	}
	pools := rm.GetIPPoolsConfig()
	if pools == nil {
		pools = []proxy.IPPoolConfig{}
	}
	return marshalJSON(pools)
}

// UpdateIPPoolsJSON overwrites the named-pool list and applies the diff to the
// running server: removed pools are dropped, added or changed ones get their
// IPs applied, and newly enabled AutoUpdate pools are fetched in background.
func (a *App) UpdateIPPoolsJSON(jsonConfig string) (err error) {
	defer recoverError(&err)
	var pools []proxy.IPPoolConfig
	if err := json.Unmarshal([]byte(jsonConfig), &pools); err != nil {
		return fmt.Errorf("parse ip pools: %w", err)
	}
	a.opMu.Lock()
	rm, srv, err := a.engineLocked()
	if err != nil {
		a.opMu.Unlock()
		return err
	}
	previous := rm.GetIPPoolsConfig()
	previousAuto := make(map[string]bool, len(previous))
	for _, pool := range previous {
		previousAuto[pool.Name] = pool.AutoUpdate
	}
	if err := rm.UpdateIPPoolsConfig(pools); err != nil {
		a.opMu.Unlock()
		return err
	}
	current := rm.GetIPPoolsConfig()
	kept := make(map[string]bool, len(current))
	for _, pool := range current {
		kept[pool.Name] = true
	}
	for _, pool := range previous {
		if !kept[pool.Name] {
			srv.RemoveIPPool(pool.Name)
		}
	}
	var toFetch []string
	for _, pool := range current {
		srv.UpdateIPPool(pool.Name, pool.PreferredIPs)
		if pool.AutoUpdate && !previousAuto[pool.Name] {
			toFetch = append(toFetch, pool.Name)
		}
	}
	a.opMu.Unlock()

	for _, name := range toFetch {
		go func(pool string) {
			if _, fetchErr := a.fetchIPPool(pool, false); fetchErr != nil {
				a.logf("[ippool] auto update %s failed: %s", pool, fetchErr.Error())
			}
		}(name)
	}
	a.pushState()
	return nil
}

// GetIPPoolStatsJSON returns the health statistics of one named pool.
func (a *App) GetIPPoolStatsJSON(name string) (out string) {
	defer recoverString(&out)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	_, srv, err := a.engineLocked()
	if err != nil {
		return "[]"
	}
	stats := srv.GetIPPoolStats(name)
	if stats == nil {
		stats = []*cfpool.IPStats{}
	}
	return marshalJSON(stats)
}

// ForceFetchIPPoolIPsJSON pulls fresh IPs for one pool from its CDN source.
func (a *App) ForceFetchIPPoolIPsJSON(name string) (out string, err error) {
	defer recoverError(&err)
	return a.fetchIPPool(name, true)
}

// RefreshIPPoolJSON fetches fresh IPs when the pool is stale and AutoUpdate is
// enabled; a disabled or missing pool is a no-op.
func (a *App) RefreshIPPoolJSON(name string) (err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	rm, _, err := a.engineLocked()
	if err != nil {
		a.opMu.Unlock()
		return err
	}
	auto := false
	for _, pool := range rm.GetIPPoolsConfig() {
		if pool.Name == name {
			auto = pool.AutoUpdate
			break
		}
	}
	a.opMu.Unlock()
	if !auto {
		return nil
	}
	_, err = a.fetchIPPool(name, false)
	return err
}

// RemoveInvalidIPPoolIPsJSON drops failed IPs and returns how many were removed.
func (a *App) RemoveInvalidIPPoolIPsJSON(name string) (removed int32, err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	_, srv, err := a.engineLocked()
	if err != nil {
		return 0, err
	}
	return int32(srv.RemoveInvalidIPPoolIPs(name)), nil
}

// TriggerIPPoolHealthCheckJSON starts an immediate health-check pass.
func (a *App) TriggerIPPoolHealthCheckJSON(name string) (err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	_, srv, err := a.engineLocked()
	if err != nil {
		return err
	}
	srv.TriggerIPPoolHealthCheck(name)
	return nil
}

func (a *App) fetchIPPool(name string, force bool) (out string, err error) {
	defer recoverError(&err)
	if name == "" {
		return "", fmt.Errorf("ip pool name is empty")
	}

	a.opMu.Lock()
	rm, srv, err := a.engineLocked()
	if err != nil {
		a.opMu.Unlock()
		return "", err
	}
	var cfg *proxy.IPPoolConfig
	for _, pool := range rm.GetIPPoolsConfig() {
		if pool.Name == name {
			value := pool
			cfg = &value
			break
		}
	}
	a.opMu.Unlock()
	if cfg == nil {
		return "", fmt.Errorf("ip pool %q not found", name)
	}
	if !force {
		if pool := srv.GetIPPool(name); pool != nil && pool.HasIPs() && !pool.IsStale(24*time.Hour) {
			return marshalJSON(map[string]any{"count": len(pool.GetAllIPs()), "skipped": true}), nil
		}
	}

	ips, fetchErr := cfpool.FetchCDNIPs(name, cfg.APIKey)
	if fetchErr != nil {
		return "", fmt.Errorf("fetch ip pool %s: %w", name, fetchErr)
	}
	if len(ips) == 0 {
		return "", fmt.Errorf("ip pool %s: source returned no IPs", name)
	}

	a.opMu.Lock()
	srv.UpdateIPPool(name, ips)
	srv.SetIPPoolFetchTime(name, time.Now())
	cfg.PreferredIPs = ips
	saveErr := a.saveIPPoolLocked(rm, *cfg)
	a.opMu.Unlock()
	if saveErr != nil {
		return "", saveErr
	}
	a.logf("[ippool] %s: fetched %d preferred IPs", name, len(ips))
	return marshalJSON(map[string]any{"count": len(ips), "ips": ips}), nil
}

func (a *App) saveIPPoolLocked(rm *proxy.RuleManager, cfg proxy.IPPoolConfig) error {
	pools := rm.GetIPPoolsConfig()
	replaced := false
	for i, pool := range pools {
		if pool.Name == cfg.Name {
			pools[i] = cfg
			replaced = true
			break
		}
	}
	if !replaced {
		pools = append(pools, cfg)
	}
	return rm.UpdateIPPoolsConfig(pools)
}

func (a *App) applyIPPoolsLocked(rm *proxy.RuleManager, srv *proxy.ProxyServer) {
	srv.UpdateCloudflareIPPool(rm.GetCloudflareConfig().PreferredIPs)
	for _, pool := range rm.GetIPPoolsConfig() {
		if len(pool.PreferredIPs) > 0 {
			srv.UpdateIPPool(pool.Name, pool.PreferredIPs)
		}
	}
}
