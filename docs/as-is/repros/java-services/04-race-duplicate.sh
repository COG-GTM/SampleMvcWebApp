#!/usr/bin/env bash
# No locking in the partner path: parallel identical POSTs for the same candidate/session.
. "$(dirname "$0")/common.sh"
SID=${SID:-161}; CAND=${CAND:-NW00000908}
say "before"; seat_row $SID
say "8 parallel POST /api/v1/bookings {sessionId:$SID,candidateNumber:$CAND,externalRef:HB-RACE}"
for i in 1 2 3 4 5 6 7 8; do
  curl -s -o /dev/null -w '%{http_code}\n' -X POST -H 'Content-Type: application/json' "$BOOKING/api/v1/bookings" \
       -d "{\"sessionId\":$SID,\"candidateNumber\":\"$CAND\",\"externalRef\":\"HB-RACE\"}" &
done; wait
sql "SELECT BookingId, Status, Channel, ExternalRef FROM dbo.Booking WHERE SessionId=$SID AND ExternalRef='HB-RACE' ORDER BY BookingId"
say "after"; seat_row $SID
