#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_DIR="${LIBREOFFICE_REST_DIR:-/work/LibreOfficeRest}"
LOG_FILE="${LIBREOFFICE_REST_DEBUG_LOG:-/tmp/libreoffice-rest.log}"
DISPLAY_VALUE="${DISPLAY:-:1}"
SCREEN_GEOMETRY="${LIBREOFFICE_REST_SCREEN:-1600x1000x24}"
VNC_PORT="${LIBREOFFICE_REST_VNC_PORT:-5901}"
NOVNC_PORT="${LIBREOFFICE_REST_NOVNC_PORT:-6080}"
HOME="${HOME:-/tmp/libreoffice-rest-home}"

export DISPLAY="$DISPLAY_VALUE"
export HOME
export LIBREOFFICE_REST_DEBUG_LOG="$LOG_FILE"
export SAL_USE_VCLPLUGIN="${SAL_USE_VCLPLUGIN:-gen}"
export GDK_BACKEND="${GDK_BACKEND:-x11}"
export QT_QPA_PLATFORM="${QT_QPA_PLATFORM:-xcb}"

LOG_DIR=/tmp/libreoffice-rest-vnc
mkdir -p "$HOME" "$LOG_DIR" "$(dirname "$LOG_FILE")"
mkdir -p "$PROJECT_DIR/tmp" 2>/dev/null || true
chmod 700 "$HOME" 2>/dev/null || true

