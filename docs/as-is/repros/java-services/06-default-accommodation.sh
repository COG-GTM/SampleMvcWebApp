#!/usr/bin/env bash
# Partner channel ignores Candidate.DefaultAccommodationCode (portal applies it when the form field is blank).
. "$(dirname "$0")/common.sh"
SID=${SID:-165}
sql "SELECT CandidateNumber, DefaultAccommodationCode FROM dbo.Candidate WHERE CandidateNumber='NW00000903'"
api POST "$BOOKING/api/v1/bookings" "{\"sessionId\":$SID,\"candidateNumber\":\"NW00000903\"}"
sql "SELECT b.BookingId, b.Status, b.AccommodationCode, b.Channel FROM dbo.Booking b JOIN dbo.Candidate c ON c.CandidateId=b.CandidateId WHERE c.CandidateNumber='NW00000903'"
