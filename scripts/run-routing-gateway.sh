#!/usr/bin/env bash
# Build and run services/routing-gateway (Java 21, Spring Cloud Gateway) on :8000.
#
#   scripts/run-routing-gateway.sh [start|stop|status|run]   (default: start)
#
#   start   build (unless SKIP_BUILD=1) and start in the background; logs and pid under .run/
#   run     build and run in the foreground
#   stop    stop the background gateway
#
# Env: JAVA21_HOME (default /usr/lib/jvm/java-21-openjdk-amd64), SKIP_BUILD=1, GATEWAY_ARGS (extra --key=value),
#      MVNW_REPOURL (Maven mirror for the wrapper). The allow-list file is services/routing-gateway/config/routing.properties.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODULE="$ROOT/services/routing-gateway"
RUN_DIR="$ROOT/.run"
PID_FILE="$RUN_DIR/routing-gateway.pid"
LOG_FILE="$RUN_DIR/routing-gateway.log"
JAVA21_HOME="${JAVA21_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
CMD="${1:-start}"

log() { echo "[run-routing-gateway] $*"; }

build() {
  if [[ "${SKIP_BUILD:-0}" == "1" && -f "$MODULE/target/routing-gateway.jar" ]]; then return; fi
  log "building with JAVA_HOME=$JAVA21_HOME"
  (cd "$ROOT/services" && JAVA_HOME="$JAVA21_HOME" ./mvnw -q -B -f routing-gateway/pom.xml -DskipTests package)
}

java_cmd() {
  echo "$JAVA21_HOME/bin/java" -jar "$MODULE/target/routing-gateway.jar" \
    "--routing.config-file=$MODULE/config/routing.properties" ${GATEWAY_ARGS:-}
}

running() { [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; }

case "$CMD" in
  start)
    if running; then log "already running (pid $(cat "$PID_FILE"))"; exit 0; fi
    build
    mkdir -p "$RUN_DIR"
    # shellcheck disable=SC2046
    nohup $(java_cmd) >"$LOG_FILE" 2>&1 &
    echo $! >"$PID_FILE"
    for _ in $(seq 1 60); do
      if curl -fsS http://localhost:8000/actuator/health >/dev/null 2>&1; then
        log "routing-gateway up on http://localhost:8000 (pid $(cat "$PID_FILE"), log $LOG_FILE)"; exit 0
      fi
      if ! running; then log "routing-gateway exited during startup:"; tail -n 20 "$LOG_FILE"; exit 1; fi
      sleep 1
    done
    log "routing-gateway did not become healthy in 60 s, see $LOG_FILE"; exit 1 ;;
  run)
    build
    # shellcheck disable=SC2046
    exec $(java_cmd) ;;
  stop)
    if running; then kill "$(cat "$PID_FILE")"; rm -f "$PID_FILE"; log "stopped"; else log "not running"; fi ;;
  status)
    if running; then log "running (pid $(cat "$PID_FILE"))"; curl -fsS http://localhost:8000/actuator/health; echo; else log "not running"; exit 1; fi ;;
  *) echo "usage: $0 [start|stop|status|run]" >&2; exit 2 ;;
esac
