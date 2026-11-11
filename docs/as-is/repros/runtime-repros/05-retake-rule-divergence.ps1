# Same retake question asked of the portal (calendar days) and booking-api (working days).
. "$PSScriptRoot\_common.ps1"
# a candidate whose only attempts at an exam are fails, latest first
$row = @(Q "SELECT TOP 1 c.CandidateNumber+'|'+e.ExamCode+'|'+CAST(e.RetakeWaitDays AS varchar)+'|'+CONVERT(varchar(10),MAX(s.StartsAt),120)
    FROM dbo.Result r JOIN dbo.Candidate c ON c.CandidateId=r.CandidateId JOIN dbo.Exam e ON e.ExamId=r.ExamId JOIN dbo.ExamSession s ON s.SessionId=r.SessionId
    GROUP BY c.CandidateNumber, e.ExamCode, e.RetakeWaitDays HAVING MAX(CAST(r.Passed AS int))=0 ORDER BY MAX(s.StartsAt) DESC, c.CandidateNumber")[0].Split('|')
$cand = $row[0]; $exam = $row[1]; $wait = [int]$row[2]; $last = [datetime]$row[3]
Write-Host "candidate $cand, exam $exam, RetakeWaitDays=$wait, last failed attempt $($last.ToString('yyyy-MM-dd ddd'))"
$cal = $last.AddDays($wait)
foreach ($d in @($cal.AddDays(-1), $cal, $cal.AddDays(7), $cal.AddDays(11), $cal.AddDays(14))) {
    $ds = $d.ToString('yyyy-MM-dd')
    $api = Api GET "$Booking/api/v1/candidates/$cand/eligibility?examCode=$exam&date=$ds"
    $page = (Invoke-WebRequest "$Portal/Eligibility?candidateNumber=$cand&examCode=$exam&date=$ds" -UseBasicParsing -TimeoutSec 60).Content
    $cells = [regex]::Matches($page, '<th>(Eligible|Reason|Earliest retake date)</th><td>(.*?)</td>', 'Singleline')
    $portalTxt = (($cells | ForEach-Object { $_.Groups[1].Value + '=' + (($_.Groups[2].Value -replace '<[^>]+>', '' -replace '\s+', ' ').Trim()) }) -join '; ')
    Write-Host "date $ds ($($d.ToString('ddd')))"
    Write-Host "   booking-api: $api"
    Write-Host "   portal     : $portalTxt"
}
# an OPEN session for the exam inside the gap: portal allows, booking-api refuses
$sid = @(Q "SELECT TOP 1 s.SessionId FROM dbo.ExamSession s JOIN dbo.Exam e ON e.ExamId=s.ExamId WHERE e.ExamCode='$exam' AND s.Status='OPEN' AND s.SeatsAvailable > 0
    AND s.StartsAt >= '$($cal.ToString('yyyy-MM-dd'))' AND s.StartsAt < '$($cal.AddDays(12).ToString('yyyy-MM-dd'))' ORDER BY s.StartsAt")
if ($sid.Count -eq 0) { Write-Host "no OPEN $exam session in the gap window - booking step not run"; return }
$sid = [int]$sid[0]
Write-Host "session $sid starts $(@(Q "SELECT CONVERT(varchar(16),StartsAt,120) FROM dbo.ExamSession WHERE SessionId=$sid")[0])"
Show 'booking-api POST' (Api POST "$Booking/api/v1/bookings" @{ sessionId = $sid; candidateNumber = $cand })
$r = PortalForm "$Portal/Booking/Book?sessionId=$sid" "$Portal/Booking/Book" @{ SessionId = "$sid"; CandidateNumber = $cand; AccommodationCode = '' }
Show 'portal book' (PortalMessage $r.Content)
# clean up so the script can be re-run: cancel the portal booking just made
$nb = @(Q "SELECT TOP 1 b.BookingId FROM dbo.Booking b JOIN dbo.Candidate c ON c.CandidateId=b.CandidateId WHERE c.CandidateNumber='$cand' AND b.SessionId=$sid AND b.Status IN ('BOOKED','WAITLISTED') ORDER BY b.BookingId DESC")
if ($nb.Count) { Show "cleanup: booking-api DELETE $($nb[0])" (Api DELETE "$Booking/api/v1/bookings/$($nb[0])?reason=rr05-cleanup") }