touch "$LOG_FILE"
rm -f \
  "$LOG_DIR"/*.log \
  /tmp/libreoffice-gui-stdout.log \
  /tmp/libreoffice-gui-stderr.log \
  /tmp/profile-init.log \
  /tmp/unopkg-add.log \
  /tmp/unopkg-remove.log

setup_nss_wrapper() {
  if getent passwd "$(id -u)" >/dev/null 2>&1; then
    return 0
  fi

  local nss_so
  nss_so="$(find /usr/lib -name libnss_wrapper.so -print -quit 2>/dev/null || true)"
  if [ -z "$nss_so" ]; then
    return 0
  fi

  export NSS_WRAPPER_PASSWD=/tmp/libreoffice-rest-passwd
  export NSS_WRAPPER_GROUP=/tmp/libreoffice-rest-group
  printf 'libreoffice-rest:x:%s:%s:LibreOfficeRest:%s:/bin/bash\n' "$(id -u)" "$(id -g)" "$HOME" >"$NSS_WRAPPER_PASSWD"
  printf 'libreoffice-rest:x:%s:\n' "$(id -g)" >"$NSS_WRAPPER_GROUP"
  export LD_PRELOAD="${nss_so}${LD_PRELOAD:+:$LD_PRELOAD}"
}

find_latest_oxt() {
  find "$PROJECT_DIR/target" -maxdepth 1 -type f -name 'LibreOfficeRest*.oxt' -print 2>/dev/null | sort -V | tail -n 1
}

resolve_oxt_path() {
  local requested="${LIBREOFFICE_REST_OXT:-}"
  local latest=""

  if [ -n "$requested" ] && [ -f "$requested" ]; then
    printf '%s\n' "$requested"
    return 0
  fi

  latest="$(find_latest_oxt || true)"
  if [ -n "$latest" ] && [ -f "$latest" ]; then
    printf '%s\n' "$latest"
    return 0
  fi

  if [ -f /tmp/LibreOfficeRest.oxt ]; then
    printf '%s\n' /tmp/LibreOfficeRest.oxt
    return 0
  fi

  if [ -n "$requested" ]; then
    printf '%s\n' "$requested"
  fi
}

resolve_example_path() {
  local requested="${LIBREOFFICE_REST_EXAMPLE:-}"

  if [ -n "$requested" ] && [ -f "$requested" ]; then
    printf '%s\n' "$requested"
    return 0
  fi

  if [ -f "$PROJECT_DIR/example-restapi.ods" ]; then
    printf '%s\n' "$PROJECT_DIR/example-restapi.ods"
    return 0
  fi

  if [ -f /tmp/example-restapi.ods ]; then
    printf '%s\n' /tmp/example-restapi.ods
    return 0
  fi

  if [ -n "$requested" ]; then
    printf '%s\n' "$requested"
  fi
}

cleanup() {
  set +e
  [ -n "${LO_PID:-}" ] && kill "$LO_PID" >/dev/null 2>&1 || true
  [ -n "${NOVNC_PID:-}" ] && kill "$NOVNC_PID" >/dev/null 2>&1 || true
  [ -n "${X11VNC_PID:-}" ] && kill "$X11VNC_PID" >/dev/null 2>&1 || true
  [ -n "${WM_PID:-}" ] && kill "$WM_PID" >/dev/null 2>&1 || true
  [ -n "${XVFB_PID:-}" ] && kill "$XVFB_PID" >/dev/null 2>&1 || true
  if [ -n "${DBUS_SESSION_BUS_PID:-}" ]; then
    kill "$DBUS_SESSION_BUS_PID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT INT TERM

wait_for_display() {
  local i
  for i in $(seq 1 80); do
    if xdpyinfo -display "$DISPLAY_VALUE" >/dev/null 2>&1; then
      return 0
    fi
    if ! kill -0 "$XVFB_PID" >/dev/null 2>&1; then
      echo "Xvfb stopped while starting." >&2
      cat "$LOG_DIR/xvfb.log" >&2 || true
      return 1
    fi
    sleep 0.25
  done

  echo "Xvfb did not become ready on ${DISPLAY_VALUE}." >&2
  cat "$LOG_DIR/xvfb.log" >&2 || true
  return 1
}

setup_nss_wrapper

if command -v dbus-launch >/dev/null 2>&1; then
  eval "$(dbus-launch --sh-syntax)"
fi

DISPLAY_NUMBER="${DISPLAY_VALUE#:}"
DISPLAY_NUMBER="${DISPLAY_NUMBER%%.*}"
rm -f "/tmp/.X${DISPLAY_NUMBER}-lock" "/tmp/.X11-unix/X${DISPLAY_NUMBER}" 2>/dev/null || true

Xvfb "$DISPLAY_VALUE" -screen 0 "$SCREEN_GEOMETRY" -dpi 96 -nolisten tcp >"$LOG_DIR/xvfb.log" 2>&1 &
XVFB_PID=$!
wait_for_display

openbox >"$LOG_DIR/openbox.log" 2>&1 &
WM_PID=$!

x11vnc \
  -display "$DISPLAY_VALUE" \
  -forever \
  -shared \
  -nopw \
  -listen 0.0.0.0 \
  -rfbport "$VNC_PORT" \
  -noxdamage \
  >"$LOG_DIR/x11vnc.log" 2>&1 &
X11VNC_PID=$!

websockify --web=/usr/share/novnc "$NOVNC_PORT" "localhost:$VNC_PORT" >"$LOG_DIR/novnc.log" 2>&1 &
NOVNC_PID=$!

OXT_PATH="$(resolve_oxt_path || true)"
EXAMPLE_PATH="$(resolve_example_path || true)"

cat <<MSG
LibreOfficeRest GUI/VNC container is running.

VNC:       localhost:${VNC_PORT}     password: none
noVNC:     http://localhost:${NOVNC_PORT}/vnc.html
Project:   ${PROJECT_DIR}
OXT:       ${OXT_PATH:-not found}
Example:   ${EXAMPLE_PATH:-not found}
Debug log: ${LOG_FILE}
Java:      $(java -version 2>&1 | head -n 1)
Office:    $(soffice --version 2>/dev/null || true)

MSG

soffice --headless --norestore --nofirststartwizard --terminate_after_init >"/tmp/profile-init.log" 2>&1 || {
  echo "LibreOffice profile initialization failed; see /tmp/profile-init.log" >&2
  cat /tmp/profile-init.log >&2 || true
  exit 1
}
pkill -u "$(id -u)" soffice.bin >/dev/null 2>&1 || true
sleep 0.5

if [ -z "$OXT_PATH" ] || [ ! -f "$OXT_PATH" ]; then
  echo "OXT not found. Put LibreOfficeRest*.oxt in ${PROJECT_DIR}/target or set LIBREOFFICE_REST_OXT." >&2
  exit 1
fi

echo "Installing extension: ${OXT_PATH}"
unopkg remove org.libreoffice.rest.LibreOfficeRest >"/tmp/unopkg-remove.log" 2>&1 || true
unopkg add --force "$OXT_PATH" >"/tmp/unopkg-add.log" 2>&1 || {
  echo "Extension install failed; see /tmp/unopkg-add.log" >&2
  cat /tmp/unopkg-add.log >&2 || true
  exit 1
}
pkill -u "$(id -u)" soffice.bin >/dev/null 2>&1 || true
sleep 0.5

if [ -n "$EXAMPLE_PATH" ] && [ -f "$EXAMPLE_PATH" ]; then
  START_DOC="$EXAMPLE_PATH"
  STALE_LOCK="$(dirname "$START_DOC")/.~lock.$(basename "$START_DOC")#"
  rm -f "$STALE_LOCK" 2>/dev/null || true
  if [ ! -w "$START_DOC" ]; then
    echo "Example file is not writable, so LibreOffice may open it read-only: ${START_DOC}" >&2
  fi
else
  START_DOC=""
fi

cat <<'MSG'
Inside Calc, test or recalculate:
  =PING()
  =JSONVALID("asd")
  =JSONNUMBER(B3;"$.result.XXBTZEUR.c[0]")

Useful logs inside the container:
  cat /tmp/unopkg-add.log
  cat /tmp/libreoffice-rest.log
  cat /tmp/libreoffice-gui-stderr.log
  cat /tmp/libreoffice-rest-vnc/xvfb.log
  cat /tmp/libreoffice-rest-vnc/x11vnc.log

MSG

echo "Starting LibreOffice Calc..."
if [ -n "$START_DOC" ]; then
  soffice --calc "$START_DOC" --norestore --nofirststartwizard --nologo >"/tmp/libreoffice-gui-stdout.log" 2>"/tmp/libreoffice-gui-stderr.log" &
else
  soffice --calc --norestore --nofirststartwizard --nologo >"/tmp/libreoffice-gui-stdout.log" 2>"/tmp/libreoffice-gui-stderr.log" &
fi
LO_PID=$!

wait "$LO_PID" || true
while pgrep -u "$(id -u)" -x soffice.bin >/dev/null 2>&1; do
  sleep 1
done
