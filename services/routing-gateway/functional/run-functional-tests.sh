#!/usr/bin/env bash
# Functional tests for routing-gateway against the REAL legacy booking-api/results-service/capacity-sync
# (scripts/run-legacy.sh, SQL Server in Docker), scheduling-service on :8090 and a portal stand-in on :8080.
#   SCHEDULING=real (default): the real scheduling-service (scripts/run-scheduling-service.sh). It is restarted on a
#                              fresh Scheduling database and a fresh AssessmentDelivery import so runs are repeatable.
#   SCHEDULING=stub:           functional/scheduling_stub.py (contract stand-in, no Scheduling database needed).
# NOTE: rebuilds the AssessmentDelivery database (db/apply.sh) several times and, with SCHEDULING=real, drops Scheduling.
#
#   services/routing-gateway/functional/run-functional-tests.sh
#
# Env: SCHEDULING, JAVA21_HOME, AD_DB_USER/AD_DB_PASSWORD (default ad_portal), AD_SA_PASSWORD (default Str0ng!Passw0rd), REPORT (JSON report path), KEEP_RUNNING=1.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE="$(cd "$HERE/.." && pwd)"
ROOT="$(cd "$MODULE/../.." && pwd)"
JAVA21_HOME="${JAVA21_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
WORK="$MODULE/target/functional"
mkdir -p "$WORK"
export ROUTING_CONFIG_FILE="$WORK/routing.properties"
printf 'routing.modern-centers=\nrouting.modern-write-centers=*\nrouting.coexistence-mode=relay\n' > "$ROUTING_CONFIG_FILE"
PIDS=()
cleanup() { if [[ "${KEEP_RUNNING:-0}" != "1" ]]; then for p in "${PIDS[@]}"; do kill "$p" 2>/dev/null || true; done; fi; }
trap cleanup EXIT

for port in 8081 8082 8083; do
  curl -fsS -o /dev/null "http://localhost:$port/" 2>/dev/null || curl -sS -o /dev/null "http://localhost:$port/" \
    || { echo "legacy service on :$port is not running - start it with scripts/run-legacy.sh" >&2; exit 1; }
done
export SCHEDULING="${SCHEDULING:-real}"
STUB_PORTS="8000 8080"; [[ "$SCHEDULING" == "stub" ]] && STUB_PORTS="$STUB_PORTS 8090"
for port in $STUB_PORTS; do
  if ss -ltn | grep -q ":$port "; then echo "port $port is busy" >&2; exit 1; fi
done

[[ -f "$MODULE/target/routing-gateway.jar" ]] || (cd "$ROOT/services" && JAVA_HOME="$JAVA21_HOME" ./mvnw -q -B -f routing-gateway/pom.xml -DskipTests package)

cd "$HERE"
# M4 dropped the shared ad_app login (stub_common.py default); read-only checks use the portal login unless overridden.
export AD_DB_USER="${AD_DB_USER:-ad_portal}" AD_DB_PASSWORD="${AD_DB_PASSWORD:-P0rtal!Dev2026}"
if [[ "$SCHEDULING" == "real" ]]; then
  SA="${AD_SA_PASSWORD:-Str0ng!Passw0rd}"
  "$ROOT/scripts/run-scheduling-service.sh" stop
  /opt/mssql-tools18/bin/sqlcmd -C -S localhost,1433 -U sa -P "$SA" -b \
    -Q "IF DB_ID('Scheduling') IS NOT NULL BEGIN ALTER DATABASE Scheduling SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE Scheduling; END"
  SKIP=0; [[ -f "$ROOT/services/scheduling-service/target/scheduling-service.jar" ]] && SKIP=1
  APPLY_DB=1 SKIP_BUILD="${SCHED_SKIP_BUILD:-$SKIP}" MSSQL_SA_PASSWORD="$SA" "$ROOT/scripts/run-scheduling-service.sh" start \
    > "$WORK/scheduling-service-start.log" 2>&1 || { tail "$WORK/scheduling-service-start.log"; exit 1; }
  echo "scheduling-service: real (fresh Scheduling DB, imported from a fresh AssessmentDelivery)"
else
  python3 scheduling_stub.py 8090 > "$WORK/scheduling-stub.log" 2>&1 & PIDS+=($!)
fi
python3 portal_stub.py 8080 > "$WORK/portal-stub.log" 2>&1 & PIDS+=($!)

# Startup guard: a production center without relay must refuse to start
if "$JAVA21_HOME/bin/java" -jar "$MODULE/target/routing-gateway.jar" --server.port=0 \
     --routing.config-file="$WORK/none.properties" --routing.modern-centers=NYC02 > "$WORK/guard.log" 2>&1; then
  echo "FAIL startup guard: gateway started with routing.modern-centers=NYC02 and no relay"; GUARD=1
else
  grep -q "Refusing to start" "$WORK/guard.log" && echo "PASS startup guard: refuses to start with routing.modern-centers=NYC02 unless routing.coexistence-mode=relay" && GUARD=0 || { echo "FAIL startup guard: unexpected failure"; tail "$WORK/guard.log"; GUARD=1; }
fi

"$JAVA21_HOME/bin/java" -jar "$MODULE/target/routing-gateway.jar" --routing.config-file="$ROUTING_CONFIG_FILE" \
  > "$WORK/gateway.log" 2>&1 & PIDS+=($!)
for _ in $(seq 1 60); do
  curl -fsS http://localhost:8000/actuator/health >/dev/null 2>&1 && curl -fsS -o /dev/null http://localhost:8090/app && break
  sleep 1
done

set +e
python3 functional_test.py
FAILS=$?
echo "route log sample:"; grep -E "route=(legacy|modern)" "$WORK/gateway.log" | grep -E "route=modern" | head -3
exit $((FAILS + GUARD))
