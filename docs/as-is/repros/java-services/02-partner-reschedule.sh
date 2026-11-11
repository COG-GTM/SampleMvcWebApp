#!/usr/bin/env bash
# Partner reschedule = proc cancel (+1) + partner book (-2); ExternalRef copied onto the new row.
. "$(dirname "$0")/common.sh"
FROM=${FROM:-155}; TO=${TO:-164}; CAND=${CAND:-NW00000902}
OUT=$(curl -s -X POST -H 'Content-Type: application/json' "$BOOKING/api/v1/bookings" -d "{\"sessionId\":$FROM,\"candidateNumber\":\"$CAND\",\"externalRef\":\"HB-RS-1\"}")
echo "booked: $OUT"; BID=$(echo "$OUT" | sed -n 's/.*"bookingId" : \([0-9]*\).*/\1/p' | head -1)
[ -n "$BID" ] || BID=$(val "SELECT MAX(BookingId) FROM dbo.Booking WHERE ExternalRef='HB-RS-1'")
say "before reschedule"; seat_row $FROM; seat_row $TO
api POST "$BOOKING/api/v1/bookings/$BID/reschedule" "{\"newSessionId\":$TO}"
say "after reschedule"; seat_row $FROM; seat_row $TO
sql "SELECT BookingId, SessionId, Status, Channel, ExternalRef, CancelReason FROM dbo.Booking WHERE ExternalRef='HB-RS-1' ORDER BY BookingId"
