<#
.SYNOPSIS
    drift-before-after.ps1 - seat drift before/after the single writer (MBA-3823) on Windows; writes
    reports/drift-before-after.md. Windows twin of scripts/drift-before-after.sh (same workload, same checks).

.DESCRIPTION
    BEFORE: the legacy estate at -BeforeRef (default legacy-baseline) in a separate worktree (-BeforeDir, default
            ..\SampleMvcWebApp-legacy-baseline): scripts\run-legacy.ps1 (fresh seed), scripts\mixed-traffic.ps1
            (20 portal bookings through the real portal + 20 partner bookings on booking-api, 3 cancels per channel),
            a capacity-sync run (POST :8083/sync/run), then db\checks\seat-drift.sql. Expected: more than 0 rows.
    AFTER:  this checkout: scripts\run-legacy.ps1 (fresh seed + single-writer DENYs), scripts\run-modern.ps1
            (scheduling-service + outbox relay, routing gateway :8000), the same workload through the routing layer
            (scripts\mixed-traffic.ps1 -RoutingUrl http://localhost:8000), capacity-sync (now a skipped run),
            `reconcile` and seat-drift.sql. Expected: 0 mismatches and 0 rows.
    DROPS and re-creates AssessmentDelivery and Scheduling - dev only. Leaves the AFTER estate running.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\drift-before-after.ps1 -SkipBuild
#>
param(
    [string]$BeforeRef  = 'legacy-baseline',
    [string]$BeforeDir  = '',
    [string]$Report     = '',
    [switch]$SkipBuild,
    [switch]$NoPortal,
    [string]$SaPassword = $(if ($env:AD_SA_PASSWORD) { $env:AD_SA_PASSWORD } else { 'Str0ng!Passw0rd' }),
    [string]$Sqlcmd     = $(if ($env:SQLCMD) { $env:SQLCMD } else { 'sqlcmd' })
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$root = Split-Path -Parent $PSScriptRoot
if (-not $BeforeDir) { $BeforeDir = Join-Path (Split-Path -Parent $root) 'SampleMvcWebApp-legacy-baseline' }
if (-not $Report) { $Report = Join-Path $root 'reports\drift-before-after.md' }
$work = Join-Path $root '.run\drift'
New-Item -ItemType Directory -Force -Path $work, (Split-Path -Parent $Report) | Out-Null
if (-not (Get-Command $Sqlcmd -ErrorAction SilentlyContinue)) {
    if (Test-Path "$env:ProgramFiles\SqlCmd\sqlcmd.exe") { $Sqlcmd = "$env:ProgramFiles\SqlCmd\sqlcmd.exe" }
    else { throw 'sqlcmd not found (pass -Sqlcmd or set SQLCMD)' }
}

function Write-Log([string]$msg) { Write-Host "[drift-before-after] $msg" }
function Invoke-Ps([string]$script, [string[]]$scriptArgs, [string]$log) {
    & powershell -NoProfile -ExecutionPolicy Bypass -File $script @scriptArgs *> $log
    return $LASTEXITCODE
}
function Invoke-Sa([string[]]$sqlArgs) { & $Sqlcmd -S localhost,1433 -U sa -P $SaPassword -C -b @sqlArgs }
function Measure-Drift([string]$outFile) {
    $out = Invoke-Sa @('-W', '-s', '|', '-i', (Join-Path $root 'db\checks\seat-drift.sql')) 2>&1
    @($out | Where-Object { "$_" -notlike 'Changed database context*' }) | Set-Content $outFile -Encoding UTF8
    $n = Invoke-Sa @('-h', '-1', '-W', '-Q', "SET NOCOUNT ON; SELECT COUNT(*) FROM AssessmentDelivery.dbo.ExamSession s
        LEFT JOIN (SELECT SessionId, COUNT(*) n FROM AssessmentDelivery.dbo.Booking WHERE Status IN ('BOOKED','ATTENDED')
                   GROUP BY SessionId) b ON b.SessionId = s.SessionId
        WHERE s.SeatsAvailable <> s.Capacity - ISNULL(b.n, 0)")
    return ("$n" -replace '\s', '')
}
function Wait-Http([string]$url, [int]$seconds) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        try { Invoke-WebRequest $url -UseBasicParsing -TimeoutSec 5 | Out-Null; return } catch { Start-Sleep -Seconds 1 }
    }
    throw "no answer from $url"
}
function Stop-All {
    if (Test-Path (Join-Path $BeforeDir 'scripts\run-legacy.ps1')) {
        Invoke-Ps (Join-Path $BeforeDir 'scripts\run-legacy.ps1') @('-Stop') (Join-Path $work 'stop-before.log') | Out-Null
    }
    Invoke-Ps (Join-Path $root 'scripts\run-modern.ps1') @('-Stop') (Join-Path $work 'stop-modern.log') | Out-Null
    Invoke-Ps (Join-Path $root 'scripts\run-legacy.ps1') @('-Stop') (Join-Path $work 'stop-legacy.log') | Out-Null
}
function Get-Booked([string]$log) {
    $t = Get-Content $log -Raw
    $portal = if ($t -match 'portal\s*:\s*(\d+) ok') { $Matches[1] } else { '0' }
    $partner = if ($t -match 'partner:\s*(\d+) ok') { $Matches[1] } else { '0' }
    return "$portal+$partner"
}
function Get-Summary([string]$log) {
    $lines = Get-Content $log | ForEach-Object { "$_" }
    $start = [array]::FindIndex([string[]]$lines, [Predicate[string]]{ param($l) $l -match 'session summary' })
    if ($start -lt 0) { return @() }
    $lines[$start..($lines.Count - 1)] | Where-Object { $_ -notmatch 'seat-drift.sql:' -and $_ -notmatch '\] done' }
}

$afterSha = (git -C $root rev-parse HEAD).Trim()
# final head = the assessment-delivery-demo commit this checkout is based on (the evidence checkout adds only reports/harness)
$finalHead = (git -C $root merge-base HEAD origin/assessment-delivery-demo 2>$null)
$finalHead = if ($finalHead) { "$finalHead".Trim() } else { $afterSha }
$branch = (git -C $root rev-parse --abbrev-ref HEAD).Trim()
$dirty = if (git -C $root status --porcelain -- . ':!reports' ':!characterization/reports') { ' (+ uncommitted changes)' } else { '' }
Stop-All

# ---------------------------------------------------------------- BEFORE
if (-not (Test-Path $BeforeDir)) {
    Write-Log "git worktree add $BeforeDir $BeforeRef"
    git -C $root worktree add --detach $BeforeDir $BeforeRef | Out-Null
} else {
    git -C $BeforeDir checkout -q --detach $BeforeRef
}
$beforeSha = (git -C $BeforeDir rev-parse HEAD).Trim()
Write-Log "BEFORE: $BeforeRef ($beforeSha) - fresh seed + legacy services + portal"
Invoke-Sa @('-Q', "IF DB_ID('Scheduling') IS NOT NULL BEGIN ALTER DATABASE Scheduling SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE Scheduling; END") | Out-Null
$legacyArgs = @(); if ($SkipBuild) { $legacyArgs += '-SkipBuild' }
if ((Invoke-Ps (Join-Path $BeforeDir 'scripts\run-legacy.ps1') $legacyArgs (Join-Path $work 'before-run-legacy.log')) -ne 0) {
    throw "baseline start failed, see $work\before-run-legacy.log"
}
Wait-Http 'http://localhost:8081/api/v1/sessions?from=2099-01-01' 60
Invoke-Ps (Join-Path $BeforeDir 'scripts\mixed-traffic.ps1') @() (Join-Path $work 'before-mixed-traffic.log') | Out-Null
$beforeSync = (Invoke-WebRequest 'http://localhost:8083/sync/run' -Method Post -UseBasicParsing -TimeoutSec 120).Content
$beforeRows = Measure-Drift (Join-Path $work 'before-seat-drift.txt')
Write-Log "BEFORE: seat-drift rows = $beforeRows"
Invoke-Ps (Join-Path $BeforeDir 'scripts\run-legacy.ps1') @('-Stop') (Join-Path $work 'stop-before.log') | Out-Null

# ---------------------------------------------------------------- AFTER
Write-Log "AFTER: $afterSha$dirty - fresh seed + DENYs, legacy services, scheduling-service + relay, gateway :8000"
$routing = 'http://localhost:8000'
$afterArgs = @(); if ($SkipBuild) { $afterArgs += '-SkipBuild' }; if ($NoPortal) { $afterArgs += '-NoPortal' }
if ((Invoke-Ps (Join-Path $root 'scripts\run-legacy.ps1') $afterArgs (Join-Path $work 'after-run-legacy.log')) -ne 0) {
    throw "start failed, see $work\after-run-legacy.log"
}
$modernArgs = @(); if ($SkipBuild) { $modernArgs += '-SkipBuild' }
if ((Invoke-Ps (Join-Path $root 'scripts\run-modern.ps1') $modernArgs (Join-Path $work 'after-modern.log')) -ne 0) {
    throw "scheduling-service/gateway start failed, see $work\after-modern.log"
}
Wait-Http "$routing/api/v1/sessions?from=2099-01-01" 60
Invoke-Ps (Join-Path $root 'scripts\mixed-traffic.ps1') @('-RoutingUrl', $routing, '-BookingApiUrl', $routing) (Join-Path $work 'after-mixed-traffic.log') | Out-Null
$afterSync = (Invoke-WebRequest "$routing/sync/run" -Method Post -UseBasicParsing -TimeoutSec 120).Content
$java = Get-ChildItem "$env:ProgramFiles\Eclipse Adoptium" -Directory | Where-Object { $_.Name -match '^jdk-?21' } |
    Sort-Object Name -Descending | Select-Object -First 1
if ($env:JAVA21_HOME) { $javaExe = "$env:JAVA21_HOME\bin\java.exe" } else { $javaExe = Join-Path $java.FullName 'bin\java.exe' }
$env:SCHEDULING_DB_PASSWORD = $SaPassword
$env:RECONCILIATION_REPORT_DIR = Join-Path $root 'reports'
$reconcileLog = Join-Path $work 'after-reconcile.log'
& cmd /c "`"$javaExe`" -jar `"$root\services\scheduling-service\target\scheduling-service.jar`" reconcile --wait-seconds=30 > `"$reconcileLog`" 2>&1"
$reconcileExit = $LASTEXITCODE
$reconcileLine = @(Get-Content $reconcileLog | Where-Object { $_ -match '^reconcile:' }) | Select-Object -Last 1
$mismatches = if ($reconcileLine -match 'mismatches=(\d+)') { $Matches[1] } else { '?' }
$reconReport = if ($reconcileLine -match 'report=(.*)$') { $Matches[1] } else { '' }
$afterRows = Measure-Drift (Join-Path $work 'after-seat-drift.txt')
Write-Log "AFTER: seat-drift rows = $afterRows, reconcile mismatches = $mismatches (exit $reconcileExit)"

$beforeBooked = Get-Booked (Join-Path $work 'before-mixed-traffic.log')
$afterBooked = Get-Booked (Join-Path $work 'after-mixed-traffic.log')
$why = Get-Content (Join-Path $PSScriptRoot 'drift-before-after.sh') -Raw
$why = [regex]::Match($why, "(?s)cat <<'WHY'\r?\n(.*?)\r?\nWHY").Groups[1].Value
$md = New-Object System.Collections.Generic.List[string]
$md.Add('# Seat drift before/after MBA-3823 (single writer + outbox relay)')
$md.Add('')
$md.Add("Generated by ``scripts/drift-before-after.ps1`` (Windows twin of ``scripts/drift-before-after.sh``) on $((Get-Date).ToUniversalTime().ToString('yyyy-MM-dd HH:mm:ss'))Z.")
$md.Add("Final head SHA (``assessment-delivery-demo``): ``$finalHead``; evidence checkout ``$afterSha``.")
$md.Add('Same workload both times: `scripts/mixed-traffic.ps1` (20 portal + 20 partner bookings, 3 cancels per channel,')
$md.Add('fresh seed) followed by a capacity-sync run (before: `POST :8083/sync/run`; after: `POST :8000/sync/run` through the gateway).')
$md.Add('')
$md.Add('| | Code | Commit | bookings accepted (portal+partner) | seat-drift.sql rows | reconcile mismatches |')
$md.Add('|---|---|---|---|---|---|')
$md.Add("| Before | ``$BeforeRef`` | ``$beforeSha`` | $beforeBooked | **$beforeRows** | n/a (no scheduling-service) |")
$md.Add("| After | ``$branch``$dirty | ``$afterSha`` | $afterBooked | **$afterRows** | **$mismatches** (reconcile exit $reconcileExit) |")
$md.Add('')
$md.Add('Before-run write path: portal channel = the real .NET portal (`/Booking/Book`, `/Booking/Cancel`, stored procedures),')
$md.Add('partner channel = booking-api `:8081` (`POST/DELETE /api/v1/bookings`).')
$md.Add("After-run write path: portal channel ``POST/DELETE $routing/api/v2/bookings`` (channel PORTAL, what the modern UI does),")
$md.Add("partner channel ``POST/DELETE $routing/api/v1/bookings`` (gateway booking-api v1 translation) -> scheduling-service -> outbox -> relay (``ad_sched_relay``) -> AssessmentDelivery.")
$md.Add('')
$md.Add($why.TrimEnd())
$md.Add('')
$md.Add("## Before ($BeforeRef)"); $md.Add(''); $md.Add('```')
(Get-Summary (Join-Path $work 'before-mixed-traffic.log')) | ForEach-Object { $md.Add($_) }
$md.Add("capacity-sync: $beforeSync"); $md.Add('```'); $md.Add(''); $md.Add('db/checks/seat-drift.sql:'); $md.Add(''); $md.Add('```')
Get-Content (Join-Path $work 'before-seat-drift.txt') | ForEach-Object { $md.Add($_) }
$md.Add('```'); $md.Add('')
$md.Add("## After ($afterSha)"); $md.Add(''); $md.Add('```')
(Get-Summary (Join-Path $work 'after-mixed-traffic.log')) | ForEach-Object { $md.Add($_) }
$md.Add("capacity-sync: $afterSync"); $md.Add("$reconcileLine"); $md.Add('```'); $md.Add(''); $md.Add('db/checks/seat-drift.sql:'); $md.Add(''); $md.Add('```')
Get-Content (Join-Path $work 'after-seat-drift.txt') | ForEach-Object { $md.Add($_) }
$md.Add('```')
if ($reconReport) { $md.Add(''); $md.Add("Reconciliation report: ``$($reconReport.Replace($root + '\', '').Replace('\', '/'))``") }
[IO.File]::WriteAllText($Report, (($md -join "`n") + "`n"), (New-Object Text.UTF8Encoding $false))
Write-Log "wrote $Report"

if ($afterBooked -eq '0+0') { throw "the after workload booked nothing, see $work\after-mixed-traffic.log" }
if (-not ([int]$beforeRows -gt 0 -and $afterRows -eq '0' -and $mismatches -eq '0')) { exit 1 }
exit 0
