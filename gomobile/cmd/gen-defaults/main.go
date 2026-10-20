// Command gen-defaults converts the desktop default rules/config into the
// CA-free Android defaults and writes them to gomobile/defaults and the app
// assets directory.
//
// It is a one-off migration tool committed for reproducibility. It never
// modifies the desktop repository and never touches snishaper-core.
//
// Usage:
//
//	go run ./cmd/gen-defaults \
//	  -desktop-rules  D:/c++/Contributions/SniShaper-main/rules/config.json \
//	  -desktop-settings D:/c++/Contributions/SniShaper-main/config/settings.json \
//	  -out ./defaults \
//	  -assets ../app/src/main/assets/defaults
package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/SnishaperTeam/snishaper-core/config"
	"github.com/SnishaperTeam/snishaper-core/proxy"
)

func main() {
	desktopRulesPath := flag.String("desktop-rules", "", "path to SniShaper-main/rules/config.json")
	desktopSettingsPath := flag.String("desktop-settings", "", "path to SniShaper-main/config/settings.json (optional)")
	outDir := flag.String("out", "defaults", "gomobile/defaults output directory")
	assetsDir := flag.String("assets", "../app/src/main/assets/defaults", "Android assets output directory")
	flag.Parse()

	if *desktopRulesPath == "" {
		fatal("-desktop-rules is required")
	}

	raw, err := os.ReadFile(*desktopRulesPath)
	if err != nil {
		fatal("read desktop rules: %v", err)
	}
	var remote proxy.RulesConfig
	if err := json.Unmarshal(raw, &remote); err != nil {
		fatal("parse desktop rules: %v", err)
	}

	rewritten, cleared := sanitizeRulesConfig(&remote)
	rulesJSON, err := json.MarshalIndent(remote, "", "  ")
	if err != nil {
		fatal("marshal rules: %v", err)
	}
	rulesJSON = append(rulesJSON, '\n')

	settings := proxy.SettingsConfig{
		ListenPort:     "8080",
		AutoRouting:    proxy.AutoRoutingConfig{Mode: ""},
		TUN:            proxy.TUNConfig{Enabled: false, MTU: 1500, DNSHijack: true, AutoRoute: false, StrictRoute: false, Stack: "gvisor"},
		Fragment:       proxy.DefaultFragmentConfig(),
		Language:       "zh",
		Theme:          "dark",
		AppearanceMode: "system",
		MigrationEnabled: func() *bool {
			value := false
			return &value
		}(),
		MigrationServer: "",
	}
	if *desktopSettingsPath != "" {
		if settingsRaw, readErr := os.ReadFile(*desktopSettingsPath); readErr == nil {
			var desktopSettings proxy.SettingsConfig
			if err := json.Unmarshal(settingsRaw, &desktopSettings); err == nil {
				// Keep the desktop Cloudflare preferred IP list; everything else
				// is replaced by the Android defaults above.
				settings.CloudflareConfig = desktopSettings.CloudflareConfig
			} else {
				fmt.Printf("warning: parse desktop settings: %v\n", err)
			}
		}
	}
	if settings.CloudflareConfig.AutoUpdate {
		settings.CloudflareConfig.AutoUpdate = false
	}
	settingsJSON, err := json.MarshalIndent(settings, "", "  ")
	if err != nil {
		fatal("marshal settings: %v", err)
	}
	settingsJSON = append(settingsJSON, '\n')

	for _, dir := range []string{*outDir, *assetsDir} {
		if err := os.MkdirAll(dir, 0o755); err != nil {
			fatal("mkdir %s: %v", dir, err)
		}
		if err := os.WriteFile(filepath.Join(dir, "rules.json"), rulesJSON, 0o644); err != nil {
			fatal("write rules.json to %s: %v", dir, err)
		}
		if err := os.WriteFile(filepath.Join(dir, "settings.json"), settingsJSON, 0o644); err != nil {
			fatal("write settings.json to %s: %v", dir, err)
		}
	}

	mitm, quic, other := 0, 0, 0
	for _, group := range remote.SiteGroups {
		switch strings.ToLower(strings.TrimSpace(group.Mode)) {
		case "tls-rf":
			other++
		case "transparent":
			other++
		case "direct":
			other++
		default:
			mitm++
		}
	}
	fmt.Printf("desktop site groups     : %d\n", len(remote.SiteGroups))
	fmt.Printf("rewritten rules         : %d (%d mode rewrites + CA field clears)\n", rewritten, cleared)
	fmt.Printf("resulting modes         : tls-rf/transparent/direct=%d, unexpected=%d\n", other, mitm+quic)
	fmt.Printf("upstreams/dns/ech/nat64 : %d / %d / %d / %d\n", len(remote.Upstreams), len(remote.DNSNodes), len(remote.ECHProfiles), len(remote.NAT64Profiles))
	fmt.Printf("written                 : %s and %s\n", *outDir, *assetsDir)
}

// sanitizeRulesConfig rewrites the rules snapshot in place and reports the
// number of modified rules plus the number of rules whose mode was rewritten.
func sanitizeRulesConfig(rc *proxy.RulesConfig) (rewritten int, modeRewrites int) {
	for i := range rc.SiteGroups {
		group := &rc.SiteGroups[i]
		changed := false
		switch strings.ToLower(strings.TrimSpace(group.Mode)) {
		case "tls-rf", "transparent", "direct":
			group.Mode = strings.ToLower(strings.TrimSpace(group.Mode))
		default:
			group.Mode = "tls-rf"
			changed = true
			modeRewrites++
		}
		if group.SniFake != "" {
			group.SniFake = ""
			changed = true
		}
		if !group.CertVerify.IsZero() {
			group.CertVerify = config.CertVerifyConfig{}
			changed = true
		}
		if group.ECHEnabled {
			group.ECHEnabled = false
			changed = true
		}
		if group.ECHProfileID != "" {
			group.ECHProfileID = ""
			changed = true
		}
		if group.ECHDomain != "" {
			group.ECHDomain = ""
			changed = true
		}
		if group.ECHDiscoveryDomain != "" {
			group.ECHDiscoveryDomain = ""
			changed = true
		}
		if group.ECHDoHUpstream != "" {
			group.ECHDoHUpstream = ""
			changed = true
		}
		if changed {
			rewritten++
		}
	}
	return rewritten, modeRewrites
}

func fatal(format string, args ...any) {
	fmt.Fprintf(os.Stderr, "gen-defaults: "+format+"\n", args...)
	os.Exit(1)
}
