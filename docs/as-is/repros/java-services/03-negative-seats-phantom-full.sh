#!/usr/bin/env bash
# Sequential partner bookings drive SeatsAvailable below zero, then real free seats get WAITLISTED.
. "$(dirname "$0")/common.sh"
SID=${SID:-157}
say "before"; seat_row $SID
for n in 904 905 906 907; do
  printf 'NW00000%s -> ' $n
  curl -s -X POST -H 'Content-Type: application/json' "$BOOKING/api/v1/bookings" -d "{\"sessionId\":$SID,\"candidateNumber\":\"NW00000$n\"}" | tr -d '\r\n '; echo
  seat_row $SID | tail -1
done
api GET "$BOOKING/api/v1/sessions/$SID"
