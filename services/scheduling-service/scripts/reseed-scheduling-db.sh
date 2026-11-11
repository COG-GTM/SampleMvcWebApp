#!/usr/bin/env bash
# reseed-scheduling-db.sh - restore the Scheduling DB to the legacy seed (dev/replay only).
#
# Re-copies sessions, bookings and waitlist from AssessmentDelivery (read-only login) into Scheduling,
# keeping legacy ids, via POST /api/v2/internal/import/sessions?mode=reset. Snapshots are refreshed too.
#
#   RESEED_LEGACY=1   first rebuild AssessmentDelivery from db/ (db/apply.sh; slow, ~1 min)
#   SCHEDULING_URL    default http://localhost:8090
#
# Replay usage (characterization/README.md):
#   characterization/replay --target modern --base-url http://localhost:8090 \
#       --reseed command --reseed-cmd services/scheduling-service/scripts/reseed-scheduling-db.sh --compare http
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
URL="${SCHEDULING_URL:-http://localhost:8090}"

if [ "${RESEED_LEGACY:-0}" = "1" ]; then
  "$ROOT/db/apply.sh" >/dev/null
fi
out="$(curl -sS -f -X POST "$URL/api/v2/internal/import/sessions?mode=reset")" || {
  echo "reseed failed: is scheduling-service running at $URL?" >&2; exit 1; }
[ -n "${RESEED_VERBOSE:-}" ] && echo "$out"
exit 0
