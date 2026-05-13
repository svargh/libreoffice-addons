#!/usr/bin/env bash
set -euo pipefail

IDL_FILE="${1:?IDL file path required}"
OUT_DIR="${2:?UNO generated output directory required}"
RDB_FILE="${3:?RDB file path required}"
CLASS_OUT_DIR="${4:?generated Java class output directory required}"

find_tool() {
  local tool="$1"
  shift

  if command -v "$tool" >/dev/null 2>&1; then
    command -v "$tool"
    return 0
  fi

  local candidate
  for candidate in "$@"; do
    if [ -x "$candidate" ]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done

  return 1
}

find_file() {
  local env_value="$1"
  shift

  if [ -n "$env_value" ] && [ -f "$env_value" ]; then
    printf '%s\n' "$env_value"
    return 0
  fi

  local candidate
  for candidate in "$@"; do
    if [ -f "$candidate" ]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done

  return 1
}

SDK_HOME="${LO_SDK_HOME:-${OO_SDK_HOME:-}}"
OFFICE_PROGRAM="${OFFICE_PROGRAM_PATH:-${UNO_PATH:-}}"

UNOIDL_WRITE_CANDIDATES=()
JAVAMAKER_CANDIDATES=()
IDLC_CANDIDATES=()
REGMERGE_CANDIDATES=()
IDL_DIR_CANDIDATES=()
UNO_TYPES_CANDIDATES=()
OFFAPI_TYPES_CANDIDATES=()

if [ -n "$SDK_HOME" ]; then
  UNOIDL_WRITE_CANDIDATES+=("$SDK_HOME/bin/unoidl-write" "$SDK_HOME/linux/bin/unoidl-write")
  JAVAMAKER_CANDIDATES+=("$SDK_HOME/bin/javamaker" "$SDK_HOME/linux/bin/javamaker")
  IDLC_CANDIDATES+=("$SDK_HOME/bin/idlc" "$SDK_HOME/linux/bin/idlc")
  REGMERGE_CANDIDATES+=("$SDK_HOME/bin/regmerge" "$SDK_HOME/linux/bin/regmerge")
  IDL_DIR_CANDIDATES+=("$SDK_HOME/idl")
fi

if [ -n "$OFFICE_PROGRAM" ]; then
  UNOIDL_WRITE_CANDIDATES+=("$OFFICE_PROGRAM/unoidl-write")
  JAVAMAKER_CANDIDATES+=("$OFFICE_PROGRAM/javamaker")
  IDLC_CANDIDATES+=("$OFFICE_PROGRAM/idlc")
  REGMERGE_CANDIDATES+=("$OFFICE_PROGRAM/regmerge")
  UNO_TYPES_CANDIDATES+=("$OFFICE_PROGRAM/types.rdb")
  OFFAPI_TYPES_CANDIDATES+=("$OFFICE_PROGRAM/types/offapi.rdb")
fi

UNOIDL_WRITE_CANDIDATES+=(
  "/usr/lib/libreoffice/program/unoidl-write"
  "/usr/lib/libreoffice/sdk/bin/unoidl-write"
  "/usr/lib/libreoffice/sdk/linux/bin/unoidl-write"
  "/usr/share/libreoffice/sdk/bin/unoidl-write"
  "/opt/libreoffice/program/unoidl-write"
  "/opt/libreoffice/sdk/bin/unoidl-write"
)
JAVAMAKER_CANDIDATES+=(
  "/usr/lib/libreoffice/program/javamaker"
  "/usr/lib/libreoffice/sdk/bin/javamaker"
  "/usr/lib/libreoffice/sdk/linux/bin/javamaker"
  "/usr/share/libreoffice/sdk/bin/javamaker"
  "/opt/libreoffice/program/javamaker"
  "/opt/libreoffice/sdk/bin/javamaker"
)
IDLC_CANDIDATES+=(
  "/usr/lib/libreoffice/sdk/bin/idlc"
  "/usr/lib/libreoffice/sdk/linux/bin/idlc"
  "/usr/share/libreoffice/sdk/bin/idlc"
  "/opt/libreoffice/sdk/bin/idlc"
)
REGMERGE_CANDIDATES+=(
  "/usr/lib/libreoffice/sdk/bin/regmerge"
  "/usr/lib/libreoffice/sdk/linux/bin/regmerge"
  "/usr/share/libreoffice/sdk/bin/regmerge"
  "/opt/libreoffice/sdk/bin/regmerge"
)
IDL_DIR_CANDIDATES+=(
  "/usr/lib/libreoffice/sdk/idl"
  "/usr/share/libreoffice/sdk/idl"
  "/opt/libreoffice/sdk/idl"
)
UNO_TYPES_CANDIDATES+=(
  "/usr/lib/libreoffice/program/types.rdb"
  "/usr/share/libreoffice/program/types.rdb"
  "/opt/libreoffice/program/types.rdb"
)
OFFAPI_TYPES_CANDIDATES+=(
  "/usr/lib/libreoffice/program/types/offapi.rdb"
  "/usr/share/libreoffice/program/types/offapi.rdb"
  "/opt/libreoffice/program/types/offapi.rdb"
)

