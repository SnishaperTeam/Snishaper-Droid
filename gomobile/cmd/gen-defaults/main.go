// Command gen-defaults converts the desktop default rules/config into the
// Android defaults and writes them to the app assets directory (the single
// committed copy, consumed by AssetSeeder and sanitize_test.go).
//
// The rules snapshot is migrated with full fidelity: mitm / quic modes and
// the sni_fake / cert_verify / ECH fields are preserved as-is. Only the
// settings are overridden with the Android platform defaults (smart routing
// off, migration off, Cloudflare auto-update off).
//
// It is a one-off migration tool committed for reproducibility. It never
// modifies the desktop repository and never touches snishaper-core.
//
// Usage:
//
//	go run ./cmd/gen-defaults \
//	  -desktop-rules  D:/c++/Contributions/SniShaper-main/rules/config.json \
//	  -desktop-settings D:/c++/Contributions/SniShaper-main/config/settings.json \
//	  -assets ../app/src/main/assets/defaults
package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/SnishaperTeam/snishaper-core/proxy"
)

func main() {
	desktopRulesPath := flag.String("desktop-rules", "", "path to SniShaper-main/rules/config.json")
	desktopSettingsPath := flag.String("desktop-settings", "", "path to SniShaper-main/config/settings.json (optional)")
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

	// Full-fidelity migration: site groups pass through untouched.
	remote.ConfigVersion = proxy.CurrentConfigVersion
	rulesJSON, err := json.MarshalIndent(remote, "", "  ")
	if err != nil {
		fatal("marshal rules: %v", err)
	}
	rulesJSON = append(rulesJSON, '\n')

	settings := proxy.SettingsConfig{
		ConfigVersion:  proxy.CurrentConfigVersion,
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

	dir := *assetsDir
	if err := os.MkdirAll(dir, 0o755); err != nil {
		fatal("mkdir %s: %v", dir, err)
	}
	if err := os.WriteFile(filepath.Join(dir, "rules.json"), rulesJSON, 0o644); err != nil {
		fatal("write rules.json to %s: %v", dir, err)
	}
	if err := os.WriteFile(filepath.Join(dir, "settings.json"), settingsJSON, 0o644); err != nil {
		fatal("write settings.json to %s: %v", dir, err)
	}

	modes := make(map[string]int)
	for _, group := range remote.SiteGroups {
		modes[strings.ToLower(strings.TrimSpace(group.Mode))]++
	}
	fmt.Printf("desktop site groups     : %d\n", len(remote.SiteGroups))
	fmt.Printf("mode distribution       : mitm=%d tls-rf=%d quic=%d transparent=%d direct=%d\n",
		modes["mitm"], modes["tls-rf"], modes["quic"], modes["transparent"], modes["direct"])
	fmt.Printf("upstreams/dns/ech/nat64 : %d / %d / %d / %d\n", len(remote.Upstreams), len(remote.DNSNodes), len(remote.ECHProfiles), len(remote.NAT64Profiles))
	fmt.Printf("written                 : %s\n", *assetsDir)
}

func fatal(format string, args ...any) {
	fmt.Fprintf(os.Stderr, "gen-defaults: "+format+"\n", args...)
	os.Exit(1)
}
