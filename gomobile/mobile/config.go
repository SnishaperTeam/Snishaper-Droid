package mobile

import (
	"context"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"net"
	"strconv"
	"strings"
	"time"

	"github.com/SnishaperTeam/snishaper-core/cfpool"
	"github.com/SnishaperTeam/snishaper-core/config"
	"github.com/SnishaperTeam/snishaper-core/dohresolver"
	"github.com/SnishaperTeam/snishaper-core/proxy"
)

// GetSettingsJSON returns proxy.SettingsConfig as JSON. Android forces the
// CA-free invariants: auto routing mode "", migration disabled.
func (a *App) GetSettingsJSON() (out string) {
	defer recoverString(&out)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, _, err := a.engineLocked()
	if err != nil {
		return "{}"
	}
	settings := proxy.SettingsConfig{
		ListenPort:       rm.GetListenPort(),
		AutoRouting:      proxy.AutoRoutingConfig{Mode: ""},
		TUN:              rm.GetTUNConfig(),
		Fragment:         rm.GetFragmentConfig(),
		Language:         rm.GetLanguage(),
		Theme:            rm.GetTheme(),
		AppearanceMode:   rm.GetAppearanceMode(),
		CloudflareConfig: rm.GetCloudflareConfig(),
		MigrationEnabled: boolPtr(false),
		MigrationServer:  "",
	}
	return marshalJSON(settings)
}

// UpdateSettingsJSON applies user settings. Desktop-only fields (tray,
// auto-start, update channel, system proxy) are ignored; CA-free invariants are
// forced no matter what the caller sends.
func (a *App) UpdateSettingsJSON(jsonConfig string) (err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, srv, err := a.engineLocked()
	if err != nil {
		return err
	}
	var settings proxy.SettingsConfig
	if err := json.Unmarshal([]byte(jsonConfig), &settings); err != nil {
		return fmt.Errorf("parse settings: %w", err)
	}
	settings.AutoRouting = proxy.AutoRoutingConfig{Mode: ""}
	settings.MigrationEnabled = boolPtr(false)
	settings.MigrationServer = ""
	settings.UpdateChannel = ""
	settings.DownloadSource = ""
	settings.CustomDownloadSource = ""
	// Desktop-only booleans are deliberately dropped.

	if strings.TrimSpace(settings.ListenPort) != "" {
		if !isNumericPort(settings.ListenPort) {
			return fmt.Errorf("invalid listen port %q", settings.ListenPort)
		}
		rm.SetListenPort(strings.TrimSpace(settings.ListenPort))
	}
	if err := rm.UpdateAutoRoutingConfig(settings.AutoRouting); err != nil {
		return err
	}
	if err := rm.SetMigrationEnabled(false); err != nil {
		return err
	}
	if err := rm.UpdateTUNConfig(settings.TUN); err != nil {
		return err
	}
	if err := rm.SetFragmentConfig(settings.Fragment); err != nil {
		return err
	}
	if settings.Language != "" {
		if err := rm.SetLanguage(settings.Language); err != nil {
			return err
		}
	}
	if settings.Theme != "" {
		if err := rm.SetTheme(settings.Theme); err != nil {
			return err
		}
	}
	if settings.AppearanceMode != "" {
		if err := rm.SetAppearanceMode(settings.AppearanceMode); err != nil {
			return err
		}
	}
	if err := rm.UpdateCloudflareConfig(settings.CloudflareConfig); err != nil {
		return err
	}
	if err := rm.SaveConfig(); err != nil {
		return err
	}
	if srv != nil && !srv.IsRunning() {
		_ = srv.SetListenAddr("127.0.0.1:" + strings.TrimSpace(rm.GetListenPort()))
	}

	// A changed port requires a proxy restart.
	if srv.IsRunning() {
		if _, port, splitErr := net.SplitHostPort(srv.GetListenAddr()); splitErr != nil || port != strings.TrimSpace(rm.GetListenPort()) {
			if err := a.stopProxyLocked(); err != nil {
				return err
			}
			if err := a.startProxyLocked(); err != nil {
				return err
			}
		}
	}
	a.afterConfigChangedLocked()
	return nil
}