mkdir -p "$OUT_DIR" "$CLASS_OUT_DIR"
rm -f "$RDB_FILE"
rm -f "$CLASS_OUT_DIR/org/libreoffice/rest/XLibreOfficeRest.class" \
      "$CLASS_OUT_DIR/org/libreoffice/rest/LibreOfficeRest.class" 2>/dev/null || true

JAVAMAKER="$(find_tool javamaker "${JAVAMAKER_CANDIDATES[@]}" || true)"
if [ -z "$JAVAMAKER" ]; then
  cat >&2 <<'MSG'
LibreOffice SDK tool javamaker not found.

javamaker is required. It generates the Java UNO .class files from the
LibreOfficeRest IDL/RDB. A hand-written X*.java interface can compile but can
fail at runtime with Basic/Calc errors such as "illegal object given" or #VALUE!.

Use the Docker build image, install the LibreOffice SDK, or export LO_SDK_HOME.
MSG
  exit 1
fi

UNO_TYPES="$(find_file "${LO_TYPES_RDB:-}" "${UNO_TYPES_CANDIDATES[@]}" || true)"
OFFAPI_TYPES="$(find_file "${LO_OFFAPI_RDB:-}" "${OFFAPI_TYPES_CANDIDATES[@]}" || true)"

if [ -z "$UNO_TYPES" ] || [ -z "$OFFAPI_TYPES" ]; then
  cat >&2 <<'MSG'
LibreOffice runtime type libraries not found.

Set these explicitly if auto-detection fails:
  export LO_TYPES_RDB=/usr/lib/libreoffice/program/types.rdb
  export LO_OFFAPI_RDB=/usr/lib/libreoffice/program/types/offapi.rdb
MSG
  exit 1
fi

UNOIDL_WRITE="$(find_tool unoidl-write "${UNOIDL_WRITE_CANDIDATES[@]}" || true)"
if [ -n "$UNOIDL_WRITE" ]; then
  printf 'Using unoidl-write: %s\n' "$UNOIDL_WRITE"
  printf 'Using UNO types:     %s\n' "$UNO_TYPES"
  printf 'Using offapi types:  %s\n' "$OFFAPI_TYPES"
  "$UNOIDL_WRITE" "$UNO_TYPES" "$OFFAPI_TYPES" "$IDL_FILE" "$RDB_FILE"
else
  IDLC="$(find_tool idlc "${IDLC_CANDIDATES[@]}" || true)"
  REGMERGE="$(find_tool regmerge "${REGMERGE_CANDIDATES[@]}" || true)"

  if [ -z "$IDLC" ] || [ -z "$REGMERGE" ]; then
    cat >&2 <<'MSG'
LibreOffice SDK IDL tools not found.

Current SDKs provide unoidl-write; older SDKs provide idlc/regmerge.
Use the Docker build image, install the LibreOffice SDK, or export LO_SDK_HOME.
MSG
    exit 1
  fi

  IDL_DIR="${LO_IDL_DIR:-}"
  if [ -z "$IDL_DIR" ]; then
    for candidate in "${IDL_DIR_CANDIDATES[@]}"; do
      if [ -d "$candidate" ]; then
        IDL_DIR="$candidate"
        break
      fi
    done
  fi

  if [ -z "$IDL_DIR" ] || [ ! -d "$IDL_DIR" ]; then
    echo "LibreOffice SDK IDL directory not found. Set LO_IDL_DIR." >&2
    exit 1
  fi

  mkdir -p "$OUT_DIR/urd"
  rm -f "$OUT_DIR/urd/XLibreOfficeRest.urd"
  printf 'Using legacy idlc:     %s\n' "$IDLC"
  printf 'Using legacy regmerge: %s\n' "$REGMERGE"
  "$IDLC" -C -I"$(dirname "$IDL_FILE")" -I"$IDL_DIR" -O"$OUT_DIR/urd" "$IDL_FILE"
  "$REGMERGE" "$RDB_FILE" /UCR "$OUT_DIR/urd/XLibreOfficeRest.urd"
fi

printf 'Generated UNO type library: %s\n' "$RDB_FILE"
printf 'Using javamaker: %s\n' "$JAVAMAKER"
"$JAVAMAKER" \
  -nD \
  -O"$CLASS_OUT_DIR" \
  -Torg.libreoffice.rest.XLibreOfficeRest \
  "$RDB_FILE" \
  -X"$UNO_TYPES" \
  -X"$OFFAPI_TYPES"

GENERATED_INTERFACE_CLASS="$CLASS_OUT_DIR/org/libreoffice/rest/XLibreOfficeRest.class"
if [ ! -f "$GENERATED_INTERFACE_CLASS" ]; then
  echo "javamaker did not generate expected interface class: $GENERATED_INTERFACE_CLASS" >&2
  find "$CLASS_OUT_DIR" -maxdepth 6 -type f -print >&2 || true
  exit 1
fi

printf 'Generated Java UNO interface class: %s\n' "$GENERATED_INTERFACE_CLASS"
