package mobile

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/SnishaperTeam/snishaper-core/proxy"
)

func TestNormalizeAndroidMode(t *testing.T) {
	cases := []struct {
		in      string
		want    string
		changed bool
	}{
		{"tls-rf", modeTLSRF, false},
		{"tls_rf", modeTLSRF, true},
		{"transparent", modeTransparent, false},
		{"direct", modeDirect, false},
		{"mitm", modeTLSRF, true},
		{"MITM", modeTLSRF, true},
		{"quic", modeTLSRF, true},
		{"migration", modeTLSRF, true},
		{"", modeTransparent, true},
		{"whatever", modeTLSRF, true},
	}
	for _, tc := range cases {
		got, changed := normalizeAndroidMode(tc.in)
		if got != tc.want || changed != tc.changed {
			t.Errorf("normalizeAndroidMode(%q) = (%q,%v), want (%q,%v)", tc.in, got, changed, tc.want, tc.changed)
		}
	}
}

func TestSiteGroupWriteRejectsCAModes(t *testing.T) {
	for _, mode := range []string{"mitm", "quic", "migration", "direct", ""} {
		group := proxy.SiteGroup{Name: "x", Mode: mode, Domains: []string{"example.com"}}
		if err := sanitizeSiteGroupForWrite(&group); err == nil {
			t.Errorf("mode %q must be rejected on Android", mode)
		}
	}
	for _, mode := range []string{"tls-rf", "transparent"} {
		group := proxy.SiteGroup{Name: "x", Mode: mode, Domains: []string{"example.com"}}
		if err := sanitizeSiteGroupForWrite(&group); err != nil {
			t.Errorf("mode %q must be accepted: %v", mode, err)
		}
	}
	empty := proxy.SiteGroup{Name: "x", Mode: "tls-rf"}
	if err := sanitizeSiteGroupForWrite(&empty); err == nil {
		t.Error("site group without domains must be rejected")
	}
}

func TestSiteGroupWriteClearsCAFields(t *testing.T) {
	group := proxy.SiteGroup{
		Name:               "x",
		Mode:               "tls-rf",
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
	if group.SniFake != "" || group.ECHEnabled || group.ECHProfileID != "" || group.ECHDomain != "" ||
		group.ECHDiscoveryDomain != "" || group.ECHDoHUpstream != "" || !group.CertVerify.IsZero() {
		t.Fatalf("CA fields were not cleared: %+v", group)
	}
}

func TestSanitizeRulesConfigDesktopDefaults(t *testing.T) {
	raw, err := os.ReadFile(filepath.Join("testdata", "desktop-rules.json"))
	if err != nil {
		t.Fatalf("read testdata: %v", err)
	}
	var rulesConfig proxy.RulesConfig
	if err := json.Unmarshal(raw, &rulesConfig); err != nil {
		t.Fatalf("parse desktop rules: %v", err)
	}
	if len(rulesConfig.SiteGroups) != 222 {
		t.Fatalf("desktop site groups = %d, want 222", len(rulesConfig.SiteGroups))
	}
	rewritten := sanitizeRulesConfig(&rulesConfig)
	if rewritten == 0 {
		t.Fatal("expected at least one rewritten rule")
	}
	direct := 0
	for _, group := range rulesConfig.SiteGroups {
		switch group.Mode {
		case "tls-rf", "transparent":
		case "direct":
			direct++
		default:
			t.Fatalf("rule %q still has unsupported mode %q", group.Name, group.Mode)
		}
		if group.SniFake != "" || !group.CertVerify.IsZero() || group.ECHEnabled ||
			group.ECHProfileID != "" || group.ECHDomain != "" {
			t.Fatalf("rule %q still carries CA fields: %+v", group.Name, group)
		}
	}
	if direct != 1 {
		t.Fatalf("direct built-in rules = %d, want 1", direct)
	}
}

func TestSanitizeImportExportJSON(t *testing.T) {
	input := `{
	  "mitm": "{\"version\":\"1.0\",\"type\":\"mitm\",\"rules\":[{\"name\":\"a\",\"mode\":\"mitm\",\"domains\":[\"a.test\"],\"sni_fake\":\"fake\",\"cert_verify\":{\"mode\":\"strict_real\"},\"ech_enabled\":true}]}",
	  "transparent": "{\"version\":\"1.0\",\"type\":\"transparent\",\"rules\":[{\"name\":\"b\",\"mode\":\"tls-rf\",\"domains\":[\"b.test\"],\"ech_enabled\":true}]}"
	}`
	sanitized, rewritten, err := sanitizeImportExportJSON(input)
	if err != nil {
		t.Fatalf("sanitize: %v", err)
	}
	if rewritten != 2 {
		t.Fatalf("rewritten = %d, want 2", rewritten)
	}
	var wrapper map[string]string
	if err := json.Unmarshal([]byte(sanitized), &wrapper); err != nil {
		t.Fatalf("parse sanitized wrapper: %v", err)
	}
	for _, key := range []string{"mitm", "transparent"} {
		var cf proxy.ConfigFile
		if err := json.Unmarshal([]byte(wrapper[key]), &cf); err != nil {
			t.Fatalf("parse %s config: %v", key, err)
		}
		for _, rule := range cf.Rules {
			if rule.Mode != "tls-rf" && rule.Mode != "transparent" {
				t.Fatalf("%s rule mode = %q", key, rule.Mode)
			}
			if rule.SniFake != "" || !rule.CertVerify.IsZero() || rule.ECHEnabled {
				t.Fatalf("%s rule still carries CA fields: %+v", key, rule)
			}
		}
	}
}

func TestCommittedDefaultsAreCAFree(t *testing.T) {
	rulesRaw, err := os.ReadFile(filepath.Join("..", "defaults", "rules.json"))
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
	if rewritten := sanitizeRulesConfig(&rulesConfig); rewritten != 0 {
		t.Fatalf("committed defaults are not CA-free: %d rule(s) needed sanitizing", rewritten)
	}

	settingsRaw, err := os.ReadFile(filepath.Join("..", "defaults", "settings.json"))
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
