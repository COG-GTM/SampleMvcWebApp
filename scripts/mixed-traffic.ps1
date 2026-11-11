<#
.SYNOPSIS
    mixed-traffic.ps1 - smoke/load script that exercises both booking channels (Windows twin of mixed-traffic.sh).

.DESCRIPTION
    PORTAL channel  : retired in MBA-3824 (the portal booking pages are gone); -PortalBookings must stay 0.
                      Portal-channel writes now go through the modern API: scripts/mixed-traffic.sh with ROUTING_URL.
    PARTNER channel : booking-api REST on -BookingApiUrl (POST /api/v1/bookings, DELETE /api/v1/bookings/{id})

    Run it on a freshly seeded database (scripts\run-legacy.ps1). Inputs are picked by query so
    repeated runs on a fresh seed book the same candidates onto the same sessions:
    candidates from NW00000901 upward (left unbooked by the seed), and the first OPEN future sessions
    by SessionId with at least 5 free seats. Ends with db\checks\seat-drift.sql.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\mixed-traffic.ps1
    powershell -ExecutionPolicy Bypass -File scripts\mixed-traffic.ps1 -PartnerBookings 10
#>
param(
    [int]$PortalBookings  = $(if ($env:PORTAL_BOOKINGS)  { [int]$env:PORTAL_BOOKINGS }  else { 0 }),
    [int]$PartnerBookings = $(if ($env:PARTNER_BOOKINGS) { [int]$env:PARTNER_BOOKINGS } else { 20 }),
    [int]$Cancels         = $(if ($env:CANCELS)          { [int]$env:CANCELS }          else { 3 }),
    [int]$Sessions        = $(if ($env:SESSIONS)         { [int]$env:SESSIONS }         else { 4 }),
    [int]$FirstCandidate  = $(if ($env:FIRST_CANDIDATE)  { [int]$env:FIRST_CANDIDATE }  else { 901 }),
    [string]$BookingApiUrl = $(if ($env:BOOKING_API_URL) { $env:BOOKING_API_URL } else { 'http://localhost:8081' }),
    [string]$Server        = $(if ($env:AD_SQL_SERVER)   { $env:AD_SQL_SERVER }   else { 'localhost,1433' }),
    [string]$SaPassword    = $(if ($env:AD_SA_PASSWORD)  { $env:AD_SA_PASSWORD }  else { 'Str0ng!Passw0rd' }),
    [string]$Sqlcmd        = $(if ($env:SQLCMD)          { $env:SQLCMD }          else { 'sqlcmd' })
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$root = Split-Path -Parent $PSScriptRoot
$BookingApiUrl = $BookingApiUrl.TrimEnd('/')

function Write-Log([string]$msg) { Write-Host "[mixed-traffic] $msg" }

if (-not (Get-Command $Sqlcmd -ErrorAction SilentlyContinue)) {
    if (Test-Path "$env:ProgramFiles\SqlCmd\sqlcmd.exe") { $Sqlcmd = "$env:ProgramFiles\SqlCmd\sqlcmd.exe" }
    else { throw 'sqlcmd not found (pass -Sqlcmd or set SQLCMD)' }
}

# bare rows, | separated
function Invoke-Query([string]$tsql) {
    $out = & $Sqlcmd -S $Server -U sa -P $SaPassword -C -b -d AssessmentDelivery -h -1 -W -s '|' -Q "SET NOCOUNT ON; $tsql" 2>&1
    if ($LASTEXITCODE -ne 0) { throw "query failed: $out" }
    return @($out | ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne '' })
}

try { Invoke-WebRequest "$BookingApiUrl/api/v1/sessions?from=2099-01-01" -UseBasicParsing -TimeoutSec 30 | Out-Null }
catch { throw "booking-api not reachable at $BookingApiUrl" }
if ($PortalBookings -gt 0) { throw 'PORTAL channel retired (MBA-3824): use -PortalBookings 0, or scripts/mixed-traffic.sh with ROUTING_URL' }

$total = $PartnerBookings
$firstNumber = 'NW{0:D8}' -f $FirstCandidate

