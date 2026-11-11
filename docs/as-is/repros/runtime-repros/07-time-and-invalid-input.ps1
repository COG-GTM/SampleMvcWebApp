# Time handling around session start and error responses for invalid input, portal vs booking-api.
. "$PSScriptRoot\_common.ps1"
Show 'SQL Server GETDATE() / SYSDATETIMEOFFSET()' (Q "SELECT CONVERT(varchar(19),GETDATE(),120)+' | '+CONVERT(varchar(34),SYSDATETIMEOFFSET(),121)")
Show 'OS time zone' ((Get-TimeZone).Id + ' / now ' + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'))
# OPEN sessions whose StartsAt is already in the past (no close-out job exists)
$past = @(Q "SELECT TOP 1 s.SessionId FROM dbo.ExamSession s WHERE s.Status='OPEN' AND s.StartsAt < GETDATE() AND s.SeatsAvailable > 0 ORDER BY s.StartsAt DESC")
Show 'OPEN sessions with StartsAt < GETDATE()' (Q "SELECT CAST(s.SessionId AS varchar)+' '+CONVERT(varchar(16),s.StartsAt,120)+' '+t.CenterCode+' '+t.TimeZone+' seats='+CAST(s.SeatsAvailable AS varchar) FROM dbo.ExamSession s JOIN dbo.TestCenter t ON t.TestCenterId=s.TestCenterId WHERE s.Status='OPEN' AND s.StartsAt < GETDATE()")
if ($past.Count) {
    $sid = [int]$past[0]; $c = Pick-Candidates 2
    Show "booking-api GET /api/v1/sessions/$sid" (Api GET "$Booking/api/v1/sessions/$sid")
    Show "booking-api book past session $sid" (Api POST "$Booking/api/v1/bookings" @{ sessionId = $sid; candidateNumber = $c[0] })
    Show "portal book past session $sid" (PortalMessage (PortalForm "$Portal/Booking/Book?sessionId=$sid" "$Portal/Booking/Book" @{ SessionId = "$sid"; CandidateNumber = $c[1]; AccommodationCode = '' }).Content)
    $today = (Get-Date).ToString('yyyy-MM-dd')
    $list = Api GET "$Booking/api/v1/sessions?from=$today&to=$today"
    Show "booking-api sessions?from=$today&to=$today contains past session ${sid}?" ($list -match "`"sessionId`" : $sid,")
    $page = (Invoke-WebRequest "$Portal/Booking?DateFrom=$today&DateTo=$today" -UseBasicParsing -TimeoutSec 60).Content
    Show "portal /Booking search for today lists Book link for ${sid}?" ($page -match "sessionId=$sid")
}
Write-Host 'invalid input, booking-api:'
$probe = [ordered]@{
  'POST bookings {} '                       = @('POST', "$Booking/api/v1/bookings", @{})
  'POST bookings unknown session'           = @('POST', "$Booking/api/v1/bookings", @{ sessionId = 999999; candidateNumber = 'NW00000001' })
  'POST bookings lower-case candidate'      = @('POST', "$Booking/api/v1/bookings", @{ sessionId = 999999; candidateNumber = 'nw00000001' })
  'POST bookings accommodationCode XX'      = @('POST', "$Booking/api/v1/bookings", @{ sessionId = 1; candidateNumber = 'NW00000001'; accommodationCode = 'XX' })
  'POST bookings accommodationCode et25'    = @('POST', "$Booking/api/v1/bookings", @{ sessionId = 999999; candidateNumber = 'NW00000001'; accommodationCode = 'et25' })
  'GET sessions?from=11/11/2026'            = @('GET', "$Booking/api/v1/sessions?from=11/11/2026", $null)
  'GET sessions/abc'                        = @('GET', "$Booking/api/v1/sessions/abc", $null)
  'GET bookings/999999'                     = @('GET', "$Booking/api/v1/bookings/999999", $null)
  'DELETE bookings/999999'                  = @('DELETE', "$Booking/api/v1/bookings/999999", $null)
  'GET eligibility date=2026-13-45'         = @('GET', "$Booking/api/v1/candidates/NW00000113/eligibility?examCode=HPI-ACC1&date=2026-13-45", $null)
  'GET eligibility no examCode'             = @('GET', "$Booking/api/v1/candidates/NW00000113/eligibility", $null)
  'GET results-service candidate xyz'       = @('GET', "$Results/api/v1/results/candidate/xyz", $null)
  'GET capacity-sync /sync/run (GET)'       = @('GET', "$Sync/sync/run", $null)
}
foreach ($k in $probe.Keys) { $p = $probe[$k]; $r = Api $p[0] $p[1] $p[2]; if ($r.Length -gt 260) { $r = $r.Substring(0,260) + '...' }; Write-Host "   $k -> $r" }
$raw = & curl.exe -s -X POST -H 'Content-Type: application/json' -d '{"sessionId":"x"}' "$Booking/api/v1/bookings"
Write-Host "   POST bookings sessionId:'x' -> $((($raw -join ' ') -replace '\s+',' ').Substring(0, [Math]::Min(260, ($raw -join ' ').Length)))"
Write-Host 'invalid input, portal:'
foreach ($u in @("$Portal/Booking/MyBookings?candidateNumber=XYZ", "$Portal/Booking/Book?sessionId=999999", "$Portal/Booking/Cancel/999999", "$Portal/Eligibility?candidateNumber=NW00000113&examCode=HPI-ACC1&date=notadate", "$Portal/Results?candidateNumber=nw00000113")) {
    try { $r = Invoke-WebRequest $u -UseBasicParsing -TimeoutSec 60; Write-Host "   GET $u -> HTTP $([int]$r.StatusCode) $(PortalMessage $r.Content)" }
    catch { Write-Host "   GET $u -> HTTP $([int]$_.Exception.Response.StatusCode)" }
}
