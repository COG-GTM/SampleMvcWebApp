<#
.SYNOPSIS
    Builds and runs scheduling-service (Java 21 / Spring Boot 3 + React UI) on :8090 on Windows (dev only).

.DESCRIPTION
    1. Connects to the SQL Server that scripts\run-legacy.ps1 installs (localhost,1433, SQL auth as sa).
       AssessmentDelivery must already exist (run scripts\run-legacy.ps1 first); the read-only importer login
       ad_sched_import is (re)applied from db\security\035_sched_import_login.sql and the Scheduling DB is
       created if missing (Flyway creates the tables on startup).
    2. Installs Temurin 21 and Node.js LTS through winget (or choco) when they are missing.
    3. Builds services\scheduling-service with its Maven wrapper (frontend-maven-plugin builds the UI;
       Testcontainers ITs skip without Docker) unless -SkipBuild.
    4. Starts the jar on :8090 (copied to .run\ first so a rebuild cannot rewrite it under the JVM) and waits
       for /actuator/health. Logs and the pid file go to .run\ in the repo root.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\run-scheduling-service.ps1
    powershell -ExecutionPolicy Bypass -File scripts\run-scheduling-service.ps1 -SkipBuild
    powershell -ExecutionPolicy Bypass -File scripts\run-scheduling-service.ps1 -Status
    powershell -ExecutionPolicy Bypass -File scripts\run-scheduling-service.ps1 -Stop
