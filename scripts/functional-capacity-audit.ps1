<#
.SYNOPSIS
    functional-capacity-audit.ps1 - API-level functional test of the portal capacity audit page (MBA-3828).

.DESCRIPTION
    1. Fresh database + legacy estate (scripts\run-legacy.ps1: db\apply.ps1, capacity-sync :8083, portal :8080) and the
       modern estate (scripts\run-modern.ps1: scheduling-service :8090 with the outbox relay on, routing gateway :8000).
       -SkipStart uses an estate that is already running on a fresh seed.
    2. Three capacity changes on two sessions of centre C1 and one on a C2 session through
       PUT <RoutingUrl>/api/v2/sessions/{id}/capacity with actor admin-ui (what the modern admin UI does),
       then POST <CapacitySyncUrl>/sync/run.
    3. Waits for the relay to project the changes into AuditLog, then checks the HTML of
       GET <PortalUrl>/Admin/Centers/{c1}/CapacityAudit (rows newest first, old -> new, delta, actor, sync panel),
       the ?sessionId= variant, the C2 page, a 404 for an unknown centre, and that the page reads were writes-free.
    Exits non-zero on the first failed check.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\functional-capacity-audit.ps1
    powershell -ExecutionPolicy Bypass -File scripts\functional-capacity-audit.ps1 -SkipStart
#>
param(
    [switch]$SkipStart,
    [switch]$SkipBuild,
    [string]$PortalUrl       = $(if ($env:PORTAL_URL)        { $env:PORTAL_URL }        else { 'http://localhost:8080' }),
    [string]$RoutingUrl      = $(if ($env:ROUTING_URL)       { $env:ROUTING_URL }       else { 'http://localhost:8000' }),
    [string]$CapacitySyncUrl = $(if ($env:CAPACITY_SYNC_URL) { $env:CAPACITY_SYNC_URL } else { 'http://localhost:8083' }),
    [int]$RelayTimeoutSeconds = 60,
    [string]$Server     = $(if ($env:AD_SQL_SERVER)  { $env:AD_SQL_SERVER }  else { 'localhost,1433' }),
    [string]$SaPassword = $(if ($env:AD_SA_PASSWORD) { $env:AD_SA_PASSWORD } else { 'Str0ng!Passw0rd' }),
    [string]$Sqlcmd     = $(if ($env:SQLCMD)         { $env:SQLCMD }         else { 'sqlcmd' })
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$root = Split-Path -Parent $PSScriptRoot
$PortalUrl = $PortalUrl.TrimEnd('/'); $RoutingUrl = $RoutingUrl.TrimEnd('/'); $CapacitySyncUrl = $CapacitySyncUrl.TrimEnd('/')
$script:passed = 0

function Write-Step([string]$msg) { Write-Host "[capacity-audit] $msg" }
function Pass([string]$msg) { $script:passed++; Write-Host "PASS: $msg" }
function Fail([string]$msg) { Write-Host "FAIL: $msg" -ForegroundColor Red; exit 1 }
function Check([bool]$ok, [string]$msg) { if ($ok) { Pass $msg } else { Fail $msg } }

function Invoke-Sql([string]$query) {
    $out = & $Sqlcmd -S $Server -U sa -P $SaPassword -C -b -I -d AssessmentDelivery -h -1 -W -Q "SET NOCOUNT ON; $query"
    if ($LASTEXITCODE -ne 0) { Fail "sqlcmd failed: $query`n$out" }
    return @($out | Where-Object { $_ -and $_.Trim() }) | ForEach-Object { $_.Trim() }
}

function Wait-Url([string]$url, [int]$seconds) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        try { Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 10 | Out-Null; return } catch { Start-Sleep -Seconds 2 }
    }
    Fail "$url did not answer within $seconds s"
}

function Get-Page([string]$url) {
    try { $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 60 } catch { Fail "GET $url failed: $($_.Exception.Message)" }
    if ($r.StatusCode -ne 200) { Fail "GET $url returned $($r.StatusCode)" }
    return $r.Content
}

function Get-AuditRows([string]$html) {
    $rows = @()
    foreach ($m in [regex]::Matches($html, '(?s)<tr data-audit-id="(\d+)" data-session-id="(\d+)">(.*?)</tr>')) {
        $cells = $m.Groups[3].Value
        $change = [regex]::Match($cells, 'capacity-change">\s*(-?\d+) &rarr; (-?\d+)\s*<')
        $delta = [regex]::Match($cells, 'capacity-delta">\s*([+-]?\d+)\s*<')
        $tds = [regex]::Matches($cells, '(?s)<td[^>]*>(.*?)</td>') | ForEach-Object { $_.Groups[1].Value.Trim() }
        $rows += [pscustomobject]@{
            AuditId = [long]$m.Groups[1].Value; SessionId = [int]$m.Groups[2].Value
            Old = $(if ($change.Success) { [int]$change.Groups[1].Value } else { $null })
            New = $(if ($change.Success) { [int]$change.Groups[2].Value } else { $null })
            Delta = $(if ($delta.Success) { [int]$delta.Groups[1].Value } else { $null })
            Actor = $tds[$tds.Count - 3]
        }
    }
    return ,$rows
}

