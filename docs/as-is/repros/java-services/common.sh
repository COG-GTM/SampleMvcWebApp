#!/usr/bin/env bash
# Shared helpers for the java-services repros. Run with Git Bash on Windows ("$BASH" script.sh)
# or bash on Linux, against an estate started by scripts/run-legacy.ps1 / run-legacy.sh.
set -u
SQLCMD="${SQLCMD:-$(command -v sqlcmd || echo "/c/Program Files/SqlCmd/sqlcmd.exe")}"
[ -x "$SQLCMD" ] || SQLCMD=/opt/mssql-tools18/bin/sqlcmd
SA_PW="${AD_SA_PASSWORD:-Str0ng!Passw0rd}"
BOOKING="${BOOKING_API_URL:-http://localhost:8081}"
RESULTS="${RESULTS_URL:-http://localhost:8082}"
SYNC="${CAPACITY_SYNC_URL:-http://localhost:8083}"

sql() {   # sql "<T-SQL>"  -> tab-free, header-on, trimmed output
  "$SQLCMD" -S localhost,1433 -U sa -P "$SA_PW" -C -d AssessmentDelivery -W -Q "SET NOCOUNT ON; $1" < /dev/null | tr -d '\r'
}
say() { printf '\n$ %s\n' "$*"; }
api() {   # api METHOD URL [json-body]
  local m=$1 u=$2 b=${3:-}
  if [ -n "$b" ]; then say "curl -X $m $u -d '$b'"; curl -s -w '\nHTTP %{http_code}\n' -X "$m" -H 'Content-Type: application/json' "$u" -d "$b"
  else say "curl -X $m $u"; curl -s -w '\nHTTP %{http_code}\n' -X "$m" "$u"; fi | tr -d '\r'
}
seat_row() {  # seat_row <sessionId>: counter vs bookings
  sql "SELECT s.SessionId, s.Capacity, s.AccommodationCapacity AS AccCap, s.SeatsAvailable,
       (SELECT COUNT(*) FROM dbo.Booking b WHERE b.SessionId=s.SessionId AND b.Status IN ('BOOKED','ATTENDED')) AS SeatHolding,
       s.Capacity-(SELECT COUNT(*) FROM dbo.Booking b WHERE b.SessionId=s.SessionId AND b.Status IN ('BOOKED','ATTENDED')) AS ExpectedPerSeatDriftSql
       FROM dbo.ExamSession s WHERE s.SessionId=$1"
}
val() { sql "$1" | grep -E '^-?[0-9]+$' | tail -1; }   # single integer result
