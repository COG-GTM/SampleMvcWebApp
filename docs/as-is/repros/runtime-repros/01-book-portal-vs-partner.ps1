# Same booking/cancel through the portal and through booking-api; compare SeatsAvailable, drift and AuditLog.
. "$PSScriptRoot\_common.ps1"
$sid = [int](Pick-Session 4)
$c = Pick-Candidates 2; $pc = $c[0]; $ac = $c[1]
Write-Host "session $sid, portal candidate $pc, partner candidate $ac"
Show 'before' (SeatState $sid)

$r = PortalForm "$Portal/Booking/Book?sessionId=$sid" "$Portal/Booking/Book" @{ SessionId = "$sid"; CandidateNumber = $pc; AccommodationCode = '' }
Show 'portal book message' (PortalMessage $r.Content)
Show 'after portal book' (SeatState $sid)

$resp = Api POST "$Booking/api/v1/bookings" @{ sessionId = $sid; candidateNumber = $ac; externalRef = "RR01-$sid" }
Show 'booking-api POST /api/v1/bookings' $resp
Show 'after partner book' (SeatState $sid)

$pb = @(Q "SELECT TOP 1 b.BookingId FROM dbo.Booking b JOIN dbo.Candidate c ON c.CandidateId=b.CandidateId WHERE c.CandidateNumber='$pc' AND b.SessionId=$sid ORDER BY b.BookingId DESC")[0]
$ab = @(Q "SELECT TOP 1 b.BookingId FROM dbo.Booking b JOIN dbo.Candidate c ON c.CandidateId=b.CandidateId WHERE c.CandidateNumber='$ac' AND b.SessionId=$sid ORDER BY b.BookingId DESC")[0]
Show 'bookings' @(Q "SELECT BookingId, Status, Channel, ISNULL(ExternalRef,'-') FROM dbo.Booking WHERE BookingId IN ($pb,$ab)")

$r = PortalForm "$Portal/Booking/Cancel/${pb}?candidateNumber=$pc" "$Portal/Booking/Cancel/$pb" @{ CandidateNumber = $pc; Reason = 'rr01 portal cancel' }
Show 'portal cancel message' (PortalMessage $r.Content)
Show 'booking-api DELETE' (Api DELETE "$Booking/api/v1/bookings/${ab}?reason=rr01")
Show 'after both cancels' (SeatState $sid)
Show 'AuditLog for these bookings (Action|EntityName|EntityId|Actor|Detail)' @(Q "SELECT Action, EntityName, EntityId, Actor, ISNULL(LEFT(Detail,90),'NULL') FROM dbo.AuditLog WHERE (EntityName='Booking' AND EntityId IN ($pb,$ab)) OR (EntityName='ExamSession' AND EntityId=$sid) ORDER BY AuditId")
Show 'seat-drift for session (db/checks/seat-drift.sql formula)' @(Q "SELECT 'SessionId='+CAST(SessionId AS varchar)+' SeatsAvailable='+CAST(SeatsAvailable AS varchar)+' Expected='+CAST(Capacity-(SELECT COUNT(*) FROM dbo.Booking b WHERE b.SessionId=s.SessionId AND b.Status IN ('BOOKED','ATTENDED')) AS varchar) FROM dbo.ExamSession s WHERE SessionId=$sid")
