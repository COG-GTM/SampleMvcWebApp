<#
.SYNOPSIS
  Builds and starts services/routing-gateway (MBA-3822 strangler routing layer) on :8000 (Windows).

.DESCRIPTION
  Fronts the legacy portal (:8080) and booking-api (:8081) and sends booking/capacity traffic for the centres in
  services/routing-gateway/config/routing.properties (routing.modern-centers) to scheduling-service (:8090).
  Start scripts/run-legacy.ps1 and scripts/run-scheduling-service.ps1 first. Needs JDK 21 (JAVA_HOME or Temurin
  21 under Program Files) and Maven 3.9 (MAVEN_CMD, mvn on PATH, or the module's mvnw.cmd).
  Run from Git Bash with stdin closed:  powershell -ExecutionPolicy Bypass -File scripts/run-routing-gateway.ps1 < /dev/null

.PARAMETER Stop      Stop the gateway.
.PARAMETER Status    Show whether it is running.
.PARAMETER SkipBuild Reuse target/routing-gateway.jar.
#>
param(
    [switch]$Stop,
    [switch]$Status,
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
$root    = Split-Path -Parent $PSScriptRoot
$svcDir  = Join-Path $root 'services\routing-gateway'
$runDir  = Join-Path $root '.run'
$jar     = Join-Path $svcDir 'target\routing-gateway.jar'
$runJar  = Join-Path $runDir 'routing-gateway.jar'
$pidFile = Join-Path $runDir 'routing-gateway.pid'
$logFile = Join-Path $runDir 'routing-gateway.log'
$port    = 8000

function Write-Step([string]$m) { Write-Host "[run-routing-gateway] $m" }

function Get-RunningPid {
    if (-not (Test-Path $pidFile)) { return $null }
    $id = [int](Get-Content $pidFile -Raw)
    if (Get-Process -Id $id -ErrorAction SilentlyContinue) { return $id }
    return $null
}

function Test-Health {
    try { (Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 "http://localhost:$port/actuator/health").StatusCode -eq 200 } catch { $false }
}

function Get-Jdk21 {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        $v = & (Join-Path $env:JAVA_HOME 'bin\java.exe') -version 2>&1 | Out-String
        if ($v -match 'version "21') { return $env:JAVA_HOME }
    }
    $c = Get-ChildItem 'C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Java' -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^(jdk-)?21' } | Select-Object -First 1
    if (-not $c) { throw 'JDK 21 not found: set JAVA_HOME (scripts/run-scheduling-service.ps1 installs one)' }
    return $c.FullName
}

if ($Status) {
    $id = Get-RunningPid
    if ($id -and (Test-Health)) { Write-Step "running (pid $id) on :$port" } else { Write-Step 'not running' }
    return
}
if ($Stop) {
    $id = Get-RunningPid
    if ($id) { Stop-Process -Id $id -Force; Write-Step "stopped pid $id" } else { Write-Step 'not running' }
    Remove-Item $pidFile -ErrorAction SilentlyContinue
    return
}

$jdk = Get-Jdk21
if (-not ($SkipBuild -and (Test-Path $jar))) {
    Write-Step "building routing-gateway (JAVA_HOME=$jdk)"
    $env:JAVA_HOME = $jdk
    $mvn = if ($env:MAVEN_CMD) { $env:MAVEN_CMD }
           elseif (Get-Command mvn.cmd -ErrorAction SilentlyContinue) { 'mvn.cmd' }
           else { Join-Path $svcDir 'mvnw.cmd' }
    Push-Location $svcDir
    try {
        & $mvn -B -q package -DskipTests
        if ($LASTEXITCODE -ne 0) { throw 'maven build failed' }
    } finally { Pop-Location }
}

$id = Get-RunningPid
if ($id) { Write-Step "already running (pid $id)"; return }
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
Copy-Item $jar $runJar -Force
$env:ROUTING_CONFIG_FILE = Join-Path $svcDir 'config\routing.properties'
$java = Join-Path $jdk 'bin\java.exe'
Write-Step "starting on :$port (allow-list file $env:ROUTING_CONFIG_FILE, log $logFile)"
$p = Start-Process -FilePath $java -ArgumentList '-jar', "`"$runJar`"" -WorkingDirectory $svcDir -WindowStyle Hidden `
    -RedirectStandardOutput $logFile -RedirectStandardError "$logFile.err" -PassThru
Set-Content $pidFile $p.Id
for ($i = 0; $i -lt 60; $i++) {
    if (Test-Health) { Write-Step "up: http://localhost:$port  (portal + booking-api via the strangler)"; return }
    if ($p.HasExited) { throw "routing-gateway exited with code $($p.ExitCode) - see $logFile" }
    Start-Sleep -Seconds 2
}
throw "routing-gateway did not become healthy in 120s - see $logFile"
