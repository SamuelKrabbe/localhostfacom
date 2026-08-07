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
  ENGINE="podman"
elif command -v docker >/dev/null 2>&1; then
  COMPOSE="docker compose"
  ENGINE="docker"
else
  echo "Need podman or docker installed." >&2
  exit 1
fi

API_PID=""
UI_PID=""

STOP_INFRA="${STOP_INFRA:-0}"

# Killing the PID alone would only reap the wrapper shell and orphan the real java/node
# child, leaving :8080 and :5173 held. setsid puts each in its own process group so the
# whole tree can be signalled at once.
kill_group() {
  [ -n "$1" ] || return 0
  kill -TERM "-$1" 2>/dev/null || true
}

cleanup() {
  echo
  echo "Stopping..."
  kill_group "$UI_PID"
  kill_group "$API_PID"
  wait 2>/dev/null || true
  if [ "$STOP_INFRA" = "1" ]; then
    (cd "$ROOT_DIR" && $COMPOSE down)
    echo "Stopped, infra containers included."
  else
    echo "Stopped. Infra containers (postgres/minio) are still up — run with STOP_INFRA=1 to take them down too, or 'podman compose down'."
  fi
}
trap cleanup EXIT INT TERM

step() { echo "==> [${SECONDS}s] $1"; }

step "Starting infrastructure (Postgres, MinIO)..."
(cd "$ROOT_DIR" && $COMPOSE up -d)

# Asking compose for a single service's health is not portable — podman-compose rejects
# `ps <service>` outright, which used to make this loop fail every iteration and burn its
# whole timeout on every run. Container ids plus an engine-level inspect work on both.
all_containers_healthy() {
  local ids id status
  ids=$($COMPOSE ps -q 2>/dev/null) || return 1
  [ -n "$ids" ] || return 1
  for id in $ids; do
    # minio-init has no healthcheck and exits once the bucket exists; nothing to wait for.
    status=$($ENGINE inspect --format \
      '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$id" 2>/dev/null) || return 1
    case "$status" in
      healthy | none) ;;
      *) return 1 ;;
    esac
  done
}

step "Waiting for Postgres and MinIO to be healthy..."
for i in $(seq 1 60); do
  if all_containers_healthy; then
    break
  fi
  if [ "$i" -eq 60 ]; then
    echo "Infrastructure never reported healthy. Check '$COMPOSE ps'." >&2
    exit 1
  fi
  sleep 1
done

step "Starting the API (dev profile) — log: $API_LOG"
setsid bash -c "cd '$ROOT_DIR/api' && exec ./mvnw -ntp spring-boot:run -Dspring-boot.run.profiles=dev" >"$API_LOG" 2>&1 &
API_PID=$!

step "Waiting for the API on :8080..."
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

step "Starting the frontend (Vite) — log: $UI_LOG"
if [ ! -d "$ROOT_DIR/ui/node_modules" ]; then
  echo "    (first run: installing UI dependencies)"
  (cd "$ROOT_DIR/ui" && npm install)
fi
setsid bash -c "cd '$ROOT_DIR/ui' && exec npm run dev -- --port 5173 --strictPort" >"$UI_LOG" 2>&1 &
UI_PID=$!

step "Waiting for the frontend on :5173..."
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

step "Ready."

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
