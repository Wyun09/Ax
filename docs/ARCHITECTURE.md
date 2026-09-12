# Architecture

Ax has two primary runtime pieces.

## Android client

The Android app is the user-facing control plane. It polls the agent for service snapshots and sends explicit start/stop actions. It contains no shell execution engine.

## Agent daemon

The Go daemon owns process lifecycle. Every executable command comes from a local JSON configuration file. HTTP callers can reference a service ID but cannot provide a command string.

```text
┌─────────────────────┐       HTTP/JSON        ┌─────────────────────┐
│ Android / Compose   │ ─────────────────────▶ │ Ax Go agent         │
│ status + controls   │ ◀───────────────────── │ process supervisor  │
└─────────────────────┘                        └──────────┬──────────┘
                                                        │
                                              predefined commands
                                                        │
                                      ┌─────────────────┼──────────────┐
                                      ▼                 ▼              ▼
                                 Claude proxy       Codex CLI      other daemon
```

## Security boundary

The important boundary is between a **service ID** and a **command**. Commands live on the controlled device, outside the remote request. This prevents the API from becoming a generic unauthenticated shell.

The bootstrap HTTP transport is loopback-only. A future remote transport should add device pairing, rotating credentials and TLS before allowing LAN/WAN exposure.
