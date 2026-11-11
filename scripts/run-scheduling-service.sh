#!/usr/bin/env bash
# run-scheduling-service.sh - build and run scheduling-service (Java 21 / Spring Boot 3) on :8090 (Linux, dev)
#
#   scripts/run-scheduling-service.sh [start]  SQL Server container (shared with run-legacy.sh), legacy seed if
#                                              missing, build (incl. the React UI), start on :8090
#   scripts/run-scheduling-service.sh status   show whether it runs
#   scripts/run-scheduling-service.sh stop     stop it (the SQL container is left running)
#   scripts/run-scheduling-service.sh e2e      run the Playwright browser flow (steps 1-7) against :8090
#
# Env: MSSQL_CONTAINER (mssql)  MSSQL_SA_PASSWORD (Str0ng!Passw0rd)  JAVA_HOME (JDK 21, auto-detected)
#      SKIP_BUILD=1 reuse target/scheduling-service.jar   RESEED_LEGACY=1 rebuild AssessmentDelivery first
#      MVNW_REPOURL / ~/.m2 mirror if Maven Central rate-limits (see README)
# The service creates its own "Scheduling" DB (Flyway) and imports snapshots on start-up
# (SCHEDULING_IMPORT_ON_STARTUP=true) through the read-only login ad_sched_import.
# On-call runbook (probes, metrics, JSON logs, rollback): docs/runbooks/scheduling-service.md
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SVC="$ROOT/services/scheduling-service"
RUN_DIR="$ROOT/.run"
PID_FILE="$RUN_DIR/scheduling-service.pid"
LOG_FILE="$RUN_DIR/scheduling-service.log"
MSSQL_CONTAINER="${MSSQL_CONTAINER:-mssql}"
MSSQL_IMAGE="${MSSQL_IMAGE:-mcr.microsoft.com/mssql/server:2022-latest}"
export MSSQL_SA_PASSWORD="${MSSQL_SA_PASSWORD:-Str0ng!Passw0rd}"
PORT=8090

log() { echo "[run-scheduling] $*"; }
die() { echo "[run-scheduling] ERROR: $*" >&2; exit 1; }

find_java21() {
    if [ -n "${JAVA_HOME:-}" ] && "$JAVA_HOME/bin/java" -version 2>&1 | grep -q 'version "21'; then return; fi
    for d in /usr/lib/jvm/temurin-21-jdk-amd64 /usr/lib/jvm/java-21-openjdk-amd64 /usr/lib/jvm/java-21-openjdk \
             /usr/lib/jvm/openjdk-21; do
        if [ -x "$d/bin/java" ]; then export JAVA_HOME="$d"; return; fi
    done
    die "JDK 21 not found - install Temurin 21 or OpenJDK 21 and set JAVA_HOME"
}

sqlcmd_sa() {
    local s
    s="$(command -v sqlcmd || true)"; [ -z "$s" ] && [ -x /opt/mssql-tools18/bin/sqlcmd ] && s=/opt/mssql-tools18/bin/sqlcmd
    if [ -n "$s" ]; then "$s" -S localhost,1433 -U sa -P "$MSSQL_SA_PASSWORD" -C -b -h -1 "$@"
    else docker exec -i "$MSSQL_CONTAINER" /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -b -h -1 "$@"; fi
}