// GetSiteGroupsJSON returns the site groups as a sanitized JSON array.
func (a *App) GetSiteGroupsJSON() (out string) {
	defer recoverString(&out)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, _, err := a.engineLocked()
	if err != nil {
		return "[]"
	}
	return marshalJSON(sanitizeSiteGroupsForOutput(rm.GetSiteGroups()))
}

// GetSiteGroupJSON returns one site group as a sanitized JSON object.
func (a *App) GetSiteGroupJSON(id string) (out string) {
	defer recoverString(&out)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, _, err := a.engineLocked()
	if err != nil {
		return "{}"
	}
	target := strings.TrimSpace(id)
	for _, group := range rm.GetSiteGroups() {
		if group.ID == target {
			return marshalJSON(sanitizeSiteGroupForOutput(group))
		}
	}
	return "{}"
}

// AddSiteGroupJSON validates, sanitizes and adds a site group, returning its ID.
func (a *App) AddSiteGroupJSON(jsonConfig string) (id string, err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, _, err := a.engineLocked()
	if err != nil {
		return "", err
	}
	var group proxy.SiteGroup
	if err := json.Unmarshal([]byte(jsonConfig), &group); err != nil {
		return "", fmt.Errorf("parse site group: %w", err)
	}
	group.ID = ""
	if err := sanitizeSiteGroupForWrite(&group); err != nil {
		return "", err
	}
	before := rm.GetSiteGroups()
	waitForUniqueGeneratedID(siteGroupIDs(before))
	if err := rm.AddSiteGroup(group); err != nil {
		return "", err
	}
	newID := findNewID(before, rm.GetSiteGroups(), func(item proxy.SiteGroup) string { return item.ID })
	a.afterConfigChangedLocked()
	return newID, nil
}

// UpdateSiteGroupJSON validates, sanitizes and updates a site group.
func (a *App) UpdateSiteGroupJSON(jsonConfig string) (err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, _, err := a.engineLocked()
	if err != nil {
		return err
	}
	var group proxy.SiteGroup
	if err := json.Unmarshal([]byte(jsonConfig), &group); err != nil {
		return fmt.Errorf("parse site group: %w", err)
	}
	if strings.TrimSpace(group.ID) == "" {
		return fmt.Errorf("site group id is required")
	}
	if err := sanitizeSiteGroupForWrite(&group); err != nil {
		return err
	}
	if err := rm.UpdateSiteGroup(group); err != nil {
		return err
	}
	a.afterConfigChangedLocked()
	return nil
}

// DeleteSiteGroup removes a site group by ID.
func (a *App) DeleteSiteGroup(id string) (err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, _, err := a.engineLocked()
	if err != nil {
		return err
	}
	if err := rm.DeleteSiteGroup(strings.TrimSpace(id)); err != nil {
		return err
	}
	a.afterConfigChangedLocked()
	return nil
}

// GetUpstreamsJSON returns all upstreams as JSON.
func (a *App) GetUpstreamsJSON() (out string) {
	defer recoverString(&out)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, _, err := a.engineLocked()
	if err != nil {
		return "[]"
	}
	return marshalJSON(nonNil(rm.GetUpstreams()))
}

// AddUpstreamJSON adds an upstream and returns its ID.
func (a *App) AddUpstreamJSON(jsonConfig string) (id string, err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, _, err := a.engineLocked()
	if err != nil {
		return "", err
	}
	var upstream proxy.Upstream
	if err := json.Unmarshal([]byte(jsonConfig), &upstream); err != nil {
		return "", fmt.Errorf("parse upstream: %w", err)
	}
	upstream.ID = ""
	if strings.TrimSpace(upstream.Name) == "" {
		return "", fmt.Errorf("upstream name is required")
	}
	before := rm.GetUpstreams()
	waitForUniqueGeneratedID(upstreamIDs(before))
	if err := rm.AddUpstream(upstream); err != nil {
		return "", err
	}
	newID := findNewID(before, rm.GetUpstreams(), func(item proxy.Upstream) string { return item.ID })
	a.afterConfigChangedLocked()
	return newID, nil
}