function Get-TableSnapshot {
    $tables = Invoke-Sql "SELECT name FROM sys.tables WHERE schema_id = SCHEMA_ID('dbo') AND is_ms_shipped = 0 ORDER BY name"
    $parts = $tables | ForEach-Object { "SELECT '$_' + ':' + CAST(COUNT_BIG(*) AS VARCHAR(20)) + ':' + ISNULL(CAST(CHECKSUM_AGG(BINARY_CHECKSUM(*)) AS VARCHAR(20)), '-') FROM dbo.[$_]" }
    return (Invoke-Sql ($parts -join ' UNION ALL ')) -join ' '
}

function Set-Capacity([int]$sessionId, [int]$newCapacity) {
    $body = @{ newCapacity = $newCapacity; actor = 'admin-ui' } | ConvertTo-Json
    try {
        $r = Invoke-RestMethod -Method Put -Uri "$RoutingUrl/api/v2/sessions/$sessionId/capacity" -ContentType 'application/json' -Body $body
    } catch { Fail "PUT capacity of session $sessionId to $newCapacity failed: $($_.Exception.Message)" }
    Write-Step "session $sessionId capacity -> $newCapacity (seats available $($r.session.seatsAvailable))"
}

function Get-Capacity([int]$sessionId) {
    return [int](Invoke-RestMethod -Uri "$RoutingUrl/api/v2/sessions/$sessionId").capacity
}

if (-not $SkipStart) {
    Write-Step 'fresh database + legacy estate (run-legacy.ps1)'
    $legacyArgs = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', (Join-Path $PSScriptRoot 'run-legacy.ps1'))
    if ($SkipBuild) { $legacyArgs += '-SkipBuild' }
    & powershell @legacyArgs
    if ($LASTEXITCODE -ne 0) { Fail "run-legacy.ps1 failed (exit $LASTEXITCODE)" }
    Write-Step 'modern estate: scheduling-service with the relay on + routing gateway (run-modern.ps1)'
    $modernArgs = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', (Join-Path $PSScriptRoot 'run-modern.ps1'))
    if ($SkipBuild) { $modernArgs += '-SkipBuild' }
    & powershell @modernArgs
    if ($LASTEXITCODE -ne 0) { Fail "run-modern.ps1 failed (exit $LASTEXITCODE)" }
}
Wait-Url "$PortalUrl/Admin/Centers" 180
Wait-Url "$RoutingUrl/api/v2/sessions/1" 120
Wait-Url "$CapacitySyncUrl/sync/last" 120

# C1 = first centre with two OPEN future sessions, C2 = the next centre with one
$pick = Invoke-Sql @"
WITH s AS (SELECT SessionId, TestCenterId, ROW_NUMBER() OVER (PARTITION BY TestCenterId ORDER BY SessionId) AS rn
           FROM dbo.ExamSession WHERE Status = 'OPEN' AND StartsAt > GETDATE() AND SeatsAvailable >= 2),
     c AS (SELECT TestCenterId, ROW_NUMBER() OVER (ORDER BY TestCenterId) AS cn FROM s WHERE rn = 2)
SELECT CONCAT(c1.TestCenterId, ',', a.SessionId, ',', b.SessionId, ',', c2.TestCenterId, ',', d.SessionId)
FROM c c1 JOIN s a ON a.TestCenterId = c1.TestCenterId AND a.rn = 1 JOIN s b ON b.TestCenterId = c1.TestCenterId AND b.rn = 2
JOIN c c2 ON c2.cn = 2 JOIN s d ON d.TestCenterId = c2.TestCenterId AND d.rn = 1
WHERE c1.cn = 1
"@
$c1, $s1, $s2, $c2, $s4 = ($pick | Select-Object -First 1) -split ',' | ForEach-Object { [int]$_ }
Write-Step "C1 = centre $c1 (sessions $s1, $s2), C2 = centre $c2 (session $s4)"
$auditBefore = [long](Invoke-Sql "SELECT ISNULL(MAX(AuditId), 0) FROM dbo.AuditLog" | Select-Object -First 1)

$cap1 = Get-Capacity $s1; $cap2 = Get-Capacity $s2; $cap4 = Get-Capacity $s4
Set-Capacity $s1 ($cap1 + 2); Start-Sleep -Milliseconds 1100
Set-Capacity $s2 ($cap2 + 3); Start-Sleep -Milliseconds 1100
Set-Capacity $s4 ($cap4 + 4); Start-Sleep -Milliseconds 1100
Set-Capacity $s1 ($cap1 + 1)
try { $run = Invoke-RestMethod -Method Post -Uri "$CapacitySyncUrl/sync/run" } catch { Fail "POST $CapacitySyncUrl/sync/run failed: $($_.Exception.Message)" }
Write-Step "capacity-sync run: $($run | ConvertTo-Json -Compress)"

