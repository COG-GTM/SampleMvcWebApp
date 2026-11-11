# Reschedule via portal vs booking-api, cancel with a waitlist, default accommodation, and concurrent usp_BookSeat.
. "$PSScriptRoot\_common.ps1"
function BookingRow([int]$id) { @(Q "SELECT CAST(BookingId AS varchar)+' '+Status+' session='+CAST(SessionId AS varchar)+' channel='+Channel+' acc='+ISNULL(AccommodationCode,'NULL')+' ref='+ISNULL(ExternalRef,'NULL') FROM dbo.Booking WHERE BookingId=$id")[0] }
$maxAudit = [long]@(Q "SELECT MAX(AuditId) FROM dbo.AuditLog")[0]
# (a) reschedule
$pair = @(Q "SELECT TOP 1 CAST(a.SessionId AS varchar)+'|'+CAST(b.SessionId AS varchar) FROM dbo.ExamSession a JOIN dbo.ExamSession b ON b.ExamId=a.ExamId AND b.SessionId<>a.SessionId
    WHERE a.Status='OPEN' AND b.Status='OPEN' AND a.StartsAt > DATEADD(day,3,GETDATE()) AND b.StartsAt > DATEADD(day,3,GETDATE()) AND a.SeatsAvailable>=4 AND b.SeatsAvailable>=4 AND a.AccommodationCapacity=0 AND b.AccommodationCapacity=0
    ORDER BY a.SessionId DESC, b.SessionId DESC")[0].Split('|')
$x = [int]$pair[0]; $y = [int]$pair[1]; $c = @(Pick-Candidates 2)
Write-Host "(a) sessions $x -> $y (same exam); candidates $($c -join ', ')"
foreach ($cn in $c) { PortalForm "$Portal/Booking/Book?sessionId=$x" "$Portal/Booking/Book" @{ SessionId = "$x"; CandidateNumber = $cn; AccommodationCode = '' } | Out-Null }
$ids = @(Q "SELECT b.BookingId FROM dbo.Booking b JOIN dbo.Candidate c ON c.CandidateId=b.CandidateId WHERE b.SessionId=$x AND c.CandidateNumber IN ('$($c[0])','$($c[1])') AND b.Status='BOOKED' ORDER BY c.CandidateNumber")
Show "x=$x before" (SeatState $x); Show "y=$y before" (SeatState $y)
Show "portal reschedule $($ids[0])" (PortalMessage (PortalForm "$Portal/Booking/Reschedule/$($ids[0])?candidateNumber=$($c[0])" "$Portal/Booking/Reschedule/$($ids[0])" @{ NewSessionId = "$y"; CandidateNumber = $c[0] }).Content)
Show "booking-api reschedule $($ids[1])" (Api POST "$Booking/api/v1/bookings/$($ids[1])/reschedule" @{ newSessionId = $y })
Show "x=$x after" (SeatState $x); Show "y=$y after" (SeatState $y)
Show 'new bookings on y' (Q "SELECT CAST(b.BookingId AS varchar)+' '+b.Status+' channel='+b.Channel+' acc='+ISNULL(b.AccommodationCode,'NULL') FROM dbo.Booking b JOIN dbo.Candidate c ON c.CandidateId=b.CandidateId WHERE b.SessionId=$y AND c.CandidateNumber IN ('$($c[0])','$($c[1])')")
$newApi = @(Q "SELECT b.BookingId FROM dbo.Booking b JOIN dbo.Candidate c ON c.CandidateId=b.CandidateId WHERE b.SessionId=$y AND c.CandidateNumber='$($c[1])' AND b.Status='BOOKED'")[0]
Show "booking-api reschedule $newApi to its own session" (Api POST "$Booking/api/v1/bookings/$newApi/reschedule" @{ newSessionId = $y })
$newPortal = @(Q "SELECT b.BookingId FROM dbo.Booking b JOIN dbo.Candidate c ON c.CandidateId=b.CandidateId WHERE b.SessionId=$y AND c.CandidateNumber='$($c[0])' AND b.Status='BOOKED'")[0]
Show "usp_RescheduleBooking $newPortal to its own session $y (what the portal proc allows)" (Q "DECLARE @n INT, @s VARCHAR(12); EXEC dbo.usp_RescheduleBooking $newPortal, $y, @n OUTPUT, @s OUTPUT; SELECT 'NewBookingId='+CAST(@n AS varchar)+' Status='+@s")
Show "y=$y after self-reschedule" (SeatState $y)
Show "booking-api reschedule of CANCELLED $($ids[0])" (Api POST "$Booking/api/v1/bookings/$($ids[0])/reschedule" @{ newSessionId = $y })
Show 'AuditLog rows written by (a)' (Q "SELECT Action+'|'+EntityName+'|'+CAST(EntityId AS varchar)+'|'+Actor+'|'+ISNULL(LEFT(Detail,70),'NULL') FROM dbo.AuditLog WHERE AuditId > $maxAudit ORDER BY AuditId")

# (b) cancel a BOOKED seat on a session that has a waitlist
$w = [int]@(Q "SELECT TOP 1 s.SessionId FROM dbo.ExamSession s WHERE s.Status='OPEN' AND s.StartsAt > GETDATE() AND EXISTS (SELECT 1 FROM dbo.Waitlist w WHERE w.SessionId=s.SessionId) ORDER BY s.SessionId")[0]
$bk = @(Q "SELECT TOP 1 CAST(b.BookingId AS varchar)+'|'+c.CandidateNumber FROM dbo.Booking b JOIN dbo.Candidate c ON c.CandidateId=b.CandidateId WHERE b.SessionId=$w AND b.Status='BOOKED' ORDER BY b.BookingId")[0].Split('|')
Write-Host "(b) session $w has a waitlist; portal-cancel BOOKED booking $($bk[0])"
Show 'before' (SeatState $w); Show 'waitlist (BookingId|Position)' (Q "SELECT BookingId, Position FROM dbo.Waitlist WHERE SessionId=$w ORDER BY Position")
Show 'portal cancel' (PortalMessage (PortalForm "$Portal/Booking/Cancel/$($bk[0])?candidateNumber=$($bk[1])" "$Portal/Booking/Cancel/$($bk[0])" @{ CandidateNumber = $bk[1]; Reason = 'rr08' }).Content)
Show 'after' (SeatState $w); Show 'waitlist after' (Q "SELECT BookingId, Position FROM dbo.Waitlist WHERE SessionId=$w ORDER BY Position")

# (c) blank accommodation: portal applies the candidate default, booking-api does not
$acc = @(Q "SELECT TOP 2 CandidateNumber+'|'+DefaultAccommodationCode FROM dbo.Candidate c WHERE DefaultAccommodationCode IN ('ET25','ET50') AND NOT EXISTS (SELECT 1 FROM dbo.Booking b WHERE b.CandidateId=c.CandidateId) ORDER BY CandidateNumber")
$s3 = [int]@(Q "SELECT TOP 1 s.SessionId FROM dbo.ExamSession s WHERE s.Status='OPEN' AND s.StartsAt > DATEADD(day,3,GETDATE()) AND s.SeatsAvailable>=3 AND s.AccommodationCapacity >= 2
    AND (SELECT COUNT(*) FROM dbo.Booking b WHERE b.SessionId=s.SessionId AND b.Status='BOOKED' AND b.AccommodationCode IN ('ET25','ET50')) = 0 ORDER BY s.SessionId DESC")[0]
$pa = $acc[0].Split('|'); $aa = $acc[1].Split('|')
Write-Host "(c) session $s3; $($pa[0]) default $($pa[1]) via portal, $($aa[0]) default $($aa[1]) via booking-api, both with blank accommodation"
Show 'portal' (PortalMessage (PortalForm "$Portal/Booking/Book?sessionId=$s3" "$Portal/Booking/Book" @{ SessionId = "$s3"; CandidateNumber = $pa[0]; AccommodationCode = '' }).Content)
Show 'booking-api' (Api POST "$Booking/api/v1/bookings" @{ sessionId = $s3; candidateNumber = $aa[0] })
Show 'rows' (Q "SELECT c.CandidateNumber+' '+b.Channel+' acc='+ISNULL(b.AccommodationCode,'NULL') FROM dbo.Booking b JOIN dbo.Candidate c ON c.CandidateId=b.CandidateId WHERE b.SessionId=$s3 AND c.CandidateNumber IN ('$($pa[0])','$($aa[0])')")

# (d) 6 concurrent usp_BookSeat calls (the portal path) for a session with 2 free seats
$s4 = [int]@(Q "SELECT TOP 1 s.SessionId FROM dbo.ExamSession s WHERE s.Status='OPEN' AND s.StartsAt > DATEADD(day,3,GETDATE()) AND s.SeatsAvailable = 2 AND s.AccommodationCapacity = 0
    AND s.SeatsAvailable = s.Capacity - (SELECT COUNT(*) FROM dbo.Booking b WHERE b.SessionId=s.SessionId AND b.Status IN ('BOOKED','ATTENDED')) ORDER BY s.SessionId DESC")[0]
$cids = @(Q "SELECT TOP 6 CandidateId FROM dbo.Candidate c WHERE CandidateNumber BETWEEN 'NW00000901' AND 'NW00000999' AND DefaultAccommodationCode IS NULL AND NOT EXISTS (SELECT 1 FROM dbo.Booking b WHERE b.CandidateId=c.CandidateId) ORDER BY CandidateNumber")
Write-Host "(d) session $s4, 6 parallel sqlcmd EXEC usp_BookSeat"
Show 'before' (SeatState $s4)
$procs = foreach ($cid in $cids) {
    $q = "SET NOCOUNT ON; DECLARE @b INT, @s VARCHAR(12); EXEC dbo.usp_BookSeat $s4, $cid, NULL, @b OUTPUT, @s OUTPUT; SELECT 'candidateId=$cid booking='+CAST(@b AS varchar)+' '+@s"
    Start-Process -FilePath $Sqlcmd -ArgumentList '-S', $Server, '-U', 'ad_app', '-P', 'Northw1nd!Dev2011', '-C', '-d', 'AssessmentDelivery', '-h', '-1', '-W', '-Q', "`"$q`"" -RedirectStandardOutput "$env:TEMP\rr08-$cid.txt" -NoNewWindow -PassThru
}
$procs | Wait-Process
foreach ($cid in $cids) { Write-Host "   $((Get-Content "$env:TEMP\rr08-$cid.txt" | Where-Object { $_.Trim() }) -join ' ')" }
Show 'after' (SeatState $s4); Show 'waitlist positions' (Q "SELECT BookingId, Position FROM dbo.Waitlist WHERE SessionId=$s4 ORDER BY Position")
