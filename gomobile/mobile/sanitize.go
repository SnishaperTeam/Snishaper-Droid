package mobile

import (
	"fmt"
	"strings"

	"github.com/SnishaperTeam/snishaper-core/proxy"
)

// Rule validation for the Android port.
//
// The full desktop engine is available: the CA manager generates the local
// root and the MITM / QUIC / migration code paths are reachable, so rules pass
// through untouched. This file only validates user input on the CRUD paths:
// mode whitelist with alias normalization plus domain de-duplication.

// sanitizeSiteGroupForWrite validates a user supplied rule. Allowed modes
// mirror the desktop RuleForm: mitm / tls-rf / quic / transparent / migration.
func sanitizeSiteGroupForWrite(sg *proxy.SiteGroup) error {
	if sg == nil {
		return fmt.Errorf("site group is nil")
	}
	mode := strings.ToLower(strings.TrimSpace(sg.Mode))
	switch mode {
	case "mitm", "quic", "migration", "transparent":
		sg.Mode = mode
	case "tls-rf", "tls_rf":
		sg.Mode = "tls-rf"
	default:
		return fmt.Errorf("unsupported rule mode %q: allowed modes are mitm, tls-rf, quic, transparent, migration", sg.Mode)
	}

	domains := make([]string, 0, len(sg.Domains))
	seen := make(map[string]struct{}, len(sg.Domains))
	for _, domain := range sg.Domains {
		domain = strings.TrimSpace(domain)
		if domain == "" {
			continue
		}
		if _, ok := seen[domain]; ok {
			continue
		}
		seen[domain] = struct{}{}
		domains = append(domains, domain)
	}
	if len(domains) == 0 {
		return fmt.Errorf("site group must contain at least one domain")
	}
	sg.Domains = domains
	return nil
}