// UpdateUpstreamJSON updates an upstream by ID.
func (a *App) UpdateUpstreamJSON(jsonConfig string) (err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, _, err := a.engineLocked()
	if err != nil {
		return err
	}
	var upstream proxy.Upstream
	if err := json.Unmarshal([]byte(jsonConfig), &upstream); err != nil {
		return fmt.Errorf("parse upstream: %w", err)
	}
	if strings.TrimSpace(upstream.ID) == "" {
		return fmt.Errorf("upstream id is required")
	}
	if err := rm.UpdateUpstream(upstream); err != nil {
		return err
	}
	a.afterConfigChangedLocked()
	return nil
}

// DeleteUpstream removes an upstream by ID.
func (a *App) DeleteUpstream(id string) (err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, _, err := a.engineLocked()
	if err != nil {
		return err
	}
	if err := rm.DeleteUpstream(strings.TrimSpace(id)); err != nil {
		return err
	}
	a.afterConfigChangedLocked()
	return nil
}

// GetDNSNodesJSON returns all DoH nodes as JSON.
func (a *App) GetDNSNodesJSON() (out string) {
	defer recoverString(&out)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, _, err := a.engineLocked()
	if err != nil {
		return "[]"
	}
	return marshalJSON(nonNil(rm.GetDNSNodes()))
}

// AddDNSNodeJSON adds a DoH node and returns its ID.
func (a *App) AddDNSNodeJSON(jsonConfig string) (id string, err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, _, err := a.engineLocked()
	if err != nil {
		return "", err
	}
	var node proxy.DNSNode
	if err := json.Unmarshal([]byte(jsonConfig), &node); err != nil {
		return "", fmt.Errorf("parse dns node: %w", err)
	}
	node.ID = ""
	if strings.TrimSpace(node.URL) == "" {
		return "", fmt.Errorf("dns node url is required")
	}
	before := rm.GetDNSNodes()
	waitForUniqueGeneratedID(dnsNodeIDs(before))
	if err := rm.AddDNSNode(node); err != nil {
		return "", err
	}
	newID := findNewID(before, rm.GetDNSNodes(), func(item proxy.DNSNode) string { return item.ID })
	a.afterConfigChangedLocked()
	return newID, nil
}

// UpdateDNSNodeJSON updates a DoH node by ID.
func (a *App) UpdateDNSNodeJSON(jsonConfig string) (err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, _, err := a.engineLocked()
	if err != nil {
		return err
	}
	var node proxy.DNSNode
	if err := json.Unmarshal([]byte(jsonConfig), &node); err != nil {
		return fmt.Errorf("parse dns node: %w", err)
	}
	if strings.TrimSpace(node.ID) == "" {
		return fmt.Errorf("dns node id is required")
	}
	if err := rm.UpdateDNSNode(node); err != nil {
		return err
	}
	a.afterConfigChangedLocked()
	return nil
}

// DeleteDNSNode removes a DoH node by ID.
func (a *App) DeleteDNSNode(id string) (err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, _, err := a.engineLocked()
	if err != nil {
		return err
	}
	if err := rm.DeleteDNSNode(strings.TrimSpace(id)); err != nil {
		return err
	}
	a.afterConfigChangedLocked()
	return nil
}

// SetDNSNodePriority moves a DoH node to the given 0-based position.
func (a *App) SetDNSNodePriority(id string, index int32) (err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, _, err := a.engineLocked()
	if err != nil {
		return err
	}
	if err := rm.SetDNSNodePriority(strings.TrimSpace(id), int(index)); err != nil {
		return err
	}
	a.afterConfigChangedLocked()
	return nil
}

