#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ADDON_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$ADDON_DIR/.." && pwd)"
IMAGE="${LIBREOFFICE_REST_BUILD_IMAGE:-libreoffice-rest-build:local}"
M2_DIR="$REPO_ROOT/.m2-docker"
DOCKERFILE="$ADDON_DIR/docker/LibreOfficeRest.Dockerfile"

if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
  cat <<'MSG'
Usage:
  ./LibreOfficeRest/tools/build-libreoffice-rest-docker.sh [maven args...]

Default:
  ./LibreOfficeRest/tools/build-libreoffice-rest-docker.sh
  -> mvn -Puno-sdk-build clean verify -DskipITs=true

Update src-generated/java/org/libreoffice/rest/XLibreOfficeRest.java only:
  ./LibreOfficeRest/tools/build-libreoffice-rest-docker.sh generate-sources
MSG
  exit 0
fi

mkdir -p "$M2_DIR"
docker build -f "$DOCKERFILE" -t "$IMAGE" "$ADDON_DIR"

if [ "$#" -eq 0 ]; then
  set -- clean verify -DskipITs=true
fi

docker run --rm \
  -u "$(id -u):$(id -g)" \
  --mount "type=bind,source=$REPO_ROOT,target=/work" \
  --mount "type=bind,source=$M2_DIR,target=/var/maven/.m2" \
  -e MAVEN_CONFIG=/var/maven/.m2 \
  -w /work/LibreOfficeRest \
  "$IMAGE" \
  mvn -B -Puno-sdk-build -Duser.home=/var/maven "$@"
