package config

import (
	"os"
	"path/filepath"
	"testing"
)

func TestLoadRestartDefaults(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "ax.json")
	data := []byte(`{
  "services": [
    {"id":"worker","command":["sh","-c","exit 1"],"restart":"on-failure"}
  ]
}`)
	if err := os.WriteFile(path, data, 0o600); err != nil {
		t.Fatal(err)
	}

	cfg, err := Load(path)
	if err != nil {
		t.Fatal(err)
	}
	service := cfg.Services[0]
	if service.Restart != "on-failure" {
		t.Fatalf("Restart = %q", service.Restart)
	}
	if service.RestartLimit != 5 {
		t.Fatalf("RestartLimit = %d, want 5", service.RestartLimit)
	}
	if service.RestartBackoffSeconds != 2 {
		t.Fatalf("RestartBackoffSeconds = %d, want 2", service.RestartBackoffSeconds)
	}
}

func TestLoadRejectsInvalidRestartPolicy(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "ax.json")
	data := []byte(`{"services":[{"id":"worker","command":["true"],"restart":"forever"}]}`)
	if err := os.WriteFile(path, data, 0o600); err != nil {
		t.Fatal(err)
	}
	if _, err := Load(path); err == nil {
		t.Fatal("Load succeeded, want restart policy validation error")
	}
}
