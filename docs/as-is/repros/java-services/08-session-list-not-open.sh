#!/usr/bin/env bash
# GET /api/v1/sessions returns every status and past sessions; the portal search only lists OPEN.
. "$(dirname "$0")/common.sh"
say "curl $BOOKING/api/v1/sessions | count by status"
curl -s "$BOOKING/api/v1/sessions" | tr -d '\r' | grep '"status"' | sort | uniq -c
say "curl '$BOOKING/api/v1/sessions?from=2026-09-01&to=2026-09-30' | count by status"
curl -s "$BOOKING/api/v1/sessions?from=2026-09-01&to=2026-09-30" | tr -d '\r' | grep '"status"' | sort | uniq -c
sql "SELECT Status, COUNT(*) AS Sessions FROM dbo.ExamSession GROUP BY Status"
