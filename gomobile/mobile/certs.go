package mobile

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	"github.com/SnishaperTeam/snishaper-core/certmanager"
)

// CA management bridge. Mirrors the desktop app_api surface minus the
// desktop-only file-manager helpers (OpenCAFile / OpenCertDir): the WebView
// frontend exports the certificate itself instead of opening Explorer.

// certManagerLocked returns the CA manager. Caller holds opMu.
func (a *App) certManagerLocked() *certmanager.CertManager {
	a.mu.Lock()
	defer a.mu.Unlock()
	return a.certs
}

// InstallCA installs the local root CA into the Android system trust store.
// Requires root (Magisk / KernelSU); a missing root surfaces as an error the
// frontend renders verbatim.
func (a *App) InstallCA() (err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	certs := a.certManagerLocked()
	if certs == nil {
		return fmt.Errorf("engine is not started")
	}
	a.appendLog("[cert] InstallCA called")
	if err := certs.InstallCA(); err != nil {
		a.appendLog("[cert] InstallCA failed: " + err.Error())
		return err
	}
	a.appendLog("[cert] InstallCA succeeded")
	return nil
}

// RegenerateCert uninstalls the current CA, generates a fresh root and
// installs it. The MITM path reads the CA per connection, so the new material
// is live without a proxy restart.
func (a *App) RegenerateCert() (err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	certs := a.certManagerLocked()
	if certs == nil {
		return fmt.Errorf("engine is not started")
	}
	a.appendLog("[cert] RegenerateCert called")
	if err := certs.RegenerateCA(); err != nil {
		a.appendLog("[cert] RegenerateCert failed: " + err.Error())
		return err
	}
	a.appendLog("[cert] RegenerateCert succeeded")
	return nil
}

// UninstallCert removes a previously installed certificate by thumbprint or
// store token (InstalledCert.Token).
func (a *App) UninstallCert(token string) (err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	certs := a.certManagerLocked()
	if certs == nil {
		return fmt.Errorf("engine is not started")
	}
	a.appendLog("[cert] UninstallCert called: " + token)
	if err := certs.UninstallCertificate(strings.TrimSpace(token)); err != nil {
		a.appendLog("[cert] UninstallCert failed: " + err.Error())
		return err
	}
	a.appendLog("[cert] UninstallCert succeeded: " + token)
	return nil
}

// GetCAInstallStatusJSON returns the CAInstallStatus snapshot (capitalized
// keys Installed / Platform / CertPath / InstallHelp — the frozen frontend
// contract shared with the desktop build).
func (a *App) GetCAInstallStatusJSON() (out string) {
	defer recoverString(&out)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	certs := a.certManagerLocked()
	if certs == nil {
		return marshalJSON(certmanager.CAInstallStatus{})
	}
	return marshalJSON(certs.GetCAInstallStatus())
}

// GetInstalledCertsJSON lists the certificates this app owns in the trust store.
func (a *App) GetInstalledCertsJSON() (out string) {
	defer recoverString(&out)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	certs := a.certManagerLocked()
	if certs == nil {
		return "[]"
	}
	installed, err := certs.GetInstalledCertificates()
	if err != nil {
		a.appendLog("[cert] list installed certs failed: " + err.Error())
		return "[]"
	}
	return marshalJSON(nonNil(installed))
}

// ExportCert returns the CA certificate in PEM form, or "" when unavailable.
func (a *App) ExportCert() (out string) {
	defer recoverString(&out)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	certs := a.certManagerLocked()
	if certs == nil {
		return ""
	}
	data, err := certs.ExportCert()
	if err != nil {
		a.appendLog("[cert] export cert failed: " + err.Error())
		return ""
	}
	return string(data)
}

// GetCACertPEM returns the loaded CA certificate in PEM form ("" when absent).
func (a *App) GetCACertPEM() (out string) {
	defer recoverString(&out)
	certs := a.certManagerLocked()
	if certs == nil {
		return ""
	}
	return certs.GetCACertPEM()
}

// GetCACertPath returns the CA certificate file path.
func (a *App) GetCACertPath() (out string) {
	defer recoverString(&out)
	certs := a.certManagerLocked()
	if certs == nil {
		return ""
	}
	return certs.GetCACertPath()
}

// TestMigration probes a migration server end to end (session ticket against
// cloudflare.com:443) and returns the human-readable result.
func (a *App) TestMigration(server string) (out string, err error) {
	defer func() {
		if r := recover(); r != nil {
			out, err = "", fmt.Errorf("snishaper: panic: %v", r)
		}
	}()
	server = strings.TrimSpace(server)
	if server == "" {
		return "", fmt.Errorf("migration server URL is empty")
	}
	apiURL := fmt.Sprintf("%s?target=%s&ip=1.1.1.1", server, "cloudflare.com:443")
	client := &http.Client{Timeout: 15 * time.Second}
	req, reqErr := http.NewRequest(http.MethodGet, apiURL, nil)
	if reqErr != nil {
		return "", reqErr
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
	resp, doErr := client.Do(req)
	if doErr != nil {
		return "", doErr
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(io.LimitReader(resp.Body, 4096))
	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("API returned %d: %s", resp.StatusCode, string(body))
	}
	var result struct {
		Ticket string `json:"ticket"`
	}
	if parseErr := json.Unmarshal(body, &result); parseErr != nil {
		return "", fmt.Errorf("invalid response: %v", parseErr)
	}
	if result.Ticket == "" {
		return "", fmt.Errorf("no session ticket in response")
	}
	return fmt.Sprintf("Session ticket acquired from %s", server), nil
}
