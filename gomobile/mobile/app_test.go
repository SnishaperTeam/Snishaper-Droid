package mobile

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/SnishaperTeam/snishaper-core/proxy"
)

func newTestApp(t *testing.T) *App {
	t.Helper()
	app, err := NewApp(t.TempDir())
	if err != nil {
		t.Fatalf("NewApp: %v", err)
	}
	if err := app.Start(); err != nil {
		t.Fatalf("Start: %v", err)
	}
	t.Cleanup(func() {
		if err := app.Stop(); err != nil {
			t.Logf("Stop: %v", err)
		}
	})
	return app
}

func TestAppLifecycleAndStatus(t *testing.T) {
	app := newTestApp(t)
	if app.IsRunning() {
		t.Fatal("proxy must not auto-start in Start()")
	}

	var status map[string]any
	if err := json.Unmarshal([]byte(app.GetStatusJSON()), &status); err != nil {
		t.Fatalf("parse status: %v", err)
	}
	if running, _ := status["proxyRunning"].(bool); running {
		t.Fatal("status.proxyRunning should be false")
	}
	if _, ok := status["tunSupported"]; !ok {
		t.Fatal("status.tunSupported missing")
	}
	if _, ok := status["ipv6Available"]; !ok {
		t.Fatal("status.ipv6Available missing")
	}

	var settings proxy.SettingsConfig
	if err := json.Unmarshal([]byte(app.GetSettingsJSON()), &settings); err != nil {
		t.Fatalf("parse settings: %v", err)
	}
	if strings.TrimSpace(string(settings.AutoRouting.Mode)) != "" {
		t.Fatalf("auto routing mode = %q, want empty", settings.AutoRouting.Mode)
	}
	if settings.MigrationEnabled != nil && *settings.MigrationEnabled {
		t.Fatal("migration must be disabled")
	}
	if settings.ListenPort != "8080" {
		t.Fatalf("listen port = %q, want 8080", settings.ListenPort)
	}

	var buildInfo map[string]any
	if err := json.Unmarshal([]byte(app.GetBuildInfoJSON()), &buildInfo); err != nil {
		t.Fatalf("parse build info: %v", err)
	}
	if buildInfo["coreVersion"] != "v0.4.0" {
		t.Fatalf("coreVersion = %v", buildInfo["coreVersion"])
	}
	if groups := app.GetSiteGroupsJSON(); groups != "[]" {
		t.Fatalf("fresh site groups = %s, want []", groups)
	}
}

func TestImportDesktopRulesBecomesCAFree(t *testing.T) {
	app := newTestApp(t)
	raw, err := os.ReadFile(filepath.Join("testdata", "desktop-rules.json"))
	if err != nil {
		t.Fatalf("read desktop rules: %v", err)
	}
	out, err := app.ImportConfigWithSummaryJSON(string(raw))
	if err != nil {
		t.Fatalf("import desktop rules: %v", err)
	}
	var summary importSummaryPayload
	if err := json.Unmarshal([]byte(out), &summary); err != nil {
		t.Fatalf("parse import summary %q: %v", out, err)
	}
	if summary.Format != "rules" {
		t.Fatalf("import format = %q, want rules", summary.Format)
	}
	if summary.Total < 222 || summary.Added < 222 {
		t.Fatalf("import summary = %+v, want total/added >= 222", summary)
	}
	if summary.Rewritten == 0 {
		t.Fatalf("import summary = %+v, want rewritten > 0", summary)
	}

	// Inspect the stored rules (not just the sanitized JSON output).
	groups := app.rules.GetSiteGroups()
	if len(groups) != 222 {
		t.Fatalf("stored site groups = %d, want 222", len(groups))
	}
	caDependent := 0
	for _, group := range groups {
		if group.Mode != "tls-rf" && group.Mode != "transparent" && group.Mode != "direct" {
			caDependent++
			t.Errorf("stored rule %q has mode %q", group.Name, group.Mode)
		}
		if group.SniFake != "" || !group.CertVerify.IsZero() || group.ECHEnabled || group.ECHProfileID != "" || group.ECHDomain != "" {
			t.Errorf("stored rule %q still carries CA fields", group.Name)
		}
	}
	if caDependent != 0 {
		t.Fatalf("%d stored rule(s) still require a local CA", caDependent)
	}
}

