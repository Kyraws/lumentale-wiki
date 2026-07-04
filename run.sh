#!/usr/bin/env bash
# Build what's missing, then run the wiki: backend jar (:8084) + vite preview (:4173).
#
#   ./run.sh              build if needed, run both
#   ./run.sh --rebuild    force a fresh build first
#
# CWD must be the repo root — the backend resolves data/ and wiki-db/migrations
# relative to it. Ctrl-C stops both processes.
set -euo pipefail
cd "$(dirname "$0")"

REBUILD=0
[ "${1:-}" = "--rebuild" ] && REBUILD=1

JAR=$(ls -t backend/build/libs/*.jar 2>/dev/null | head -1 || true)
if [ "$REBUILD" = 1 ] || [ -z "$JAR" ]; then ./build.sh backend; fi
if [ "$REBUILD" = 1 ] || [ ! -d frontend/dist ]; then ./build.sh frontend; fi
JAR=$(ls -t backend/build/libs/*.jar | head -1)

echo "==> backend: java -jar $JAR (:8084)"
java -jar "$JAR" &
BACKEND_PID=$!
trap 'kill $BACKEND_PID 2>/dev/null || true' EXIT

# wait for the API to come up
for i in $(seq 1 60); do
  curl -sf http://localhost:8084/api/meta >/dev/null 2>&1 && break
  sleep 1
done

# vite preview serves frontend/dist and proxies /api + /data to :8084
echo "==> frontend preview on :4173"
(cd frontend && npm run preview)
