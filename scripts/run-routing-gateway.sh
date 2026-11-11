#!/usr/bin/env bash
# run-routing-gateway.sh - build and run services/routing-gateway (MBA-3822 strangler) on :8000 (Linux, dev)
#
#   scripts/run-routing-gateway.sh [start]  build (unless SKIP_BUILD=1) and start on :8000
#   scripts/run-routing-gateway.sh status   show whether it runs
#   scripts/run-routing-gateway.sh stop     stop it
#   scripts/run-routing-gateway.sh test     unit + integration tests (./mvnw test)
#
# Start run-legacy.sh and run-scheduling-service.sh first. The allow-list is
# services/routing-gateway/config/routing.properties (ROUTING_CONFIG_FILE), hot-reloaded on save.
# Env: JAVA_HOME (JDK 21, auto-detected)  ROUTING_*_URL upstream overrides  ROUTING_COEXISTENCE_MODE
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SVC="$ROOT/services/routing-gateway"
RUN_DIR="$ROOT/.run"
PID_FILE="$RUN_DIR/routing-gateway.pid"
LOG_FILE="$RUN_DIR/routing-gateway.log"
PORT=8000

log() { echo "[run-routing-gateway] $*"; }
die() { echo "[run-routing-gateway] ERROR: $*" >&2; exit 1; }

find_java21() {
    if [ -n "${JAVA_HOME:-}" ] && "$JAVA_HOME/bin/java" -version 2>&1 | grep -q 'version "21'; then return; fi
    for d in /usr/lib/jvm/temurin-21-jdk-amd64 /usr/lib/jvm/java-21-openjdk-amd64 /usr/lib/jvm/java-21-openjdk \
             /usr/lib/jvm/openjdk-21; do
        if [ -x "$d/bin/java" ]; then export JAVA_HOME="$d"; return; fi
    done
    die "JDK 21 not found - install Temurin 21 or OpenJDK 21 and set JAVA_HOME"
}

running_pid() {
    [ -f "$PID_FILE" ] || return 1
    local p; p="$(cat "$PID_FILE")"
    kill -0 "$p" 2>/dev/null && echo "$p"
}

healthy() { curl -fsS -o /dev/null --max-time 3 "http://localhost:$PORT/actuator/health"; }

case "${1:-start}" in
    status)
        if p="$(running_pid)" && healthy; then log "running (pid $p) on :$PORT"; else log "not running"; fi ;;
    stop)
        if p="$(running_pid)"; then kill "$p"; log "stopped pid $p"; else log "not running"; fi
        rm -f "$PID_FILE" ;;
    test)
        find_java21; cd "$SVC" && ./mvnw -B test ;;
    start)
        find_java21
        if p="$(running_pid)"; then log "already running (pid $p)"; exit 0; fi
        if [ "${SKIP_BUILD:-0}" != "1" ] || [ ! -f "$SVC/target/routing-gateway.jar" ]; then
            log "building (JAVA_HOME=$JAVA_HOME)"
            (cd "$SVC" && ./mvnw -B -q package -DskipTests)
        fi
        mkdir -p "$RUN_DIR"
        cp "$SVC/target/routing-gateway.jar" "$RUN_DIR/routing-gateway.jar"
        export ROUTING_CONFIG_FILE="${ROUTING_CONFIG_FILE:-$SVC/config/routing.properties}"
        log "starting on :$PORT (allow-list $ROUTING_CONFIG_FILE, log $LOG_FILE)"
        (cd "$SVC" && nohup "$JAVA_HOME/bin/java" -jar "$RUN_DIR/routing-gateway.jar" >"$LOG_FILE" 2>&1 & echo $! >"$PID_FILE")
        for _ in $(seq 1 60); do
            if healthy; then log "up: http://localhost:$PORT"; exit 0; fi
            kill -0 "$(cat "$PID_FILE")" 2>/dev/null || die "routing-gateway exited - see $LOG_FILE"
            sleep 2
        done
        die "routing-gateway did not become healthy in 120s - see $LOG_FILE" ;;
    *) die "usage: $0 [start|status|stop|test]" ;;
esac
