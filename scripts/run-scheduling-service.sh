#!/usr/bin/env bash
# run-scheduling-service.sh - start scheduling-service (Java 21 / Spring Boot 3) on :8090 (dev only)
#
#   scripts/run-scheduling-service.sh [start]   SQL Server + read-only importer login + build + java -jar
#   scripts/run-scheduling-service.sh status
#   scripts/run-scheduling-service.sh stop
#
# The service owns the Scheduling database (created by Flyway on first start) and imports
# reference data, sessions, bookings and waitlists from AssessmentDelivery with the read-only
# ad_sched_import login (db/security/035_sched_import_login.sql). It never writes to AssessmentDelivery.
#
# Environment overrides:
#   MSSQL_CONTAINER (mssql)  MSSQL_SA_PASSWORD (dev default, same as run-legacy.sh)  MSSQL_IMAGE
#   JAVA_HOME     must point at a JDK 21 (auto-detected under /usr/lib/jvm if unset)
#   APPLY_DB=1    (re-)create AssessmentDelivery with db/apply.sh first (DROPS it). Default: only
#                 when it does not exist yet.
#   SKIP_BUILD=1  reuse services/scheduling-service/target/scheduling-service.jar
#   SCHEDULING_IMPORT_ON_STARTUP (true)  import all centers at startup (idempotent)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODULE="$ROOT/services/scheduling-service"
RUN_DIR="$ROOT/.run"
PID_FILE="$RUN_DIR/scheduling-service.pid"
LOG_FILE="$RUN_DIR/scheduling-service.log"
PORT=8090
MSSQL_CONTAINER="${MSSQL_CONTAINER:-mssql}"
MSSQL_IMAGE="${MSSQL_IMAGE:-mcr.microsoft.com/mssql/server:2022-latest}"
export MSSQL_SA_PASSWORD="${MSSQL_SA_PASSWORD:-Str0ng!Passw0rd}"

log() { echo "[run-scheduling-service] $*"; }
die() { echo "[run-scheduling-service] ERROR: $*" >&2; exit 1; }

find_sqlcmd() {
    # db/apply.sh and the -i scripts below need a host sqlcmd (the files are read from this checkout).
    if command -v sqlcmd >/dev/null 2>&1; then SQLCMD="$(command -v sqlcmd)"
    elif [ -x /opt/mssql-tools18/bin/sqlcmd ]; then SQLCMD=/opt/mssql-tools18/bin/sqlcmd
    else die "sqlcmd not found - install mssql-tools18 (https://learn.microsoft.com/sql/linux/sql-server-linux-setup-tools)"
    fi
}

sa_sql() {
    "$SQLCMD" -S localhost,1433 -U sa -P "$MSSQL_SA_PASSWORD" -C -b "$@"
}

find_java21() {
    if [ -n "${JAVA_HOME:-}" ] && "$JAVA_HOME/bin/java" -version 2>&1 | grep -q 'version "21'; then return; fi
    for d in /usr/lib/jvm/java-21-openjdk-amd64 /usr/lib/jvm/temurin-21-jdk-amd64 /usr/lib/jvm/java-21-openjdk /usr/lib/jvm/jdk-21; do
        if [ -x "$d/bin/java" ]; then export JAVA_HOME="$d"; return; fi
    done
    die "JDK 21 not found - install OpenJDK/Temurin 21 and set JAVA_HOME"
}

start_sql() {
    command -v docker >/dev/null 2>&1 || die "docker is required"
    find_sqlcmd
    if ! docker ps --format '{{.Names}}' | grep -qx "$MSSQL_CONTAINER"; then
        if docker ps -a --format '{{.Names}}' | grep -qx "$MSSQL_CONTAINER"; then
            log "starting existing container '$MSSQL_CONTAINER'"
            docker start "$MSSQL_CONTAINER" >/dev/null
        else
            log "creating SQL Server 2022 container '$MSSQL_CONTAINER' on :1433"
            docker run -d --name "$MSSQL_CONTAINER" -e ACCEPT_EULA=Y -e "MSSQL_SA_PASSWORD=$MSSQL_SA_PASSWORD" \
                -e MSSQL_PID=Developer -p 1433:1433 "$MSSQL_IMAGE" >/dev/null
        fi
    fi
    for _ in $(seq 1 60); do
        sa_sql -Q "SELECT 1" >/dev/null 2>&1 && return
        sleep 2
    done
    die "SQL Server did not come up within 120s (docker logs $MSSQL_CONTAINER)"
}

