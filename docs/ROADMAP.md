# Roadmap

## M0 — Bootstrap

- [x] Repository structure
- [x] Go supervisor daemon
- [x] Local HTTP/JSON protocol
- [x] Native Compose dashboard shell
- [x] Basic log tail
- [x] Agent CI
- [x] Android debug APK CI

## M1 — Termux controller

- [x] Agent install script for Termux
- [x] Claude Code Proxy starter profile
- [x] Android endpoint/settings screen
- [ ] Foreground notification for connected agent
- [x] Better process exit/restart policy
- [ ] Per-service environment editor
- [ ] Live log streaming (SSE or WebSocket)

## M2 — Agent runtime

- [ ] Health checks per service
- [x] Restart policies with backoff
- [ ] CPU/RAM metrics
- [ ] Persisted event history
- [ ] Import/export service profiles

## M3 — Remote nodes

- [ ] Pairing handshake
- [ ] Authenticated encrypted transport
- [ ] Multiple devices/nodes
- [ ] Desktop daemon support
- [ ] SSH transport option

## M4 — Product layer

- [ ] Agent templates (Claude Code, Codex, MCP servers)
- [ ] Task/command queue with explicit capabilities
- [ ] Plugin model
- [ ] Signed profile bundles
