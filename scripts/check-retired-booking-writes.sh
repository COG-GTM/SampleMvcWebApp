#!/usr/bin/env bash
# MBA-3824 guard: fails if the retired booking/capacity write paths reappear in the portal (C#) or booking-api.
# docs/adr/0004-retire-portal-booking-slice.md
set -uo pipefail
cd "$(dirname "$0")/.."

PORTAL=(AssessmentDelivery.Web AssessmentDelivery.Services AssessmentDelivery.Business AssessmentDelivery.Data)
BOOKING_API=services/booking-api/src/main
fail=0

check() {
    local what=$1 pattern=$2
    shift 2
    local hits
    hits=$(grep -rnEI --include='*.cs' --include='*.cshtml' --include='*.java' "$pattern" "$@")
    if [ -n "$hits" ]; then
        echo "FAIL: $what"
        echo "$hits"
        fail=1
    fi
}

check "retired booking procs referenced" 'usp_(BookSeat|CancelBooking|ChangeCapacity|RescheduleBooking)' \
    "${PORTAL[@]}" AssessmentDelivery.Tests "$BOOKING_API"
check "SeatsAvailable updated" '\.SeatsAvailable[[:space:]]*(\+\+|--|[-+]?=[^=>])|SET[[:space:]]+SeatsAvailable' \
    "${PORTAL[@]}" "$BOOKING_API"
check "Booking/Waitlist written" '\.(Bookings|Waitlist)\.(Add|AddRange|Remove|RemoveRange|Attach)\(|(INSERT[[:space:]]+INTO|UPDATE|DELETE[[:space:]]+FROM)[[:space:]]+(dbo\.)?(Booking|Waitlist)([^A-Za-z]|$)' \
    "${PORTAL[@]}" "$BOOKING_API"

if [ "$fail" -eq 0 ]; then
    echo "OK: no retired booking/capacity writes"
fi
exit "$fail"