// TestDNSNode queries one DoH node and returns {success,latencyMs,ips,error}.
func (a *App) TestDNSNode(id string) (out string, err error) {
	defer func() {
		if r := recover(); r != nil {
			out, err = "", fmt.Errorf("snishaper: panic: %v", r)
		}
	}()
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, srv, err := a.engineLocked()
	if err != nil {
		return "", err
	}
	target := strings.TrimSpace(id)
	var node *proxy.DNSNode
	for _, candidate := range rm.GetDNSNodes() {
		if candidate.ID == target {
			copy := candidate
			node = &copy
			break
		}
	}
	if node == nil {
		return "", fmt.Errorf("dns node %s not found", id)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	start := time.Now()
	ips, testErr := srv.GetDoHResolver().TestNode(ctx, dohresolver.DNSNode{
		Name:          node.Name,
		URL:           node.URL,
		SNI:           node.SNI,
		IPs:           node.IPs,
		ECHEnabled:    node.ECHEnabled,
		ECHProfileID:  node.ECHProfileID,
		ECHAutoUpdate: node.ECHAutoUpdate,
		QUIC:          node.QUIC,
		Enabled:       node.Enabled,
		CertVerify:    node.CertVerify,
	})
	payload := map[string]any{
		"success":   testErr == nil && len(ips) > 0,
		"latencyMs": time.Since(start).Milliseconds(),
		"ips":       nonNil(ips),
		"error":     errText(testErr),
	}
	return marshalJSON(payload), nil
}

// GetECHProfilesJSON returns the ECH profiles (DoH ECH; not CA related).
func (a *App) GetECHProfilesJSON() (out string) {
	defer recoverString(&out)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, _, err := a.engineLocked()
	if err != nil {
		return "[]"
	}
	return marshalJSON(nonNil(rm.GetECHProfiles()))
}

// UpsertECHProfileJSON adds or updates an ECH profile.
func (a *App) UpsertECHProfileJSON(jsonConfig string) (err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, _, err := a.engineLocked()
	if err != nil {
		return err
	}
	var profile proxy.ECHProfile
	if err := json.Unmarshal([]byte(jsonConfig), &profile); err != nil {
		return fmt.Errorf("parse ech profile: %w", err)
	}
	if strings.TrimSpace(profile.ID) == "" {
		waitForUniqueGeneratedID(idsOf(rm.GetECHProfiles(), func(item proxy.ECHProfile) string { return item.ID }))
	}
	if err := rm.UpsertECHProfile(profile); err != nil {
		return err
	}
	a.afterConfigChangedLocked()
	return nil
}

// DeleteECHProfile removes an ECH profile by ID.
func (a *App) DeleteECHProfile(id string) (err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, _, err := a.engineLocked()
	if err != nil {
		return err
	}
	if err := rm.DeleteECHProfile(strings.TrimSpace(id)); err != nil {
		return err
	}
	a.afterConfigChangedLocked()
	return nil
}

// FetchECHConfigJSON resolves a fresh ECH config list for a domain via DoH.
// The returned JSON is {"config":"<base64>","length":N}.
func (a *App) FetchECHConfigJSON(domain string, dohURL string) (out string, err error) {
	defer func() {
		if r := recover(); r != nil {
			out, err = "", fmt.Errorf("snishaper: panic: %v", r)
		}
	}()
	a.opMu.Lock()
	defer a.opMu.Unlock()
	_, srv, err := a.engineLocked()
	if err != nil {
		return "", err
	}
	domain = strings.TrimSpace(domain)
	if domain == "" {
		return "", fmt.Errorf("domain is required")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	configBytes, err := srv.FetchECH(ctx, domain, strings.TrimSpace(dohURL))
	if err != nil {
		return "", fmt.Errorf("fetch ech config: %w", err)
	}
	return marshalJSON(map[string]any{
		"config": base64.StdEncoding.EncodeToString(configBytes),
		"length": len(configBytes),
	}), nil
}

// GetNAT64ProfilesJSON returns all NAT64 profiles as JSON.
func (a *App) GetNAT64ProfilesJSON() (out string) {
	defer recoverString(&out)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, _, err := a.engineLocked()
	if err != nil {
		return "[]"
	}
	return marshalJSON(nonNil(rm.GetNAT64Profiles()))
}

// AddNAT64ProfileJSON adds a NAT64 profile and returns its ID.
func (a *App) AddNAT64ProfileJSON(jsonConfig string) (id string, err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, _, err := a.engineLocked()
	if err != nil {
		return "", err
	}
	var profile proxy.NAT64Profile
	if err := json.Unmarshal([]byte(jsonConfig), &profile); err != nil {
		return "", fmt.Errorf("parse nat64 profile: %w", err)
	}
	profile.ID = ""
	if strings.TrimSpace(profile.Prefix) == "" {
		return "", fmt.Errorf("nat64 prefix is required")
	}
	before := rm.GetNAT64Profiles()
	waitForUniqueGeneratedID(nat64ProfileIDs(before))
	if err := rm.AddNAT64Profile(profile); err != nil {
		return "", err
	}
	newID := findNewID(before, rm.GetNAT64Profiles(), func(item proxy.NAT64Profile) string { return item.ID })
	a.afterConfigChangedLocked()
	return newID, nil
}

// UpdateNAT64ProfileJSON updates a NAT64 profile by ID.
func (a *App) UpdateNAT64ProfileJSON(jsonConfig string) (err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, _, err := a.engineLocked()
	if err != nil {
		return err
	}
	var profile proxy.NAT64Profile
	if err := json.Unmarshal([]byte(jsonConfig), &profile); err != nil {
		return fmt.Errorf("parse nat64 profile: %w", err)
	}
	if strings.TrimSpace(profile.ID) == "" {
		return fmt.Errorf("nat64 profile id is required")
	}
	if err := rm.UpdateNAT64Profile(profile); err != nil {
		return err
	}
	a.afterConfigChangedLocked()
	return nil
}

// DeleteNAT64Profile removes a NAT64 profile by ID.
func (a *App) DeleteNAT64Profile(id string) (err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, _, err := a.engineLocked()
	if err != nil {
		return err
	}
	if err := rm.DeleteNAT64Profile(strings.TrimSpace(id)); err != nil {
		return err
	}
	a.afterConfigChangedLocked()
	return nil
}

// TestNAT64Profile maps a probe IPv4 address into the prefix and dials it.
func (a *App) TestNAT64Profile(prefix string) (out string, err error) {
	defer func() {
		if r := recover(); r != nil {
			out, err = "", fmt.Errorf("snishaper: panic: %v", r)
		}
	}()
	prefix = strings.TrimSpace(prefix)
	if prefix == "" {
		return "", fmt.Errorf("nat64 prefix is required")
	}
	mapped, ok := config.MapNAT64Addr("1.1.1.1", prefix)
	if !ok || mapped == "" {
		return "", fmt.Errorf("invalid nat64 prefix %q", prefix)
	}
	start := time.Now()
	conn, dialErr := net.DialTimeout("tcp", net.JoinHostPort(mapped, "443"), 3*time.Second)
	latency := time.Since(start).Milliseconds()
	if conn != nil {
		_ = conn.Close()
	}
	return marshalJSON(map[string]any{
		"success":   dialErr == nil,
		"latencyMs": latency,
		"mapped":    mapped,
		"error":     errText(dialErr),
	}), nil
}

// GetFragmentConfigJSON returns the TLS fragment config.
func (a *App) GetFragmentConfigJSON() (out string) {
	defer recoverString(&out)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, _, err := a.engineLocked()
	if err != nil {
		return "{}"
	}
	return marshalJSON(rm.GetFragmentConfig())
}

// SetFragmentConfigJSON updates the TLS fragment config.
func (a *App) SetFragmentConfigJSON(jsonConfig string) (err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, _, err := a.engineLocked()
	if err != nil {
		return err
	}
	var cfg proxy.FragmentConfig
	if err := json.Unmarshal([]byte(jsonConfig), &cfg); err != nil {
		return fmt.Errorf("parse fragment config: %w", err)
	}
	if err := rm.SetFragmentConfig(cfg); err != nil {
		return err
	}
	a.pushState()
	return nil
}

// ResetFragmentConfig restores the defaults.
func (a *App) ResetFragmentConfig() (err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, _, err := a.engineLocked()
	if err != nil {
		return err
	}
	return rm.SetFragmentConfig(proxy.DefaultFragmentConfig())
}

// GetCloudflareConfigJSON returns the Cloudflare pool config.
func (a *App) GetCloudflareConfigJSON() (out string) {
	defer recoverString(&out)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, _, err := a.engineLocked()
	if err != nil {
		return "{}"
	}
	return marshalJSON(rm.GetCloudflareConfig())
}

// UpdateCloudflareConfigJSON updates the Cloudflare pool config and pool.
func (a *App) UpdateCloudflareConfigJSON(jsonConfig string) (err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, srv, err := a.engineLocked()
	if err != nil {
		return err
	}
	var cfg proxy.CloudflareConfig
	if err := json.Unmarshal([]byte(jsonConfig), &cfg); err != nil {
		return fmt.Errorf("parse cloudflare config: %w", err)
	}
	if err := rm.UpdateCloudflareConfig(cfg); err != nil {
		return err
	}
	srv.UpdateCloudflareIPPool(cfg.PreferredIPs)
	a.afterConfigChangedLocked()
	return nil
}

// GetCloudflareIPStatsJSON returns the pool IP statistics.
func (a *App) GetCloudflareIPStatsJSON() (out string) {
	defer recoverString(&out)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	_, srv, err := a.engineLocked()
	if err != nil {
		return "[]"
	}
	return marshalJSON(nonNil(srv.GetAllCFIPsWithStats()))
}

// ForceFetchCloudflareIPs pulls a fresh IP list and updates the pool.
func (a *App) ForceFetchCloudflareIPs() (out string, err error) {
	defer func() {
		if r := recover(); r != nil {
			out, err = "", fmt.Errorf("snishaper: panic: %v", r)
		}
	}()
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, srv, err := a.engineLocked()
	if err != nil {
		return "", err
	}
	cfg := rm.GetCloudflareConfig()
	ips, fetchErr := cfpool.FetchCloudflareIPs(cfg.APIKey)
	if fetchErr != nil {
		return "", fmt.Errorf("fetch cloudflare ips: %w", fetchErr)
	}
	if len(ips) > 0 {
		cfg.PreferredIPs = ips
		if err := rm.UpdateCloudflareConfig(cfg); err != nil {
			return "", err
		}
		srv.UpdateCloudflareIPPool(ips)
		srv.SetCFPoolFetchTime(time.Now())
	}
	return marshalJSON(map[string]any{
		"count": len(ips),
		"ips":   nonNil(ips),
	}), nil
}

// TriggerCFHealthCheck starts an immediate health check.
func (a *App) TriggerCFHealthCheck() (err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	_, srv, err := a.engineLocked()
	if err != nil {
		return err
	}
	srv.TriggerCFHealthCheck()
	return nil
}

// RemoveInvalidCFIPs drops failed pool IPs and returns the removed count.
func (a *App) RemoveInvalidCFIPs() (removed int32, err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	_, srv, err := a.engineLocked()
	if err != nil {
		return 0, err
	}
	return int32(srv.RemoveInvalidCFIPs()), nil
}

// ExportConfigJSON exports rules/settings as a CA-free proxy.RulesConfig JSON.
func (a *App) ExportConfigJSON() (out string, err error) {
	defer func() {
		if r := recover(); r != nil {
			out, err = "", fmt.Errorf("snishaper: panic: %v", r)
		}
	}()
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, _, err := a.engineLocked()
	if err != nil {
		return "", err
	}
	rulesConfig := proxy.RulesConfig{
		SiteGroups:    sanitizeSiteGroupsForOutput(rm.GetSiteGroups()),
		Upstreams:     nonNil(rm.GetUpstreams()),
		DNSNodes:      nonNil(rm.GetDNSNodes()),
		ECHProfiles:   nonNil(rm.GetECHProfiles()),
		NAT64Profiles: nonNil(rm.GetNAT64Profiles()),
	}
	return marshalJSONIndent(rulesConfig)
}

// ImportConfigWithSummaryJSON imports a desktop rules/config.json (RulesConfig
// format) or a desktop exported config. Everything is sanitized first; the
// returned JSON is ImportSummary plus {"rewritten","format"}.
func (a *App) ImportConfigWithSummaryJSON(content string) (out string, err error) {
	defer func() {
		if r := recover(); r != nil {
			out, err = "", fmt.Errorf("snishaper: panic: %v", r)
		}
	}()
	trimmed := strings.TrimSpace(content)
	if trimmed == "" {
		return "", fmt.Errorf("config content is empty")
	}
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, _, err := a.engineLocked()
	if err != nil {
		return "", err
	}

	// 1) RulesConfig format (desktop rules/config.json, also our export format).
	var probe map[string]json.RawMessage
	if err := json.Unmarshal([]byte(trimmed), &probe); err != nil {
		return "", fmt.Errorf("parse config: %w", err)
	}
	if _, ok := probe["site_groups"]; ok {
		var remote proxy.RulesConfig
		if err := json.Unmarshal([]byte(trimmed), &remote); err != nil {
			return "", fmt.Errorf("parse rules config: %w", err)
		}
		rewritten := sanitizeRulesConfig(&remote)
		summary, mergeErr := rm.MergeRemoteRules(remote, contentHash(trimmed))
		if mergeErr != nil {
			return "", mergeErr
		}
		extra, enforceErr := a.enforceRulesLocked(rm)
		if enforceErr != nil {
			return "", enforceErr
		}
		result := importSummaryPayload{
			Total:       len(remote.SiteGroups) + len(remote.Upstreams) + len(remote.DNSNodes) + len(remote.ECHProfiles) + len(remote.NAT64Profiles),
			Added:       summary.AddedGroups + summary.AddedUpstreams + summary.AddedDNSNodes + summary.AddedECHProfiles + summary.AddedNAT64,
			Overwritten: summary.UpdatedGroups + summary.UpdatedUpstreams,
			Skipped:     summary.KeptGroups + summary.KeptUpstreams,
			Rewritten:   rewritten + extra,
			Format:      "rules",
		}
		a.afterConfigChangedLocked()
		return marshalJSON(result), nil
	}

	// 2) Desktop export format ({"mitm":..., "transparent":...} or a single
	//    config file with "rules").
	sanitized, rewritten, sanitizeErr := sanitizeImportExportJSON(trimmed)
	if sanitizeErr != nil {
		return "", fmt.Errorf("sanitize config: %w", sanitizeErr)
	}
	summary, importErr := rm.ImportConfigWithSummary(sanitized)
	if importErr != nil {
		return "", importErr
	}
	extra, enforceErr := a.enforceRulesLocked(rm)
	if enforceErr != nil {
		return "", enforceErr
	}
	result := importSummaryPayload{
		Total:       summary.Total,
		Added:       summary.Added,
		Overwritten: summary.Overwritten,
		Skipped:     summary.Skipped,
		Rewritten:   rewritten + extra,
		Format:      "export",
	}
	a.afterConfigChangedLocked()
	return marshalJSON(result), nil
}

// GetTUNConfigJSON returns the persisted TUN config.
func (a *App) GetTUNConfigJSON() (out string) {
	defer recoverString(&out)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, _, err := a.engineLocked()
	if err != nil {
		return "{}"
	}
	return marshalJSON(rm.GetTUNConfig())
}

// UpdateTUNConfigJSON updates the persisted TUN config. Android ignores the
// desktop interface selection fields; MTU / stack are honoured by StartTUN.
func (a *App) UpdateTUNConfigJSON(jsonConfig string) (err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, _, err := a.engineLocked()
	if err != nil {
		return err
	}
	var cfg proxy.TUNConfig
	if err := json.Unmarshal([]byte(jsonConfig), &cfg); err != nil {
		return fmt.Errorf("parse tun config: %w", err)
	}
	if err := rm.UpdateTUNConfig(cfg); err != nil {
		return err
	}
	a.pushState()
	return nil
}

// GetAutoRoutingConfigJSON always reports the disabled preset on Android.
func (a *App) GetAutoRoutingConfigJSON() (out string) {
	defer recoverString(&out)
	return marshalJSON(proxy.AutoRoutingConfig{Mode: ""})
}

// UpdateAutoRoutingConfigJSON rejects every non-empty mode: the desktop preset
// synthesizes MITM rules for Cloudflare domains, which needs a local CA.
func (a *App) UpdateAutoRoutingConfigJSON(jsonConfig string) (err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, _, err := a.engineLocked()
	if err != nil {
		return err
	}
	var cfg proxy.AutoRoutingConfig
	if err := json.Unmarshal([]byte(jsonConfig), &cfg); err != nil {
		return fmt.Errorf("parse auto routing config: %w", err)
	}
	if strings.TrimSpace(string(cfg.Mode)) != "" {
		return fmt.Errorf("smart routing is not available on Android: its Cloudflare branch requires MITM with a local CA")
	}
	return rm.UpdateAutoRoutingConfig(proxy.AutoRoutingConfig{Mode: ""})
}

// GetAutoRoutingStatusJSON returns the read-only GFWList status.
func (a *App) GetAutoRoutingStatusJSON() (out string) {
	defer recoverString(&out)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, _, err := a.engineLocked()
	if err != nil {
		return "{}"
	}
	return marshalJSON(rm.GetAutoRoutingStatus())
}

// --- helpers ---------------------------------------------------------------

func (a *App) engineLocked() (*proxy.RuleManager, *proxy.ProxyServer, error) {
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.rules == nil || a.server == nil {
		return nil, nil, fmt.Errorf("engine is not started")
	}
	return a.rules, a.server, nil
}

func (a *App) afterConfigChangedLocked() {
	a.mu.Lock()
	rm := a.rules
	srv := a.server
	a.mu.Unlock()
	if rm != nil && srv != nil {
		srv.UpdateCloudflareIPPool(rm.GetCloudflareConfig().PreferredIPs)
	}
	a.pushState()
}

// waitForUniqueGeneratedID blocks briefly until the wall clock is past every
// numeric ID the core generated so far. snishaper-core generates IDs with
// time.Now().UnixNano() (proxy.generateID); on platforms with a coarse clock
// (observed on Windows) two consecutive adds can receive the same ID, which
// would make the IDs ambiguous for update/delete. Waiting for the next clock
// tick guarantees a strictly greater, therefore unique, generated ID.
func waitForUniqueGeneratedID(existingIDs []string) {
	var maxID int64
	for _, raw := range existingIDs {
		value, err := strconv.ParseInt(strings.TrimSpace(raw), 10, 64)
		if err == nil && value > maxID {
			maxID = value
		}
	}
	if maxID == 0 {
		return
	}
	deadline := time.Now().Add(50 * time.Millisecond)
	for time.Now().UnixNano() <= maxID && time.Now().Before(deadline) {
		time.Sleep(50 * time.Microsecond)
	}
}

func idsOf[T any](items []T, idOf func(T) string) []string {
	out := make([]string, 0, len(items))
	for _, item := range items {
		out = append(out, idOf(item))
	}
	return out
}

func siteGroupIDs(items []proxy.SiteGroup) []string {
	return idsOf(items, func(item proxy.SiteGroup) string { return item.ID })
}

func upstreamIDs(items []proxy.Upstream) []string {
	return idsOf(items, func(item proxy.Upstream) string { return item.ID })
}

func dnsNodeIDs(items []proxy.DNSNode) []string {
	return idsOf(items, func(item proxy.DNSNode) string { return item.ID })
}

func nat64ProfileIDs(items []proxy.NAT64Profile) []string {
	return idsOf(items, func(item proxy.NAT64Profile) string { return item.ID })
}

func findNewID[T any](before []T, after []T, idOf func(T) string) string {
	seen := make(map[string]struct{}, len(before))
	for _, item := range before {
		seen[idOf(item)] = struct{}{}
	}
	for _, item := range after {
		if _, ok := seen[idOf(item)]; !ok {
			return idOf(item)
		}
	}
	return ""
}

func nonNil[T any](items []T) []T {
	if items == nil {
		return []T{}
	}
	return items
}

func errText(err error) string {
	if err == nil {
		return ""
	}
	return err.Error()
}

func contentHash(content string) string {
	sum := sha256.Sum256([]byte(content))
	return hex.EncodeToString(sum[:])
}

type importSummaryPayload struct {
	Total       int    `json:"total"`
	Added       int    `json:"added"`
	Overwritten int    `json:"overwritten"`
	Skipped     int    `json:"skipped"`
	Rewritten   int    `json:"rewritten"`
	Format      string `json:"format"`
}
