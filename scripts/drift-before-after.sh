#!/usr/bin/env bash
# drift-before-after.sh - seat drift before/after the single writer (MBA-3823); writes reports/drift-before-after.md
#
# BEFORE: the legacy estate at $BEFORE_REF (default legacy-baseline) in a separate worktree ($BEFORE_DIR, default
#         ../before): fresh seed, scripts/mixed-traffic.sh (portal procs + partner booking-api), then a capacity-sync
#         run (POST :8083/sync/run), then db/checks/seat-drift.sql. Expected: more than 0 rows.
# AFTER:  this checkout: fresh seed + single-writer DENYs, the three Java 8 services, scheduling-service with the
#         outbox relay, the routing gateway (:8000, when services/routing-gateway exists), the same workload through
#         the routing layer (ROUTING_URL), capacity-sync (now a skipped run), `reconcile` and seat-drift.sql.
#         Expected: 0 mismatches and 0 rows.
#
# Re-runnable on any later head (M6 parity). DROPS and re-creates AssessmentDelivery and Scheduling - dev only.
# Env: BEFORE_REF, BEFORE_DIR, MSSQL_SA_PASSWORD (Str0ng!Passw0rd), MVNW_REPOURL, SKIP_BUILD=1 (reuse jars),
#      REPORT (reports/drift-before-after.md)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BEFORE_REF="${BEFORE_REF:-legacy-baseline}"
BEFORE_DIR="${BEFORE_DIR:-$(dirname "$ROOT")/before}"
REPORT="${REPORT:-$ROOT/reports/drift-before-after.md}"
WORK="$ROOT/.run/drift"
export MSSQL_SA_PASSWORD="${MSSQL_SA_PASSWORD:-Str0ng!Passw0rd}"
export MVNW_REPOURL="${MVNW_REPOURL:-https://maven-central.storage-download.googleapis.com/maven2}"
SQLCMD="${SQLCMD:-/opt/mssql-tools18/bin/sqlcmd}"
GATEWAY=0; [ -d "$ROOT/services/routing-gateway" ] && GATEWAY=1

log() { echo "[drift-before-after] $*"; }
die() { echo "[drift-before-after] ERROR: $*" >&2; exit 1; }
sa() { "$SQLCMD" -S localhost,1433 -U sa -P "$MSSQL_SA_PASSWORD" -C -b "$@"; }

drift() {  # drift <out-file>  -> prints the row count, saves the seat-drift.sql output
    sa -W -s '|' -i "$ROOT/db/checks/seat-drift.sql" | grep -v -e '^Changed database context' > "$1" || true
    sa -h -1 -W -Q "SET NOCOUNT ON; SELECT COUNT(*) FROM AssessmentDelivery.dbo.ExamSession s
        LEFT JOIN (SELECT SessionId, COUNT(*) n FROM AssessmentDelivery.dbo.Booking WHERE Status IN ('BOOKED','ATTENDED')
                   GROUP BY SessionId) b ON b.SessionId = s.SessionId
        WHERE s.SeatsAvailable <> s.Capacity - ISNULL(b.n, 0)" | tr -d '[:space:]'
}

stop_all() {
    [ -d "$BEFORE_DIR" ] && (cd "$BEFORE_DIR" && bash scripts/run-legacy.sh stop >/dev/null 2>&1 || true)
    [ "$GATEWAY" = 1 ] && (bash "$ROOT/scripts/run-routing-gateway.sh" stop >/dev/null 2>&1 || true)
    bash "$ROOT/scripts/run-scheduling-service.sh" stop >/dev/null 2>&1 || true
    bash "$ROOT/scripts/run-legacy.sh" stop >/dev/null 2>&1 || true
}

wait_http() {  # wait_http <url> <seconds>
    for _ in $(seq 1 "$2"); do curl -fs -o /dev/null "$1" && return 0; sleep 1; done
    die "no answer from $1"
}

mkdir -p "$WORK" "$(dirname "$REPORT")"
AFTER_SHA="$(git -C "$ROOT" rev-parse HEAD)"
DIRTY=""; [ -n "$(git -C "$ROOT" status --porcelain -- . ':!reports')" ] && DIRTY=" (+ uncommitted changes)"
stop_all

# ---------------------------------------------------------------- BEFORE
if [ ! -d "$BEFORE_DIR" ]; then
    log "git worktree add $BEFORE_DIR $BEFORE_REF"
    git -C "$ROOT" worktree add --detach "$BEFORE_DIR" "$BEFORE_REF" >/dev/null
else
    git -C "$BEFORE_DIR" checkout -q --detach "$BEFORE_REF"
fi
BEFORE_SHA="$(git -C "$BEFORE_DIR" rev-parse HEAD)"
log "BEFORE: $BEFORE_REF ($BEFORE_SHA) - fresh seed + legacy services"
sa -Q "IF DB_ID('Scheduling') IS NOT NULL BEGIN ALTER DATABASE Scheduling SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE Scheduling; END" >/dev/null
(cd "$BEFORE_DIR" && bash scripts/run-legacy.sh start) > "$WORK/before-run-legacy.log" 2>&1 || die "baseline start failed, see $WORK/before-run-legacy.log"
wait_http http://localhost:8081/api/v1/sessions 60
(cd "$BEFORE_DIR" && bash scripts/mixed-traffic.sh) > "$WORK/before-mixed-traffic.log" 2>&1
BEFORE_SYNC="$(curl -s -X POST http://localhost:8083/sync/run)"
BEFORE_ROWS="$(drift "$WORK/before-seat-drift.txt")"
log "BEFORE: seat-drift rows = $BEFORE_ROWS"
(cd "$BEFORE_DIR" && bash scripts/run-legacy.sh stop) >/dev/null 2>&1 || true

