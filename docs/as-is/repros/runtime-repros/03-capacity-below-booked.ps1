# Reduce capacity below the booked count through the portal admin screen (usp_ChangeCapacity + trg_ExamSession_Update).
. "$PSScriptRoot\_common.ps1"
$sid = [int]@(Q "SELECT SessionId FROM dbo.ExamSession s WHERE Status='OPEN' AND StartsAt > DATEADD(day,2,GETDATE()) AND SeatsAvailable >= 3
    AND Capacity - AccommodationCapacity >= 6 AND NOT EXISTS (SELECT 1 FROM dbo.Booking b WHERE b.SessionId=s.SessionId AND b.Status='WAITLISTED') ORDER BY SessionId DESC OFFSET 3 ROWS FETCH NEXT 1 ROWS ONLY")[0]
$cands = Pick-Candidates 2
foreach ($c in $cands) {
    $r = PortalForm "$Portal/Booking/Book?sessionId=$sid" "$Portal/Booking/Book" @{ SessionId = "$sid"; CandidateNumber = $c; AccommodationCode = '' }
    Write-Host "   portal book $c -> $(PortalMessage $r.Content)"
}
Show "session $sid before capacity change" (SeatState $sid)
$booked = [int]@(Q "SELECT COUNT(*) FROM dbo.Booking WHERE SessionId=$sid AND Status='BOOKED'")[0]
$acc = [int]@(Q "SELECT AccommodationCapacity FROM dbo.ExamSession WHERE SessionId=$sid")[0]
$new = [Math]::Max($acc, $booked - 2)
$maxAudit = [long]@(Q "SELECT ISNULL(MAX(AuditId),0) FROM dbo.AuditLog")[0]
Write-Host "POST /Admin/Capacity/$sid newCapacity=$new (BOOKED=$booked, AccommodationCapacity=$acc)"
$r = PortalForm "$Portal/Admin/Capacity/$sid" "$Portal/Admin/Capacity/$sid" @{ newCapacity = "$new" }
Show 'portal message' (PortalMessage $r.Content)
Show 'after' (SeatState $sid)
Show 'bookings changed by the trigger (BookingId|Status|BookedAt|Waitlist.Position)' (Q "SELECT b.BookingId, b.Status, CONVERT(varchar(19),b.BookedAt,120), ISNULL(CAST(w.Position AS varchar),'-') FROM dbo.Booking b LEFT JOIN dbo.Waitlist w ON w.BookingId=b.BookingId WHERE b.SessionId=$sid AND b.Status='WAITLISTED' ORDER BY w.Position")
Show 'new AuditLog rows' (Q "SELECT Action, EntityName, EntityId, Actor, ISNULL(LEFT(Detail,80),'NULL') FROM dbo.AuditLog WHERE AuditId > $maxAudit ORDER BY AuditId")
Write-Host "Raise capacity back to $($new + 3)"
$r = PortalForm "$Portal/Admin/Capacity/$sid" "$Portal/Admin/Capacity/$sid" @{ newCapacity = "$($new + 3)" }
Show 'portal message' (PortalMessage $r.Content)
Show 'after raise (no waitlist promotion?)' (SeatState $sid)
