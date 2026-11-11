<#
.SYNOPSIS
    mixed-traffic.ps1 - smoke/load script that exercises both booking channels (Windows twin of mixed-traffic.sh).

.DESCRIPTION
    PORTAL channel  : the real portal over HTTP - GET + POST /Booking/Book and /Booking/Cancel/{id}
                      (antiforgery token picked up from the form)
    PARTNER channel : booking-api REST on -BookingApiUrl (POST /api/v1/bookings, DELETE /api/v1/bookings/{id})

    Run it on a freshly seeded database (scripts\run-legacy.ps1). Inputs are picked by query so
    repeated runs on a fresh seed book the same candidates onto the same sessions:
    candidates from NW00000901 upward (left unbooked by the seed), and the first OPEN future sessions
    by SessionId with at least 5 free seats. Ends with db\checks\seat-drift.sql.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\mixed-traffic.ps1
    powershell -ExecutionPolicy Bypass -File scripts\mixed-traffic.ps1 -PortalBookings 10 -PartnerBookings 10
    powershell -ExecutionPolicy Bypass -File scripts\mixed-traffic.ps1 -E2E -Target legacy
    powershell -ExecutionPolicy Bypass -File scripts\mixed-traffic.ps1 -E2E -Target migrated
#>
param(
    [int]$PortalBookings  = $(if ($env:PORTAL_BOOKINGS)  { [int]$env:PORTAL_BOOKINGS }  else { 20 }),
    [int]$PartnerBookings = $(if ($env:PARTNER_BOOKINGS) { [int]$env:PARTNER_BOOKINGS } else { 20 }),
    [int]$Cancels         = $(if ($env:CANCELS)          { [int]$env:CANCELS }          else { 3 }),
    [int]$Sessions        = $(if ($env:SESSIONS)         { [int]$env:SESSIONS }         else { 4 }),
    [int]$FirstCandidate  = $(if ($env:FIRST_CANDIDATE)  { [int]$env:FIRST_CANDIDATE }  else { 901 }),
    [string]$PortalUrl     = $(if ($env:PORTAL_URL)      { $env:PORTAL_URL }      else { 'http://localhost:8080' }),
    [string]$BookingApiUrl = $(if ($env:BOOKING_API_URL) { $env:BOOKING_API_URL } else { 'http://localhost:8081' }),
    [string]$Server        = $(if ($env:AD_SQL_SERVER)   { $env:AD_SQL_SERVER }   else { 'localhost,1433' }),
    [string]$SaPassword    = $(if ($env:AD_SA_PASSWORD)  { $env:AD_SA_PASSWORD }  else { 'Str0ng!Passw0rd' }),
    [string]$Sqlcmd        = $(if ($env:SQLCMD)          { $env:SQLCMD }          else { 'sqlcmd' }),
    [switch]$E2E           = $($env:E2E -eq '1'),
    [ValidateSet('legacy', 'migrated')]
    [string]$Target        = $(if ($env:E2E_TARGET)      { $env:E2E_TARGET }      else { 'legacy' }),
    [string]$AsOf          = $(if ($env:E2E_AS_OF)       { $env:E2E_AS_OF }       else { '2026-11-11T00:00:00' })
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$root = Split-Path -Parent $PSScriptRoot
$PortalUrl = $PortalUrl.TrimEnd('/')
$BookingApiUrl = $BookingApiUrl.TrimEnd('/')

function Write-Log([string]$msg) { Write-Host "[mixed-traffic] $msg" }

if (-not (Get-Command $Sqlcmd -ErrorAction SilentlyContinue)) {
    if (Test-Path "$env:ProgramFiles\SqlCmd\sqlcmd.exe") { $Sqlcmd = "$env:ProgramFiles\SqlCmd\sqlcmd.exe" }
    else { throw 'sqlcmd not found (pass -Sqlcmd or set SQLCMD)' }
}

# scheduling tables: AssessmentDelivery (legacy) or scheduling-service's own Scheduling database (migrated)
$stateDb = if ($Target -eq 'migrated') { 'Scheduling' } else { 'AssessmentDelivery' }
$env:SQLCMD = $Sqlcmd; $env:E2E_AS_OF = $AsOf; $env:E2E_TARGET = $Target
$py = (Get-Command python -ErrorAction SilentlyContinue).Source
$e2eOut = Join-Path $root 'build\e2e'
$stopFile = Join-Path $e2eOut "sampler-$Target.stop"
$sampler = $null
if ($E2E) {
    if (-not $py) { throw 'python 3 not found (needed by scripts\e2e)' }
    New-Item -ItemType Directory -Force $e2eOut | Out-Null
    Remove-Item $stopFile -ErrorAction SilentlyContinue
    $sampler = Start-Process -FilePath $py -NoNewWindow -PassThru -ArgumentList @(
        "`"$(Join-Path $root 'scripts\e2e\load.py')`"", 'sample', '--target', $Target, '--stop-file', "`"$stopFile`"",
        '--out', "`"$(Join-Path $e2eOut "traffic-drift-$Target.json")`"", '--interval', '0.5')
    Write-Log "E2E: seat-drift sampler running (pid $($sampler.Id)) against $stateDb"
}

# bare rows, | separated
function Invoke-Query([string]$tsql, [string]$db = $stateDb) {
    $out = & $Sqlcmd -S $Server -U sa -P $SaPassword -C -b -d $db -h -1 -W -s '|' -Q "SET NOCOUNT ON; $tsql" 2>&1
    if ($LASTEXITCODE -ne 0) { throw "query failed: $out" }
    return @($out | ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne '' })
}

function Get-FormToken([string]$html) {
    $m = [regex]::Match($html, 'name="__RequestVerificationToken"[^>]*value="([^"]*)"')
    if ($m.Success) { return $m.Groups[1].Value }
    return $null
}

function Invoke-PortalForm([string]$getUrl, [string]$postUrl, [hashtable]$fields) {
    # one web session per call so the antiforgery cookie and form token always match
    $ws = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $page = Invoke-WebRequest -Uri $getUrl -WebSession $ws -UseBasicParsing -TimeoutSec 60
    $token = Get-FormToken $page.Content
    if ($token) { $fields['__RequestVerificationToken'] = $token }
    return Invoke-WebRequest -Uri $postUrl -Method Post -Body $fields -WebSession $ws -UseBasicParsing -TimeoutSec 60
}

try { Invoke-WebRequest "$BookingApiUrl/api/v1/sessions?from=2099-01-01" -UseBasicParsing -TimeoutSec 30 | Out-Null }
catch { throw "booking-api not reachable at $BookingApiUrl" }
if ($PortalBookings -gt 0) {
    try { Invoke-WebRequest "$PortalUrl/Booking" -UseBasicParsing -TimeoutSec 60 | Out-Null }
    catch { throw "portal not reachable at $PortalUrl" }
}

$total = $PortalBookings + $PartnerBookings
$firstNumber = 'NW{0:D8}' -f $FirstCandidate

# --- inputs ---------------------------------------------------------------
$sessionIds = @(Invoke-Query "SELECT TOP ($Sessions) s.SessionId FROM dbo.ExamSession s
    WHERE s.Status = 'OPEN' AND s.StartsAt > '$AsOf' AND s.SeatsAvailable >= 5
    ORDER BY s.SessionId")
if ($sessionIds.Count -eq 0) { throw 'no OPEN future sessions with free seats - is the DB seeded?' }

$candidates = @(Invoke-Query "SELECT TOP ($total) c.CandidateId, c.CandidateNumber FROM dbo.Candidate c
    WHERE c.CandidateNumber >= '$firstNumber'
      AND (c.DefaultAccommodationCode IS NULL OR c.DefaultAccommodationCode NOT IN ('ET25','ET50'))
    ORDER BY c.CandidateNumber" 'AssessmentDelivery' | ForEach-Object {
        $p = $_.Split('|'); [pscustomobject]@{ Id = [int]$p[0]; Number = $p[1] } })
if ($candidates.Count -lt $total) { throw "need $total candidates from $firstNumber, found $($candidates.Count)" }

Write-Log "sessions: $($sessionIds -join ' ')"
Write-Log "candidates: $($candidates[0].Number) .. $($candidates[$total - 1].Number)"

$portalIds = New-Object System.Collections.ArrayList
$partnerIds = New-Object System.Collections.ArrayList
$stats = @{ PortalOk = 0; PortalFail = 0; PartnerOk = 0; PartnerFail = 0 }

function Add-PortalBooking($cand, [string]$sessionId) {
    try {
        $r = Invoke-PortalForm "$PortalUrl/Booking/Book?sessionId=$sessionId&candidateNumber=$($cand.Number)" "$PortalUrl/Booking/Book" @{
            SessionId = $sessionId; CandidateNumber = $cand.Number; AccommodationCode = '' }
        $m = [regex]::Match($r.Content, 'Booking (\d+) (confirmed|has been placed on the waitlist)')
        if ($m.Success) { $id = $m.Groups[1].Value }
        else {
            $id = Invoke-Query "SELECT TOP 1 BookingId FROM dbo.Booking WHERE SessionId = $sessionId AND CandidateId = $($cand.Id)
                AND Channel = 'PORTAL' AND Status IN ('BOOKED','WAITLISTED') ORDER BY BookingId DESC" | Select-Object -First 1
        }
        if ("$id" -match '^\d+$') { [void]$portalIds.Add([int]$id); $stats.PortalOk++ } else { $stats.PortalFail++ }
    } catch { $stats.PortalFail++ }
}

function Add-PartnerBooking($cand, [string]$sessionId, [string]$ref) {
    $body = @{ sessionId = [int]$sessionId; candidateNumber = $cand.Number; externalRef = $ref } | ConvertTo-Json -Compress
    try {
        $r = Invoke-WebRequest "$BookingApiUrl/api/v1/bookings" -Method Post -ContentType 'application/json' -Body $body -UseBasicParsing -TimeoutSec 60
        if ($r.StatusCode -eq 201) {
            [void]$partnerIds.Add([int]($r.Content | ConvertFrom-Json).bookingId); $stats.PartnerOk++
        } else { $stats.PartnerFail++ }
    } catch { $stats.PartnerFail++ }
}

# --- bookings: interleave the two channels, round-robin over sessions ----
Write-Log "booking $PortalBookings portal + $PartnerBookings partner seats"
$i = 0; $p = 0; $q = 0
while ($p -lt $PortalBookings -or $q -lt $PartnerBookings) {
    if ($p -lt $PortalBookings) {
        Add-PortalBooking $candidates[$i] $sessionIds[$i % $sessionIds.Count]
        $p++; $i++
    }
    if ($q -lt $PartnerBookings) {
        Add-PartnerBooking $candidates[$i] $sessionIds[$i % $sessionIds.Count] ('MT-{0:D4}' -f ($q + 1))
        $q++; $i++
    }
}
Write-Log "portal : $($stats.PortalOk) ok, $($stats.PortalFail) failed"
Write-Log "partner: $($stats.PartnerOk) ok, $($stats.PartnerFail) failed"

# --- cancels: the first few bookings on each channel ---------------------
$cPortal = 0; $cPartner = 0
for ($k = 0; $k -lt $Cancels -and $k -lt $portalIds.Count; $k++) {
    $id = $portalIds[$k]
    try {
        $r = Invoke-PortalForm "$PortalUrl/Booking/Cancel/$id" "$PortalUrl/Booking/Cancel/$id" @{ Reason = 'mixed-traffic'; CandidateNumber = '' }
        if ($r.Content -match "Booking $id cancelled") { $cPortal++ }
    } catch { }
}
for ($k = 0; $k -lt $Cancels -and $k -lt $partnerIds.Count; $k++) {
    try {
        $r = Invoke-WebRequest "$BookingApiUrl/api/v1/bookings/$($partnerIds[$k])?reason=mixed-traffic" -Method Delete -UseBasicParsing -TimeoutSec 60
        if ($r.StatusCode -eq 200) { $cPartner++ }
    } catch { }
}
Write-Log "cancelled: $cPortal portal, $cPartner partner"

# --- summary ---------------------------------------------------------------
Write-Log 'session summary (SessionId|Capacity|SeatsAvailable|Booked):'
Invoke-Query "SELECT s.SessionId, s.Capacity, s.SeatsAvailable,
        (SELECT COUNT(*) FROM dbo.Booking b WHERE b.SessionId = s.SessionId AND b.Status IN ('BOOKED','ATTENDED'))
     FROM dbo.ExamSession s WHERE s.SessionId IN ($($sessionIds -join ',')) ORDER BY s.SessionId" |
    ForEach-Object { Write-Host "    $_" }

$check = if ($Target -eq 'migrated') { Join-Path $root 'services\scheduling-service\db\checks\seat-drift.sql' } else { Join-Path $root 'db\checks\seat-drift.sql' }
if (Test-Path $check) {
    $out = & $Sqlcmd -S $Server -U sa -P $SaPassword -C -b -h -1 -W -s '|' -i $check 2>&1
    $rows = @($out | ForEach-Object { "$_".Trim() } |
        Where-Object { $_ -ne '' -and $_ -notlike 'Changed database context*' -and $_ -notmatch 'rows? affected' })
    Write-Log "$(Split-Path -Leaf $check): $($rows.Count) row(s)"
} else {
    Write-Log "$check not found, skipping the seat check"
}
if (-not $E2E) { Write-Log 'done'; return }

# --- E2E: stop the sampler, then the full suite ----------------------------
New-Item -ItemType File -Force $stopFile | Out-Null
$sampler.WaitForExit(60000) | Out-Null
$d = Get-Content (Join-Path $e2eOut "traffic-drift-$Target.json") -Raw | ConvertFrom-Json
Write-Log ("E2E: drift during traffic: {0} samples over {1}s, max raw {2}, max unexplained {3}" -f $d.samples, $d.seconds, $d.maxRawDrift, $d.maxUnexplainedDrift)
& $py (Join-Path $root 'scripts\e2e\suite.py') --target $Target --out (Join-Path $e2eOut "$Target.json")
$code = $LASTEXITCODE
Write-Log "E2E suite exit $code ($e2eOut\$Target.json)"
exit $code
