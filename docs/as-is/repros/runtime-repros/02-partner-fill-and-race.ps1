# (a) Fill a session one partner booking at a time; (b) fire concurrent partner bookings at a session with few seats.
. "$PSScriptRoot\_common.ps1"
Add-Type -AssemblyName System.Net.Http

# (a) sequential fill
$sid = [int]@(Q "SELECT TOP 1 SessionId FROM dbo.ExamSession WHERE Status='OPEN' AND StartsAt > DATEADD(day,2,GETDATE()) AND SeatsAvailable BETWEEN 4 AND 6 ORDER BY SessionId DESC")[0]
$seats = [int]@(Q "SELECT SeatsAvailable FROM dbo.ExamSession WHERE SessionId=$sid")[0]
$cands = Pick-Candidates ($seats + 1)
Write-Host "(a) session $sid starts with SeatsAvailable=$seats; booking $($cands.Count) candidates one by one via booking-api"
Show 'before' (SeatState $sid)
foreach ($c in $cands) { Write-Host "   $c -> $(Api POST "$Booking/api/v1/bookings" @{ sessionId = $sid; candidateNumber = $c })" }
Show 'after sequential partner fill' (SeatState $sid)
Show 'booking-api GET session' (Api GET "$Booking/api/v1/sessions/$sid")

# (b) concurrent race
$sid2 = [int]@(Q "SELECT TOP 1 SessionId FROM dbo.ExamSession WHERE Status='OPEN' AND StartsAt > DATEADD(day,2,GETDATE()) AND SeatsAvailable BETWEEN 4 AND 6 AND SessionId <> $sid ORDER BY SessionId DESC")[0]
$n = 8
$cands2 = Pick-Candidates $n
Write-Host "(b) session ${sid2}: $n concurrent POST /api/v1/bookings"
Show 'before' (SeatState $sid2)
$http = New-Object System.Net.Http.HttpClient
$tasks = foreach ($c in $cands2) {
    $json = "{`"sessionId`":$sid2,`"candidateNumber`":`"$c`"}"
    $http.PostAsync("$Booking/api/v1/bookings", (New-Object System.Net.Http.StringContent($json, [Text.Encoding]::UTF8, 'application/json')))
}
[System.Threading.Tasks.Task]::WaitAll([System.Threading.Tasks.Task[]]$tasks)
$i = 0
foreach ($t in $tasks) { Write-Host "   $($cands2[$i]) -> HTTP $([int]$t.Result.StatusCode) $(($t.Result.Content.ReadAsStringAsync().Result -replace '\s+',' '))"; $i++ }
Show 'after concurrent partner bookings' (SeatState $sid2)
Show 'Waitlist positions' (Q "SELECT BookingId, Position FROM dbo.Waitlist WHERE SessionId=$sid2 ORDER BY Position, BookingId")
