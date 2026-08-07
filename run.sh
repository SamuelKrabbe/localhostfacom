#!/usr/bin/env bash
# Runs the whole stack locally: Postgres + MinIO (via podman/docker compose),
# the Spring Boot API in the `dev` profile, and the Vite dev server.
# Ctrl+C stops everything it started.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$ROOT_DIR/.run-logs"
mkdir -p "$LOG_DIR"

API_LOG="$LOG_DIR/api.log"
UI_LOG="$LOG_DIR/ui.log"

: "${APP_BOOTSTRAP_ADMIN_EMAIL:=admin@localhost.facom}"
: "${APP_BOOTSTRAP_ADMIN_PASSWORD:=troque-esta-senha}"
export APP_BOOTSTRAP_ADMIN_EMAIL APP_BOOTSTRAP_ADMIN_PASSWORD

if command -v podman >/dev/null 2>&1; then
  COMPOSE="podman compose"
elif command -v docker >/dev/null 2>&1; then
  COMPOSE="docker compose"
else
  echo "Need podman or docker installed." >&2
  exit 1
fi

API_PID=""
UI_PID=""

cleanup() {
  echo
  echo "Stopping..."
  [ -n "$UI_PID" ] && kill "$UI_PID" 2>/dev/null || true
  [ -n "$API_PID" ] && kill "$API_PID" 2>/dev/null || true
  wait 2>/dev/null || true
  echo "Stopped. Infra containers (postgres/minio) are left running — 'podman compose down' to stop them too."
}
trap cleanup EXIT INT TERM

echo "==> Starting infrastructure (Postgres, MinIO)..."
(cd "$ROOT_DIR" && $COMPOSE up -d)

echo "==> Waiting for Postgres and MinIO to be healthy..."
for i in $(seq 1 30); do
  PG_STATUS=$($COMPOSE ps postgres --format '{{.Health}}' 2>/dev/null || echo "")
  MINIO_STATUS=$($COMPOSE ps minio --format '{{.Health}}' 2>/dev/null || echo "")
  if [ "$PG_STATUS" = "healthy" ] && [ "$MINIO_STATUS" = "healthy" ]; then
    break
  fi
  sleep 2
done

echo "==> Starting the API (dev profile) — log: $API_LOG"
(cd "$ROOT_DIR/api" && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev >"$API_LOG" 2>&1) &
API_PID=$!

echo "==> Waiting for the API on :8080..."
for i in $(seq 1 60); do
  if curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/api/public/products 2>/dev/null | grep -q '^200$'; then
    break
  fi
  if ! kill -0 "$API_PID" 2>/dev/null; then
    echo "API process died. Last log lines:" >&2
    tail -n 40 "$API_LOG" >&2
    exit 1
  fi
  sleep 2
done

echo "==> Starting the frontend (Vite) — log: $UI_LOG"
if [ ! -d "$ROOT_DIR/ui/node_modules" ]; then
  echo "    (first run: installing UI dependencies)"
  (cd "$ROOT_DIR/ui" && npm install)
fi
(cd "$ROOT_DIR/ui" && npm run dev -- --port 5173 --strictPort >"$UI_LOG" 2>&1) &
UI_PID=$!

echo "==> Waiting for the frontend on :5173..."
for i in $(seq 1 30); do
  if curl -s -o /dev/null -w '%{http_code}' http://localhost:5173/ 2>/dev/null | grep -q '^200$'; then
    break
  fi
  if ! kill -0 "$UI_PID" 2>/dev/null; then
    echo "Frontend process died. Last log lines:" >&2
    tail -n 40 "$UI_LOG" >&2
    exit 1
  fi
  sleep 1
done

cat <<EOF

==================================================
  App:       http://localhost:5173
  API:       http://localhost:8080
  Admin:     $APP_BOOTSTRAP_ADMIN_EMAIL / $APP_BOOTSTRAP_ADMIN_PASSWORD
             (only created once, when the admin table is empty)

  Logs:      $API_LOG
             $UI_LOG

  Ctrl+C to stop the API and frontend.
==================================================

EOF

wait
