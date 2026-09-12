#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

REPO_URL="https://github.com/Wyun09/Ax.git"
ROOT="${HOME}/.local/share/ax"
BIN_DIR="${HOME}/.local/bin"
CONFIG_DIR="${HOME}/.config/ax"
STATE_DIR="${HOME}/.local/state/ax"

mkdir -p "${BIN_DIR}" "${CONFIG_DIR}" "${STATE_DIR}"

if ! command -v git >/dev/null 2>&1; then
  pkg install -y git
fi
if ! command -v go >/dev/null 2>&1; then
  pkg install -y golang
fi

if [ -d "${ROOT}/.git" ]; then
  git -C "${ROOT}" fetch --depth=1 origin main
  git -C "${ROOT}" reset --hard origin/main
else
  rm -rf "${ROOT}"
  git clone --depth=1 "${REPO_URL}" "${ROOT}"
fi

(
  cd "${ROOT}/agent"
  go build -trimpath -ldflags="-s -w" -o "${BIN_DIR}/ax-agent" ./cmd/ax-agent
)

if [ ! -f "${CONFIG_DIR}/ax.json" ]; then
  cp "${ROOT}/agent/ax.example.json" "${CONFIG_DIR}/ax.json"
fi

install -m 755 "${ROOT}/scripts/ax-start.sh" "${BIN_DIR}/ax-start"
install -m 755 "${ROOT}/scripts/ax-stop.sh" "${BIN_DIR}/ax-stop"

case ":${PATH}:" in
  *":${BIN_DIR}:"*) ;;
  *)
    echo
    echo "Add this to your shell profile:"
    echo "  export PATH=\"\$HOME/.local/bin:\$PATH\""
    ;;
esac

echo
echo "Ax installed."
echo "Config: ${CONFIG_DIR}/ax.json"
echo "Start:  ${BIN_DIR}/ax-start"
echo "Stop:   ${BIN_DIR}/ax-stop"
echo "API:    http://127.0.0.1:18766"
