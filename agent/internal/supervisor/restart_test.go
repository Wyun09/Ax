package supervisor

import (
	"errors"
	"testing"
	"time"

	"github.com/Wyun09/Ax/agent/internal/config"
)

func TestRestartDelay(t *testing.T) {
	cases := []struct {
		attempt int
		want    time.Duration
	}{
		{1, 2 * time.Second},
		{2, 4 * time.Second},
		{3, 8 * time.Second},
		{4, 16 * time.Second},
		{5, 30 * time.Second},
		{8, 30 * time.Second},
	}
	for _, tc := range cases {
		if got := restartDelay(2, tc.attempt); got != tc.want {
			t.Fatalf("restartDelay(2, %d) = %s, want %s", tc.attempt, got, tc.want)
		}
	}
}

func TestShouldRestart(t *testing.T) {
	failure := errors.New("boom")
	cases := []struct {
		policy string
		err    error
		want   bool
	}{
		{"never", failure, false},
		{"on-failure", nil, false},
		{"on-failure", failure, true},
		{"always", nil, true},
		{"always", failure, true},
	}
	for _, tc := range cases {
		s := &Service{profile: config.Service{Restart: tc.policy}}
		if got := s.shouldRestart(tc.err); got != tc.want {
			t.Fatalf("policy=%q err=%v: got %v, want %v", tc.policy, tc.err, got, tc.want)
		}
	}
}

func TestStopCancelsPendingRestart(t *testing.T) {
	s := &Service{
		profile: config.Service{Restart: "always", RestartLimit: 2, RestartBackoffSeconds: 1},
		logs:    NewRing(10),
	}
	s.mu.Lock()
	if !s.scheduleRestartLocked() {
		t.Fatal("scheduleRestartLocked returned false")
	}
	s.mu.Unlock()

	if err := s.Stop(); err != nil {
		t.Fatal(err)
	}
	snapshot := s.Snapshot()
	if snapshot.Status != "stopped" || snapshot.RestartInSeconds != 0 {
		t.Fatalf("snapshot after Stop = %#v", snapshot)
	}
}
