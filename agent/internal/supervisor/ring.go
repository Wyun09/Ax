package supervisor

import (
	"strings"
	"sync"
)

type Ring struct {
	mu      sync.Mutex
	max     int
	lines   []string
	partial string
}

func NewRing(max int) *Ring {
	if max < 1 {
		max = 1
	}
	return &Ring{max: max}
}

func (r *Ring) Write(p []byte) (int, error) {
	r.mu.Lock()
	defer r.mu.Unlock()

	text := r.partial + string(p)
	parts := strings.Split(text, "\n")
	r.partial = parts[len(parts)-1]
	for _, line := range parts[:len(parts)-1] {
		r.appendLocked(strings.TrimSuffix(line, "\r"))
	}
	return len(p), nil
}

func (r *Ring) appendLocked(line string) {
	r.lines = append(r.lines, line)
	if extra := len(r.lines) - r.max; extra > 0 {
		copy(r.lines, r.lines[extra:])
		r.lines = r.lines[:r.max]
	}
}

func (r *Ring) Tail(n int) []string {
	r.mu.Lock()
	defer r.mu.Unlock()

	if n <= 0 || n > len(r.lines) {
		n = len(r.lines)
	}
	start := len(r.lines) - n
	out := append([]string(nil), r.lines[start:]...)
	if r.partial != "" && len(out) < n {
		out = append(out, r.partial)
	}
	return out
}
