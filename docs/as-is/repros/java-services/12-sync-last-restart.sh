#!/usr/bin/env bash
# /sync/last is in-memory only: after a restart it reports NEVER_RUN although AuditLog has the runs.
# Windows: stops the capacity-sync JVM and restarts it with scripts/run-legacy.ps1 -SkipDb -SkipBuild -NoPortal
# (running services are left alone by Start-Services).
. "$(dirname "$0")/common.sh"
ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
api GET "$SYNC/sync/last"
sql "SELECT COUNT(*) AS CapacitySyncAuditRows FROM dbo.AuditLog WHERE Action='CAPACITY_SYNC'"
say "stop capacity-sync JVM and restart via run-legacy.ps1 -SkipDb -SkipBuild -NoPortal"
powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -like '*capacity-sync*' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force; 'stopped pid ' + \$_.ProcessId }" < /dev/null
powershell -NoProfile -ExecutionPolicy Bypass -File "$(cygpath -w "$ROOT/scripts/run-legacy.ps1")" -SkipDb -SkipBuild -NoPortal < /dev/null 2>&1 | tr -d '\r' | grep -i 'capacity-sync' | head -3
api GET "$SYNC/sync/last"
sql "SELECT COUNT(*) AS CapacitySyncAuditRows FROM dbo.AuditLog WHERE Action='CAPACITY_SYNC'"
