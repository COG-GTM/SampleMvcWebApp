<#
    apply.ps1 - rebuilds the AssessmentDelivery database (DEV ONLY - drops the database).
    Runs schema -> procs -> triggers -> seed in file-name order, same as apply.sh.

      .\db\apply.ps1                      rebuild + seed against localhost,1433
      .\db\apply.ps1 -NoSeed              rebuild without seed data
      .\db\apply.ps1 -Server "MYBOX\SQL2022" -SaPassword "..."

    Needs sqlcmd on the PATH (SQL Server command line utilities) or pass -Sqlcmd.
    Environment overrides: AD_SQL_SERVER, AD_SQL_USER, AD_SA_PASSWORD, SQLCMD.
#>
param(
    [string]$Server     = $(if ($env:AD_SQL_SERVER)  { $env:AD_SQL_SERVER }  else { "localhost,1433" }),
    [string]$User       = $(if ($env:AD_SQL_USER)    { $env:AD_SQL_USER }    else { "sa" }),
    [string]$SaPassword = $(if ($env:AD_SA_PASSWORD) { $env:AD_SA_PASSWORD } else { "Str0ng!Passw0rd" }),
    [string]$Sqlcmd     = $(if ($env:SQLCMD)         { $env:SQLCMD }         else { "sqlcmd" }),
    [switch]$NoSeed
)

$ErrorActionPreference = "Stop"
$dbDir = Split-Path -Parent $MyInvocation.MyCommand.Path

if (-not (Get-Command $Sqlcmd -ErrorAction SilentlyContinue)) {
    Write-Error "sqlcmd not found. Install the SQL Server command line utilities or pass -Sqlcmd <path>."
    exit 1
}

# -b stop on error, -I QUOTED_IDENTIFIER ON, -C trust the dev certificate
function Invoke-SqlFile([string]$Database, [string[]]$SqlArgs) {
    $out = & $Sqlcmd -S $Server -U $User -P $SaPassword -C -b -I -d $Database @SqlArgs 2>&1
    $code = $LASTEXITCODE
    return @{ Code = $code; Output = $out }
}

Write-Host "== waiting for $Server"
$ok = $false
for ($i = 1; $i -le 30; $i++) {
    if ((Invoke-SqlFile "master" @("-Q", "SELECT 1")).Code -eq 0) { $ok = $true; break }
    Start-Sleep -Seconds 2
}
if (-not $ok) { Write-Error "SQL Server not reachable at $Server"; exit 1 }

Write-Host "== dropping AssessmentDelivery"
$r = Invoke-SqlFile "master" @("-Q", "IF DB_ID('AssessmentDelivery') IS NOT NULL BEGIN ALTER DATABASE AssessmentDelivery SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE AssessmentDelivery; END")
if ($r.Code -ne 0) { $r.Output | Write-Host; Write-Error "drop failed"; exit 1 }

$folders = @("schema", "procs", "triggers")
if (-not $NoSeed) { $folders += "seed" }
# security last (same as apply.sh): 035 read-only importer grants, 040 single-writer DENYs (MBA-3823)
$folders += "security"

foreach ($folder in $folders) {
    Get-ChildItem -Path (Join-Path $dbDir $folder) -Filter *.sql | Sort-Object Name | ForEach-Object {
        Write-Host "-- $folder/$($_.Name)"
        $r = Invoke-SqlFile "master" @("-i", $_.FullName)
        $r.Output | Where-Object { "$_" -ne "" -and "$_" -notlike "Changed database context*" } | ForEach-Object { Write-Host $_ }
        if ($r.Code -ne 0) { Write-Error "FAILED: $($_.FullName)"; exit 1 }
    }
}

Write-Host "== done"
(Invoke-SqlFile "AssessmentDelivery" @("-W", "-i", (Join-Path $dbDir "checks/row-counts.sql"))).Output | Select-Object -First 13 | ForEach-Object { Write-Host $_ }
