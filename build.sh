#!/usr/bin/env bash
# Build the LumenTale wiki web app.
#
#   ./build.sh                build both
#   ./build.sh frontend       build only the frontend (dist/)
#   ./build.sh backend        build only the backend jar
#   ./build.sh --with-tests   backend build also runs the unit tests
#
# Artifacts:
#   frontend/dist/                      static SPA bundle
#   backend/build/libs/*-SNAPSHOT.jar   runnable Spring Boot jar
#
# Running the result (CWD must be the repo root — the backend resolves
# data/ and wiki-db/migrations relative to it):
#   java -jar backend/build/libs/<jar>.jar          # API on :8084
#   (cd frontend && npm run preview)                # serves dist/, proxies
#                                                   # /api + /data to :8084
set -euo pipefail
cd "$(dirname "$0")"

TARGET="all"
GRADLE_ARGS=(-x test)
for arg in "$@"; do
  case "$arg" in
    frontend|backend) TARGET="$arg" ;;
    --with-tests) GRADLE_ARGS=() ;;
    -h|--help) sed -n '2,17p' "$0"; exit 0 ;;
    *) echo "unknown arg: $arg (try --help)"; exit 1 ;;
  esac
done

if [ "$TARGET" = "all" ] || [ "$TARGET" = "frontend" ]; then
  echo "==> frontend: npm install + build"
  (
    cd frontend
    npm install
    npm run build
  )
  echo "==> frontend OK: frontend/dist/"
fi

if [ "$TARGET" = "all" ] || [ "$TARGET" = "backend" ]; then
  echo "==> backend: gradle bootJar ${GRADLE_ARGS[*]:-}"
  (cd backend && ./gradlew bootJar "${GRADLE_ARGS[@]}")
  JAR=$(ls -t backend/build/libs/*.jar 2>/dev/null | head -1 || true)
  echo "==> backend OK: ${JAR:-backend/build/libs/}"
fi

echo
echo "Run from the repo root:"
echo "  java -jar backend/build/libs/<jar>.jar      # API :8084"
echo "  (cd frontend && npm run preview)            # SPA, proxies to :8084"
