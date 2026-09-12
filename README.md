# Ax

**Agent eXecution for Android + Termux.**

Ax is a lightweight control plane for running and observing local AI coding agents and background services from Android. The first target is Termux: start or stop predefined services, inspect PID/uptime, and read recent logs from a compact native dashboard.

## Why Ax

AI coding tools increasingly behave like local services: proxies, MCP servers, agent runtimes, build daemons and automation workers. Ax gives those processes one small, explicit control surface instead of turning the phone into a general-purpose remote shell.

## MVP

- Native Android dashboard (Kotlin + Jetpack Compose)
- Go supervisor daemon designed for Termux/Linux
- Start/stop only **preconfigured** service profiles
- PID, state and uptime reporting
- Bounded in-memory log tail
- Loopback-only HTTP/JSON API by default
- Clean protocol boundary for future desktop/remote nodes

## Repository layout

```text
android/      Native Android client
agent/        Go process supervisor and HTTP API
protocol/     API contract and examples
docs/         Architecture and roadmap
```

## Run the agent

```bash
cd agent
cp ax.example.json ax.json
# edit ax.json to match commands installed on this device
go run ./cmd/ax-agent
```

The default endpoint is `http://127.0.0.1:18766`.

```bash
curl http://127.0.0.1:18766/v1/health
curl http://127.0.0.1:18766/v1/services
curl -X POST http://127.0.0.1:18766/v1/services/demo/start
curl http://127.0.0.1:18766/v1/services/demo/logs?tail=50
curl -X POST http://127.0.0.1:18766/v1/services/demo/stop
```

## Android

Open `android/` as a Gradle project in Android Studio. The bootstrap client talks to `127.0.0.1:18766`, which is suitable for an Ax agent running in Termux on the same Android device.

The Android project intentionally starts small: one dashboard screen, no account system, no cloud dependency.

## Security

Ax does **not** provide an arbitrary command endpoint. Commands must exist in the local agent configuration before the HTTP API can start them. The agent binds to loopback by default and rejects non-loopback listeners unless `allow_remote` is explicitly enabled.

Remote control is intentionally deferred until authenticated pairing and encrypted transport are implemented.

## Roadmap

See [`docs/ROADMAP.md`](docs/ROADMAP.md).

## License

MIT