func TestExportImportRoundTrip(t *testing.T) {
	source := newTestApp(t)
	raw, err := os.ReadFile(filepath.Join("testdata", "desktop-rules.json"))
	if err != nil {
		t.Fatalf("read desktop rules: %v", err)
	}
	if _, err := source.ImportConfigWithSummaryJSON(string(raw)); err != nil {
		t.Fatalf("import: %v", err)
	}
	exported, err := source.ExportConfigJSON()
	if err != nil {
		t.Fatalf("export: %v", err)
	}
	if strings.Contains(exported, `"mode": "mitm"`) || strings.Contains(exported, `"mode": "quic"`) {
		t.Fatal("export contains CA-dependent modes")
	}
	var exportedRules proxy.RulesConfig
	if err := json.Unmarshal([]byte(exported), &exportedRules); err != nil {
		t.Fatalf("parse export: %v", err)
	}
	for _, group := range exportedRules.SiteGroups {
		if group.Mode != "tls-rf" && group.Mode != "transparent" && group.Mode != "direct" {
			t.Fatalf("exported rule %q has mode %q", group.Name, group.Mode)
		}
		if group.SniFake != "" || !group.CertVerify.IsZero() || group.ECHEnabled {
			t.Fatalf("exported rule %q carries CA fields", group.Name)
		}
	}

	target := newTestApp(t)
	out, err := target.ImportConfigWithSummaryJSON(exported)
	if err != nil {
		t.Fatalf("re-import: %v", err)
	}
	var summary importSummaryPayload
	if err := json.Unmarshal([]byte(out), &summary); err != nil {
		t.Fatalf("parse summary: %v", err)
	}
	if len(target.rules.GetSiteGroups()) != 222 {
		t.Fatalf("round-trip site groups = %d, want 222", len(target.rules.GetSiteGroups()))
	}
}

func TestSiteGroupCRUDAndCAModeRejection(t *testing.T) {
	app := newTestApp(t)

	if _, err := app.AddSiteGroupJSON(`{"name":"x","mode":"mitm","domains":["x.test"]}`); err == nil {
		t.Fatal("adding a mitm rule must fail")
	}
	if _, err := app.AddSiteGroupJSON(`{"name":"x","mode":"quic","domains":["x.test"]}`); err == nil {
		t.Fatal("adding a quic rule must fail")
	}
	if _, err := app.AddSiteGroupJSON(`{"name":"x","mode":"transparent","domains":["x.test"]}`); err != nil {
		t.Fatalf("adding a transparent rule: %v", err)
	}

	id, err := app.AddSiteGroupJSON(`{"name":"console.test","mode":"tls-rf","domains":["console.test"],"sni_fake":"fake.example","cert_verify":{"mode":"strict_real"},"ech_enabled":true}`)
	if err != nil {
		t.Fatalf("add site group: %v", err)
	}
	if id == "" {
		t.Fatal("add site group returned empty id")
	}
	stored := findStoredGroup(t, app, id)
	if stored.Mode != "tls-rf" || stored.SniFake != "" || !stored.CertVerify.IsZero() || stored.ECHEnabled {
		t.Fatalf("stored group not sanitized: %+v", stored)
	}

	if err := app.UpdateSiteGroupJSON(`{"id":"` + id + `","name":"console.test","mode":"mitm","domains":["console.test"]}`); err == nil {
		t.Fatal("updating to mitm must fail")
	}
	if err := app.UpdateSiteGroupJSON(`{"id":"` + id + `","name":"console.test","mode":"transparent","domains":["console.test"]}`); err != nil {
		t.Fatalf("update to transparent: %v", err)
	}
	if err := app.DeleteSiteGroup(id); err != nil {
		t.Fatalf("delete: %v", err)
	}
	if stored := findStoredGroup(t, app, id); stored != nil {
		t.Fatalf("group %s still present after delete", id)
	}
}

func TestAutoRoutingForcedOff(t *testing.T) {
	app := newTestApp(t)
	if err := app.UpdateAutoRoutingConfigJSON(`{"mode":"default"}`); err == nil {
		t.Fatal("enabling smart routing must fail on Android")
	}
	if err := app.UpdateAutoRoutingConfigJSON(`{"mode":""}`); err != nil {
		t.Fatalf("disabling smart routing: %v", err)
	}
	// Even a settings payload that asks for smart routing must be neutralized.
	if err := app.UpdateSettingsJSON(`{"listen_port":"8080","auto_routing":{"mode":"default"},"migration_enabled":true}`); err != nil {
		t.Fatalf("update settings: %v", err)
	}
	var settings proxy.SettingsConfig
	if err := json.Unmarshal([]byte(app.GetSettingsJSON()), &settings); err != nil {
		t.Fatalf("parse settings: %v", err)
	}
	if strings.TrimSpace(string(settings.AutoRouting.Mode)) != "" {
		t.Fatalf("auto routing mode = %q after update", settings.AutoRouting.Mode)
	}
	if settings.MigrationEnabled != nil && *settings.MigrationEnabled {
		t.Fatal("migration enabled after update")
	}
}

