#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

PIDFILE="${HOME}/.local/state/ax/agent.pid"

if [ ! -f "${PIDFILE}" ]; then
  echo "Ax agent is not running"
  exit 0
fi

pid="$(cat "${PIDFILE}" 2>/dev/null || true)"
if [ -z "${pid}" ] || ! kill -0 "${pid}" 2>/dev/null; then
  rm -f "${PIDFILE}"
  echo "Ax agent is not running"
  exit 0
fi

kill "${pid}"
for _ in 1 2 3 4 5; do
  if ! kill -0 "${pid}" 2>/dev/null; then
    rm -f "${PIDFILE}"
    echo "Ax agent stopped"
    exit 0
  fi
  sleep 0.4
done

kill -9 "${pid}" 2>/dev/null || true
rm -f "${PIDFILE}"
echo "Ax agent force-stopped"
