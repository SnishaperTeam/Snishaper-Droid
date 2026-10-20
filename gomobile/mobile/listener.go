package mobile

import (
	"strings"
	"sync"
)

// Listener receives asynchronous events from the Go engine. Implement it in
// Kotlin and keep a strong reference to the implementation for the whole
// application lifetime (the bindings hold only the Go proxy of the interface).
type Listener interface {
	// OnLog is a formatted single log line. It is high frequency; the Kotlin
	// side should only enqueue it and must not block.
	OnLog(line string)
	// OnRouteEvent reports a routing decision. mode is one of
	// "direct", "transparent", "tls-rf" on Android.
	OnRouteEvent(domain string, mode string)
	// OnStateChanged carries the JSON snapshot documented by GetStatusJSON.
	OnStateChanged(stateJSON string)
	// OnEvolutionProgress carries an evolution.TestTask JSON snapshot.
	OnEvolutionProgress(taskJSON string)
}

// ringLog is a fixed-capacity in-memory log buffer.
type ringLog struct {
	mu    sync.Mutex
	lines []string
	start int
	size  int
}

func newRingLog(capacity int) *ringLog {
	if capacity <= 0 {
		capacity = ringLogCapacity
	}
	return &ringLog{lines: make([]string, capacity)}
}

func (r *ringLog) add(line string) {
	r.mu.Lock()
	defer r.mu.Unlock()
	if len(r.lines) == 0 {
		return
	}
	if r.size < len(r.lines) {
		r.lines[(r.start+r.size)%len(r.lines)] = line
		r.size++
		return
	}
	r.lines[r.start] = line
	r.start = (r.start + 1) % len(r.lines)
}

func (r *ringLog) recent(max int) string {
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.size == 0 {
		return ""
	}
	if max <= 0 || max > r.size {
		max = r.size
	}
	out := make([]string, 0, max)
	for i := r.size - max; i < r.size; i++ {
		out = append(out, r.lines[(r.start+i)%len(r.lines)])
	}
	return strings.Join(out, "\n")
}

func (r *ringLog) clear() {
	r.mu.Lock()
	defer r.mu.Unlock()
	for i := range r.lines {
		r.lines[i] = ""
	}
	r.start = 0
	r.size = 0
}
