#!/usr/bin/env bash
# MBA-3824 guard: the .NET booking/capacity slice is retired. Fails when C# code in the portal projects
# references booking/waitlist writes, SeatsAvailable updates, the four dropped booking procs or the deleted
# booking types again. Patterns (extended regex, case-insensitive) live in scripts/retired-booking-patterns.txt;
# AssessmentDelivery.Tests/UnitTests/RetiredBookingGuardTests.cs runs the same check under NUnit.
set -euo pipefail
cd "$(dirname "$0")/.."
patterns=scripts/retired-booking-patterns.txt
hits=$(git ls-files 'AssessmentDelivery.*/*.cs' 'AssessmentDelivery.*/*.cshtml' \
    | grep -v '^AssessmentDelivery.Tests/' \
    | xargs grep -n -i -E -f "$patterns" || true)
if [ -n "$hits" ]; then
    echo "Retired booking/capacity code found (MBA-3824):" >&2
    echo "$hits" >&2
    exit 1
fi
echo "OK: no C# booking writes, SeatsAvailable updates or retired booking procs"
