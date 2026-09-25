#!/bin/bash
# Waits for SQL Server, then applies infra/db/*.sql (idempotent). Run as the db-init one-shot service.
set -euo pipefail
SQLCMD=/opt/mssql-tools18/bin/sqlcmd
HOST="${DB_HOST:-db}"
for i in $(seq 1 60); do
  if $SQLCMD -S "$HOST" -U sa -P "$MSSQL_SA_PASSWORD" -C -Q "SELECT 1" >/dev/null 2>&1; then break; fi
  echo "[db-init] waiting for SQL Server ($i)"; sleep 2
done
for f in /sql/*.sql; do
  echo "[db-init] applying $f"
  $SQLCMD -S "$HOST" -U sa -P "$MSSQL_SA_PASSWORD" -C -b -v REPORTING_DB_PASSWORD="$REPORTING_DB_PASSWORD" -i "$f"
done
echo "[db-init] done"
