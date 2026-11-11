# Shared helpers for the runtime repros. Dot-source from each repro script.
# Needs the estate started with scripts\run-legacy.ps1 (portal :8080, booking-api :8081,
# results-service :8082, capacity-sync :8083, SQL Server localhost,1433).
$ErrorActionPreference = 'Stop'
$Server     = if ($env:AD_SQL_SERVER)  { $env:AD_SQL_SERVER }  else { 'localhost,1433' }
$SaPassword = if ($env:AD_SA_PASSWORD) { $env:AD_SA_PASSWORD } else { 'Str0ng!Passw0rd' }
$Sqlcmd     = if ($env:SQLCMD) { $env:SQLCMD } elseif (Test-Path "$env:ProgramFiles\SqlCmd\sqlcmd.exe") { "$env:ProgramFiles\SqlCmd\sqlcmd.exe" } else { 'sqlcmd' }
$Portal  = 'http://localhost:8080'
$Booking = 'http://localhost:8081'
$Results = 'http://localhost:8082'
$Sync    = 'http://localhost:8083'

# bare rows, | separated
function Q([string]$tsql) {
    $out = & $Sqlcmd -S $Server -U sa -P $SaPassword -C -b -d AssessmentDelivery -h -1 -W -s '|' -Q "SET NOCOUNT ON; $tsql" 2>&1
    if ($LASTEXITCODE -ne 0) { throw "query failed: $out" }
    return @($out | ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne '' })
}
function Show([string]$label, $rows) { Write-Host "-- $label"; @($rows) | ForEach-Object { Write-Host "   $_" } }

# HTTP call that never throws: returns "HTTP <code> <compact body>"
function Api([string]$method, [string]$url, $body = $null) {
    try {
        $p = @{ Uri = $url; Method = $method; UseBasicParsing = $true; TimeoutSec = 60 }
        if ($body -ne $null) { $p.Body = ($body | ConvertTo-Json -Compress); $p.ContentType = 'application/json' }
        $r = Invoke-WebRequest @p
        return "HTTP $([int]$r.StatusCode) " + (($r.Content -replace '\s+', ' ').Trim())
    } catch [System.Net.WebException] {
        $resp = $_.Exception.Response
        if ($resp -eq $null) { return "HTTP ERR $($_.Exception.Message)" }
        $txt = (New-Object System.IO.StreamReader($resp.GetResponseStream())).ReadToEnd()
        return "HTTP $([int]$resp.StatusCode) " + (($txt -replace '\s+', ' ').Trim())
    }
}

function Get-FormToken([string]$html) {
    $m = [regex]::Match($html, 'name="__RequestVerificationToken"[^>]*value="([^"]*)"')
    if ($m.Success) { return $m.Groups[1].Value } else { return $null }
}
# GET the form page, POST with its antiforgery token; returns the final page (redirects followed)
function PortalForm([string]$getUrl, [string]$postUrl, [hashtable]$fields) {
    $ws = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $page = Invoke-WebRequest -Uri $getUrl -WebSession $ws -UseBasicParsing -TimeoutSec 60
    $t = Get-FormToken $page.Content
    if ($t) { $fields['__RequestVerificationToken'] = $t }
    return Invoke-WebRequest -Uri $postUrl -Method Post -Body $fields -WebSession $ws -UseBasicParsing -TimeoutSec 60
}
# first alert / validation message on a portal page
function PortalMessage([string]$html) {
    $m = [regex]::Matches($html, '<(div|li)[^>]*class="[^"]*(alert|validation-summary-errors)[^"]*"[^>]*>(.*?)</\1>', 'Singleline')
    $msgs = @($m | ForEach-Object { ($_.Groups[3].Value -replace '<[^>]+>', ' ' -replace '\s+', ' ').Trim() } | Where-Object { $_ })
    if ($msgs.Count) { return ($msgs -join ' | ') } else { return '(no message)' }
}
# seat-holding counts for one session
function SeatState([int]$sid) {
    Q "SELECT 'Capacity='+CAST(s.Capacity AS varchar)+' AccCap='+CAST(s.AccommodationCapacity AS varchar)+' SeatsAvailable='+CAST(s.SeatsAvailable AS varchar)
        +' BOOKED='+CAST((SELECT COUNT(*) FROM dbo.Booking b WHERE b.SessionId=s.SessionId AND b.Status='BOOKED') AS varchar)
        +' ATTENDED='+CAST((SELECT COUNT(*) FROM dbo.Booking b WHERE b.SessionId=s.SessionId AND b.Status='ATTENDED') AS varchar)
        +' WAITLISTED='+CAST((SELECT COUNT(*) FROM dbo.Booking b WHERE b.SessionId=s.SessionId AND b.Status='WAITLISTED') AS varchar)
        +' Drift='+CAST(s.Capacity-(SELECT COUNT(*) FROM dbo.Booking b WHERE b.SessionId=s.SessionId AND b.Status IN ('BOOKED','ATTENDED'))-s.SeatsAvailable AS varchar)
        FROM dbo.ExamSession s WHERE s.SessionId=$sid"
}
# an OPEN future session with at least $minSeats free seats and no bookings by NW00000901..999, newest id first to avoid seed hot spots
function Pick-Session([int]$minSeats = 3, [int]$skip = 0) {
    @(Q "SELECT SessionId FROM dbo.ExamSession WHERE Status='OPEN' AND StartsAt > GETDATE() AND SeatsAvailable >= $minSeats ORDER BY SessionId DESC OFFSET $skip ROWS FETCH NEXT 1 ROWS ONLY")[0]
}
# candidates with no bookings and no ET default (seed NW00000901..NW00000999), skipping any already used by a repro
function Pick-Candidates([int]$n) {
    @(Q "SELECT TOP ($n) CandidateNumber FROM dbo.Candidate c WHERE CandidateNumber BETWEEN 'NW00000901' AND 'NW00000999'
        AND NOT EXISTS (SELECT 1 FROM dbo.Booking b WHERE b.CandidateId=c.CandidateId)
        AND (DefaultAccommodationCode IS NULL OR DefaultAccommodationCode NOT IN ('ET25','ET50')) ORDER BY CandidateNumber")
}
