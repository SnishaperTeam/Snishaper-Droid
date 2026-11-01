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
	if buildInfo["coreVersion"] != "v0.8.4" {
		t.Fatalf("coreVersion = %v", buildInfo["coreVersion"])
	}
	if groups := app.GetSiteGroupsJSON(); groups != "[]" {
		t.Fatalf("fresh site groups = %s, want []", groups)
	}
}

func TestImportDesktopRulesPassthrough(t *testing.T) {
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

	// Desktop rules import unmodified: modes and CA fields survive.
	groups := app.rules.GetSiteGroups()
	if len(groups) != 222 {
		t.Fatalf("stored site groups = %d, want 222", len(groups))
	}
	counts := map[string]int{}
	mitmWithCA := 0
	for _, group := range groups {
		counts[group.Mode]++
		switch group.Mode {
		case "mitm", "tls-rf", "quic", "transparent", "migration", "direct":
		default:
			t.Errorf("stored rule %q has mode %q", group.Name, group.Mode)
		}
		if group.Mode == "mitm" && (group.SniFake != "" || !group.CertVerify.IsZero() || group.ECHEnabled) {
			mitmWithCA++
		}
	}
	if counts["mitm"] != 68 || counts["quic"] != 9 {
		t.Fatalf("stored mode counts = %v, want mitm=68 quic=9", counts)
	}
	if mitmWithCA == 0 {
		t.Fatal("no stored mitm rule kept its CA fields")
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
	var exportedRules proxy.RulesConfig
	if err := json.Unmarshal([]byte(exported), &exportedRules); err != nil {
		t.Fatalf("parse export: %v", err)
	}
	counts := map[string]int{}
	for _, group := range exportedRules.SiteGroups {
		counts[group.Mode]++
	}
	if counts["mitm"] != 68 || counts["quic"] != 9 || counts["tls-rf"] != 124 {
		t.Fatalf("exported mode counts = %v, want mitm=68 quic=9 tls-rf=124", counts)
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

func TestSiteGroupCRUDPreservesCAFields(t *testing.T) {
	app := newTestApp(t)

	if _, err := app.AddSiteGroupJSON(`{"name":"x","mode":"mitm","domains":["x.test"]}`); err != nil {
		t.Fatalf("adding a mitm rule: %v", err)
	}
	if _, err := app.AddSiteGroupJSON(`{"name":"y","mode":"quic","domains":["y.test"]}`); err != nil {
		t.Fatalf("adding a quic rule: %v", err)
	}
	if _, err := app.AddSiteGroupJSON(`{"name":"z","mode":"transparent","domains":["z.test"]}`); err != nil {
		t.Fatalf("adding a transparent rule: %v", err)
	}
	if _, err := app.AddSiteGroupJSON(`{"name":"bad","mode":"direct","domains":["bad.test"]}`); err == nil {
		t.Fatal("adding a direct rule must fail")
	}

	id, err := app.AddSiteGroupJSON(`{"name":"console.test","mode":"mitm","domains":["console.test"],"sni_fake":"fake.example","cert_verify":{"mode":"strict_real"},"ech_enabled":true}`)
	if err != nil {
		t.Fatalf("add site group: %v", err)
	}
	if id == "" {
		t.Fatal("add site group returned empty id")
	}
	stored := findStoredGroup(t, app, id)
	if stored.Mode != "mitm" || stored.SniFake != "fake.example" || stored.CertVerify.IsZero() || !stored.ECHEnabled {
		t.Fatalf("stored group lost CA fields: %+v", stored)
	}

	if err := app.UpdateSiteGroupJSON(`{"id":"` + id + `","name":"console.test","mode":"quic","domains":["console.test"],"sni_fake":"fake2.example"}`); err != nil {
		t.Fatalf("update to quic: %v", err)
	}
	stored = findStoredGroup(t, app, id)
	if stored.Mode != "quic" || stored.SniFake != "fake2.example" {
		t.Fatalf("updated group not stored verbatim: %+v", stored)
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
	if err := app.UpdateSettingsJSON(`{"listen_port":"8080","auto_routing":{"mode":"default"}}`); err != nil {
		t.Fatalf("update settings: %v", err)
	}
	var settings proxy.SettingsConfig
	if err := json.Unmarshal([]byte(app.GetSettingsJSON()), &settings); err != nil {
		t.Fatalf("parse settings: %v", err)
	}
	if strings.TrimSpace(string(settings.AutoRouting.Mode)) != "" {
		t.Fatalf("auto routing mode = %q after update", settings.AutoRouting.Mode)
	}
}

func TestMigrationSettingsPassthrough(t *testing.T) {
	app := newTestApp(t)
	payload := `{"listen_port":"8080","migration_enabled":true,"migration_server":"https://migration.example"}`
	if err := app.UpdateSettingsJSON(payload); err != nil {
		t.Fatalf("update settings: %v", err)
	}
	var settings proxy.SettingsConfig
	if err := json.Unmarshal([]byte(app.GetSettingsJSON()), &settings); err != nil {
		t.Fatalf("parse settings: %v", err)
	}
	if settings.MigrationEnabled == nil || !*settings.MigrationEnabled {
		t.Fatalf("migration enabled = %v, want true", settings.MigrationEnabled)
	}
	if settings.MigrationServer != "https://migration.example" {
		t.Fatalf("migration server = %q", settings.MigrationServer)
	}

	if err := app.UpdateSettingsJSON(`{"listen_port":"8080","migration_enabled":false,"migration_server":""}`); err != nil {
		t.Fatalf("disable migration: %v", err)
	}
	// Fresh struct: omitempty drops an empty migration_server from the payload,
	// which must not leave a stale value from the previous unmarshal behind.
	var cleared proxy.SettingsConfig
	if err := json.Unmarshal([]byte(app.GetSettingsJSON()), &cleared); err != nil {
		t.Fatalf("parse settings: %v", err)
	}
	if cleared.MigrationEnabled != nil && *cleared.MigrationEnabled {
		t.Fatal("migration still enabled after disable")
	}
	if cleared.MigrationServer != "" {
		t.Fatalf("migration server = %q after clear", cleared.MigrationServer)
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

// TestStartCreatesCAMaterial verifies the cert manager wiring: Start()
// generates the CA under dataDir/certs and the certificate bridge returns it.
func TestStartCreatesCAMaterial(t *testing.T) {
	dir := t.TempDir()
	app, err := NewApp(dir)
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

	caPath := filepath.Join(dir, "certs", "ca.crt")
	keyPath := filepath.Join(dir, "certs", "ca.key")
	if _, err := os.Stat(caPath); err != nil {
		t.Fatalf("CA certificate missing: %v", err)
	}
	if _, err := os.Stat(keyPath); err != nil {
		t.Fatalf("CA key missing: %v", err)
	}

	if got := app.GetCACertPath(); !strings.HasSuffix(got, filepath.Join("certs", "ca.crt")) {
		t.Fatalf("GetCACertPath = %q", got)
	}
	pemData := app.GetCACertPEM()
	if !strings.Contains(pemData, "BEGIN CERTIFICATE") {
		t.Fatalf("GetCACertPEM returned no PEM block: %q", pemData)
	}
	if exported := app.ExportCert(); exported != pemData {
		t.Fatalf("ExportCert differs from GetCACertPEM")
	}

	var status map[string]any
	if err := json.Unmarshal([]byte(app.GetCAInstallStatusJSON()), &status); err != nil {
		t.Fatalf("parse CA install status: %v", err)
	}
	for _, key := range []string{"Installed", "Platform", "CertPath", "InstallHelp"} {
		if _, ok := status[key]; !ok {
			t.Fatalf("CA install status missing key %q: %v", key, status)
		}
	}
	var installed []map[string]any
	if err := json.Unmarshal([]byte(app.GetInstalledCertsJSON()), &installed); err != nil {
		t.Fatalf("parse installed certs: %v", err)
	}
}

