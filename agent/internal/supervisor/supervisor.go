package supervisor

import (
	"errors"
	"fmt"
	"os"
	"os/exec"
	"sort"
	"sync"
	"time"

	"github.com/Wyun09/Ax/agent/internal/config"
)

var ErrNotFound = errors.New("service not found")

type Snapshot struct {
	ID               string `json:"id"`
	Name             string `json:"name"`
	Status           string `json:"status"`
	PID              int    `json:"pid"`
	UptimeSeconds    int64  `json:"uptime_seconds"`
	LastError        string `json:"last_error,omitempty"`
	RestartPolicy    string `json:"restart_policy"`
	RestartCount     int    `json:"restart_count"`
	RestartInSeconds int64  `json:"restart_in_seconds,omitempty"`
}

type Service struct {
	profile config.Service
	logs    *Ring

	mu            sync.Mutex
	cmd           *exec.Cmd
	startedAt     time.Time
	done          chan struct{}
	lastError     string
	stopRequested bool
	restartCount  int
	restartAt     time.Time
	restartCancel chan struct{}
}

type Manager struct {
	services map[string]*Service
}

func NewManager(profiles []config.Service) (*Manager, error) {
	m := &Manager{services: make(map[string]*Service, len(profiles))}
	for _, profile := range profiles {
		if _, exists := m.services[profile.ID]; exists {
			return nil, fmt.Errorf("duplicate service %q", profile.ID)
		}
		m.services[profile.ID] = &Service{profile: profile, logs: NewRing(500)}
	}
	return m, nil
}

func (m *Manager) List() []Snapshot {
	out := make([]Snapshot, 0, len(m.services))
	for _, service := range m.services {
		out = append(out, service.Snapshot())
	}
	sort.Slice(out, func(i, j int) bool { return out[i].ID < out[j].ID })
	return out
}

func (m *Manager) Start(id string) error {
	service, ok := m.services[id]
	if !ok {
		return ErrNotFound
	}
	return service.Start()
}

func (m *Manager) Stop(id string) error {
	service, ok := m.services[id]
	if !ok {
		return ErrNotFound
	}
	return service.Stop()
}

func (m *Manager) Logs(id string, tail int) ([]string, error) {
	service, ok := m.services[id]
	if !ok {
		return nil, ErrNotFound
	}
	return service.logs.Tail(tail), nil
}

func (m *Manager) StopAll() {
	for _, service := range m.services {
		_ = service.Stop()
	}
}

func (s *Service) Start() error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.cmd != nil {
		return nil
	}

	s.cancelRestartLocked()
	s.stopRequested = false
	s.restartCount = 0
	return s.startLocked()
}

func (s *Service) startLocked() error {
	cmd := exec.Command(s.profile.Command[0], s.profile.Command[1:]...)
	cmd.Dir = s.profile.WorkDir
	cmd.Env = os.Environ()
	for key, value := range s.profile.Env {
		cmd.Env = append(cmd.Env, key+"="+value)
	}
	cmd.Stdout = s.logs
	cmd.Stderr = s.logs

	if err := cmd.Start(); err != nil {
		s.lastError = err.Error()
		return err
	}

	s.cmd = cmd
	s.startedAt = time.Now()
	s.lastError = ""
	s.done = make(chan struct{})
	done := s.done
	go s.wait(cmd, done)
	return nil
}

func (s *Service) wait(cmd *exec.Cmd, done chan struct{}) {
	err := cmd.Wait()

	s.mu.Lock()
	if s.cmd == cmd {
		runtime := time.Since(s.startedAt)
		s.cmd = nil
		if err != nil {
			s.lastError = err.Error()
		}
		if runtime >= time.Minute {
			s.restartCount = 0
		}
		if !s.stopRequested && s.shouldRestart(err) {
			s.scheduleRestartLocked()
		}
	}
	s.mu.Unlock()
	close(done)
}

func (s *Service) shouldRestart(err error) bool {
	switch s.profile.Restart {
	case "always":
		return true
	case "on-failure":
		return err != nil
	default:
		return false
	}
}

func (s *Service) scheduleRestartLocked() bool {
	limit := s.profile.RestartLimit
	if limit <= 0 {
		limit = 5
	}
	if s.restartCount >= limit {
		if s.lastError != "" {
			s.lastError += "; "
		}
		s.lastError += fmt.Sprintf("restart limit reached (%d)", limit)
		return false
	}

	s.restartCount++
	delay := restartDelay(s.profile.RestartBackoffSeconds, s.restartCount)
	cancel := make(chan struct{})
	s.restartCancel = cancel
	s.restartAt = time.Now().Add(delay)
	go s.restartAfter(delay, cancel)
	return true
}

func (s *Service) restartAfter(delay time.Duration, cancel chan struct{}) {
	timer := time.NewTimer(delay)
	defer timer.Stop()

	select {
	case <-timer.C:
	case <-cancel:
		return
	}

	s.mu.Lock()
	defer s.mu.Unlock()
	if s.restartCancel != cancel || s.stopRequested || s.cmd != nil {
		return
	}
	s.restartCancel = nil
	s.restartAt = time.Time{}
	if err := s.startLocked(); err != nil && !s.stopRequested && s.shouldRestart(err) {
		s.scheduleRestartLocked()
	}
}

func restartDelay(baseSeconds, attempt int) time.Duration {
	if baseSeconds <= 0 {
		baseSeconds = 2
	}
	delay := time.Duration(baseSeconds) * time.Second
	for i := 1; i < attempt; i++ {
		if delay >= 30*time.Second {
			return 30 * time.Second
		}
		delay *= 2
	}
	if delay > 30*time.Second {
		return 30 * time.Second
	}
	return delay
}

func (s *Service) cancelRestartLocked() {
	if s.restartCancel != nil {
		close(s.restartCancel)
		s.restartCancel = nil
	}
	s.restartAt = time.Time{}
}

func (s *Service) Stop() error {
	s.mu.Lock()
	s.stopRequested = true
	s.cancelRestartLocked()
	cmd := s.cmd
	done := s.done
	s.mu.Unlock()
	if cmd == nil || cmd.Process == nil {
		return nil
	}

	_ = cmd.Process.Signal(os.Interrupt)
	if done != nil {
		select {
		case <-done:
			return nil
		case <-time.After(3 * time.Second):
		}
	}
	return cmd.Process.Kill()
}

func (s *Service) Snapshot() Snapshot {
	s.mu.Lock()
	defer s.mu.Unlock()

	policy := s.profile.Restart
	if policy == "" {
		policy = "never"
	}
	snapshot := Snapshot{
		ID:            s.profile.ID,
		Name:          s.profile.Name,
		Status:        "stopped",
		LastError:     s.lastError,
		RestartPolicy: policy,
		RestartCount:  s.restartCount,
	}
	if snapshot.Name == "" {
		snapshot.Name = snapshot.ID
	}
	if s.cmd != nil && s.cmd.Process != nil {
		snapshot.Status = "running"
		snapshot.PID = s.cmd.Process.Pid
		snapshot.UptimeSeconds = int64(time.Since(s.startedAt).Seconds())
	} else if s.restartCancel != nil && !s.restartAt.IsZero() {
		snapshot.Status = "restarting"
		remaining := time.Until(s.restartAt)
		if remaining > 0 {
			snapshot.RestartInSeconds = int64((remaining + time.Second - 1) / time.Second)
		}
	}
	return snapshot
}
