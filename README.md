# Ax

**Agent eXecution for Android + Termux.**

Ax is a lightweight control plane for running and observing local AI coding agents and background services from Android. The first target is Termux: start or stop predefined services, inspect PID/uptime, and read recent logs from a compact native dashboard.

## Why Ax

AI coding tools increasingly behave like local services: proxies, MCP servers, agent runtimes, build daemons and automation workers. Ax gives those processes one small, explicit control surface instead of turning the phone into a general-purpose remote shell.

## MVP

- Native Android dashboard (Kotlin + Jetpack Compose)
- Go supervisor daemon designed for Termux/Linux
- One-command Termux installer and lifecycle scripts
- Start/stop only **preconfigured** service profiles
- Claude Code Proxy profile on port `18765`
- PID, state and uptime reporting
- Bounded in-memory log tail
- Loopback-only HTTP/JSON API by default
- GitHub Actions for Go checks and debug APK builds

## Repository layout

```text
android/      Native Android client
agent/        Go process supervisor and HTTP API
scripts/      Termux install/start/stop helpers
protocol/     API contract and examples
docs/         Architecture, Termux guide and roadmap
```

## Quick start on Termux

```bash
pkg install -y curl
curl -fsSL https://raw.githubusercontent.com/Wyun09/Ax/main/scripts/install-termux.sh | bash
~/.local/bin/ax-start
```

Then verify:

```bash
curl http://127.0.0.1:18766/v1/health
curl http://127.0.0.1:18766/v1/services
```

The default config lives at `~/.config/ax/ax.json`. It includes a `claude-proxy` service that runs:

```bash
claude-code-proxy serve --port 18765
```

See [`docs/TERMUX.md`](docs/TERMUX.md) for details.

## Run the agent manually

```bash
cd agent
cp ax.example.json ax.json
# edit ax.json to match commands installed on this device
go run ./cmd/ax-agent
```

The default endpoint is `http://127.0.0.1:18766`.

## Android

Open `android/` as a Gradle project in Android Studio, or build the debug APK with:

```bash
cd android
./gradlew assembleDebug
```

`android/gradlew` bootstraps Gradle 8.9, the minimum Gradle line required by Android Gradle Plugin 8.7. The Android client talks to `127.0.0.1:18766`, suitable for an Ax agent running in Termux on the same device.

GitHub Actions uploads `app-debug.apk` as the `ax-debug-apk` artifact on Android changes.

## Security

Ax does **not** provide an arbitrary command endpoint. Commands must exist in the local agent configuration before the HTTP API can start them. The agent binds to loopback by default and rejects non-loopback listeners unless `allow_remote` is explicitly enabled.

Remote control is intentionally deferred until authenticated pairing and encrypted transport are implemented.

## Roadmap

See [`docs/ROADMAP.md`](docs/ROADMAP.md).

## License

MIT
