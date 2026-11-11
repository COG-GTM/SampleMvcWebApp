# Score ingestion via results-service (REST + CSV) and how it surfaces in the portal and booking state.
. "$PSScriptRoot\_common.ps1"
$sid = [int](Pick-Session 3 5)
$cand = @(Pick-Candidates 1)[0]
$start = @(Q "SELECT CONVERT(varchar(16),StartsAt,120)+' '+s.Status FROM dbo.ExamSession s WHERE SessionId=$sid")[0]
$exam = @(Q "SELECT e.ExamCode FROM dbo.ExamSession s JOIN dbo.Exam e ON e.ExamId=s.ExamId WHERE s.SessionId=$sid")[0]
Write-Host "future session $sid ($start, $exam), candidate $cand"
$bk = Api POST "$Booking/api/v1/bookings" @{ sessionId = $sid; candidateNumber = $cand }
Show 'book via booking-api' $bk
$bid = [int]([regex]::Match($bk, '"bookingId" : (\d+)').Groups[1].Value)
Show 'before result' (SeatState $sid)
Show 'POST /api/v1/results score=999 for a session that has not started (no scoredAt)' (Api POST "$Results/api/v1/results" @{ bookingId = $bid; score = 999 })
Show 'booking row now' (Q "SELECT BookingId, Status, Channel FROM dbo.Booking WHERE BookingId=$bid")
Show 'after result' (SeatState $sid)
Show 'Result row (Score|Passed|ScoredAt|SourceFile|ReceivedAt)' (Q "SELECT Score, Passed, CONVERT(varchar(23),ScoredAt,121), ISNULL(SourceFile,'NULL'), CONVERT(varchar(19),ReceivedAt,120) FROM dbo.Result WHERE BookingId=$bid")
Show 'results-service GET candidate' (Api GET "$Results/api/v1/results/candidate/$cand")
$page = (Invoke-WebRequest "$Portal/Results?candidateNumber=$cand" -UseBasicParsing -TimeoutSec 60).Content
$row = [regex]::Match($page, '<tbody>(.*?)</tbody>', 'Singleline').Groups[1].Value -replace '<[^>]+>', ' ' -replace '\s+', ' '
Show 'portal /Results row' $row.Trim()
Show 'portal cancel of the ATTENDED booking' (PortalMessage (PortalForm "$Portal/Booking/Cancel/${bid}?candidateNumber=$cand" "$Portal/Booking/Cancel/$bid" @{ CandidateNumber = $cand; Reason = 'rr06' }).Content)
Show 'booking-api eligibility for the same exam' (Api GET "$Booking/api/v1/candidates/$cand/eligibility?examCode=$exam")
Write-Host 'invalid REST inputs:'
Show 'duplicate' (Api POST "$Results/api/v1/results" @{ bookingId = $bid; score = 500 })
Show 'score 1001' (Api POST "$Results/api/v1/results" @{ bookingId = $bid; score = 1001 })
$cancelled = @(Q "SELECT TOP 1 BookingId FROM dbo.Booking WHERE Status='CANCELLED' ORDER BY BookingId DESC")[0]
Show "cancelled booking $cancelled" (Api POST "$Results/api/v1/results" @{ bookingId = [int]$cancelled; score = 500 })
Show 'unknown booking' (Api POST "$Results/api/v1/results" @{ bookingId = 999999; score = 500 })
Show 'scoredAt 11/11/2026' (Api POST "$Results/api/v1/results" @{ bookingId = $bid; score = 500; scoredAt = '11/11/2026' })
Show 'missing score' (Api POST "$Results/api/v1/results" @{ bookingId = $bid })
# CSV upload: BOM header, good row, quoted field, lower-case candidate, unbooked candidate, blank line, second score for same booking
$c2 = @(Pick-Candidates 3)
foreach ($c in $c2[0..1]) { Api POST "$Booking/api/v1/bookings" @{ sessionId = $sid; candidateNumber = $c } | Out-Null }
$csv = [string][char]0xFEFF + "candidateNumber,sessionId,score,scoredAt`r`n$($c2[0]),$sid,640,2026-11-11T08:00:00`r`n`"$($c2[1])`",$sid,700,2026-11-11`r`n$($c2[1].ToLower()),$sid,500,2026-11-11`r`n$($c2[2]),$sid,500,2026-11-11 09:00:00`r`n`r`n$($c2[0]),$sid,650,2026-11-11`r`n"
$file = Join-Path $env:TEMP 'rr06-scores.csv'; [IO.File]::WriteAllText($file, $csv, (New-Object Text.UTF8Encoding($false)))
Write-Host "CSV sent:"; Get-Content $file | ForEach-Object { Write-Host "   $_" }
$out = & curl.exe -s -F "file=@$file" "$Results/api/v1/results/upload"
Show 'POST /api/v1/results/upload' (($out -join ' ') -replace '\s+', ' ')
Show 'latest INGEST audit rows' (Q "SELECT TOP 3 Action, EntityName, Actor, LEFT(Detail,80) FROM dbo.AuditLog WHERE Action='INGEST' ORDER BY AuditId DESC")
