#!/usr/bin/env bash
# MBA-3827 end-to-end check of idempotent results ingestion against a running estate
# (scripts/run-legacy.sh; db/schema/015_result_upload.sql applied). Uses unbooked seed
# candidates, so run it on a fresh seed or set CANDIDATE_BASE to an unused block.
. "$(dirname "$0")/../../../docs/as-is/repros/java-services/common.sh"
set -e
BASE=${CANDIDATE_BASE:-950}
cand() { printf 'NW%08d' $((BASE + $1)); }
SID=$(val "SELECT TOP 1 SessionId FROM dbo.ExamSession WHERE Status='OPEN' AND SeatsAvailable >= 15 ORDER BY SeatsAvailable DESC, SessionId")
book() {  # book <n> -> bookingId via the partner channel
  curl -s -X POST -H 'Content-Type: application/json' "$BOOKING/api/v1/bookings" \
       -d "{\"sessionId\":$SID,\"candidateNumber\":\"$(cand "$1")\"}" | tr -d ' \r\n' | sed -E 's/.*"bookingId":([0-9]+).*/\1/'
}
B1=$(book 0); B2=$(book 1); B3=$(book 2); B4=$(book 3); B5=$(book 4); B6=$(book 5)
echo "session $SID, bookings $B1 $B2 $B3 $B4 $B5 $B6"

echo; echo "### 1. REST: first post, identical replay, conflicting score"
api POST "$RESULTS/api/v1/results" "{\"bookingId\":$B1,\"score\":740,\"scoredAt\":\"2026-11-10T09:30:00\"}"
api POST "$RESULTS/api/v1/results" "{\"bookingId\":$B1,\"score\":740,\"scoredAt\":\"2026-11-10T09:30:00\"}"
api POST "$RESULTS/api/v1/results" "{\"bookingId\":$B1,\"score\":650,\"scoredAt\":\"2026-11-10T09:30:00\"}"
say "sql: Result / Booking / AuditLog for booking $B1"
sql "SELECT ResultId, BookingId, Score, Passed, ScoredAt, SourceFile FROM dbo.Result WHERE BookingId=$B1;
     SELECT BookingId, Status FROM dbo.Booking WHERE BookingId=$B1;
     SELECT a.Action, a.Detail, a.Actor FROM dbo.AuditLog a JOIN dbo.Result r ON r.ResultId=a.EntityId
       WHERE a.EntityName='Result' AND r.BookingId=$B1 ORDER BY a.AuditId"

echo; echo "### 2. REST: 10 concurrent identical posts"
for i in $(seq 1 10); do
  curl -s -o /dev/null -w '%{http_code}\n' -X POST -H 'Content-Type: application/json' "$RESULTS/api/v1/results" \
       -d "{\"bookingId\":$B2,\"score\":610,\"scoredAt\":\"2026-11-10T11:00:00\"}" &
done | sort | uniq -c
wait
sql "SELECT COUNT(*) AS ResultRows FROM dbo.Result WHERE BookingId=$B2"

echo; echo "### 3. CSV: upload, then upload the same file again"
F=$(mktemp -d)/NWE2E_REPLAY.csv
printf 'candidateNumber,sessionId,score,scoredAt\r\n%s,%s,720,2026-11-10T09:00:00\r\n%s,%s,455,2026-11-10T09:05:00\r\n' \
  "$(cand 2)" "$SID" "$(cand 3)" "$SID" > "$F"
say "curl -F file=@NWE2E_REPLAY.csv $RESULTS/api/v1/results/upload"
curl -s -w '\nHTTP %{http_code}\n' -F "file=@$F" "$RESULTS/api/v1/results/upload" | tr -d '\r'
say "upload the same file again"
curl -s -w '\nHTTP %{http_code}\n' -F "file=@$F" "$RESULTS/api/v1/results/upload" | tr -d '\r'
sql "SELECT UploadId, FileName, Sha256, RowsRead, Imported, Duplicates, Conflicts, Errors FROM dbo.ResultUpload WHERE FileName='NWE2E_REPLAY.csv';
     SELECT COUNT(*) AS ResultRows FROM dbo.Result WHERE SourceFile='NWE2E_REPLAY.csv'"

echo; echo "### 4. CSV: mixed file (new, duplicate, conflict, bad row, new)"
M=$(mktemp -d)/NWE2E_MIXED.csv
printf 'candidateNumber,sessionId,score,scoredAt\r\n%s,%s,777,2026-11-10T08:30:00\r\n%s,%s,720,2026-11-10T09:00:00\r\n%s,%s,999,2026-11-10T09:05:00\r\nNW00000001,1,abc,2026-11-10\r\n%s,%s,333,2026-11-10T08:45:00\r\n' \
  "$(cand 4)" "$SID" "$(cand 2)" "$SID" "$(cand 3)" "$SID" "$(cand 5)" "$SID" > "$M"
cat "$M" | tr -d '\r'
say "curl -F file=@NWE2E_MIXED.csv $RESULTS/api/v1/results/upload"
curl -s -w '\nHTTP %{http_code}\n' -F "file=@$M" "$RESULTS/api/v1/results/upload" | tr -d '\r'
sql "SELECT r.BookingId, r.Score, r.SourceFile FROM dbo.Result r WHERE r.BookingId IN ($B1,$B3,$B4,$B5,$B6) ORDER BY r.BookingId;
     SELECT a.EntityId AS ResultId, a.Action, a.Detail FROM dbo.AuditLog a WHERE a.Action='DUPLICATE_REJECTED' ORDER BY a.AuditId"