prepare_legacy_db() {
    local exists
    exists="$(sa_sql -h -1 -W -Q "SET NOCOUNT ON; SELECT CASE WHEN DB_ID('AssessmentDelivery') IS NULL THEN 0 ELSE 1 END" | tr -d '[:space:]')"
    if [ "${APPLY_DB:-0}" = "1" ] || [ "$exists" != "1" ]; then
        log "creating AssessmentDelivery with db/apply.sh (drops it if present)"
        SQLCMD="$SQLCMD" AD_SA_PASSWORD="$MSSQL_SA_PASSWORD" bash "$ROOT/db/apply.sh" >/dev/null
    fi
    log "ensuring the read-only importer login (db/security/035_sched_import_login.sql)"
    sa_sql -i "$ROOT/db/security/035_sched_import_login.sql" >/dev/null
}

build() {
    if [ "${SKIP_BUILD:-0}" = "1" ]; then log "SKIP_BUILD=1, using the existing jar"; return; fi
    log "building scheduling-service (frontend included, tests skipped; JAVA_HOME=$JAVA_HOME)"
    (cd "$MODULE" && mvn -q -B -DskipTests package)
}

pid_alive() { [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; }
port_open() { (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null; }

cmd_start() {
    find_java21
    pid_alive && { log "already running (pid $(cat "$PID_FILE"))"; return; }
    port_open "$PORT" && die "port $PORT is already in use"
    start_sql
    prepare_legacy_db
    build
    local jar="$MODULE/target/scheduling-service.jar"
    [ -f "$jar" ] || die "$jar missing - run without SKIP_BUILD"
    mkdir -p "$RUN_DIR"
    [ -f "$RUN_DIR/.gitignore" ] || echo "*" > "$RUN_DIR/.gitignore"
    log "starting scheduling-service on :$PORT (log: .run/scheduling-service.log)"
    SCHEDULING_IMPORT_ON_STARTUP="${SCHEDULING_IMPORT_ON_STARTUP:-true}" SCHEDULING_DB_PASSWORD="$MSSQL_SA_PASSWORD" \
        nohup "$JAVA_HOME/bin/java" ${JAVA_OPTS:-} -jar "$jar" > "$LOG_FILE" 2>&1 &
    echo $! > "$PID_FILE"
    for _ in $(seq 1 120); do
        curl -fs "http://localhost:$PORT/actuator/health" >/dev/null 2>&1 && break
        pid_alive || die "scheduling-service exited during startup, see .run/scheduling-service.log"
        sleep 1
    done
    curl -fs "http://localhost:$PORT/actuator/health" >/dev/null 2>&1 || die "no health on :$PORT, see $LOG_FILE"
    cat <<URLS

  modern UI        http://localhost:$PORT/app
  center admin     http://localhost:$PORT/app/admin/centers/BOS01
  API              http://localhost:$PORT/api/v2/centers/BOS01/sessions
  routing          http://localhost:$PORT/internal/routing/resolve?centerCode=BOS01
  database         localhost,1433  db Scheduling (own); reads AssessmentDelivery as ad_sched_import
URLS
}

cmd_status() {
    if pid_alive; then echo "scheduling-service running (pid $(cat "$PID_FILE"), :$PORT)"; else echo "scheduling-service stopped"; fi
}

cmd_stop() {
    if pid_alive; then
        local pid; pid="$(cat "$PID_FILE")"
        log "stopping (pid $pid)"
        kill "$pid" 2>/dev/null || true
        for _ in $(seq 1 20); do kill -0 "$pid" 2>/dev/null || break; sleep 0.5; done
        kill -9 "$pid" 2>/dev/null || true
    fi
    rm -f "$PID_FILE"
}

case "${1:-start}" in
    start) cmd_start ;;
    status) cmd_status ;;
    stop) cmd_stop ;;
    *) die "usage: $0 [start|status|stop]" ;;
esac
