# Termux setup

Ax is designed to run its Go agent inside Termux while the Android app talks to it over loopback.

## Install

```bash
pkg install -y curl
curl -fsSL https://raw.githubusercontent.com/Wyun09/Ax/main/scripts/install-termux.sh | bash
```

The installer clones Ax into `~/.local/share/ax`, builds `ax-agent`, creates `~/.config/ax/ax.json` on first install, and installs `ax-start` / `ax-stop` into `~/.local/bin`.

## Start the agent

```bash
~/.local/bin/ax-start
```

Verify it:

```bash
curl http://127.0.0.1:18766/v1/health
curl http://127.0.0.1:18766/v1/services
```

## Claude Code Proxy

The default config already includes:

```json
{
  "id": "claude-proxy",
  "name": "Claude Code Proxy",
  "command": ["claude-code-proxy", "serve", "--port", "18765"]
}
```

Install and configure `claude-code-proxy` in Termux first. Ax does not install third-party agent tools automatically.

## Files

- Config: `~/.config/ax/ax.json`
- Agent log: `~/.local/state/ax/agent.log`
- Agent PID: `~/.local/state/ax/agent.pid`
- Source checkout: `~/.local/share/ax`

Keep the HTTP listener on `127.0.0.1` until authenticated remote pairing exists.
