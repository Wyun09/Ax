package config

import (
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"os"
	"regexp"
)

var serviceIDPattern = regexp.MustCompile(`^[A-Za-z0-9._-]+$`)

type Config struct {
	Listen      string    `json:"listen"`
	AllowRemote bool      `json:"allow_remote"`
	Services    []Service `json:"services"`
}

type Service struct {
	ID      string            `json:"id"`
	Name    string            `json:"name"`
	Command []string          `json:"command"`
	WorkDir string            `json:"workdir,omitempty"`
	Env     map[string]string `json:"env,omitempty"`
}

func Load(path string) (Config, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return Config{}, err
	}

	var cfg Config
	if err := json.Unmarshal(data, &cfg); err != nil {
		return Config{}, fmt.Errorf("decode JSON: %w", err)
	}
	if cfg.Listen == "" {
		cfg.Listen = "127.0.0.1:18766"
	}
	if err := validate(cfg); err != nil {
		return Config{}, err
	}
	return cfg, nil
}

func validate(cfg Config) error {
	host, _, err := net.SplitHostPort(cfg.Listen)
	if err != nil {
		return fmt.Errorf("invalid listen address %q: %w", cfg.Listen, err)
	}
	if !cfg.AllowRemote && !isLoopbackHost(host) {
		return fmt.Errorf("refusing non-loopback listener %q while allow_remote=false", cfg.Listen)
	}

	seen := make(map[string]struct{}, len(cfg.Services))
	for i, service := range cfg.Services {
		if service.ID == "" || !serviceIDPattern.MatchString(service.ID) {
			return fmt.Errorf("services[%d]: invalid id %q", i, service.ID)
		}
		if len(service.Command) == 0 || service.Command[0] == "" {
			return fmt.Errorf("services[%d] %q: command is required", i, service.ID)
		}
		if _, ok := seen[service.ID]; ok {
			return fmt.Errorf("duplicate service id %q", service.ID)
		}
		seen[service.ID] = struct{}{}
	}
	return nil
}

func isLoopbackHost(host string) bool {
	if host == "localhost" || host == "" {
		return true
	}
	ip := net.ParseIP(host)
	return ip != nil && ip.IsLoopback()
}

var ErrNotFound = errors.New("service not found")
