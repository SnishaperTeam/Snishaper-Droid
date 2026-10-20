package mobile

import (
	"encoding/json"
	"fmt"
	"strings"

	"github.com/SnishaperTeam/snishaper-core/config"
	"github.com/SnishaperTeam/snishaper-core/proxy"
)

// CA-free rule policy for the Android port.
//
// Android runs without any local CA: the per-rule modes that require local TLS
// termination (mitm / quic) and the migration mode are not supported. Rules are
// restricted to tls-rf / transparent; "direct" survives only inside the shipped
// read-only built-in rules. Rule level CA fields (sni_fake / cert_verify /
// ECH settings) are cleared because they are only meaningful on the MITM path.
//
// This file is the single place that implements the policy. Every entry point
// that can introduce rules (startup load, import, CRUD) goes through it.

const (
	modeTLSRF       = "tls-rf"
	modeTransparent = "transparent"
	modeDirect      = "direct"
)

// normalizeAndroidMode maps any desktop rule mode to an Android supported mode.
// The second return value reports whether the input had to be rewritten.
func normalizeAndroidMode(raw string) (string, bool) {
	value := strings.ToLower(strings.TrimSpace(raw))
	switch value {
	case modeTLSRF, "tls_rf":
		return modeTLSRF, raw != modeTLSRF
	case modeTransparent:
		return modeTransparent, raw != modeTransparent
	case modeDirect:
		return modeDirect, raw != modeDirect
	case "":
		// Empty mode inherits the proxy runtime mode (transparent on desktop).
		// Make it explicit instead of leaving a mode-less rule behind.
		return modeTransparent, true
	default:
		// mitm / quic / migration / unknown desktop modes all need CA or are
		// removed on Android; tls-rf is the closest CA-free behaviour.
		return modeTLSRF, true
	}
}

// neutralizeSiteGroup rewrites one site group in place to the CA-free policy and
// reports whether anything changed.
func neutralizeSiteGroup(sg *proxy.SiteGroup) bool {
	changed := false
	if mode, modeChanged := normalizeAndroidMode(sg.Mode); modeChanged {
		sg.Mode = mode
		changed = true
	}
	if sg.SniFake != "" {
		sg.SniFake = ""
		changed = true
	}
	if !sg.CertVerify.IsZero() {
		sg.CertVerify = config.CertVerifyConfig{}
		changed = true
	}
	if sg.ECHEnabled {
		sg.ECHEnabled = false
		changed = true
	}
	if sg.ECHProfileID != "" {
		sg.ECHProfileID = ""
		changed = true
	}
	if sg.ECHDomain != "" {
		sg.ECHDomain = ""
		changed = true
	}
	if sg.ECHDiscoveryDomain != "" {
		sg.ECHDiscoveryDomain = ""
		changed = true
	}
	if sg.ECHDoHUpstream != "" {
		sg.ECHDoHUpstream = ""
		changed = true
	}
	return changed
}

// sanitizeSiteGroupForOutput returns a copy that is safe to hand to Kotlin.
func sanitizeSiteGroupForOutput(sg proxy.SiteGroup) proxy.SiteGroup {
	out := sg
	_ = neutralizeSiteGroup(&out)
	if out.Domains == nil {
		out.Domains = []string{}
	}
	return out
}

// sanitizeSiteGroupsForOutput sanitizes a slice for JSON output.
func sanitizeSiteGroupsForOutput(groups []proxy.SiteGroup) []proxy.SiteGroup {
	out := make([]proxy.SiteGroup, 0, len(groups))
	for _, sg := range groups {
		out = append(out, sanitizeSiteGroupForOutput(sg))
	}
	return out
}

// sanitizeSiteGroupForWrite validates a user supplied rule and neutralizes it.
// Only tls-rf / transparent may be written from Android.
func sanitizeSiteGroupForWrite(sg *proxy.SiteGroup) error {
	if sg == nil {
		return fmt.Errorf("site group is nil")
	}
	mode := strings.ToLower(strings.TrimSpace(sg.Mode))
	switch mode {
	case modeTLSRF, "tls_rf":
		sg.Mode = modeTLSRF
	case modeTransparent:
		sg.Mode = modeTransparent
	default:
		return fmt.Errorf("unsupported rule mode %q on Android: only \"tls-rf\" and \"transparent\" are allowed (mitm/quic/migration require a local CA and are not implemented)", sg.Mode)
	}

	domains := make([]string, 0, len(sg.Domains))
	seen := make(map[string]struct{}, len(sg.Domains))
	for _, domain := range sg.Domains {
		domain = strings.TrimSpace(domain)
		if domain == "" {
			continue
		}
		if _, ok := seen[domain]; ok {
			continue
		}
		seen[domain] = struct{}{}
		domains = append(domains, domain)
	}
	if len(domains) == 0 {
		return fmt.Errorf("site group must contain at least one domain")
	}
	sg.Domains = domains

	// Fields below only have an effect when the proxy terminates TLS locally.
	sg.SniFake = ""
	sg.CertVerify = config.CertVerifyConfig{}
	sg.ECHEnabled = false
	sg.ECHProfileID = ""
	sg.ECHDomain = ""
	sg.ECHDiscoveryDomain = ""
	sg.ECHDoHUpstream = ""
	return nil
}

