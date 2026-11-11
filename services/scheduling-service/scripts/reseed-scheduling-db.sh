#!/usr/bin/env bash
# reseed-scheduling-db.sh - restore the Scheduling DB to the legacy seed (dev/replay only).
#
# 1. Restores AssessmentDelivery to the deterministic seed: the first run executes db/apply.sh and takes a
#    COPY_ONLY backup, later runs RESTORE that backup (seconds). Needed because some M1 scenarios write to
#    AssessmentDelivery directly (db-proc steps, SQL setup) - this script does that as sa, never the service.
#    RESEED_LEGACY=0 skips this step; RESEED_LEGACY=apply forces db/apply.sh + a fresh backup.
# 2. POST /api/v2/internal/import/sessions?mode=reset: the service re-copies sessions, bookings and waitlist
#    (keeping legacy ids) through its read-only login and refreshes the snapshots.
#
#   SCHEDULING_URL (http://localhost:8090)  AD_SQL_SERVER (localhost,1433)  AD_SA_PASSWORD (Str0ng!Passw0rd)
#
# Replay (characterization/README.md):
#   characterization/replay --target modern --base-url http://localhost:8090 --compare http \
#       --reseed command --reseed-cmd services/scheduling-service/scripts/reseed-scheduling-db.sh
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
URL="${SCHEDULING_URL:-http://localhost:8090}"
SERVER="${AD_SQL_SERVER:-localhost,1433}"
export AD_SA_PASSWORD="${AD_SA_PASSWORD:-${MSSQL_SA_PASSWORD:-Str0ng!Passw0rd}}"
BAK="${RESEED_BACKUP_PATH:-/var/opt/mssql/data/AssessmentDelivery_scheduling_seed.bak}"
MODE="${RESEED_LEGACY:-restore}"

SQLCMD="$(command -v sqlcmd || echo /opt/mssql-tools18/bin/sqlcmd)"
sql() { "$SQLCMD" -S "$SERVER" -U sa -P "$AD_SA_PASSWORD" -C -b -h -1 -d master -Q "SET NOCOUNT ON; $1"; }

backup_exists() {
    [ "$(sql "DECLARE @r TABLE (e INT, d INT, p INT); INSERT @r EXEC master.dbo.xp_fileexist N'$BAK'; SELECT e FROM @r;" | tr -d '[:space:]')" = "1" ]
}

if [ "$MODE" != "0" ]; then
    if [ "$MODE" = "apply" ] || ! backup_exists; then
        "$ROOT/db/apply.sh" >/dev/null
        sql "BACKUP DATABASE [AssessmentDelivery] TO DISK = N'$BAK' WITH INIT, FORMAT, COPY_ONLY;" >/dev/null
    else
        sql "ALTER DATABASE [AssessmentDelivery] SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
             RESTORE DATABASE [AssessmentDelivery] FROM DISK = N'$BAK' WITH REPLACE;
             ALTER DATABASE [AssessmentDelivery] SET MULTI_USER;" >/dev/null
    fi
fi

out="$(curl -sS -f -X POST "$URL/api/v2/internal/import/sessions?mode=reset")" || {
    echo "reseed failed: is scheduling-service running at $URL?" >&2; exit 1; }
[ -n "${RESEED_VERBOSE:-}" ] && echo "$out"
exit 0
