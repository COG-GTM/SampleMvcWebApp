<#
.SYNOPSIS
    run-modern.ps1 - start/stop the modern estate on Windows: scheduling-service (:8090, outbox relay on) and the
    routing gateway (:8000). Windows twin of scripts/run-scheduling-service.sh + scripts/run-routing-gateway.sh.

.DESCRIPTION
    Needs SQL Server on localhost,1433 with AssessmentDelivery applied by db\apply.ps1 (scripts\run-legacy.ps1 does it;
    db\apply.ps1 also applies db\security\035 + 040). Builds the two jars with Maven unless -SkipBuild (or the jars
    exist and -SkipBuild is given). Logs and pids under .run\.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\run-modern.ps1 -SkipBuild
    powershell -ExecutionPolicy Bypass -File scripts\run-modern.ps1 -Stop
#>
param(
    [switch]$Stop,
    [switch]$Status,
    [switch]$SkipBuild,
    [switch]$NoGateway,
    [string]$Java21Home = $(if ($env:JAVA21_HOME) { $env:JAVA21_HOME } else { '' }),
    [string]$Mvn        = $(if ($env:MVN) { $env:MVN } else { 'mvn' }),
    [string]$SaPassword = $(if ($env:AD_SA_PASSWORD) { $env:AD_SA_PASSWORD } else { 'Str0ng!Passw0rd' })
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$root = Split-Path -Parent $PSScriptRoot
$runDir = Join-Path $root '.run'
$schedJar = Join-Path $root 'services\scheduling-service\target\scheduling-service.jar'
$gwJar = Join-Path $root 'services\routing-gateway\target\routing-gateway.jar'

function Write-Step([string]$msg) { Write-Host "[run-modern] $msg" }

function Find-Java21 {
    if ($Java21Home -and (Test-Path "$Java21Home\bin\java.exe")) { return $Java21Home }
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe") -and
        ((& cmd /c "`"$env:JAVA_HOME\bin\java.exe`" -version 2>&1") -match 'version "21')) { return $env:JAVA_HOME }
    foreach ($r in @("$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java", "$env:ProgramFiles\Microsoft")) {
        if (-not (Test-Path $r)) { continue }
        $d = Get-ChildItem $r -Directory | Where-Object { $_.Name -match '^jdk-?21' } | Sort-Object Name -Descending | Select-Object -First 1
        if ($d) { return $d.FullName }
    }
    throw 'JDK 21 not found - install Temurin 21 or pass -Java21Home'
}

function Test-Port([int]$port) {
    $c = New-Object Net.Sockets.TcpClient
    try { $c.Connect('127.0.0.1', $port); return $true } catch { return $false } finally { $c.Close() }
}

function Get-PortPids([int]$port) {
    @(netstat -ano | Select-String -Pattern "^\s*TCP\s+\S+:$port\s+\S+\s+LISTENING\s+(\d+)" |
        ForEach-Object { [int]$_.Matches[0].Groups[1].Value } | Sort-Object -Unique)
}

function Wait-Health([string]$url, [int]$seconds, [string]$name, $proc) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        if ($proc -and $proc.HasExited) { throw "$name exited during startup, see .run\$name.log" }
        try { if ((Invoke-WebRequest $url -UseBasicParsing -TimeoutSec 5).StatusCode -eq 200) { return } } catch { }
        Start-Sleep -Seconds 1
    }
    throw "$name did not answer $url within $seconds s, see .run\$name.log"
}

function Start-Jar([string]$name, [string]$java, [string]$jar, [string[]]$jarArgs, [hashtable]$envVars) {
    foreach ($k in $envVars.Keys) { [Environment]::SetEnvironmentVariable($k, $envVars[$k], 'Process') }
    $log = Join-Path $runDir "$name.log"
    $argList = @('-jar', "`"$jar`"") + $jarArgs
    $p = Start-Process -FilePath "$java\bin\java.exe" -ArgumentList $argList -WorkingDirectory $root -PassThru `
        -WindowStyle Hidden -RedirectStandardOutput $log -RedirectStandardError (Join-Path $runDir "$name.err.log")
    Set-Content (Join-Path $runDir "$name.pid") $p.Id
    return $p
}

function Stop-Modern {
    foreach ($s in @(@{ Name = 'routing-gateway'; Port = 8000 }, @{ Name = 'scheduling-service'; Port = 8090 })) {
        $ids = @()
        $pidFile = Join-Path $runDir "$($s.Name).pid"
        if (Test-Path $pidFile) { $ids += [int](Get-Content $pidFile) }
        $ids += Get-PortPids $s.Port
        foreach ($i in ($ids | Sort-Object -Unique)) {
            if (Get-Process -Id $i -ErrorAction SilentlyContinue) {
                Write-Step "stopping $($s.Name) (pid $i)"; Stop-Process -Id $i -Force -ErrorAction SilentlyContinue
            }
        }
        Remove-Item $pidFile -ErrorAction SilentlyContinue
    }
}

New-Item -ItemType Directory -Force -Path $runDir | Out-Null
if (-not (Test-Path "$runDir\.gitignore")) { Set-Content "$runDir\.gitignore" '*' }
if ($Stop) { Stop-Modern; exit 0 }
if ($Status) {
    foreach ($p in 8090, 8000) { '{0,-6} {1}' -f ":$p", $(if (Test-Port $p) { 'up' } else { 'down' }) | Write-Host }
    exit 0
}

$java = Find-Java21
Write-Step "JDK 21: $java"
if (Test-Port 8090) { throw 'port 8090 is already in use (run with -Stop first)' }
if (-not $NoGateway -and (Test-Port 8000)) { throw 'port 8000 is already in use (run with -Stop first)' }

if (-not $SkipBuild -or -not (Test-Path $schedJar) -or (-not $NoGateway -and -not (Test-Path $gwJar))) {
    $env:JAVA_HOME = $java
    Write-Step 'building scheduling-service (frontend included, tests skipped)'
    & $Mvn -q -B -f (Join-Path $root 'services\scheduling-service\pom.xml') -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw "scheduling-service build failed (exit $LASTEXITCODE)" }
    if (-not $NoGateway) {
        Write-Step 'building routing-gateway (tests skipped)'
        & $Mvn -q -B -f (Join-Path $root 'services\routing-gateway\pom.xml') -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw "routing-gateway build failed (exit $LASTEXITCODE)" }
    }
}

Write-Step 'starting scheduling-service on :8090 (import on startup, outbox relay on; log .run\scheduling-service.log)'
$sched = Start-Jar 'scheduling-service' $java $schedJar @() @{
    SCHEDULING_IMPORT_ON_STARTUP = 'true'; SCHEDULING_RELAY_ENABLED = 'true'; SCHEDULING_DB_PASSWORD = $SaPassword
    RECONCILIATION_REPORT_DIR = (Join-Path $root 'reports') }
Wait-Health 'http://localhost:8090/actuator/health' 180 'scheduling-service' $sched
$deadline = (Get-Date).AddSeconds(180)
while (-not (Select-String -Path (Join-Path $runDir 'scheduling-service.log') -Pattern 'Import finished' -Quiet)) {
    if ((Get-Date) -gt $deadline) { throw 'scheduling-service bootstrap import did not finish, see .run\scheduling-service.log' }
    Start-Sleep -Seconds 1
}
Write-Step 'scheduling-service up, bootstrap import finished'

if (-not $NoGateway) {
    Write-Step 'starting routing-gateway on :8000 (log .run\routing-gateway.log)'
    $cfg = Join-Path $root 'services\routing-gateway\config\routing.properties'
    $gw = Start-Jar 'routing-gateway' $java $gwJar @("--routing.config-file=`"$cfg`"") @{}
    Wait-Health 'http://localhost:8000/actuator/health' 120 'routing-gateway' $gw
    Write-Step 'routing-gateway up'
}
@"

  modern UI (via gateway)  http://localhost:8000/app
  center admin             http://localhost:8000/app/admin/centers/BOS01
  scheduling-service       http://localhost:8090/actuator/health
  stop:                    scripts\run-modern.ps1 -Stop
"@ | Write-Host
