#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ADDON_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DOCKERFILE="$ADDON_ROOT/docker/LibreOfficeRest.GuiVnc.Dockerfile"
IMAGE="${LIBREOFFICE_REST_GUI_IMAGE:-libreoffice-rest-gui-vnc:26.2.3}"
OXT="${1:-}"
HOST_UID="${HOST_UID:-${SUDO_UID:-$(id -u)}}"
HOST_GID="${HOST_GID:-${SUDO_GID:-$(id -g)}}"

if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
  cat <<'MSG'
Usage:
  ./LibreOfficeRest/tools/run-libreoffice-rest-gui-vnc.sh [OXT]

Examples:
  ./LibreOfficeRest/tools/run-libreoffice-rest-gui-vnc.sh
  ./LibreOfficeRest/tools/run-libreoffice-rest-gui-vnc.sh LibreOfficeRest/target/LibreOfficeRest-1.2026.05.12.oxt

The image installs LibreOffice 26.2.3 from the hardcoded Document Foundation URL and verifies the .asc signature.
MSG
  exit 0
fi

if [ -z "$OXT" ]; then
  OXT="$(ls "$ADDON_ROOT"/target/LibreOfficeRest-*.oxt 2>/dev/null | head -n 1 || true)"
fi

if [ -z "$OXT" ] || [ ! -f "$OXT" ]; then
  cat >&2 <<'MSG'
OXT not found.

Build first:
  ./LibreOfficeRest/tools/build-libreoffice-rest-docker.sh

Then run:
  ./LibreOfficeRest/tools/run-libreoffice-rest-gui-vnc.sh
MSG
  exit 1
fi

EXAMPLE="$ADDON_ROOT/example-restapi.ods"
if [ ! -f "$EXAMPLE" ]; then
  echo "Example file missing: $EXAMPLE" >&2
  exit 1
fi

OXT_ABS="$(cd "$(dirname "$OXT")" && pwd)/$(basename "$OXT")"
EXAMPLE_ABS="$(cd "$(dirname "$EXAMPLE")" && pwd)/$(basename "$EXAMPLE")"
VNC_PORT="${LIBREOFFICE_REST_VNC_PORT:-5901}"
NOVNC_PORT="${LIBREOFFICE_REST_NOVNC_PORT:-6080}"

if [ "$HOST_UID" = "0" ]; then
  echo "Refusing to run the GUI container as root. Run as your normal user." >&2
  exit 1
fi

echo "Building GUI/VNC image: $IMAGE"
docker build -f "$DOCKERFILE" -t "$IMAGE" "$ADDON_ROOT"

echo "Starting container. Host LibreOffice profile is not mounted."
echo "OXT:     $OXT_ABS"
echo "Example: $EXAMPLE_ABS"
echo "VNC:     localhost:$VNC_PORT"
echo "noVNC:   http://localhost:$NOVNC_PORT/vnc.html"

docker run --rm -it \
  -u "$HOST_UID:$HOST_GID" \
  -p "127.0.0.1:${VNC_PORT}:5901" \
  -p "127.0.0.1:${NOVNC_PORT}:6080" \
  --mount "type=bind,source=$OXT_ABS,target=/tmp/LibreOfficeRest.oxt,readonly" \
  --mount "type=bind,source=$EXAMPLE_ABS,target=/tmp/example-restapi.ods,readonly=false" \
  -e HOME=/tmp/libreoffice-rest-home \
  -e LIBREOFFICE_REST_OXT=/tmp/LibreOfficeRest.oxt \
  -e LIBREOFFICE_REST_EXAMPLE=/tmp/example-restapi.ods \
  -e LIBREOFFICE_REST_DEBUG_LOG=/tmp/libreoffice-rest.log \
  "$IMAGE"
