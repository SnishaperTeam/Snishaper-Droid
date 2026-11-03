package mobile

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
)

func newProfileTestApp(t *testing.T) *App {
	t.Helper()
	app, err := NewApp(t.TempDir())
	if err != nil {
		t.Fatalf("NewApp: %v", err)
	}
	if err := app.Start(); err != nil {
		t.Fatalf("Start: %v", err)
	}
	t.Cleanup(func() { _ = app.Stop() })
	return app
}

func TestProfileLifecycle(t *testing.T) {
	app := newProfileTestApp(t)

	if _, err := app.CreateProfileJSON("工作"); err != nil {
		t.Fatalf("create: %v", err)
	}
	if _, err := os.Stat(filepath.Join(app.profilesDir(), "工作", "settings.json")); err != nil {
		t.Fatalf("profile snapshot missing: %v", err)
	}

	var infos []profileInfo
	if err := json.Unmarshal([]byte(app.ListProfilesJSON()), &infos); err != nil {
		t.Fatalf("list: %v", err)
	}
	if len(infos) != 1 || infos[0].Name != "工作" {
		t.Fatalf("unexpected profile list: %+v", infos)
	}

	if err := app.RenameProfileJSON("工作", "office"); err != nil {
		t.Fatalf("rename: %v", err)
	}
	if err := app.DuplicateProfileJSON("office", "office-copy"); err != nil {
		t.Fatalf("duplicate: %v", err)
	}

	if _, err := app.CreateProfileJSON("office"); err == nil {
		t.Fatal("duplicate create must fail")
	}
	if err := app.RenameProfileJSON("office", "_reserved"); err == nil {
		t.Fatal("reserved name must be rejected")
	}

	bundle, err := app.ExportProfileJSON("office")
	if err != nil {
		t.Fatalf("export: %v", err)
	}
	var parsed profileBundle
	if err := json.Unmarshal([]byte(bundle), &parsed); err != nil {
		t.Fatalf("parse bundle: %v", err)
	}
	if parsed.Format != profileBundleFormat || parsed.Name != "office" {
		t.Fatalf("unexpected bundle: %+v", parsed)
	}

	parsed.Name = "imported"
	raw, _ := json.Marshal(parsed)
	result, err := app.ImportProfileJSON(string(raw))
	if err != nil {
		t.Fatalf("import: %v", err)
	}
	var imported profileImportResult
	if err := json.Unmarshal([]byte(result), &imported); err != nil {
		t.Fatalf("parse import result: %v", err)
	}
	if !imported.Valid || imported.Name != "imported" {
		t.Fatalf("unexpected import result: %+v", imported)
	}

	if err := app.SwitchProfileJSON("imported"); err != nil {
		t.Fatalf("switch: %v", err)
	}
	if got := app.GetActiveProfileJSON(); got != "imported" {
		t.Fatalf("active profile = %q, want imported", got)
	}
}

func TestIPPoolsRoundTrip(t *testing.T) {
	app := newProfileTestApp(t)

	apply := func(pools []map[string]any) {
		raw, _ := json.Marshal(pools)
		if err := app.UpdateIPPoolsJSON(string(raw)); err != nil {
			t.Fatalf("update pools: %v", err)
		}
	}
	apply([]map[string]any{
		{"name": "cdn-a", "preferred_ips": []string{"1.1.1.1", "2.2.2.2"}, "auto_update": false},
	})

	var pools []map[string]any
	if err := json.Unmarshal([]byte(app.GetIPPoolsJSON()), &pools); err != nil {
		t.Fatalf("get pools: %v", err)
	}
	if len(pools) != 1 || pools[0]["name"] != "cdn-a" {
		t.Fatalf("unexpected pools: %+v", pools)
	}

	apply([]map[string]any{
		{"name": "cloudflare", "preferred_ips": []string{"9.9.9.9"}},
		{"name": "cdn-b", "preferred_ips": []string{"3.3.3.3"}},
	})
	if err := json.Unmarshal([]byte(app.GetIPPoolsJSON()), &pools); err != nil {
		t.Fatalf("get pools: %v", err)
	}
	if len(pools) != 1 || pools[0]["name"] != "cdn-b" {
		t.Fatalf("reserved pool must be dropped: %+v", pools)
	}

	if _, err := app.ForceFetchIPPoolIPsJSON("missing"); err == nil {
		t.Fatal("fetch on a missing pool must fail")
	}
}

func TestExplainMatchAndConnections(t *testing.T) {
	app := newProfileTestApp(t)

	var explanation map[string]any
	if err := json.Unmarshal([]byte(mustExplain(t, app, "example.com")), &explanation); err != nil {
		t.Fatalf("parse explanation: %v", err)
	}
	if explanation["host"] != "example.com" {
		t.Fatalf("unexpected explanation: %+v", explanation)
	}
	if _, err := app.ExplainMatchJSON("   ", ""); err == nil {
		t.Fatal("empty host must be rejected")
	}

	if got := app.GetActiveConnectionsJSON(); got != "[]" {
		t.Fatalf("idle connections = %s, want []", got)
	}
}

func mustExplain(t *testing.T, app *App, host string) string {
	t.Helper()
	out, err := app.ExplainMatchJSON(host, "")
	if err != nil {
		t.Fatalf("explain: %v", err)
	}
	return out
}
