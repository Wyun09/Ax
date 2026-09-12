package httpapi

import (
	"encoding/json"
	"errors"
	"net/http"
	"strconv"
	"strings"

	"github.com/Wyun09/Ax/agent/internal/supervisor"
)

type api struct {
	manager *supervisor.Manager
	version string
}

func New(manager *supervisor.Manager, version string) http.Handler {
	a := &api{manager: manager, version: version}
	mux := http.NewServeMux()
	mux.HandleFunc("GET /v1/health", a.health)
	mux.HandleFunc("GET /v1/services", a.services)
	mux.HandleFunc("/v1/services/", a.serviceAction)
	return mux
}

func (a *api) health(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{
		"status":  "ok",
		"version": a.version,
	})
}

func (a *api) services(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, a.manager.List())
}

func (a *api) serviceAction(w http.ResponseWriter, r *http.Request) {
	path := strings.TrimPrefix(r.URL.Path, "/v1/services/")
	parts := strings.Split(strings.Trim(path, "/"), "/")
	if len(parts) != 2 || parts[0] == "" {
		writeError(w, http.StatusNotFound, "not found")
		return
	}
	id, action := parts[0], parts[1]

	switch {
	case r.Method == http.MethodPost && action == "start":
		if err := a.manager.Start(id); err != nil {
			a.managerError(w, err)
			return
		}
		writeJSON(w, http.StatusAccepted, map[string]string{"status": "starting"})
	case r.Method == http.MethodPost && action == "stop":
		if err := a.manager.Stop(id); err != nil {
			a.managerError(w, err)
			return
		}
		writeJSON(w, http.StatusAccepted, map[string]string{"status": "stopping"})
	case r.Method == http.MethodGet && action == "logs":
		tail := 100
		if value := r.URL.Query().Get("tail"); value != "" {
			if parsed, err := strconv.Atoi(value); err == nil && parsed >= 1 && parsed <= 500 {
				tail = parsed
			}
		}
		lines, err := a.manager.Logs(id, tail)
		if err != nil {
			a.managerError(w, err)
			return
		}
		writeJSON(w, http.StatusOK, map[string]any{"id": id, "lines": lines})
	default:
		writeError(w, http.StatusNotFound, "not found")
	}
}

func (a *api) managerError(w http.ResponseWriter, err error) {
	if errors.Is(err, supervisor.ErrNotFound) {
		writeError(w, http.StatusNotFound, err.Error())
		return
	}
	writeError(w, http.StatusConflict, err.Error())
}

func writeError(w http.ResponseWriter, status int, message string) {
	writeJSON(w, status, map[string]string{"error": message})
}

func writeJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}
