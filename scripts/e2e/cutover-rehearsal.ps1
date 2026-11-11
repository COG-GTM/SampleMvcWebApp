<#
.SYNOPSIS
    Cutover rehearsal (Phase 3): dual-run one workload on the legacy estate and on the migrated stack, both started from
    the same production copy, and diff Booking/ExamSession/Waitlist/AuditLog. Zero drift required.

.DESCRIPTION
    Production copy = AssessmentDelivery rebuilt from the estate/legacy-baseline db\ scripts (deterministic seed).
      1. legacy   : <LegacyRoot>\db\apply.ps1 builds the copy (backed up once), <LegacyRoot>\scripts\run-legacy.ps1 -SkipDb starts the legacy estate (procs + triggers);
                    cutover.py capture --target legacy
      2. migrated : restore the same copy from that backup, apply this branch's cutover migrations
                    (db\schema\016, 017, db\seed\046), re-create Scheduling (db\bootstrap.sql), start scheduling-service
                    (initial import from the copy), then this checkout's portal / booking-api / results-service
                    (scripts\run-legacy.ps1 -SkipDb); cutover.py capture --target migrated
      3. cutover.py diff -> build\cutover\diff.json (exit 1 on any drift)
    The migrated stack is left running so scripts\mixed-traffic.ps1 -E2E can run the e2e suite against it.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\e2e\cutover-rehearsal.ps1 -LegacyRoot ..\ad-legacy
#>
param(
    [string]$LegacyRoot = $(if ($env:LEGACY_ROOT) { $env:LEGACY_ROOT } else { Join-Path (Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))) 'ad-legacy' }),
    [string]$OutDir = 'build\cutover',
    [switch]$SkipBuild,
    [string]$Server = 'localhost,1433',
    [string]$CopyFile = 'AssessmentDelivery_cutover_copy.bak',   # relative = the instance's default backup directory
    [string]$SaPassword = $(if ($env:AD_SA_PASSWORD) { $env:AD_SA_PASSWORD } else { 'Str0ng!Passw0rd' })
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$LegacyRoot = (Resolve-Path $LegacyRoot).Path
$OutDir = Join-Path $root $OutDir
New-Item -ItemType Directory -Force $OutDir | Out-Null
$runDir = Join-Path $root '.run'
New-Item -ItemType Directory -Force $runDir | Out-Null
$sqlcmd = (Get-Command sqlcmd -ErrorAction SilentlyContinue).Source
if (-not $sqlcmd) { $sqlcmd = "$env:ProgramFiles\SqlCmd\sqlcmd.exe" }
$env:SQLCMD = $sqlcmd
$py = (Get-Command python -ErrorAction SilentlyContinue).Source
$jdk21 = Get-ChildItem "$env:ProgramFiles\Eclipse Adoptium" -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($env:JAVA21_HOME) { $java21 = Join-Path $env:JAVA21_HOME 'bin\java.exe' } else { $java21 = Join-Path $jdk21.FullName 'bin\java.exe' }
$schedJar = Join-Path $root 'services\scheduling-service\target\scheduling-service.jar'

function Step([string]$m) { Write-Host "[cutover] $m" }
function Sql([string[]]$a) {
    $o = & $sqlcmd -S $Server -U sa -P $SaPassword -C -b -I @a 2>&1
    if ($LASTEXITCODE -ne 0) { throw "sqlcmd failed: $o" }
}
function Stop-Scheduling {
    Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -and $_.CommandLine.Contains('scheduling-service.jar') } |
        ForEach-Object { Step "stopping scheduling-service (pid $($_.ProcessId))"; Stop-Process -Id $_.ProcessId -Force }
}
function Stop-All {
    & (Join-Path $root 'scripts\run-legacy.ps1') -Stop
    & (Join-Path $LegacyRoot 'scripts\run-legacy.ps1') -Stop
    Stop-Scheduling
}
function Invoke-Py([string[]]$a) {
    & $py @a
    if ($LASTEXITCODE -ne 0) { throw "python $($a -join ' ') failed (exit $LASTEXITCODE)" }
}
function Wait-Http([string]$url, [int]$seconds) {
    for ($i = 0; $i -lt $seconds; $i++) {
        try { $r = Invoke-WebRequest $url -UseBasicParsing -TimeoutSec 5; if ($r.StatusCode -lt 500) { return } } catch {
            if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -lt 500) { return } }
        Start-Sleep -Seconds 1
    }
    throw "$url not ready after $seconds s"
}

$legacyHead = (& git -C $LegacyRoot rev-parse HEAD).Trim()
$head = (& git -C $root rev-parse HEAD).Trim()
Step "legacy worktree $LegacyRoot @ $legacyHead; migrated worktree $root @ $head"

