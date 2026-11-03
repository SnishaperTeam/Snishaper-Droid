package mobile

import (
	"fmt"
	"strings"

	"github.com/SnishaperTeam/snishaper-core/proxy"
)

// GetActiveConnectionsJSON returns the live connection table.
func (a *App) GetActiveConnectionsJSON() (out string) {
	defer recoverString(&out)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	_, srv, err := a.engineLocked()
	if err != nil {
		return "[]"
	}
	conns := srv.ActiveConnections()
	if conns == nil {
		conns = []proxy.ConnInfo{}
	}
	return marshalJSON(conns)
}

// ExplainMatchJSON runs the real matching pipeline for host without dialing.
func (a *App) ExplainMatchJSON(host string, processName string) (out string, err error) {
	defer recoverError(&err)
	a.opMu.Lock()
	defer a.opMu.Unlock()
	rm, srv, err := a.engineLocked()
	if err != nil {
		return "", err
	}
	host = strings.TrimSpace(host)
	if host == "" {
		return "", fmt.Errorf("host is empty")
	}
	return marshalJSON(rm.ExplainMatch(host, srv.GetMode(), strings.TrimSpace(processName))), nil
}

// GetLastMigrationReportJSON returns the report produced while loading the
// configuration at startup ({} when nothing was migrated).
func (a *App) GetLastMigrationReportJSON() string {
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.migrationJSON == "" {
		return "{}"
	}
	return a.migrationJSON
}

func (a *App) setMigrationReport(report proxy.MigrationReport) {
	a.mu.Lock()
	a.migrationJSON = marshalJSON(report)
	a.mu.Unlock()
}
