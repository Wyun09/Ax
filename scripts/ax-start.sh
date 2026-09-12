#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

BIN="${HOME}/.local/bin/ax-agent"
CONFIG="${HOME}/.config/ax/ax.json"
STATE_DIR="${HOME}/.local/state/ax"
PIDFILE="${STATE_DIR}/agent.pid"
LOGFILE="${STATE_DIR}/agent.log"

mkdir -p "${STATE_DIR}"

if [ -f "${PIDFILE}" ]; then
  old_pid="$(cat "${PIDFILE}" 2>/dev/null || true)"
  if [ -n "${old_pid}" ] && kill -0 "${old_pid}" 2>/dev/null; then
    echo "Ax agent already running (PID ${old_pid})"
    exit 0
  fi
  rm -f "${PIDFILE}"
fi

if [ ! -x "${BIN}" ]; then
  echo "Ax agent not installed: ${BIN}" >&2
  exit 1
fi
if [ ! -f "${CONFIG}" ]; then
  echo "Ax config not found: ${CONFIG}" >&2
  exit 1
fi

nohup "${BIN}" -config "${CONFIG}" >>"${LOGFILE}" 2>&1 &
pid=$!
echo "${pid}" > "${PIDFILE}"
sleep 0.5

if kill -0 "${pid}" 2>/dev/null; then
  echo "Ax agent started (PID ${pid})"
  echo "Log: ${LOGFILE}"
else
  echo "Ax agent exited during startup. Check ${LOGFILE}" >&2
  rm -f "${PIDFILE}"
  exit 1
fi