#>
param(
    [switch]$Stop,
    [switch]$Status,
    [switch]$SkipBuild,
    [string]$Server = $(if ($env:AD_SQL_SERVER) { $env:AD_SQL_SERVER } else { 'localhost,1433' }),
    [string]$SaPassword = $(if ($env:AD_SA_PASSWORD) { $env:AD_SA_PASSWORD } else { 'Str0ng!Passw0rd' }),
    # dev password created by db\security\035_sched_import_login.sql
    [string]$ImportPassword = $(if ($env:SCHEDULING_LEGACY_DB_PASSWORD) { $env:SCHEDULING_LEGACY_DB_PASSWORD } else { 'Sch3dImport!Dev2026' })
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$root    = Split-Path -Parent $PSScriptRoot
$svcDir  = Join-Path $root 'services\scheduling-service'
$runDir  = Join-Path $root '.run'
$jar     = Join-Path $svcDir 'target\scheduling-service.jar'
$runJar  = Join-Path $runDir 'scheduling-service.jar'
$pidFile = Join-Path $runDir 'scheduling-service.pid'
$logFile = Join-Path $runDir 'scheduling-service.log'
$port    = 8090
$health  = "http://localhost:$port/actuator/health"

function Write-Step([string]$msg) { Write-Host "[run-scheduling] $msg" }

function Update-SessionPath {
    $env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [Environment]::GetEnvironmentVariable('Path', 'User')
}

function Test-Port([int]$p) {
    $c = New-Object System.Net.Sockets.TcpClient
    try {
        $ar = $c.BeginConnect('127.0.0.1', $p, $null, $null)
        if (-not $ar.AsyncWaitHandle.WaitOne(1000)) { return $false }
        $c.EndConnect($ar)
        return $true
    } catch { return $false } finally { $c.Close() }
}

function Test-Health {
    try { return (Invoke-RestMethod $health -TimeoutSec 3).status -eq 'UP' } catch { return $false }
}

function Install-Package([string]$wingetId, [string]$chocoId) {
    if (Get-Command winget -ErrorAction SilentlyContinue) {
        winget install --id $wingetId -e --silent --accept-source-agreements --accept-package-agreements | Out-Host
    } elseif (Get-Command choco -ErrorAction SilentlyContinue) {
        choco install $chocoId -y --no-progress | Out-Host
    } else {
        throw "$wingetId is missing and neither winget nor choco is available - install it manually"
    }
    Update-SessionPath
}

# ---------------------------------------------------------------- process bookkeeping
function Get-RunningPid {
    if (-not (Test-Path $pidFile)) { return $null }
    $id = [int](Get-Content $pidFile -Raw)
    # pids get reused: only trust the file if that process is still our jar
    $p = Get-CimInstance Win32_Process -Filter "ProcessId=$id" -ErrorAction SilentlyContinue
    if ($p -and $p.CommandLine -like '*scheduling-service.jar*') { return $id }
    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
    return $null
}

function Show-Status {
    $id = Get-RunningPid
    if ($id -and (Test-Health)) { Write-Step "running (pid $id): http://localhost:$port/app"; return $true }
    if ($id) { Write-Step "process $id is alive but $health is not UP (see $logFile)"; return $false }
    if (Test-Port $port) { Write-Step "not started by this script, but something listens on :$port"; return $false }
    Write-Step 'not running'
    return $false
}

function Stop-Service8090 {
    $id = Get-RunningPid
    if ($id) {
        Stop-Process -Id $id -Force
        Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
        Write-Step "stopped (pid $id)"
    } else {
        Write-Step 'not running'
    }
}

if ($Status) { if (Show-Status) { exit 0 } else { exit 1 } }
if ($Stop) { Stop-Service8090; exit 0 }

# ---------------------------------------------------------------- SQL Server (from run-legacy.ps1)
function Find-Sqlcmd {
    if ($env:SQLCMD -and (Test-Path $env:SQLCMD)) { return $env:SQLCMD }
    $cmd = Get-Command sqlcmd -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($p in @("$env:ProgramFiles\SqlCmd\sqlcmd.exe",
                     "$env:ProgramFiles\Microsoft SQL Server\Client SDK\ODBC\170\Tools\Binn\SQLCMD.EXE",
                     "$env:ProgramFiles\Microsoft SQL Server\Client SDK\ODBC\180\Tools\Binn\SQLCMD.EXE")) {
        if (Test-Path $p) { return $p }
    }
    Install-Package 'Microsoft.Sqlcmd' 'sqlcmd'
    $cmd = Get-Command sqlcmd -ErrorAction SilentlyContinue
    if (-not $cmd) { throw 'sqlcmd not found - install the SQL Server command line utilities' }
    return $cmd.Source
}

function Invoke-Sql([string[]]$SqlArgs) {
    $out = & $script:sqlcmd -S $Server -U sa -P $SaPassword -C -b -l 5 @SqlArgs 2>&1
    if ($LASTEXITCODE -ne 0) { throw "sqlcmd failed: $($out | Out-String)" }
    return $out
}

function Initialize-Databases {
    $script:sqlcmd = Find-Sqlcmd
    if (-not (Test-Port 1433)) { throw "nothing listens on $Server - run scripts\run-legacy.ps1 first (it installs SQL Server Express)" }
    $has = (Invoke-Sql @('-h', '-1', '-Q', "SET NOCOUNT ON; SELECT COUNT(*) FROM sys.databases WHERE name = 'AssessmentDelivery'") | Out-String).Trim()
    if ($has -ne '1') { throw 'AssessmentDelivery not found - run scripts\run-legacy.ps1 first' }
    Write-Step 'applying db\security\035_sched_import_login.sql (read-only importer login ad_sched_import)'
    Invoke-Sql @('-d', 'AssessmentDelivery', '-I', '-i', (Join-Path $root 'db\security\035_sched_import_login.sql')) | Out-Null
    Invoke-Sql @('-Q', "IF DB_ID('Scheduling') IS NULL CREATE DATABASE [Scheduling];") | Out-Null
}

# ---------------------------------------------------------------- JDK 21 / Node LTS
function Test-Jdk21([string]$jdkHome) {
    if (-not $jdkHome) { return $false }
    $java = Join-Path $jdkHome 'bin\java.exe'
    if (-not (Test-Path $java)) { return $false }
    $v = (& cmd /c "`"$java`" -version 2>&1") | Out-String
    return $v -match 'version "21'
}

function Find-Jdk21 {
    if (Test-Jdk21 $env:JAVA_HOME) { return $env:JAVA_HOME }
    $roots = @("$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java", "$env:ProgramFiles\Microsoft")
    foreach ($r in $roots) {
        if (-not (Test-Path $r)) { continue }
        foreach ($d in Get-ChildItem $r -Directory -Filter 'jdk-21*' | Sort-Object Name -Descending) {
            if (Test-Jdk21 $d.FullName) { return $d.FullName }
        }
    }
    return $null
}

function Get-Jdk21 {
    $jdk = Find-Jdk21
    if ($jdk) { return $jdk }
    Write-Step 'JDK 21 not found - installing Temurin 21'
    Install-Package 'EclipseAdoptium.Temurin.21.JDK' 'temurin21'
    $jdk = Find-Jdk21
    if (-not $jdk) { throw 'Temurin 21 install finished but no JDK 21 was found' }
    return $jdk
}

function Confirm-Node {
    # frontend-maven-plugin downloads its own Node into frontend\node; a system Node LTS is only needed to
    # run the Playwright e2e (services\scheduling-service\e2e) next to the service.
    if (Get-Command node -ErrorAction SilentlyContinue) { return }
    Write-Step 'Node.js not found - installing Node.js LTS'
    Install-Package 'OpenJS.NodeJS.LTS' 'nodejs-lts'
}

# ---------------------------------------------------------------- build / start
function Build-Service([string]$jdk) {
    if ($SkipBuild -and (Test-Path $jar)) { Write-Step 'skipping build (-SkipBuild)'; return }
    Write-Step "building scheduling-service (JAVA_HOME=$jdk)"
    $env:JAVA_HOME = $jdk
    if (-not $env:MVNW_REPOURL) { $env:MVNW_REPOURL = 'https://maven-central.storage-download.googleapis.com/maven2' }
    Push-Location $svcDir
    try {
        & .\mvnw.cmd -B -q package -DskipITs
        if ($LASTEXITCODE -ne 0) { throw 'maven build failed' }
    } finally { Pop-Location }
}

function Start-Service8090([string]$jdk) {
    $id = Get-RunningPid
    if ($id) { Write-Step "already running (pid $id)"; return }
    if (Test-Port $port) { throw ":$port is already in use by another process" }
    New-Item -ItemType Directory -Force -Path $runDir | Out-Null
    Copy-Item $jar $runJar -Force
    $env:SCHEDULING_DB_URL = "jdbc:sqlserver://$($Server -replace ',', ':');databaseName=Scheduling;encrypt=true;trustServerCertificate=true"
    $env:SCHEDULING_DB_USER = 'sa'
    $env:SCHEDULING_DB_PASSWORD = $SaPassword
    $env:SCHEDULING_LEGACY_DB_URL = "jdbc:sqlserver://$($Server -replace ',', ':');databaseName=AssessmentDelivery;encrypt=true;trustServerCertificate=true;applicationIntent=ReadOnly"
    $env:SCHEDULING_LEGACY_DB_USER = 'ad_sched_import'
    $env:SCHEDULING_LEGACY_DB_PASSWORD = $ImportPassword
    $env:SCHEDULING_IMPORT_ON_STARTUP = 'true'
    $env:SCHEDULING_RELAY_DB_URL = "jdbc:sqlserver://$($Server -replace ',', ':');databaseName=AssessmentDelivery;encrypt=true;trustServerCertificate=true"
    if (-not $env:SCHEDULING_RELAY_ENABLED) { $env:SCHEDULING_RELAY_ENABLED = 'true' }
    $java = Join-Path $jdk 'bin\java.exe'
    Write-Step "starting on :$port (log $logFile)"
    $p = Start-Process -FilePath $java -ArgumentList '-jar', "`"$runJar`"" -WorkingDirectory $root -WindowStyle Hidden `
        -RedirectStandardOutput $logFile -RedirectStandardError "$logFile.err" -PassThru
    Set-Content $pidFile $p.Id
    for ($i = 0; $i -lt 90; $i++) {
        if (Test-Health) { Write-Step "up: http://localhost:$port/app  (API http://localhost:$port/api/v2)"; return }
        if ($p.HasExited) { throw "scheduling-service exited with code $($p.ExitCode) - see $logFile" }
        Start-Sleep -Seconds 2
    }
    throw "scheduling-service did not become healthy in 180s - see $logFile"
}

Initialize-Databases
$jdk = Get-Jdk21
Confirm-Node
Build-Service $jdk
Start-Service8090 $jdk
