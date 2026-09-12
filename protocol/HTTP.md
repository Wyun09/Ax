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
    "uptime_seconds":42
  }
]
```

## Start

`POST /v1/services/{id}/start`

The `{id}` must already exist in the daemon's local configuration. There is no endpoint for submitting a shell command.

## Stop

`POST /v1/services/{id}/stop`

The supervisor sends an interrupt first and force-kills the process after a short grace period if it does not exit.

## Logs

`GET /v1/services/{id}/logs?tail=100`

`tail` is clamped by the server to a maximum of 500 lines.

```json
{"id":"claude-proxy","lines":["listening on :18765","request completed"]}
```

## Remote mode

The current v1 API has no authentication. Keep it on loopback. `allow_remote=true` exists only as an explicit escape hatch for development; authenticated pairing/TLS is required before remote mode is considered production-ready.
