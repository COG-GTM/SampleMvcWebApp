#!/usr/bin/env bash
# ExternalRef is not an idempotency key: replay -> 409, same ref on another candidate -> second row.
. "$(dirname "$0")/common.sh"
SID=${SID:-165}
api POST "$BOOKING/api/v1/bookings" "{\"sessionId\":$SID,\"candidateNumber\":\"NW00000909\",\"externalRef\":\"PTR-IDEM-1\"}"
api POST "$BOOKING/api/v1/bookings" "{\"sessionId\":$SID,\"candidateNumber\":\"NW00000909\",\"externalRef\":\"PTR-IDEM-1\"}"
api POST "$BOOKING/api/v1/bookings" "{\"sessionId\":$SID,\"candidateNumber\":\"NW00000910\",\"externalRef\":\"PTR-IDEM-1\"}"
sql "SELECT BookingId, SessionId, CandidateId, Status, ExternalRef FROM dbo.Booking WHERE ExternalRef='PTR-IDEM-1'"
