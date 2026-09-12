# Ax HTTP protocol (v1)

The bootstrap protocol is intentionally small and local-first. Default base URL:

```text
http://127.0.0.1:18766
```

## Health

`GET /v1/health`

```json
{"status":"ok","version":"0.1.0"}
```

## Services

`GET /v1/services`

```json
[
  {
    "id":"claude-proxy",
    "name":"Claude Code Proxy",
    "status":"running",
    "pid":12345,
    "uptime_seconds":42,
    "restart_policy":"on-failure",
    "restart_count":0
  }
]
```

A service may report `status: "restarting"` while waiting for its backoff timer. In that state, `restart_in_seconds` reports the approximate delay before the next launch attempt.

## Start

`POST /v1/services/{id}/start`

The `{id}` must already exist in the daemon's local configuration. There is no endpoint for submitting a shell command. A manual start resets the current restart-attempt counter.

## Stop

`POST /v1/services/{id}/stop`

The supervisor sends an interrupt first and force-kills the process after a short grace period if it does not exit. A manual stop also cancels any pending automatic restart.

## Restart policy

Each local service profile may set:

- `restart`: `never`, `on-failure`, or `always`
- `restart_limit`: maximum automatic restart attempts, default `5` when restart is enabled
- `restart_backoff_seconds`: initial delay, default `2` seconds

Backoff doubles after each failed run and is capped at 30 seconds. If a process stays alive for at least one minute, its automatic restart counter resets.

## Logs

`GET /v1/services/{id}/logs?tail=100`

`tail` is clamped by the server to a maximum of 500 lines.

```json
{"id":"claude-proxy","lines":["listening on :18765","request completed"]}
```

## Remote mode

The current v1 API has no authentication. Keep it on loopback. `allow_remote=true` exists only as an explicit escape hatch for development; authenticated pairing/TLS is required before remote mode is considered production-ready.