# --- inputs ---------------------------------------------------------------
$sessionIds = @(Invoke-Query "SELECT TOP ($Sessions) s.SessionId FROM dbo.ExamSession s
    WHERE s.Status = 'OPEN' AND s.StartsAt > GETDATE() AND s.SeatsAvailable >= 5
    ORDER BY s.SessionId")
if ($sessionIds.Count -eq 0) { throw 'no OPEN future sessions with free seats - is the DB seeded?' }

$candidates = @(Invoke-Query "SELECT TOP ($total) c.CandidateId, c.CandidateNumber FROM dbo.Candidate c
    WHERE c.CandidateNumber >= '$firstNumber'
      AND (c.DefaultAccommodationCode IS NULL OR c.DefaultAccommodationCode NOT IN ('ET25','ET50'))
    ORDER BY c.CandidateNumber" | ForEach-Object {
        $p = $_.Split('|'); [pscustomobject]@{ Id = [int]$p[0]; Number = $p[1] } })
if ($candidates.Count -lt $total) { throw "need $total candidates from $firstNumber, found $($candidates.Count)" }

Write-Log "sessions: $($sessionIds -join ' ')"
Write-Log "candidates: $($candidates[0].Number) .. $($candidates[$total - 1].Number)"

$partnerIds = New-Object System.Collections.ArrayList
$stats = @{ PartnerOk = 0; PartnerFail = 0 }

function Add-PartnerBooking($cand, [string]$sessionId, [string]$ref) {
    $body = @{ sessionId = [int]$sessionId; candidateNumber = $cand.Number; externalRef = $ref } | ConvertTo-Json -Compress
    try {
        $r = Invoke-WebRequest "$BookingApiUrl/api/v1/bookings" -Method Post -ContentType 'application/json' -Body $body -UseBasicParsing -TimeoutSec 60
        if ($r.StatusCode -eq 201) {
            [void]$partnerIds.Add([int]($r.Content | ConvertFrom-Json).bookingId); $stats.PartnerOk++
        } else { $stats.PartnerFail++ }
    } catch { $stats.PartnerFail++ }
}

# --- bookings: partner channel, round-robin over sessions ---------------
Write-Log "booking $PartnerBookings partner seats"
for ($i = 0; $i -lt $PartnerBookings; $i++) {
    Add-PartnerBooking $candidates[$i] $sessionIds[$i % $sessionIds.Count] ('MT-{0:D4}' -f ($i + 1))
}
Write-Log "partner: $($stats.PartnerOk) ok, $($stats.PartnerFail) failed"

# --- cancels: the first few partner bookings ------------------------------
$cPartner = 0
for ($k = 0; $k -lt $Cancels -and $k -lt $partnerIds.Count; $k++) {
    try {
        $r = Invoke-WebRequest "$BookingApiUrl/api/v1/bookings/$($partnerIds[$k])?reason=mixed-traffic" -Method Delete -UseBasicParsing -TimeoutSec 60
        if ($r.StatusCode -eq 200) { $cPartner++ }
    } catch { }
}
Write-Log "cancelled: $cPartner partner"

# --- summary ---------------------------------------------------------------
Write-Log 'session summary (SessionId|Capacity|SeatsAvailable|Booked):'
Invoke-Query "SELECT s.SessionId, s.Capacity, s.SeatsAvailable,
        (SELECT COUNT(*) FROM dbo.Booking b WHERE b.SessionId = s.SessionId AND b.Status IN ('BOOKED','ATTENDED'))
     FROM dbo.ExamSession s WHERE s.SessionId IN ($($sessionIds -join ',')) ORDER BY s.SessionId" |
    ForEach-Object { Write-Host "    $_" }

$check = Join-Path $root 'db\checks\seat-drift.sql'
if (Test-Path $check) {
    $out = & $Sqlcmd -S $Server -U sa -P $SaPassword -C -b -h -1 -W -s '|' -i $check 2>&1
    $rows = @($out | ForEach-Object { "$_".Trim() } |
        Where-Object { $_ -ne '' -and $_ -notlike 'Changed database context*' -and $_ -notmatch 'rows? affected' })
    Write-Log "db\checks\seat-drift.sql: $($rows.Count) row(s)"
} else {
    Write-Log 'db\checks\seat-drift.sql not found, skipping the seat check'
}
Write-Log 'done'
