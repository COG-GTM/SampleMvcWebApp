#!/usr/bin/env bash
# Rebuilds the AssessmentDelivery database from scratch (DEV ONLY - drops the database).
# Runs schema -> procs -> triggers -> seed in file-name order.
#
#   AD_SQL_SERVER    default localhost,1433
#   AD_SQL_USER      default sa
#   AD_SA_PASSWORD   default Str0ng!Passw0rd (the local docker container)
#   SQLCMD           path to sqlcmd (default: sqlcmd on PATH, else /opt/mssql-tools18 or /opt/mssql-tools)
#
#   ./db/apply.sh            rebuild + seed
#   ./db/apply.sh --no-seed  rebuild without seed data
set -euo pipefail

DB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER="${AD_SQL_SERVER:-localhost,1433}"
SQL_USER="${AD_SQL_USER:-sa}"
SQL_PASSWORD="${AD_SA_PASSWORD:-Str0ng!Passw0rd}"
SEED=1
[ "${1:-}" = "--no-seed" ] && SEED=0

if [ -z "${SQLCMD:-}" ]; then
  if command -v sqlcmd >/dev/null 2>&1; then SQLCMD="$(command -v sqlcmd)"
  elif [ -x /opt/mssql-tools18/bin/sqlcmd ]; then SQLCMD=/opt/mssql-tools18/bin/sqlcmd
  elif [ -x /opt/mssql-tools/bin/sqlcmd ]; then SQLCMD=/opt/mssql-tools/bin/sqlcmd
  else echo "sqlcmd not found - install mssql-tools18 or set SQLCMD" >&2; exit 1
  fi
fi

# -b stop on error, -I QUOTED_IDENTIFIER ON (needed for the indexes/triggers), -C trust the dev cert
run_sql() {
  "$SQLCMD" -S "$SERVER" -U "$SQL_USER" -P "$SQL_PASSWORD" -C -b -I "$@"
}

echo "== waiting for $SERVER"
for i in $(seq 1 30); do
  if run_sql -Q "SELECT 1" >/dev/null 2>&1; then break; fi
  [ "$i" -eq 30 ] && { echo "SQL Server not reachable at $SERVER" >&2; exit 1; }
  sleep 2
done

echo "== dropping AssessmentDelivery"
run_sql -d master -Q "IF DB_ID('AssessmentDelivery') IS NOT NULL BEGIN ALTER DATABASE AssessmentDelivery SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE AssessmentDelivery; END"

folders="schema procs triggers"
[ "$SEED" -eq 1 ] && folders="$folders seed"

for folder in $folders; do
  for f in $(ls "$DB_DIR/$folder"/*.sql | sort); do
    echo "-- $folder/$(basename "$f")"
    if ! out="$(run_sql -d master -i "$f" 2>&1)"; then
      echo "$out" >&2; echo "FAILED: $f" >&2; exit 1
    fi
    echo "$out" | grep -v -e "^Changed database context" -e "^$" || true
  done
done

echo "== done"
run_sql -d AssessmentDelivery -W -i "$DB_DIR/checks/row-counts.sql" | head -n 12
