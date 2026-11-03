package mobile

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
	"time"

	corelog "github.com/SnishaperTeam/snishaper-core/log"
	"github.com/SnishaperTeam/snishaper-core/proxy"
)

const (
	profileBundleFormat  = "snishaper-profile"
	profileBundleVersion = 1
)

var profileNameRe = regexp.MustCompile(`^[\p{Han}\p{Latin}0-9_\-][\p{Han}\p{Latin}0-9_\- ]{0,63}$`)

type profileInfo struct {
	Name      string `json:"name"`
	UpdatedAt string `json:"updated_at,omitempty"`
	Active    bool   `json:"active"`
}

type profileImportResult struct {
	Name     string   `json:"name"`
	Valid    bool     `json:"valid"`
	Warnings []string `json:"warnings"`
	Errors   []string `json:"errors"`
}

type profileBundle struct {
	Format     string          `json:"format"`
	Version    int             `json:"version"`
	Name       string          `json:"name"`
	ExportedAt string          `json:"exported_at"`
	Settings   json.RawMessage `json:"settings"`
	Rules      json.RawMessage `json:"rules"`
}

func (a *App) profilesDir() string { return filepath.Join(a.dataDir, "profiles") }

func (a *App) profileDir(name string) string { return filepath.Join(a.profilesDir(), name) }

func (a *App) activeProfilePath() string { return filepath.Join(a.profilesDir(), "_active.json") }

func validateProfileName(name string) error {
	if !profileNameRe.MatchString(name) {
		return fmt.Errorf("invalid profile name (letters, digits, CJK, space, _ and -, max 64 chars)")
	}
	if strings.HasPrefix(name, "_") {
		return fmt.Errorf("profile names starting with '_' are reserved")
	}
	return nil
}

// GetActiveProfileJSON returns the profile the live configuration mirrors.
func (a *App) GetActiveProfileJSON() string {
	return a.activeProfile()
}

func (a *App) activeProfile() string {
	data, err := os.ReadFile(a.activeProfilePath())
	if err != nil {
		return ""
	}
	var marker struct {
		Name string `json:"name"`
	}
	if json.Unmarshal(data, &marker) != nil || marker.Name == "" {
		return ""
	}
	if _, err := os.Stat(a.profileDir(marker.Name)); err != nil {
		return ""
	}
	return marker.Name
}

func (a *App) writeActiveProfile(name string) {
	_ = os.MkdirAll(a.profilesDir(), 0o755)
	data, _ := json.Marshal(map[string]string{
		"name":        name,
		"switched_at": time.Now().Format(time.RFC3339),
	})
	_ = os.WriteFile(a.activeProfilePath(), data, 0o644)
}

func copyProfileFile(src, dst string) error {
	data, err := os.ReadFile(src)
	if err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(dst), 0o755); err != nil {
		return err
	}
	tmp := dst + ".tmp"
	if err := os.WriteFile(tmp, data, 0o644); err != nil {
		return err
	}
	return os.Rename(tmp, dst)
}

func (a *App) snapshotLiveInto(dir string) error {
	if err := copyProfileFile(a.settingsPath, filepath.Join(dir, "settings.json")); err != nil {
		return err
	}
	return copyProfileFile(a.rulesPath, filepath.Join(dir, "rules.json"))
}

func snapshotDirInto(srcDir, dstDir string) error {
	for _, name := range []string{"settings.json", "rules.json"} {
		if err := copyProfileFile(filepath.Join(srcDir, name), filepath.Join(dstDir, name)); err != nil {
			return err
		}
	}
	return nil
}

// ListProfilesJSON returns every stored profile, newest first.
func (a *App) ListProfilesJSON() string {
	active := a.activeProfile()
	entries, err := os.ReadDir(a.profilesDir())
	if err != nil {
		return "[]"
	}
	out := make([]profileInfo, 0, len(entries))
	for _, entry := range entries {
		if !entry.IsDir() || strings.HasPrefix(entry.Name(), "_") {
			continue
		}
		updated := ""
		if info, err := entry.Info(); err == nil {
			updated = info.ModTime().Format(time.RFC3339)
		}
		out = append(out, profileInfo{Name: entry.Name(), UpdatedAt: updated, Active: entry.Name() == active})
	}
	sort.Slice(out, func(i, j int) bool { return out[i].UpdatedAt > out[j].UpdatedAt })
	return marshalJSON(out)
}

