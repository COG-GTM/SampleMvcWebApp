#!/usr/bin/env bash
# End-to-end: results CSV through results-service -> booking ATTENDED in scheduling-service, seat drift 0.
# Needs the estate from scripts/run-legacy.sh start (with scheduling-service) and MSSQL_SA_PASSWORD.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SCHED="${SCHEDULING_BASE_URL:-http://localhost:8084/api/v1}"
RESULTS="${RESULTS_URL:-http://localhost:8082/api/v1}"
: "${MSSQL_SA_PASSWORD:?set MSSQL_SA_PASSWORD}"
sql() { docker exec -i -e SQLCMDPASSWORD="$MSSQL_SA_PASSWORD" mssql /opt/mssql-tools18/bin/sqlcmd -C -S localhost -U sa -h -1 -W "$@"; }

session=$(sql -Q "SET NOCOUNT ON; SELECT TOP 1 SessionId FROM Scheduling.dbo.ExamSession
  WHERE Status = 'OPEN' AND StartsAt > GETDATE() AND SeatsAvailable > 0 ORDER BY SessionId" | tr -d '[:space:]')
candidate=$(sql -Q "SET NOCOUNT ON; SELECT TOP 1 c.CandidateNumber FROM Scheduling.dbo.RefCandidate c
  WHERE NOT EXISTS (SELECT 1 FROM Scheduling.dbo.Booking b WHERE b.CandidateId = c.CandidateId AND b.SessionId = $session)
  ORDER BY c.CandidateId DESC" | tr -d '[:space:]')
echo "== book $candidate on session $session through scheduling-service"
booking=$(curl -sf -X POST "$SCHED/bookings" -H 'Content-Type: application/json' -H 'X-Actor: e2e' \
  -d "{\"sessionId\":$session,\"candidateNumber\":\"$candidate\",\"channel\":\"PORTAL\"}")
echo "$booking"
id=$(echo "$booking" | sed -n 's/.*"bookingId":\([0-9]*\).*/\1/p')

echo "== upload score file to results-service"
csv="$(mktemp --suffix=.csv)"
printf 'CandidateNumber,SessionId,Score,ScoredAt\n%s,%s,742,2026-11-11T12:00:00\n' "$candidate" "$session" > "$csv"
curl -sf -F "file=@$csv;filename=E2E_ATTEND_$id.csv" "$RESULTS/results/upload"; echo
rm -f "$csv"

echo "== booking $id in scheduling-service"
curl -sf "$SCHED/bookings/$id"; echo
status=$(curl -sf "$SCHED/bookings/$id" | sed -n 's/.*"status":"\([A-Z]*\)".*/\1/p')

echo "== seat drift for session $session (seat-drift.sql formula: SeatsAvailable vs Capacity - BOOKED/ATTENDED)"
sql -Q "SET NOCOUNT ON; SELECT 'SessionId=' + CAST(s.SessionId AS varchar) + ' Capacity=' + CAST(s.Capacity AS varchar)
  + ' SeatsAvailable=' + CAST(s.SeatsAvailable AS varchar) + ' Active=' + CAST(b.n AS varchar)
  FROM Scheduling.dbo.ExamSession s CROSS APPLY (SELECT COUNT(*) n FROM Scheduling.dbo.Booking
  WHERE SessionId = s.SessionId AND Status IN ('BOOKED','ATTENDED')) b WHERE s.SessionId = $session"
drift=$(sql -Q "SET NOCOUNT ON; SELECT (s.Capacity - (SELECT COUNT(*) FROM Scheduling.dbo.Booking b
  WHERE b.SessionId = s.SessionId AND b.Status IN ('BOOKED','ATTENDED'))) - s.SeatsAvailable
  FROM Scheduling.dbo.ExamSession s WHERE s.SessionId = $session" | tr -d '[:space:]')
echo "drift=$drift"
[ "$status" = "ATTENDED" ] && [ "$drift" = "0" ] && echo "E2E PASS" || { echo "E2E FAIL"; exit 1; }