func TestProxyLifecycle(t *testing.T) {
	app := newTestApp(t)
	if err := app.StartProxy(); err != nil {
		t.Fatalf("StartProxy: %v", err)
	}
	defer func() {
		if err := app.StopProxy(); err != nil {
			t.Logf("StopProxy: %v", err)
		}
	}()
	if !app.IsRunning() {
		t.Fatal("proxy should be running")
	}
	addr := app.GetListenAddr()
	if !strings.HasPrefix(addr, "127.0.0.1:") {
		t.Fatalf("listen addr = %q", addr)
	}
	if err := app.StartProxy(); err != nil {
		t.Fatalf("StartProxy must be idempotent: %v", err)
	}
	if err := app.StopProxy(); err != nil {
		t.Fatalf("StopProxy: %v", err)
	}
	if app.IsRunning() {
		t.Fatal("proxy still running after StopProxy")
	}
	if err := app.StopProxy(); err != nil {
		t.Fatalf("StopProxy must be idempotent: %v", err)
	}
}

func TestProxyModeWhitelist(t *testing.T) {
	app := newTestApp(t)
	if err := app.SetProxyMode("mitm"); err == nil {
		t.Fatal("SetProxyMode(mitm) must fail")
	}
	if err := app.SetProxyMode("transparent"); err != nil {
		t.Fatalf("SetProxyMode(transparent): %v", err)
	}
	if mode := app.GetProxyMode(); mode != "transparent" {
		t.Fatalf("proxy mode = %q, want transparent", mode)
	}
	if err := app.SetProxyMode(""); err != nil {
		t.Fatalf("SetProxyMode(\"\"): %v", err)
	}
}

func TestTUNRequiresRunningProxy(t *testing.T) {
	app := newTestApp(t)
	if err := app.StartTUN(1234, 1500); err == nil {
		t.Fatal("StartTUN must fail while the proxy is not running")
	}
	var status map[string]any
	if err := json.Unmarshal([]byte(app.GetTUNStatusJSON()), &status); err != nil {
		t.Fatalf("parse tun status: %v", err)
	}
	if supported, _ := status["supported"].(bool); !supported {
		t.Fatal("TUN should be reported as supported on Android builds")
	}
}

func TestUpdateSettingsAppliesFragmentAndCloudflare(t *testing.T) {
	app := newTestApp(t)
	payload := `{
	  "listen_port": "18080",
	  "fragment": {"num_records": 2, "num_segments": 3},
	  "cloudflare_config": {"preferred_ips": ["1.2.3.4", "5.6.7.8"], "auto_update": false, "api_key": ""}
	}`
	if err := app.UpdateSettingsJSON(payload); err != nil {
		t.Fatalf("UpdateSettingsJSON: %v", err)
	}
	var fragment proxy.FragmentConfig
	if err := json.Unmarshal([]byte(app.GetFragmentConfigJSON()), &fragment); err != nil {
		t.Fatalf("parse fragment: %v", err)
	}
	if fragment.NumRecords != 2 || fragment.NumSegments != 3 {
		t.Fatalf("fragment = %+v", fragment)
	}
	if listenAddr := app.GetListenAddr(); !strings.HasSuffix(listenAddr, ":18080") {
		t.Fatalf("listen addr = %q, want port 18080", listenAddr)
	}
	var cloudflare proxy.CloudflareConfig
	if err := json.Unmarshal([]byte(app.GetCloudflareConfigJSON()), &cloudflare); err != nil {
		t.Fatalf("parse cloudflare: %v", err)
	}
	if len(cloudflare.PreferredIPs) != 2 || cloudflare.PreferredIPs[0] != "1.2.3.4" {
		t.Fatalf("cloudflare preferred ips = %v", cloudflare.PreferredIPs)
	}
}

func findStoredGroup(t *testing.T, app *App, id string) *proxy.SiteGroup {
	t.Helper()
	for _, group := range app.rules.GetSiteGroups() {
		if group.ID == id {
			copy := group
			return &copy
		}
	}
	return nil
}

// TestNoCertificateArtifactsCreated is the product-level CA-free guard: the
// engine must never write a CA certificate/key or any PEM file into dataDir.
func TestNoCertificateArtifactsCreated(t *testing.T) {
	dir := t.TempDir()
	app, err := NewApp(dir)
	if err != nil {
		t.Fatalf("NewApp: %v", err)
	}
	if err := app.Start(); err != nil {
		t.Fatalf("Start: %v", err)
	}
	if err := app.StartProxy(); err != nil {
		t.Fatalf("StartProxy: %v", err)
	}
	if err := app.StopProxy(); err != nil {
		t.Fatalf("StopProxy: %v", err)
	}
	if err := app.Stop(); err != nil {
		t.Fatalf("Stop: %v", err)
	}
	forbidden := []string{"ca.crt", "ca.key", ".pem", ".crt", ".key", "cert"}
	err = filepath.Walk(dir, func(path string, info os.FileInfo, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		if info.IsDir() {
			return nil
		}
		name := strings.ToLower(info.Name())
		for _, marker := range forbidden {
			if strings.Contains(name, marker) {
				t.Errorf("forbidden certificate artifact in dataDir: %s", path)
			}
		}
		return nil
	})
	if err != nil {
		t.Fatalf("walk dataDir: %v", err)
	}
}

