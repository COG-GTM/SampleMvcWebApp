#!/usr/bin/env bash
# run-legacy.sh - start the AssessmentDelivery legacy estate on Linux (dev only)
#
#   scripts/run-legacy.sh [start]   SQL Server container + DB scripts + the three Java services
#   scripts/run-legacy.sh status    show what is running
#   scripts/run-legacy.sh stop      stop the Java services (the SQL container is left running)
#
# Environment overrides:
#   MSSQL_CONTAINER (mssql)  MSSQL_SA_PASSWORD (Str0ng!Passw0rd)  MSSQL_IMAGE
#   JAVA_HOME     must point at a JDK 8 (auto-detected under /usr/lib/jvm if unset)
#   SKIP_DB=1     do not (re)apply the database scripts
#   SKIP_BUILD=1  reuse the jars already in services/*/target
#
# The .NET portal (:8080) is Windows-only - see scripts/run-portal.ps1.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT/.run"
MSSQL_CONTAINER="${MSSQL_CONTAINER:-mssql}"
MSSQL_IMAGE="${MSSQL_IMAGE:-mcr.microsoft.com/mssql/server:2022-latest}"
export MSSQL_SA_PASSWORD="${MSSQL_SA_PASSWORD:-Str0ng!Passw0rd}"
SERVICES="booking-api:8081 results-service:8082 capacity-sync:8083"

log() { echo "[run-legacy] $*"; }
die() { echo "[run-legacy] ERROR: $*" >&2; exit 1; }

find_sqlcmd() {
    if command -v sqlcmd >/dev/null 2>&1; then
        SQLCMD="sqlcmd"
    elif [ -x /opt/mssql-tools18/bin/sqlcmd ]; then
        SQLCMD="/opt/mssql-tools18/bin/sqlcmd"
    elif [ -x /opt/mssql-tools/bin/sqlcmd ]; then
        SQLCMD="/opt/mssql-tools/bin/sqlcmd"
    else
        SQLCMD=""   # fall back to the sqlcmd inside the container
    fi
}

sa_sql() {  # sa_sql <args...>  (runs sqlcmd as sa, host tools or the container's)
    if [ -n "$SQLCMD" ]; then
        "$SQLCMD" -S localhost,1433 -U sa -P "$MSSQL_SA_PASSWORD" -C -b "$@"
    else
        docker exec -i "$MSSQL_CONTAINER" /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -b "$@"
    fi
}

find_java8() {
    if [ -n "${JAVA_HOME:-}" ] && "$JAVA_HOME/bin/java" -version 2>&1 | grep -q '"1\.8'; then
        return
    fi
    for d in /usr/lib/jvm/temurin-8-jdk-amd64 /usr/lib/jvm/java-8-openjdk-amd64 /usr/lib/jvm/java-1.8.0-openjdk-amd64 \
             /usr/lib/jvm/java-1.8.0 /usr/lib/jvm/jre-1.8.0; do
        if [ -x "$d/bin/java" ]; then
            export JAVA_HOME="$d"
            return
        fi
    done
    die "JDK 8 not found - install Temurin 8 or OpenJDK 8 and set JAVA_HOME"
}

start_sql() {
    command -v docker >/dev/null 2>&1 || die "docker is required"
    if docker ps --format '{{.Names}}' | grep -qx "$MSSQL_CONTAINER"; then
        log "SQL Server container '$MSSQL_CONTAINER' already running"
    elif docker ps -a --format '{{.Names}}' | grep -qx "$MSSQL_CONTAINER"; then
        log "starting existing container '$MSSQL_CONTAINER'"
        docker start "$MSSQL_CONTAINER" >/dev/null
    else
        log "creating SQL Server 2022 container '$MSSQL_CONTAINER' on :1433"
        docker run -d --name "$MSSQL_CONTAINER" -e ACCEPT_EULA=Y -e "MSSQL_SA_PASSWORD=$MSSQL_SA_PASSWORD" \
            -e MSSQL_PID=Developer -p 1433:1433 "$MSSQL_IMAGE" >/dev/null
    fi
    log "waiting for SQL Server to accept connections..."
    for _ in $(seq 1 60); do
        if sa_sql -Q "SELECT 1" >/dev/null 2>&1; then
            log "SQL Server is up"
            return
        fi
        sleep 2
    done
    die "SQL Server did not come up within 120s (docker logs $MSSQL_CONTAINER)"
}

