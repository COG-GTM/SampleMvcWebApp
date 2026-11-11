<#
.SYNOPSIS
    Restores, builds and hosts the Assessment Delivery Portal on http://localhost:8080 (IIS Express).

.DESCRIPTION
    Requirements (installed on demand where possible):
      - Visual Studio 2017+ or VS Build Tools with the "Web development build tools" workload
        (Microsoft.WebApplication.targets) and the .NET Framework 4.8 targeting pack
      - nuget.exe (downloaded to .\.tools if missing)
      - IIS Express 10
      - SQL Server on localhost,1433 with the AssessmentDelivery database (db/schema, db/procs)

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\run-portal.ps1
    powershell -ExecutionPolicy Bypass -File scripts\run-portal.ps1 -Wait
#>
param(
    [int]$Port = 8080,
    [string]$Configuration = 'Debug',
    [switch]$Wait,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$sln = Join-Path $root 'AssessmentDelivery.sln'
$site = Join-Path $root 'AssessmentDelivery.Web'
$tools = Join-Path $root '.tools'
$url = "http://localhost:$Port/"

function Get-NuGet {
    $cmd = Get-Command nuget.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $local = Join-Path $tools 'nuget.exe'
    if (-not (Test-Path $local)) {
        New-Item -ItemType Directory -Force -Path $tools | Out-Null
        Write-Host 'Downloading nuget.exe ...'
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest 'https://dist.nuget.org/win-x86-commandline/latest/nuget.exe' -OutFile $local
    }
    return $local
}

function Get-VsWhere {
    $vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
    if (Test-Path $vswhere) { return $vswhere }
    return $null
}

function Get-MSBuild {
    $vswhere = Get-VsWhere
    if ($vswhere) {
        $msb = & $vswhere -latest -products * -requires Microsoft.Component.MSBuild -find 'MSBuild\**\Bin\MSBuild.exe' | Select-Object -First 1
        if ($msb) { return $msb }
    }
    Write-Host 'MSBuild not found - installing VS 2022 Build Tools (web build tools + .NET 4.8 targeting pack). This takes a while ...'
    $installer = Join-Path $env:TEMP 'vs_buildtools.exe'
    Invoke-WebRequest 'https://aka.ms/vs/17/release/vs_buildtools.exe' -OutFile $installer
    $p = Start-Process $installer -ArgumentList '--quiet','--wait','--norestart','--nocache',
        '--add','Microsoft.VisualStudio.Workload.WebBuildTools',
        '--add','Microsoft.Net.Component.4.8.TargetingPack',
        '--includeRecommended' -Wait -PassThru
    if ($p.ExitCode -ne 0 -and $p.ExitCode -ne 3010) { throw "Build Tools install failed ($($p.ExitCode))" }
    $vswhere = Get-VsWhere
    return (& $vswhere -latest -products * -requires Microsoft.Component.MSBuild -find 'MSBuild\**\Bin\MSBuild.exe' | Select-Object -First 1)
}

function Get-IISExpress {
    foreach ($p in @("$env:ProgramFiles\IIS Express\iisexpress.exe", "${env:ProgramFiles(x86)}\IIS Express\iisexpress.exe")) {
        if (Test-Path $p) { return $p }
    }
    Write-Host 'IIS Express not found - installing ...'
    if (Get-Command choco -ErrorAction SilentlyContinue) {
        choco install iisexpress -y --no-progress | Out-Host
    } else {
        $msi = Join-Path $env:TEMP 'iisexpress_amd64_en-US.msi'
        Invoke-WebRequest 'https://download.microsoft.com/download/C/E/8/CE8D18F5-D4C0-45B5-B531-ADECD637A1AA/iisexpress_amd64_en-US.msi' -OutFile $msi
        Start-Process msiexec.exe -ArgumentList '/i', $msi, '/quiet', '/norestart' -Wait
    }
    $p = "$env:ProgramFiles\IIS Express\iisexpress.exe"
    if (-not (Test-Path $p)) { throw 'IIS Express install failed' }
    return $p
}

function Test-Site {
    try {
        $r = Invoke-WebRequest -Uri ($url + 'Admin/Centers') -UseBasicParsing -TimeoutSec 20
        return $r.StatusCode -eq 200
    } catch { return $false }
}

if (-not $SkipBuild) {
    $nuget = Get-NuGet
    Write-Host "Restoring NuGet packages ($nuget) ..."
    & $nuget restore $sln -PackagesDirectory (Join-Path $root 'packages') -NonInteractive | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'nuget restore failed' }

    $msbuild = Get-MSBuild
    Write-Host "Building $Configuration with $msbuild ..."
    & $msbuild $sln "/p:Configuration=$Configuration" /m /v:m /nologo | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'build failed' }
}

$iis = Get-IISExpress
$existing = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($existing) {
    $owner = Get-Process -Id $existing[0].OwningProcess -ErrorAction SilentlyContinue
    if ($owner -and $owner.ProcessName -eq 'iisexpress') {
        Write-Host "Stopping previous IIS Express on port $Port (pid $($owner.Id))"
        Stop-Process -Id $owner.Id -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 2
    } else {
        throw "Port $Port is in use by another process (pid $($existing[0].OwningProcess)); stop it or pass -Port"
    }
}

$logDir = Join-Path $root 'logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$log = Join-Path $logDir 'iisexpress.log'
Write-Host "Starting IIS Express on $url (log: $log)"
$proc = Start-Process -FilePath $iis -ArgumentList "/path:`"$site`"", "/port:$Port", '/systray:false' `
    -RedirectStandardOutput $log -RedirectStandardError "$log.err" -WindowStyle Hidden -PassThru

$deadline = (Get-Date).AddMinutes(3)
while ((Get-Date) -lt $deadline) {
    if ($proc.HasExited) { throw "IIS Express exited (code $($proc.ExitCode)), see $log" }
    if (Test-Site) { break }
    Start-Sleep -Seconds 2
}
if (-not (Test-Site)) { throw "Portal did not answer on $url within 3 minutes, see $log" }
Write-Host "Assessment Delivery Portal is up: $url (IIS Express pid $($proc.Id))"

if ($Wait) {
    Write-Host 'Press Ctrl+C to stop.'
    Wait-Process -Id $proc.Id
}