// sanitizeRulesConfig rewrites a full rules snapshot in place. It returns the
// number of site groups that were modified.
func sanitizeRulesConfig(rc *proxy.RulesConfig) int {
	if rc == nil {
		return 0
	}
	rewritten := 0
	for i := range rc.SiteGroups {
		if neutralizeSiteGroup(&rc.SiteGroups[i]) {
			rewritten++
		}
	}
	return rewritten
}

// sanitizeConfigFile rewrites an exported config file (proxy.ConfigFile) in
// place. It returns the number of rules that were modified.
func sanitizeConfigFile(cf *proxy.ConfigFile) int {
	if cf == nil {
		return 0
	}
	rewritten := 0
	for i := range cf.Rules {
		rule := &cf.Rules[i]
		changed := false
		if mode, modeChanged := normalizeAndroidMode(rule.Mode); modeChanged {
			rule.Mode = mode
			changed = true
		}
		if rule.SniFake != "" {
			rule.SniFake = ""
			changed = true
		}
		if !rule.CertVerify.IsZero() {
			rule.CertVerify = config.CertVerifyConfig{}
			changed = true
		}
		if rule.ECHEnabled {
			rule.ECHEnabled = false
			changed = true
		}
		if rule.ECHProfileID != "" {
			rule.ECHProfileID = ""
			changed = true
		}
		if rule.ECHDomain != "" {
			rule.ECHDomain = ""
			changed = true
		}
		if changed {
			rewritten++
		}
	}
	return rewritten
}

// sanitizeImportExportJSON sanitizes the two desktop export formats:
//   - wrapper {"mitm": <config|string>, "transparent": <config|string>}
//   - a single config file {"version":..., "type":..., "rules":[...]}
//
// It returns the sanitized JSON plus the number of rewritten rules.
func sanitizeImportExportJSON(content string) (string, int, error) {
	trimmed := strings.TrimSpace(content)
	var wrapper map[string]json.RawMessage
	if err := json.Unmarshal([]byte(trimmed), &wrapper); err != nil {
		return "", 0, err
	}

	// Single config file format.
	if _, ok := wrapper["rules"]; ok {
		var cf proxy.ConfigFile
		if err := json.Unmarshal([]byte(trimmed), &cf); err != nil {
			return "", 0, err
		}
		rewritten := sanitizeConfigFile(&cf)
		out, err := json.Marshal(cf)
		if err != nil {
			return "", 0, err
		}
		return string(out), rewritten, nil
	}

	rewritten := 0
	for _, key := range []string{"mitm", "transparent"} {
		raw, ok := wrapper[key]
		if !ok {
			continue
		}
		var nested string
		if err := json.Unmarshal(raw, &nested); err == nil {
			var cf proxy.ConfigFile
			if err := json.Unmarshal([]byte(nested), &cf); err != nil {
				continue
			}
			rewritten += sanitizeConfigFile(&cf)
			data, err := json.Marshal(cf)
			if err != nil {
				continue
			}
			quoted, err := json.Marshal(string(data))
			if err != nil {
				continue
			}
			wrapper[key] = json.RawMessage(quoted)
			continue
		}
		var cf proxy.ConfigFile
		if err := json.Unmarshal(raw, &cf); err != nil {
			continue
		}
		rewritten += sanitizeConfigFile(&cf)
		data, err := json.Marshal(cf)
		if err != nil {
			continue
		}
		wrapper[key] = json.RawMessage(data)
	}

	out, err := json.Marshal(wrapper)
	if err != nil {
		return "", 0, err
	}
	return string(out), rewritten, nil
}

// enforceRulesLocked neutralizes every rule currently loaded in the manager.
// It is a safety net for legacy config files that predate the Android port.
func (a *App) enforceRulesLocked(rm *proxy.RuleManager) (int, error) {
	if rm == nil {
		return 0, nil
	}
	changed := 0
	for _, sg := range rm.GetSiteGroups() {
		if !needsNeutralize(sg) {
			continue
		}
		updated := sg
		_ = neutralizeSiteGroup(&updated)
		if err := rm.UpdateSiteGroup(updated); err != nil {
			return changed, err
		}
		changed++
	}
	return changed, nil
}

// needsNeutralize reports whether the rule violates the CA-free policy.
func needsNeutralize(sg proxy.SiteGroup) bool {
	copy := sg
	return neutralizeSiteGroup(&copy)
}