apply_db() {
    if [ "${SKIP_DB:-0}" = "1" ]; then
        log "SKIP_DB=1, leaving the database as it is"
        return
    fi
    if [ -x "$ROOT/db/apply.sh" ] || [ -f "$ROOT/db/apply.sh" ]; then
        log "applying database via db/apply.sh"
        bash "$ROOT/db/apply.sh"
    else
        log "db/apply.sh not found, applying db/schema + db/procs"
        local f
        for f in "$ROOT"/db/schema/*.sql "$ROOT"/db/procs/*.sql; do
            log "  $(basename "$f")"
            if [ -n "$SQLCMD" ]; then
                sa_sql -i "$f" >/dev/null
            else
                sa_sql < "$f" >/dev/null
            fi
        done
    fi
}

build_services() {
    if [ "${SKIP_BUILD:-0}" = "1" ]; then
        log "SKIP_BUILD=1, using existing jars"
        return
    fi
    log "building services with the Maven wrapper (JAVA_HOME=$JAVA_HOME)"
    (cd "$ROOT/services" && ./mvnw -q -B -DskipTests package)
}

pid_alive() { [ -f "$1" ] && kill -0 "$(cat "$1")" 2>/dev/null; }

port_open() { (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null; }

start_services() {
    mkdir -p "$RUN_DIR"
    [ -f "$RUN_DIR/.gitignore" ] || echo "*" > "$RUN_DIR/.gitignore"
    local entry name port jar
    for entry in $SERVICES; do
        name="${entry%%:*}"; port="${entry##*:}"
        jar="$ROOT/services/$name/target/$name.jar"
        [ -f "$jar" ] || die "$jar missing - run without SKIP_BUILD"
        if pid_alive "$RUN_DIR/$name.pid"; then
            log "$name already running (pid $(cat "$RUN_DIR/$name.pid"))"
            continue
        fi
        if port_open "$port"; then
            die "port $port is already in use by another process"
        fi
        log "starting $name on :$port"
        nohup "$JAVA_HOME/bin/java" ${JAVA_OPTS:-} -jar "$jar" > "$RUN_DIR/$name.log" 2>&1 &
        echo $! > "$RUN_DIR/$name.pid"
    done
    for entry in $SERVICES; do
        name="${entry%%:*}"; port="${entry##*:}"
        for _ in $(seq 1 90); do
            port_open "$port" && break
            pid_alive "$RUN_DIR/$name.pid" || die "$name exited during startup, see .run/$name.log"
            sleep 1
        done
        port_open "$port" || die "$name did not open :$port, see .run/$name.log"
    done
}

print_urls() {
    cat <<URLS

  booking-api      http://localhost:8081/api/v1/sessions
  results-service  http://localhost:8082/api/v1/results/candidate/{candidateNumber}
  capacity-sync    http://localhost:8083/sync/last
  SQL Server       localhost,1433  db AssessmentDelivery  login ad_app

  logs and pids:   .run/
  portal (:8080) is Windows-only (.NET Framework 4.8) - run scripts/run-portal.ps1 on Windows
URLS
}

cmd_status() {
    find_sqlcmd
    if docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "$MSSQL_CONTAINER"; then
        echo "sql-server       running  (container $MSSQL_CONTAINER, :1433)"
    else
        echo "sql-server       stopped"
    fi
    local entry name port state
    for entry in $SERVICES; do
        name="${entry%%:*}"; port="${entry##*:}"
        if pid_alive "$RUN_DIR/$name.pid"; then
            state="running  (pid $(cat "$RUN_DIR/$name.pid"), :$port)"
        else
            state="stopped"
        fi
        printf '%-16s %s\n' "$name" "$state"
    done
    echo "portal           n/a on Linux (Windows-only)"
}

cmd_stop() {
    local entry name pid
    for entry in $SERVICES; do
        name="${entry%%:*}"
        if pid_alive "$RUN_DIR/$name.pid"; then
            pid="$(cat "$RUN_DIR/$name.pid")"
            log "stopping $name (pid $pid)"
            kill "$pid" 2>/dev/null || true
            for _ in $(seq 1 20); do kill -0 "$pid" 2>/dev/null || break; sleep 0.5; done
            kill -9 "$pid" 2>/dev/null || true
        fi
        rm -f "$RUN_DIR/$name.pid"
    done
    log "services stopped (SQL Server container left running: docker stop $MSSQL_CONTAINER)"
}

cmd_start() {
    find_sqlcmd
    find_java8
    start_sql
    apply_db
    build_services
    start_services
    log "all services up"
    print_urls
}

case "${1:-start}" in
    start)  cmd_start ;;
    stop)   cmd_stop ;;
    status) cmd_status ;;
    restart) cmd_stop; cmd_start ;;
    *) echo "usage: $0 [start|stop|status|restart]" >&2; exit 2 ;;
esac