# ---------------------------------------------------------------- AFTER
log "AFTER: $AFTER_SHA$DIRTY - fresh seed + DENYs, legacy services, scheduling-service + relay"
export ROUTING_URL="http://localhost:8090"
[ "$GATEWAY" = 1 ] && ROUTING_URL="http://localhost:8000"
bash "$ROOT/scripts/run-legacy.sh" start > "$WORK/after-run-legacy.log" 2>&1 || die "start failed, see $WORK/after-run-legacy.log"
SKIP_BUILD="${SKIP_BUILD:-0}" bash "$ROOT/scripts/run-scheduling-service.sh" start > "$WORK/after-scheduling.log" 2>&1 \
    || die "scheduling-service start failed, see $WORK/after-scheduling.log"
if [ "$GATEWAY" = 1 ]; then
    bash "$ROOT/scripts/run-routing-gateway.sh" start > "$WORK/after-gateway.log" 2>&1 || die "gateway start failed, see $WORK/after-gateway.log"
    wait_http http://localhost:8000/actuator/health 90
fi
wait_http http://localhost:8081/api/v1/sessions 60
bash "$ROOT/scripts/mixed-traffic.sh" > "$WORK/after-mixed-traffic.log" 2>&1
AFTER_SYNC="$(curl -s -X POST http://localhost:8083/sync/run)"
JAR="$ROOT/services/scheduling-service/target/scheduling-service.jar"
set +e
SCHEDULING_DB_PASSWORD="$MSSQL_SA_PASSWORD" RECONCILIATION_REPORT_DIR="$ROOT/reports" \
    /usr/lib/jvm/java-21-openjdk-amd64/bin/java -jar "$JAR" reconcile --wait-seconds=30 > "$WORK/after-reconcile.log" 2>&1
RECONCILE_EXIT=$?
set -e
RECONCILE_LINE="$(grep '^reconcile:' "$WORK/after-reconcile.log" | tail -1)"
MISMATCHES="$(echo "$RECONCILE_LINE" | grep -o 'mismatches=[0-9]*' | cut -d= -f2)"
AFTER_ROWS="$(drift "$WORK/after-seat-drift.txt")"
log "AFTER: seat-drift rows = $AFTER_ROWS, reconcile mismatches = ${MISMATCHES:-?} (exit $RECONCILE_EXIT)"
RECON_REPORT="$(echo "$RECONCILE_LINE" | grep -o 'report=.*' | cut -d= -f2-)"

summary() { sed -n '/session summary/,$p' "$1" | grep -v 'seat-drift.sql:' | grep -v '\] done'; }
{
    echo "# Seat drift before/after MBA-3823 (single writer + outbox relay)"
    echo
    echo "Generated by \`scripts/drift-before-after.sh\` on $(date -u '+%Y-%m-%d %H:%M:%SZ'). Same workload both times:"
    echo "\`scripts/mixed-traffic.sh\` (20 portal + 20 partner bookings, 3 cancels per channel, fresh seed) followed by a"
    echo "capacity-sync run (\`POST :8083/sync/run\`)."
    echo
    echo "| | Code | Commit | seat-drift.sql rows | reconcile mismatches |"
    echo "|---|---|---|---|---|"
    echo "| Before | \`$BEFORE_REF\` | \`$BEFORE_SHA\` | **$BEFORE_ROWS** | n/a (no scheduling-service) |"
    echo "| After | \`$(git -C "$ROOT" rev-parse --abbrev-ref HEAD)\`$DIRTY | \`$AFTER_SHA\` | **$AFTER_ROWS** | **${MISMATCHES:-?}** (reconcile exit $RECONCILE_EXIT) |"
    echo
    echo "After-run write path: portal channel \`POST/DELETE $ROUTING_URL/api/v2/bookings\`, partner channel booking-api"
    echo "\`:8081\` -> \`$ROUTING_URL\` -> scheduling-service -> outbox -> relay (\`ad_sched_relay\`) -> AssessmentDelivery."
    [ "$GATEWAY" = 1 ] || echo "services/routing-gateway is not on this branch, so ROUTING_URL points at scheduling-service directly."
    echo
    echo "## Before ($BEFORE_REF)"
    echo; echo '```'; summary "$WORK/before-mixed-traffic.log"; echo "capacity-sync: $BEFORE_SYNC"; echo '```'
    echo; echo "db/checks/seat-drift.sql:"; echo; echo '```'; cat "$WORK/before-seat-drift.txt"; echo '```'
    echo
    echo "## After ($AFTER_SHA)"
    echo; echo '```'; summary "$WORK/after-mixed-traffic.log"; echo "capacity-sync: $AFTER_SYNC"; echo "$RECONCILE_LINE"; echo '```'
    echo; echo "db/checks/seat-drift.sql:"; echo; echo '```'; cat "$WORK/after-seat-drift.txt"; echo '```'
    [ -n "$RECON_REPORT" ] && { echo; echo "Reconciliation report: \`${RECON_REPORT#$ROOT/}\`"; }
} > "$REPORT"
log "wrote ${REPORT#$ROOT/}"
[ "$BEFORE_ROWS" -gt 0 ] && [ "$AFTER_ROWS" = 0 ] && [ "${MISMATCHES:-1}" = 0 ]
