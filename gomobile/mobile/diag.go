package mobile

import (
	"archive/zip"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"time"
)

// ExportDiagnosticsJSON writes a support bundle (status, logs, redacted
// settings, rules) into <dataDir>/diagnostics and returns its path.
func (a *App) ExportDiagnosticsJSON() (out string, err error) {
	defer recoverError(&err)

	dir := filepath.Join(a.dataDir, "diagnostics")
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return "", fmt.Errorf("create diagnostics directory: %w", err)
	}
	path := filepath.Join(dir, fmt.Sprintf("snishaper-diagnostics-%s.zip", time.Now().Format("20060102-150405")))
	file, err := os.Create(path)
	if err != nil {
		return "", fmt.Errorf("create diagnostics archive: %w", err)
	}
	defer file.Close()

	archive := zip.NewWriter(file)
	write := func(name string, payload []byte) error {
		entry, err := archive.Create(name)
		if err != nil {
			return err
		}
		_, err = entry.Write(payload)
		return err
	}

	a.mu.Lock()
	srv := a.server
	tun := a.tun
	migration := a.migrationJSON
	a.mu.Unlock()

	proxyRunning := false
	listenAddr := ""
	proxyMode := ""
	if srv != nil {
		proxyRunning = srv.IsRunning()
		listenAddr = srv.GetListenAddr()
		proxyMode = srv.GetMode()
	}
	tunRunning := false
	tunMessage := ""
	if tun != nil {
		tunRunning = tun.Running()
		tunMessage = tun.Message()
	}

	status := map[string]any{
		"exported_at":  time.Now().Format(time.RFC3339),
		"app_version":  engineVersion,
		"core_version": coreVersion,
		"channel":      releaseChannel,
		"platform":     "android",
		"proxy": map[string]any{
			"running":    proxyRunning,
			"listenAddr": listenAddr,
			"mode":       proxyMode,
		},
		"tun": map[string]any{
			"running": tunRunning,
			"message": tunMessage,
		},
	}
	if migration != "" {
		status["last_migration_report"] = json.RawMessage(migration)
	}
	statusJSON, _ := json.MarshalIndent(status, "", "  ")
	if err := write("status.json", statusJSON); err != nil {
		_ = archive.Close()
		return "", err
	}

	if err := write("logs.txt", []byte(a.logs.recent(ringLogCapacity))); err != nil {
		_ = archive.Close()
		return "", err
	}
	if raw, readErr := os.ReadFile(a.settingsPath); readErr == nil {
		if err := write("settings.redacted.json", redactSettings(raw)); err != nil {
			_ = archive.Close()
			return "", err
		}
	}
	if raw, readErr := os.ReadFile(a.rulesPath); readErr == nil {
		if err := write("rules.json", raw); err != nil {
			_ = archive.Close()
			return "", err
		}
	}

	if err := archive.Close(); err != nil {
		return "", err
	}
	a.logf("[diag] diagnostics written to %s", path)
	return path, nil
}

// redactSettings clears credential-like values before export.
func redactSettings(raw []byte) []byte {
	var document map[string]any
	if err := json.Unmarshal(raw, &document); err != nil {
		return []byte("{}")
	}
	scrub(document)
	out, err := json.MarshalIndent(document, "", "  ")
	if err != nil {
		return []byte("{}")
	}
	return out
}

func scrub(node any) {
	switch value := node.(type) {
	case map[string]any:
		for key, child := range value {
			switch lowerASCII(key) {
			case "api_key", "password", "token", "secret":
				if text, ok := child.(string); ok && text != "" {
					value[key] = "***redacted***"
					continue
				}
			}
			scrub(child)
		}
	case []any:
		for _, child := range value {
			scrub(child)
		}
	}
}

func lowerASCII(value string) string {
	out := make([]rune, 0, len(value))
	for _, r := range value {
		if r >= 'A' && r <= 'Z' {
			r += 'a' - 'A'
		}
		out = append(out, r)
	}
	return string(out)
}