// CreateProfileJSON snapshots the live configuration as a new profile.
func (a *App) CreateProfileJSON(name string) (out string, err error) {
	defer recoverError(&err)
	name = strings.TrimSpace(name)
	if err := validateProfileName(name); err != nil {
		return "", err
	}
	if _, statErr := os.Stat(a.profileDir(name)); statErr == nil {
		return "", fmt.Errorf("profile %q already exists", name)
	}
	if err := a.snapshotLiveInto(a.profileDir(name)); err != nil {
		return "", fmt.Errorf("snapshot live config: %w", err)
	}
	a.logf("[profiles] created %q from the current configuration", name)
	return marshalJSON(profileInfo{Name: name, Active: a.activeProfile() == name}), nil
}

// RenameProfileJSON renames a stored profile and keeps the marker in sync.
func (a *App) RenameProfileJSON(oldName string, newName string) (err error) {
	defer recoverError(&err)
	newName = strings.TrimSpace(newName)
	if err := validateProfileName(newName); err != nil {
		return err
	}
	if _, statErr := os.Stat(a.profileDir(oldName)); statErr != nil {
		return fmt.Errorf("profile %q does not exist", oldName)
	}
	if _, statErr := os.Stat(a.profileDir(newName)); statErr == nil {
		return fmt.Errorf("profile %q already exists", newName)
	}
	if err := os.Rename(a.profileDir(oldName), a.profileDir(newName)); err != nil {
		return err
	}
	if a.activeProfile() == oldName {
		a.writeActiveProfile(newName)
	}
	a.logf("[profiles] renamed %q to %q", oldName, newName)
	return nil
}

// DuplicateProfileJSON copies a stored profile under a new name.
func (a *App) DuplicateProfileJSON(srcName string, dstName string) (err error) {
	defer recoverError(&err)
	dstName = strings.TrimSpace(dstName)
	if err := validateProfileName(dstName); err != nil {
		return err
	}
	if _, statErr := os.Stat(a.profileDir(srcName)); statErr != nil {
		return fmt.Errorf("profile %q does not exist", srcName)
	}
	if _, statErr := os.Stat(a.profileDir(dstName)); statErr == nil {
		return fmt.Errorf("profile %q already exists", dstName)
	}
	if err := snapshotDirInto(a.profileDir(srcName), a.profileDir(dstName)); err != nil {
		return err
	}
	a.logf("[profiles] duplicated %q as %q", srcName, dstName)
	return nil
}

// SwitchProfileJSON stops the proxy, snapshots the outgoing profile, replaces
// the live configuration and reloads the engine.
func (a *App) SwitchProfileJSON(name string) (err error) {
	defer recoverError(&err)
	name = strings.TrimSpace(name)
	if _, statErr := os.Stat(a.profileDir(name)); statErr != nil {
		return fmt.Errorf("profile %q does not exist", name)
	}

	a.opMu.Lock()
	rm, srv, err := a.engineLocked()
	if err != nil {
		a.opMu.Unlock()
		return err
	}
	previous := a.activeProfile()
	if previous == name {
		a.opMu.Unlock()
		return fmt.Errorf("profile %q is already active", name)
	}
	a.logf("[profiles] switching to %q", name)

	if srv.IsRunning() {
		if stopErr := a.stopProxyLocked(); stopErr != nil {
			a.opMu.Unlock()
			return fmt.Errorf("cannot switch profiles: stopping the proxy failed: %w", stopErr)
		}
	}
	if previous != "" {
		if snapErr := a.snapshotLiveInto(a.profileDir(previous)); snapErr != nil {
			a.logf("[profiles] warning: snapshot of %q failed: %s", previous, snapErr.Error())
		}
	}
	for _, pair := range []struct{ src, dst string }{
		{filepath.Join(a.profileDir(name), "settings.json"), a.settingsPath},
		{filepath.Join(a.profileDir(name), "rules.json"), a.rulesPath},
	} {
		if copyErr := copyProfileFile(pair.src, pair.dst); copyErr != nil {
			a.opMu.Unlock()
			return fmt.Errorf("apply profile file %s: %w", pair.src, copyErr)
		}
	}
	if loadErr := rm.LoadConfig(); loadErr != nil {
		a.opMu.Unlock()
		return fmt.Errorf("reload configuration: %w", loadErr)
	}
	if sanErr := a.sanitizeConfigLocked(rm); sanErr != nil {
		a.opMu.Unlock()
		return fmt.Errorf("sanitize configuration: %w", sanErr)
	}
	a.applyIPPoolsLocked(rm, srv)
	if port := rm.GetListenPort(); isNumericPort(port) {
		_ = srv.SetListenAddr("127.0.0.1:" + strings.TrimSpace(port))
	}
	a.writeActiveProfile(name)
	a.opMu.Unlock()

	a.logf("[profiles] switched to %q (proxy stopped, start it when ready)", name)
	a.pushState()
	return nil
}