# ---------------------------------------------------------------- 1. legacy
Stop-All
$a = @(); if ($SkipBuild) { $a += '-SkipBuild' }
try { Sql @('-Q', 'SELECT 1') } catch {
    # fresh machine: the legacy launcher installs/configures SQL Server and the sa login before the db apply script can run
    Step 'SQL Server not ready: letting the legacy launcher initialize it'
    & (Join-Path $LegacyRoot 'scripts\run-legacy.ps1') -NoPortal @a
    & (Join-Path $LegacyRoot 'scripts\run-legacy.ps1') -Stop
}
Step 'legacy: building the production copy from the legacy-baseline db scripts and backing it up'
& (Join-Path $LegacyRoot 'db\apply.ps1') -Server $Server -SaPassword $SaPassword -Sqlcmd $sqlcmd
if ($LASTEXITCODE -ne 0) { throw 'production copy build failed' }
# one copy for both runs: rebuilding twice would give the seed's trigger-written AuditLog rows different LoggedAt values
Sql @('-Q', "BACKUP DATABASE AssessmentDelivery TO DISK = N'$CopyFile' WITH INIT, COPY_ONLY")
Step 'legacy: starting the legacy estate on the copy'
& (Join-Path $LegacyRoot 'scripts\run-legacy.ps1') -SkipDb @a
Invoke-Py @((Join-Path $PSScriptRoot 'cutover.py'), 'capture', '--target', 'legacy', '--repo', $LegacyRoot, '--out', (Join-Path $OutDir 'legacy.json'))
& (Join-Path $LegacyRoot 'scripts\run-legacy.ps1') -Stop

# ---------------------------------------------------------------- 2. migrated
Step 'migrated: restoring the same production copy'
Sql @('-Q', "ALTER DATABASE AssessmentDelivery SET SINGLE_USER WITH ROLLBACK IMMEDIATE; RESTORE DATABASE AssessmentDelivery FROM DISK = N'$CopyFile' WITH REPLACE; ALTER DATABASE AssessmentDelivery SET MULTI_USER")
foreach ($f in 'db\schema\016_result_session_starts_at.sql', 'db\schema\017_result_drop_scheduling_fks.sql', 'db\seed\046_seed_result_session_starts_at.sql') {
    Step "cutover migration: $f"
    Sql @('-i', (Join-Path $root $f))
}
Step 're-creating the Scheduling database'
Sql @('-Q', "IF DB_ID('Scheduling') IS NOT NULL BEGIN ALTER DATABASE Scheduling SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE Scheduling; END")
Sql @('-i', (Join-Path $root 'services\scheduling-service\db\bootstrap.sql'))
if (-not (Test-Path $schedJar) -or -not $SkipBuild) {
    Step 'building scheduling-service (JDK 21)'
    $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $java21)
    $repoUrl = $env:MVNW_REPOURL; $env:MVNW_REPOURL = $null
    Push-Location (Join-Path $root 'services')
    try { & cmd /c 'mvnw.cmd -q -B -DskipTests -f scheduling-service\pom.xml package'; if ($LASTEXITCODE -ne 0) { throw 'scheduling-service build failed' } }
    finally { Pop-Location; $env:MVNW_REPOURL = $repoUrl }
}
Step 'starting scheduling-service on :8084 (initial import from the copy)'
$env:SCHEDULING_IMPORT = 'always'
$log = Join-Path $runDir 'scheduling-service.log'
$p = Start-Process -FilePath $java21 -WorkingDirectory $root -WindowStyle Hidden -PassThru -ArgumentList '-jar', "`"$schedJar`"", "--logging.file.name=`"$log`""
Set-Content (Join-Path $runDir 'scheduling-service.pid') $p.Id
Wait-Http 'http://localhost:8084/api/v1/sessions/1' 180
Remove-Item Env:\SCHEDULING_IMPORT
Step 'starting the rewired portal, booking-api and results-service from this checkout'
& (Join-Path $root 'scripts\run-legacy.ps1') -SkipDb @a
Invoke-Py @((Join-Path $PSScriptRoot 'cutover.py'), 'capture', '--target', 'migrated', '--repo', $root, '--out', (Join-Path $OutDir 'migrated.json'))

# ---------------------------------------------------------------- 3. diff
& $py (Join-Path $PSScriptRoot 'cutover.py') diff (Join-Path $OutDir 'legacy.json') (Join-Path $OutDir 'migrated.json') --out (Join-Path $OutDir 'diff.json')
$code = $LASTEXITCODE
Step "diff exit $code (0 = zero drift); migrated stack left running"
exit $code
