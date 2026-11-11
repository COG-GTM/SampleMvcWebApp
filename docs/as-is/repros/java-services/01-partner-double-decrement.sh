#!/usr/bin/env bash
# Partner booking decrements SeatsAvailable twice (Java UPDATE + trg_Booking_SeatSync); partner cancel gives back one.
. "$(dirname "$0")/common.sh"
SID=${SID:-155}; CAND=${CAND:-NW00000911}
say "before"; seat_row $SID
api POST "$BOOKING/api/v1/bookings" "{\"sessionId\":$SID,\"candidateNumber\":\"$CAND\",\"externalRef\":\"HB-DD-1\"}"
say "after partner booking"; seat_row $SID
api GET "$BOOKING/api/v1/sessions/$SID"
BID=$(val "SELECT MAX(BookingId) FROM dbo.Booking WHERE ExternalRef='HB-DD-1'")
api DELETE "$BOOKING/api/v1/bookings/$BID?reason=repro"
say "after partner cancel"; seat_row $SID