// ExportProfileJSON renders a profile as a portable bundle (pretty JSON).
func (a *App) ExportProfileJSON(name string) (out string, err error) {
	defer recoverError(&err)
	dir := a.profileDir(name)
	if _, statErr := os.Stat(dir); statErr != nil {
		return "", fmt.Errorf("profile %q does not exist", name)
	}
	settings, err := os.ReadFile(filepath.Join(dir, "settings.json"))
	if err != nil {
		return "", err
	}
	rules, err := os.ReadFile(filepath.Join(dir, "rules.json"))
	if err != nil {
		return "", err
	}
	bundle := profileBundle{
		Format:     profileBundleFormat,
		Version:    profileBundleVersion,
		Name:       name,
		ExportedAt: time.Now().Format(time.RFC3339),
		Settings:   json.RawMessage(settings),
		Rules:      json.RawMessage(rules),
	}
	text, err := marshalJSONIndent(bundle)
	if err != nil {
		return "", err
	}
	a.logf("[profiles] exported %q", name)
	return text, nil
}

// ImportProfileJSON validates a bundle through the real load pipeline and
// stores it as a new profile.
func (a *App) ImportProfileJSON(bundleJSON string) (out string, err error) {
	defer recoverError(&err)
	result := profileImportResult{Warnings: []string{}, Errors: []string{}}
	var bundle profileBundle
	if err := json.Unmarshal([]byte(bundleJSON), &bundle); err != nil {
		return "", fmt.Errorf("not a valid profile bundle: %w", err)
	}
	if bundle.Format != profileBundleFormat {
		return "", fmt.Errorf("not a SniShaper profile bundle (format %q)", bundle.Format)
	}
	name := strings.TrimSpace(bundle.Name)
	if err := validateProfileName(name); err != nil {
		return "", err
	}
	if len(bundle.Settings) == 0 || len(bundle.Rules) == 0 {
		return "", fmt.Errorf("bundle is missing settings or rules")
	}
	if _, statErr := os.Stat(a.profileDir(name)); statErr == nil {
		return "", fmt.Errorf("profile %q already exists — rename it first", name)
	}
	result.Name = name

	tmp, err := os.MkdirTemp("", "snishaper-profile-import-")
	if err != nil {
		return "", err
	}
	defer os.RemoveAll(tmp)
	tmpSettings := filepath.Join(tmp, "settings.json")
	tmpRules := filepath.Join(tmp, "rules.json")
	if err := os.WriteFile(tmpSettings, bundle.Settings, 0o644); err != nil {
		return "", err
	}
	if err := os.WriteFile(tmpRules, bundle.Rules, 0o644); err != nil {
		return "", err
	}
	rm := proxy.NewRuleManager(tmpSettings, tmpRules, proxy.WithLogger(corelog.Func(a.logf)))
	report, loadErr := rm.LoadConfigWithMigration()
	if loadErr != nil {
		result.Errors = append(result.Errors, loadErr.Error())
		return "", fmt.Errorf("invalid profile: %s", strings.Join(result.Errors, "; "))
	}
	result.Warnings = append(result.Warnings, report.Warnings...)
	result.Errors = append(result.Errors, report.Errors...)
	if len(result.Errors) > 0 {
		return "", fmt.Errorf("invalid profile: %s", strings.Join(result.Errors, "; "))
	}
	if err := snapshotDirInto(tmp, a.profileDir(name)); err != nil {
		return "", err
	}
	result.Valid = true
	a.logf("[profiles] imported %q (%d warning(s))", name, len(result.Warnings))
	return marshalJSON(result), nil
}
