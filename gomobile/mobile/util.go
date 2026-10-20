package mobile

import (
	"encoding/json"
	"fmt"
	"strconv"
	"strings"
)

// ringLogCapacity is the number of log lines retained for GetRecentLogs.
const ringLogCapacity = 5000

// marshalJSON marshals v and never panics; callbacks crossing the language
// boundary must not panic (gobind treats a panic as a fatal error).
func marshalJSON(v any) string {
	defer func() { _ = recover() }()
	data, err := json.Marshal(v)
	if err != nil {
		return "{}"
	}
	return string(data)
}

// marshalJSONIndent is marshalJSON with two-space indentation.
func marshalJSONIndent(v any) (string, error) {
	defer func() { _ = recover() }()
	data, err := json.MarshalIndent(v, "", "  ")
	if err != nil {
		return "", err
	}
	return string(data), nil
}

// recoverError converts a recovered panic into a returned error.
func recoverError(err *error) {
	if r := recover(); r != nil {
		*err = fmt.Errorf("snishaper: panic: %v", r)
	}
}

// recoverString converts a recovered panic into a returned string.
func recoverString(out *string) {
	if r := recover(); r != nil {
		*out = ""
	}
}

func boolPtr(v bool) *bool { return &v }

func int32ToInt(v int32) int { return int(v) }

func clampInt32(v, min, max int32) int32 {
	if v < min {
		return min
	}
	if v > max {
		return max
	}
	return v
}

func isNumericPort(value string) bool {
	value = strings.TrimSpace(value)
	if value == "" {
		return false
	}
	port, err := strconv.Atoi(value)
	return err == nil && port > 0 && port <= 65535
}