start_sql() {
    command -v docker >/dev/null 2>&1 || die "docker is required"
    if docker ps --format '{{.Names}}' | grep -qx "$MSSQL_CONTAINER"; then :
    elif docker ps -a --format '{{.Names}}' | grep -qx "$MSSQL_CONTAINER"; then docker start "$MSSQL_CONTAINER" >/dev/null
    else
        log "creating SQL Server 2022 container '$MSSQL_CONTAINER' on :1433"
        docker run -d --name "$MSSQL_CONTAINER" -e ACCEPT_EULA=Y -e "MSSQL_SA_PASSWORD=$MSSQL_SA_PASSWORD" \
            -e MSSQL_PID=Developer -p 1433:1433 "$MSSQL_IMAGE" >/dev/null
    fi
    for _ in $(seq 1 60); do sqlcmd_sa -Q "SELECT 1" >/dev/null 2>&1 && break; sleep 2; done
    sqlcmd_sa -Q "SELECT 1" >/dev/null 2>&1 || die "SQL Server did not come up"
    local has
    has="$(sqlcmd_sa -Q "SET NOCOUNT ON; SELECT CASE WHEN DB_ID('AssessmentDelivery') IS NULL THEN 0 ELSE 1 END" | tr -d '[:space:]')"
    if [ "$has" != "1" ] || [ "${RESEED_LEGACY:-0}" = "1" ]; then
        log "seeding AssessmentDelivery (db/apply.sh, also creates login ad_sched_import)"
        [ -n "${SQLCMD:-}" ] || command -v sqlcmd >/dev/null 2>&1 || [ -x /opt/mssql-tools18/bin/sqlcmd ] || [ -x /opt/mssql-tools/bin/sqlcmd ] \
            || die "seeding needs a host sqlcmd (db/apply.sh): install mssql-tools18"
        AD_SA_PASSWORD="$MSSQL_SA_PASSWORD" "$ROOT/db/apply.sh" >/dev/null
    else
        # older seeds predate db/security/035 - apply it idempotently
        sqlcmd_sa -I -i "$ROOT/db/security/035_sched_import_login.sql" >/dev/null
    fi
}

is_running() { [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; }

build() {
    [ "${SKIP_BUILD:-0}" = "1" ] && [ -f "$SVC/target/scheduling-service.jar" ] && return
    log "building (mvnw package, Java 21, frontend via frontend-maven-plugin)"
    (cd "$SVC" && ./mvnw -B -q -DskipTests package)
}

start() {
    is_running && { log "already running (pid $(cat "$PID_FILE")) on :$PORT"; return; }
    find_java21
    start_sql
    build
    mkdir -p "$RUN_DIR"
    # run from a copy so a later rebuild cannot rewrite the jar under the live JVM
    cp "$SVC/target/scheduling-service.jar" "$RUN_DIR/scheduling-service.jar"
    log "starting on :$PORT (log $LOG_FILE)"
    SCHEDULING_IMPORT_ON_STARTUP="${SCHEDULING_IMPORT_ON_STARTUP:-true}" \
    SCHEDULING_ATTENDANCE_SERVICE_TOKEN="${SCHEDULING_ATTENDANCE_SERVICE_TOKEN:-dev-attendance-token-not-for-prod}" \
    SCHEDULING_DB_PASSWORD="${SCHEDULING_DB_PASSWORD:-$MSSQL_SA_PASSWORD}" \
        nohup "$JAVA_HOME/bin/java" -jar "$RUN_DIR/scheduling-service.jar" >"$LOG_FILE" 2>&1 &
    echo $! >"$PID_FILE"
    for _ in $(seq 1 90); do
        if curl -sf "http://localhost:$PORT/actuator/health" >/dev/null 2>&1; then
            log "up: http://localhost:$PORT/app  (API http://localhost:$PORT/api/v2)"; return
        fi
        is_running || { tail -40 "$LOG_FILE" >&2; die "service exited"; }
        sleep 2
    done
    die "service did not become healthy - see $LOG_FILE"
}

stop() {
    if is_running; then kill "$(cat "$PID_FILE")"; rm -f "$PID_FILE"; log "stopped"; else log "not running"; fi
}

status() {
    if is_running; then log "running (pid $(cat "$PID_FILE")) - $(curl -s "http://localhost:$PORT/actuator/health" || echo unhealthy)"
    else log "not running"; fi
}

e2e() {
    (cd "$SVC/e2e" && npm ci --no-audit --no-fund && npx playwright install chromium && BASE_URL="http://localhost:$PORT" npx playwright test)
}

case "${1:-start}" in
    start) start ;; stop) stop ;; status) status ;; e2e) e2e ;;
    *) die "usage: $0 [start|status|stop|e2e]" ;;
esac
