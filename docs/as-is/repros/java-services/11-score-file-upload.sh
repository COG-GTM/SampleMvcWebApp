#!/usr/bin/env bash
# Score file import: per-row commits, partial success, lenient date formats, strict case, re-upload reports per-row duplicates.
. "$(dirname "$0")/common.sh"
F=$(mktemp -d)/NWTEST_20261211.csv
printf 'candidateNumber,sessionId,score,scoredAt\r\nNW00000447,223,880,2026-12-15\r\nnw00000856,223,700,2026-12-15T12:00:00\r\n"NW00000856",223,700,2026-12-15T12:00:00\r\nNW00000856,223,700,2026-12-15 12:00:00\r\nNW00000410,153,500,2026-12-15T12:00:00\r\n' > "$F"
say "cat $(basename "$F")"; cat "$F" | tr -d '\r'
say "curl -F file=@$(basename "$F") $RESULTS/api/v1/results/upload"
curl -s -w '\nHTTP %{http_code}\n' -F "file=@$F" "$RESULTS/api/v1/results/upload" | tr -d '\r'
say "upload the same file again"
curl -s -w '\nHTTP %{http_code}\n' -F "file=@$F" "$RESULTS/api/v1/results/upload" | tr -d '\r'
sql "SELECT r.ResultId, r.BookingId, r.Score, r.Passed, r.ScoredAt, r.SourceFile FROM dbo.Result r WHERE r.SourceFile='NWTEST_20261211.csv'"
