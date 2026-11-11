<#
.SYNOPSIS
    Starts the whole Assessment Delivery legacy estate on Windows (dev only).

.DESCRIPTION
    1. SQL Server Express/Developer on localhost,1433 with SQL authentication
       (installs SQL Server 2022 Express if no instance is found, then enables TCP 1433 and mixed mode)
    2. db\apply.ps1 - drops and re-creates AssessmentDelivery with the seed data (-SkipDb keeps the data)
    3. JDK 8 (installs Temurin 8 if none is found), builds services\ with the Maven wrapper
    4. booking-api :8081, results-service :8082 (capacity-sync is retired into scheduling-service's reconciler)
    5. the portal on :8080 through scripts\run-portal.ps1 (IIS Express)

    Logs and pids go to .run\ in the repo root.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\run-legacy.ps1
    powershell -ExecutionPolicy Bypass -File scripts\run-legacy.ps1 -SkipDb
    powershell -ExecutionPolicy Bypass -File scripts\run-legacy.ps1 -Status
    powershell -ExecutionPolicy Bypass -File scripts\run-legacy.ps1 -Stop
#>
param(
    [switch]$Stop,
    [switch]$Status,
    [switch]$SkipDb,
    [switch]$SkipBuild,
    [switch]$NoPortal,
    [string]$SaPassword = $(if ($env:AD_SA_PASSWORD) { $env:AD_SA_PASSWORD } else { 'Str0ng!Passw0rd' })
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$root    = Split-Path -Parent $PSScriptRoot
$runDir  = Join-Path $root '.run'
$site    = Join-Path $root 'AssessmentDelivery.Web'
$server  = 'localhost,1433'
$services = @(
    @{ Name = 'booking-api';     Port = 8081; Url = 'http://localhost:8081/api/v1/sessions' },
    @{ Name = 'results-service'; Port = 8082; Url = 'http://localhost:8082/api/v1/results/candidate/NW00000001' }
)
$portalPort = 8080

# SQL Server 2022 Express (same media the chocolatey package uses)
$sqlExpressUrl = 'https://download.microsoft.com/download/3/8/d/38de7036-2433-4207-8eae-06e247e17b25/SQLEXPR_x64_ENU.exe'
$temurin8Url   = 'https://api.adoptium.net/v3/installer/latest/8/ga/windows/x64/jdk/hotspot/normal/eclipse'

function Write-Step([string]$msg) { Write-Host "[run-legacy] $msg" }

function Test-Port([int]$port) {
    $c = New-Object System.Net.Sockets.TcpClient
    try {
        $ar = $c.BeginConnect('127.0.0.1', $port, $null, $null)
        if (-not $ar.AsyncWaitHandle.WaitOne(1000)) { return $false }
        $c.EndConnect($ar)
        return $true
    } catch { return $false } finally { $c.Close() }
}

function Update-SessionPath {
    $env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [Environment]::GetEnvironmentVariable('Path', 'User')
}

function Test-Admin {
    $id = [Security.Principal.WindowsIdentity]::GetCurrent()
    return (New-Object Security.Principal.WindowsPrincipal $id).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

# ---------------------------------------------------------------- sqlcmd
function Find-Sqlcmd {
    if ($env:SQLCMD -and (Test-Path $env:SQLCMD)) { return $env:SQLCMD }
    $cmd = Get-Command sqlcmd -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = @(
        "$env:ProgramFiles\SqlCmd\sqlcmd.exe",
        "$env:ProgramFiles\Microsoft SQL Server\Client SDK\ODBC\170\Tools\Binn\SQLCMD.EXE",
        "$env:ProgramFiles\Microsoft SQL Server\Client SDK\ODBC\180\Tools\Binn\SQLCMD.EXE",
        "$env:ProgramFiles\Microsoft SQL Server\160\Tools\Binn\SQLCMD.EXE",
        "$env:ProgramFiles\Microsoft SQL Server\150\Tools\Binn\SQLCMD.EXE"
    )
    foreach ($p in $candidates) { if (Test-Path $p) { return $p } }
    return $null
}

function Get-Sqlcmd {
    $s = Find-Sqlcmd
    if ($s) { return $s }
    Write-Step 'sqlcmd not found - installing the sqlcmd utility'
    if (Get-Command choco -ErrorAction SilentlyContinue) {
        choco install sqlcmd -y --no-progress | Out-Host
    } elseif (Get-Command winget -ErrorAction SilentlyContinue) {
        winget install --id Microsoft.Sqlcmd -e --accept-source-agreements --accept-package-agreements | Out-Host
    } else {
        throw 'sqlcmd not found and neither choco nor winget is available - install the SQL Server command line utilities'
    }
    Update-SessionPath
    $s = Find-Sqlcmd
    if (-not $s) { throw 'sqlcmd install failed' }
    return $s
}

function Invoke-Sql([string[]]$SqlArgs, [switch]$Trusted) {
    if ($Trusted) { $auth = @('-E') } else { $auth = @('-U', 'sa', '-P', $SaPassword) }
    $out = & $script:sqlcmd -S $server @auth -C -b -l 5 @SqlArgs 2>&1
    return @{ Code = $LASTEXITCODE; Output = $out }
}

# ---------------------------------------------------------------- SQL Server
$sqlRegRoot = 'HKLM:\SOFTWARE\Microsoft\Microsoft SQL Server'

function Get-SqlInstances {
    # name -> instance id (e.g. MSSQLSERVER -> MSSQL16.MSSQLSERVER, SQLEXPRESS -> MSSQL16.SQLEXPRESS)
    $key = Join-Path $sqlRegRoot 'Instance Names\SQL'
    $list = @()
    if (Test-Path $key) {
        $props = Get-ItemProperty $key
        foreach ($p in $props.PSObject.Properties) {
            if ($p.Name -like 'PS*') { continue }
            $list += [pscustomobject]@{ Name = $p.Name; Id = $p.Value }
        }
    }
    return $list
}

function Get-SqlServiceName([string]$instanceName) {
    if ($instanceName -eq 'MSSQLSERVER') { return 'MSSQLSERVER' }
    return 'MSSQL$' + $instanceName
}

function Install-SqlExpress {
    if (-not (Test-Admin)) { throw 'SQL Server is not installed. Re-run from an elevated PowerShell to install SQL Server 2022 Express.' }
    $work = Join-Path $env:TEMP 'ad-sqlexpress'
    New-Item -ItemType Directory -Force -Path $work | Out-Null
    $exe = Join-Path $work 'SQLEXPR_x64_ENU.exe'
    if (-not (Test-Path $exe)) {
        Write-Step 'downloading SQL Server 2022 Express (~250 MB) ...'
        Invoke-WebRequest $sqlExpressUrl -OutFile $exe -UseBasicParsing
    }
    $media = Join-Path $work 'media'
    if (-not (Test-Path (Join-Path $media 'setup.exe'))) {
        Write-Step 'extracting setup media ...'
        Start-Process $exe -ArgumentList '/Q', "/x:`"$media`"" -Wait
    }
    # default instance so it listens on 1433 like the Linux container
    $ini = Join-Path $work 'ConfigurationFile.ini'
    @(
        '[OPTIONS]',
        'ACTION="Install"',
        'FEATURES=SQLENGINE',
        'INSTANCENAME="MSSQLSERVER"',
        'INSTANCEID="MSSQLSERVER"',
        'SECURITYMODE="SQL"',
        "SAPWD=`"$SaPassword`"",
        'TCPENABLED="1"',
        'SQLSYSADMINACCOUNTS="BUILTIN\Administrators"',
        'SQLSVCSTARTUPTYPE="Automatic"',
        'UPDATEENABLED="False"'
    ) | Set-Content -Path $ini -Encoding ASCII
    Write-Step 'installing SQL Server 2022 Express as the default instance (5-10 minutes) ...'
    $p = Start-Process (Join-Path $media 'setup.exe') -ArgumentList '/Q', '/IACCEPTSQLSERVERLICENSETERMS', "/ConfigurationFile=`"$ini`"" -Wait -PassThru
    if ($p.ExitCode -ne 0 -and $p.ExitCode -ne 3010) {
        throw "SQL Server setup failed (exit $($p.ExitCode)); see %ProgramFiles%\Microsoft SQL Server\160\Setup Bootstrap\Log\Summary.txt"
    }
    Remove-Item $ini -Force -ErrorAction SilentlyContinue
}

function Set-SqlTcpAndMixedMode($inst) {
    # returns $true when a setting changed and the service needs a restart
    $changed = $false
    $base = Join-Path $sqlRegRoot "$($inst.Id)\MSSQLServer"
    if ((Get-ItemProperty $base).LoginMode -ne 2) {
        Write-Step "enabling SQL authentication on $($inst.Name)"
        Set-ItemProperty $base -Name LoginMode -Value 2
        $changed = $true
    }
    $tcp = Join-Path $base 'SuperSocketNetLib\Tcp'
    if ((Get-ItemProperty $tcp).Enabled -ne 1) {
        Write-Step "enabling TCP/IP on $($inst.Name)"
        Set-ItemProperty $tcp -Name Enabled -Value 1
        $changed = $true
    }
    $ipAll = Join-Path $tcp 'IPAll'
    $cur = Get-ItemProperty $ipAll
    if ($cur.TcpPort -ne '1433' -or $cur.TcpDynamicPorts -ne '') {
        Write-Step "setting $($inst.Name) to listen on TCP 1433"
        Set-ItemProperty $ipAll -Name TcpPort -Value '1433'
        Set-ItemProperty $ipAll -Name TcpDynamicPorts -Value ''
        $changed = $true
    }
    return $changed
}

function Initialize-SqlServer {
    $instances = @(Get-SqlInstances)
    if ($instances.Count -eq 0) {
        if (Test-Port 1433) {
            Write-Step 'no local SQL Server instance installed, but something answers on :1433 - using it'
        } else {
            Install-SqlExpress
            $instances = @(Get-SqlInstances)
            if ($instances.Count -eq 0) { throw 'SQL Server install finished but no instance is registered' }
        }
    }
    if ($instances.Count -gt 0) {
        # prefer the default instance, then SQLEXPRESS, then whatever is there
        $inst = $instances | Where-Object { $_.Name -eq 'MSSQLSERVER' } | Select-Object -First 1
        if (-not $inst) { $inst = $instances | Where-Object { $_.Name -eq 'SQLEXPRESS' } | Select-Object -First 1 }
        if (-not $inst) { $inst = $instances[0] }
        $svcName = Get-SqlServiceName $inst.Name
        $restart = $false
        if (Test-Admin) {
            $restart = Set-SqlTcpAndMixedMode $inst
        } else {
            Write-Step 'not elevated - skipping the TCP/auth check of the SQL Server configuration'
        }
        $svc = Get-Service $svcName
        if ($svc.StartType -eq 'Disabled' -and (Test-Admin)) { Set-Service $svcName -StartupType Automatic }
        if ($restart -and $svc.Status -eq 'Running') {
            Write-Step "restarting $svcName"
            Restart-Service $svcName -Force
        } elseif ($svc.Status -ne 'Running') {
            Write-Step "starting $svcName"
            Start-Service $svcName
        }
        Write-Step "SQL Server instance $($inst.Name) ($svcName) running"
    }

    $deadline = (Get-Date).AddSeconds(90)
    while (-not (Test-Port 1433)) {
        if ((Get-Date) -gt $deadline) { throw 'SQL Server is not listening on localhost,1433' }
        Start-Sleep -Seconds 2
    }

    # sa must match what db\apply.ps1 uses; reset it through Windows auth if it does not
    if ((Invoke-Sql @('-Q', 'SELECT 1')).Code -ne 0) {
        Write-Step 'sa login failed - resetting the sa password through Windows authentication'
        $pw = $SaPassword.Replace("'", "''")
        $r = Invoke-Sql -Trusted @('-Q', "ALTER LOGIN sa WITH PASSWORD = N'$pw', CHECK_POLICY = OFF; ALTER LOGIN sa ENABLE;")
        if ($r.Code -ne 0) { $r.Output | Write-Host; throw 'could not log in as sa (set AD_SA_PASSWORD or -SaPassword)' }
        if ((Invoke-Sql @('-Q', 'SELECT 1')).Code -ne 0) { throw 'sa login still fails after the password reset' }
    }
    Write-Step "SQL Server answers on $server (SQL auth)"
}

function Invoke-ApplyDb {
    if ($SkipDb) {
        $r = Invoke-Sql @('-h', '-1', '-W', '-Q', "SET NOCOUNT ON; SELECT CASE WHEN DB_ID('AssessmentDelivery') IS NULL THEN 0 ELSE 1 END")
        if ("$($r.Output)".Trim() -eq '1') {
            Write-Step '-SkipDb: keeping the existing AssessmentDelivery database'
            return
        }
        Write-Step '-SkipDb given but AssessmentDelivery does not exist - creating it'
    }
    Write-Step 'db\apply.ps1: re-creating AssessmentDelivery (existing data is dropped; -SkipDb keeps it)'
    & (Join-Path $root 'db\apply.ps1') -Server $server -SaPassword $SaPassword -Sqlcmd $script:sqlcmd
    if ($LASTEXITCODE -ne 0) { throw "db apply failed (exit $LASTEXITCODE)" }
}

# ---------------------------------------------------------------- Java services
function Test-Jdk8([string]$jdkHome) {
    if (-not $jdkHome) { return $false }
    $javac = Join-Path $jdkHome 'bin\javac.exe'
    if (-not (Test-Path $javac)) { return $false }
    $v = (& cmd /c "`"$javac`" -version 2>&1") | Out-String
    return $v -match 'javac 1\.8\.'
}

function Find-Jdk8 {
    if (Test-Jdk8 $env:JAVA_HOME) { return $env:JAVA_HOME }
    $roots = @("$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java", "$env:ProgramFiles\Zulu", "$env:ProgramFiles\Microsoft", "$env:ProgramFiles\AdoptOpenJDK")
    foreach ($r in $roots) {
        if (-not (Test-Path $r)) { continue }
        foreach ($d in (Get-ChildItem $r -Directory | Where-Object { $_.Name -match '(jdk-?8|jdk1\.8|zulu-?8)' } | Sort-Object Name -Descending)) {
            if (Test-Jdk8 $d.FullName) { return $d.FullName }
        }
    }
    return $null
}

function Get-Jdk8 {
    $jdk = Find-Jdk8
    if ($jdk) { return $jdk }
    if (-not (Test-Admin)) { throw 'JDK 8 not found. Install Temurin 8 or re-run elevated so the script can install it.' }
    Write-Step 'JDK 8 not found - installing Eclipse Temurin 8'
    $msi = Join-Path $env:TEMP 'temurin8-jdk.msi'
    Invoke-WebRequest $temurin8Url -OutFile $msi -UseBasicParsing
    $p = Start-Process msiexec.exe -ArgumentList '/i', "`"$msi`"", '/quiet', '/norestart', 'ADDLOCAL=FeatureMain' -Wait -PassThru
    if ($p.ExitCode -ne 0 -and $p.ExitCode -ne 3010) { throw "Temurin 8 install failed (exit $($p.ExitCode))" }
    $jdk = Find-Jdk8
    if (-not $jdk) { throw 'Temurin 8 installed but no JDK 8 was found' }
    return $jdk
}

function Build-Services([string]$jdk) {
    if ($SkipBuild) { Write-Step '-SkipBuild: using the existing jars'; return }
    Write-Step "building services with the Maven wrapper (JAVA_HOME=$jdk)"
    $env:JAVA_HOME = $jdk
    # mvnw.cmd 3.3.2 builds a bad download url when MVNW_REPOURL is set, use the default url
    $repoUrl = $env:MVNW_REPOURL
    $env:MVNW_REPOURL = $null
    Push-Location (Join-Path $root 'services')
    try {
        & cmd /c 'mvnw.cmd -q -B -DskipTests package'
        if ($LASTEXITCODE -ne 0) { throw "Maven build failed (exit $LASTEXITCODE)" }
    } finally {
        Pop-Location
        $env:MVNW_REPOURL = $repoUrl
    }
}

function Get-PidFile([string]$name) { return (Join-Path $runDir "$name.pid") }

function Get-RunningPid([string]$name) {
    $f = Get-PidFile $name
    if (-not (Test-Path $f)) { return $null }
    $id = [int](Get-Content $f -Raw).Trim()
    # pids get reused, only trust the file if it is still our jar
    if ((Find-ServiceJvm $name) -contains $id) { return $id }
    return $null
}

function Find-ServiceJvm([string]$name) {
    # a JVM started from this checkout, even if its pid file is gone
    $jar = Join-Path $root "services\$name\target\$name.jar"
    Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
        Where-Object { $_.CommandLine -and $_.CommandLine.Contains($jar) } |
        Select-Object -ExpandProperty ProcessId
}

function Start-Services([string]$jdk) {
    $java = Join-Path $jdk 'bin\java.exe'
    foreach ($s in $services) {
        $jar = Join-Path $root "services\$($s.Name)\target\$($s.Name).jar"
        if (-not (Test-Path $jar)) { throw "$jar missing - run without -SkipBuild" }
        $running = Get-RunningPid $s.Name
        if (-not $running) { $running = Find-ServiceJvm $s.Name | Select-Object -First 1 }
        if ($running) {
            Write-Step "$($s.Name) already running (pid $running)"
            Set-Content (Get-PidFile $s.Name) $running
            continue
        }
        if (Test-Port $s.Port) { throw "port $($s.Port) is already in use by another process" }
        $log = Join-Path $runDir "$($s.Name).log"
        Remove-Item $log -ErrorAction SilentlyContinue
        Write-Step "starting $($s.Name) on :$($s.Port) (log .run\$($s.Name).log)"
        # no stream redirection here: a redirected child would inherit this console's handles
        $p = Start-Process -FilePath $java -WorkingDirectory $root -WindowStyle Hidden -PassThru `
            -ArgumentList '-jar', "`"$jar`"", "--logging.file=`"$log`""
        Set-Content (Get-PidFile $s.Name) $p.Id
    }
}

function Wait-Services {
    foreach ($s in $services) {
        $deadline = (Get-Date).AddSeconds(120)
        while (-not (Test-Port $s.Port)) {
            $id = Get-RunningPid $s.Name
            if (-not $id) { throw "$($s.Name) exited during startup, see .run\$($s.Name).log" }
            if ((Get-Date) -gt $deadline) { throw "$($s.Name) did not open :$($s.Port), see .run\$($s.Name).log" }
            Start-Sleep -Seconds 1
        }
        Write-Step "$($s.Name) up on :$($s.Port)"
    }
}

# ---------------------------------------------------------------- portal
function Find-PortalIisExpress {
    # IIS Express listens through http.sys, so the port owner is pid 4; find it by command line instead
    Get-CimInstance Win32_Process -Filter "Name='iisexpress.exe'" |
        Where-Object { $_.CommandLine -match "/port:$portalPort\b" -and $_.CommandLine -like "*$site*" } |
        Select-Object -ExpandProperty ProcessId
}

function Test-Portal {
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:$portalPort/Admin/Centers" -UseBasicParsing -TimeoutSec 20
        return $r.StatusCode -eq 200
    } catch { return $false }
}

function Start-Portal {
    $log = Join-Path $runDir 'portal.log'
    $runPortal = Join-Path $PSScriptRoot 'run-portal.ps1'
    $portalArgs = "-Port $portalPort"
    if ($SkipBuild) { $portalArgs += ' -SkipBuild' }
    Write-Step "starting the portal via scripts\run-portal.ps1 (restore + build + IIS Express, log .run\portal.log)"
    # separate PowerShell writing to its own log, so IIS Express does not hold on to this console
    $cmd = "& '$runPortal' $portalArgs *> '$log'; exit `$LASTEXITCODE"
    $p = Start-Process powershell.exe -WindowStyle Hidden -PassThru `
        -ArgumentList '-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', "`"$cmd`""
    $deadline = (Get-Date).AddMinutes(20)
    while ($true) {
        if ($p.HasExited) {
            if (Test-Portal) { break }
            Get-Content $log -Tail 30 -ErrorAction SilentlyContinue | Write-Host
            throw 'scripts\run-portal.ps1 failed, see .run\portal.log'
        }
        if ((Get-Date) -gt $deadline) { throw 'portal did not come up within 20 minutes, see .run\portal.log' }
        Start-Sleep -Seconds 3
    }
    $iis = Find-PortalIisExpress | Select-Object -First 1
    if ($iis) { Set-Content (Get-PidFile 'portal') $iis }
    Write-Step "portal up on :$portalPort (IIS Express pid $iis)"
}

# ---------------------------------------------------------------- stop / status
function Stop-Estate {
    # capacity-sync is retired, but an estate started before the retirement may still run it
    foreach ($s in ($services + @(@{ Name = 'capacity-sync'; Port = 8083 }))) {
        $ids = @()
        $id = Get-RunningPid $s.Name
        if ($id) { $ids += $id }
        $ids += @(Find-ServiceJvm $s.Name)
        foreach ($i in ($ids | Sort-Object -Unique)) {
            Write-Step "stopping $($s.Name) (pid $i)"
            Stop-Process -Id $i -Force -ErrorAction SilentlyContinue
        }
        Remove-Item (Get-PidFile $s.Name) -ErrorAction SilentlyContinue
    }
    foreach ($i in @(Find-PortalIisExpress)) {
        Write-Step "stopping portal IIS Express (pid $i)"
        Stop-Process -Id $i -Force -ErrorAction SilentlyContinue
    }
    Remove-Item (Get-PidFile 'portal') -ErrorAction SilentlyContinue
    Write-Step 'stopped (SQL Server is left running)'
}

function Show-Status {
    $sqlState = 'not installed'
    $inst = @(Get-SqlInstances)
    if ($inst.Count -gt 0) {
        $sqlState = ($inst | ForEach-Object { "$($_.Name)=" + (Get-Service (Get-SqlServiceName $_.Name)).Status }) -join ', '
    }
    $sqlPort = if (Test-Port 1433) { 'listening' } else { 'closed' }
    '{0,-16} {1,-10} {2}' -f 'sql-server', ":1433 $sqlPort", $sqlState | Write-Host
    foreach ($s in $services) {
        $id = Get-RunningPid $s.Name
        if (-not $id) { $id = Find-ServiceJvm $s.Name | Select-Object -First 1 }
        $state = if (Test-Port $s.Port) { 'up' } else { 'down' }
        $pidText = if ($id) { "pid $id" } else { '' }
        '{0,-16} {1,-10} {2}' -f $s.Name, ":$($s.Port) $state", $pidText | Write-Host
    }
    $iis = Find-PortalIisExpress | Select-Object -First 1
    $state = if (Test-Portal) { 'up' } else { 'down' }
    $pidText = if ($iis) { "IIS Express pid $iis" } else { '' }
    '{0,-16} {1,-10} {2}' -f 'portal', ":$portalPort $state", $pidText | Write-Host
}

function Show-Urls {
    @"

  portal           http://localhost:$portalPort/   (Admin/Centers, Booking, Eligibility, Results)
  booking-api      http://localhost:8081/api/v1/sessions
  results-service  http://localhost:8082/api/v1/results/candidate/{candidateNumber}
  SQL Server       $server  database AssessmentDelivery  login ad_app

  logs and pids:   .run\
  stop:            scripts\run-legacy.ps1 -Stop
"@ | Write-Host
}

# ---------------------------------------------------------------- main
New-Item -ItemType Directory -Force -Path $runDir | Out-Null

if ($Stop)   { Stop-Estate; exit 0 }
if ($Status) { Show-Status; exit 0 }

$script:sqlcmd = Get-Sqlcmd
Write-Step "sqlcmd: $script:sqlcmd"
Initialize-SqlServer
Invoke-ApplyDb
$jdk = Get-Jdk8
Write-Step "JDK 8: $jdk"
Build-Services $jdk
Start-Services $jdk
Wait-Services
if ($NoPortal) {
    Write-Step '-NoPortal: not starting the portal'
} elseif (Test-Portal) {
    Write-Step "portal already answering on :$portalPort"
} else {
    Start-Portal
}
Write-Step 'all components up'
Show-Urls