Write-Step 'waiting for the outbox relay to project the 4 capacity changes into AuditLog'
$deadline = (Get-Date).AddSeconds($RelayTimeoutSeconds)
do {
    $n = [int](Invoke-Sql "SELECT COUNT(*) FROM dbo.AuditLog WHERE AuditId > $auditBefore AND EntityName = 'ExamSession' AND Action = 'CAPACITY'" | Select-Object -First 1)
    if ($n -ge 4) { break }
    Start-Sleep -Seconds 1
} while ((Get-Date) -lt $deadline)
Check ($n -eq 4) "relay projected 4 CAPACITY rows into AuditLog (found $n)"
$syncRows = [int](Invoke-Sql "SELECT COUNT(*) FROM dbo.AuditLog WHERE AuditId > $auditBefore AND EntityId = 0 AND Action = 'CAPACITY_SYNC'" | Select-Object -First 1)
Check ($syncRows -ge 1) "capacity-sync wrote its CAPACITY_SYNC run row (EntityId 0)"

$snapBefore = Get-TableSnapshot

Write-Step "GET $PortalUrl/Admin/Centers/$c1/CapacityAudit"
$html = Get-Page "$PortalUrl/Admin/Centers/$c1/CapacityAudit"
$rows = @((Get-AuditRows $html) | Where-Object { $_.AuditId -gt $auditBefore })
Check ($rows.Count -eq 3) "C1 page lists exactly the 3 C1 changes (found $($rows.Count))"
Check (@($rows | Where-Object { $_.SessionId -eq $s4 }).Count -eq 0) 'the C2 change is not on the C1 page'
Check ($rows[0].AuditId -gt $rows[1].AuditId -and $rows[1].AuditId -gt $rows[2].AuditId) 'rows are newest first'
$expected = @(
    @{ S = $s1; Old = $cap1 + 2; New = $cap1 + 1; D = -1 },
    @{ S = $s2; Old = $cap2;     New = $cap2 + 3; D = 3 },
    @{ S = $s1; Old = $cap1;     New = $cap1 + 2; D = 2 })
for ($i = 0; $i -lt 3; $i++) {
    $e = $expected[$i]; $r = $rows[$i]
    Check ($r.SessionId -eq $e.S -and $r.Old -eq $e.Old -and $r.New -eq $e.New -and $r.Delta -eq $e.D -and $r.Actor -eq 'admin-ui') `
        ("row {0}: session {1} {2} -> {3} delta {4} by admin-ui (got session {5} {6} -> {7} delta {8} by {9})" -f ($i + 1), $e.S, $e.Old, $e.New, $e.D, $r.SessionId, $r.Old, $r.New, $r.Delta, $r.Actor)
}
Check ($html -match 'Automatic seat re-syncs' -and $html -match '(?s)<tr class="sync-run">.*?sync-trigger">MANUAL<.*?sync-checked">\d+<.*?sync-updated">\d+<') `
    'Automatic seat re-syncs panel shows the MANUAL capacity-sync run with checked/updated counts'

Write-Step "GET $PortalUrl/Admin/Centers/$c1/CapacityAudit?sessionId=$s2"
$html = Get-Page "$PortalUrl/Admin/Centers/$c1/CapacityAudit?sessionId=$s2"
$filtered = @(Get-AuditRows $html)
Check ($filtered.Count -ge 1 -and @($filtered | Where-Object { $_.SessionId -ne $s2 }).Count -eq 0 -and @($filtered | Where-Object { $_.AuditId -gt $auditBefore }).Count -eq 1) `
    "sessionId filter shows only session $s2 (1 new row)"
Check ($html -match "<option selected=""selected"" value=""$s2"">") 'session dropdown keeps the selected session'

$html = Get-Page "$PortalUrl/Admin/Centers/$c2/CapacityAudit"
$c2rows = @((Get-AuditRows $html) | Where-Object { $_.AuditId -gt $auditBefore })
Check ($c2rows.Count -eq 1 -and $c2rows[0].SessionId -eq $s4 -and $c2rows[0].Delta -eq 4) 'C2 page lists only its own change (+4)'

$html = Get-Page "$PortalUrl/Admin/Centers"
Check ($html -match "href=""/Admin/Centers/$c1/CapacityAudit"">Capacity audit</a>") '/Admin/Centers links each centre to its capacity audit'
$html = Get-Page "$PortalUrl/Admin/Sessions?centerId=$c1"
Check ($html -match "href=""/Admin/Centers/$c1/CapacityAudit\?sessionId=$s1"">Audit</a>") '/Admin/Sessions links each session row to its filtered audit'

$status = try { (Invoke-WebRequest -Uri "$PortalUrl/Admin/Centers/999999/CapacityAudit" -UseBasicParsing).StatusCode } catch { [int]$_.Exception.Response.StatusCode }
Check ($status -eq 404) "unknown centre returns 404 (got $status)"

$snapAfter = Get-TableSnapshot
Check ($snapBefore -eq $snapAfter) 'reads only: row counts and CHECKSUM_AGG of every dbo table unchanged by the page requests'

Write-Host "functional-capacity-audit: $script:passed checks passed"
exit 0
