package mobile

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/SnishaperTeam/snishaper-core/proxy"
)

func TestSiteGroupWriteModeWhitelist(t *testing.T) {
	for _, mode := range []string{"mitm", "tls-rf", "tls_rf", "quic", "transparent", "migration"} {
		group := proxy.SiteGroup{Name: "x", Mode: mode, Domains: []string{"example.com"}}
		if err := sanitizeSiteGroupForWrite(&group); err != nil {
			t.Errorf("mode %q must be accepted: %v", mode, err)
		}
	}
	for _, mode := range []string{"direct", "", "whatever"} {
		group := proxy.SiteGroup{Name: "x", Mode: mode, Domains: []string{"example.com"}}
		if err := sanitizeSiteGroupForWrite(&group); err == nil {
			t.Errorf("mode %q must be rejected", mode)
		}
	}
	empty := proxy.SiteGroup{Name: "x", Mode: "tls-rf"}
	if err := sanitizeSiteGroupForWrite(&empty); err == nil {
		t.Error("site group without domains must be rejected")
	}
}

func TestSiteGroupWritePreservesCAFields(t *testing.T) {
	group := proxy.SiteGroup{
		Name:               "x",
		Mode:               "mitm",
		Domains:            []string{"example.com"},
		SniFake:            "fake.example",
		ECHEnabled:         true,
		ECHProfileID:       "legacy-cloudflare",
		ECHDomain:          "crypto.cloudflare.com",
		ECHDiscoveryDomain: "crypto.cloudflare.com",
		ECHDoHUpstream:     "https://dns.example/dns-query",
	}
	group.CertVerify.Mode = "strict_real"
	if err := sanitizeSiteGroupForWrite(&group); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if group.Mode != "mitm" || group.SniFake != "fake.example" || !group.ECHEnabled ||
		group.ECHProfileID != "legacy-cloudflare" || group.CertVerify.IsZero() {
		t.Fatalf("CA fields were not preserved: %+v", group)
	}
}

func TestSiteGroupWriteDeduplicatesDomains(t *testing.T) {
	group := proxy.SiteGroup{
		Name:    "x",
		Mode:    "tls-rf",
		Domains: []string{"a.test", " a.test ", "", "b.test", "a.test"},
	}
	if err := sanitizeSiteGroupForWrite(&group); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	want := []string{"a.test", "b.test"}
	if len(group.Domains) != len(want) {
		t.Fatalf("domains = %v, want %v", group.Domains, want)
	}
	for i := range want {
		if group.Domains[i] != want[i] {
			t.Fatalf("domains = %v, want %v", group.Domains, want)
		}
	}
}

// assetsDefaults points at the committed Android defaults that both the app
// (AssetSeeder) and this test consume — a single copy, no duplicates.
var assetsDefaults = filepath.Join("..", "..", "app", "src", "main", "assets", "defaults")

func TestCommittedDefaultsAreValid(t *testing.T) {
	rulesRaw, err := os.ReadFile(filepath.Join(assetsDefaults, "rules.json"))
	if err != nil {
		t.Fatalf("read defaults rules: %v", err)
	}
	var rulesConfig proxy.RulesConfig
	if err := json.Unmarshal(rulesRaw, &rulesConfig); err != nil {
		t.Fatalf("parse defaults rules: %v", err)
	}
	if len(rulesConfig.SiteGroups) != 222 {
		t.Fatalf("defaults site groups = %d, want 222", len(rulesConfig.SiteGroups))
	}
	for _, group := range rulesConfig.SiteGroups {
		switch group.Mode {
		case "mitm", "tls-rf", "quic", "transparent", "migration", "direct":
		default:
			t.Fatalf("defaults rule %q has unsupported mode %q", group.Name, group.Mode)
		}
	}

	settingsRaw, err := os.ReadFile(filepath.Join(assetsDefaults, "settings.json"))
	if err != nil {
		t.Fatalf("read defaults settings: %v", err)
	}
	var settings proxy.SettingsConfig
	if err := json.Unmarshal(settingsRaw, &settings); err != nil {
		t.Fatalf("parse defaults settings: %v", err)
	}
	if strings.TrimSpace(string(settings.AutoRouting.Mode)) != "" {
		t.Fatalf("defaults auto routing mode = %q, want empty", settings.AutoRouting.Mode)
	}
	if settings.MigrationEnabled != nil && *settings.MigrationEnabled {
		t.Fatal("defaults must not enable the migration service")
	}
	if settings.ListenPort != "8080" {
		t.Fatalf("defaults listen port = %q", settings.ListenPort)
	}
}
