# Runs every repro against the local estate. Most scripts wrap their writes in BEGIN TRAN ... ROLLBACK.
# Usage (from repo root, after db\apply.ps1):  powershell -ExecutionPolicy Bypass -File docs\as-is\repros\sql\run-all.ps1
param(
    [string]$Server     = $(if ($env:AD_SQL_SERVER)  { $env:AD_SQL_SERVER }  else { "localhost,1433" }),
    [string]$SaPassword = $(if ($env:AD_SA_PASSWORD) { $env:AD_SA_PASSWORD } else { "Str0ng!Passw0rd" })
)
Get-ChildItem -Path $PSScriptRoot -Filter *.sql | Sort-Object Name | ForEach-Object {
    Write-Host "===== $($_.Name)"
    & sqlcmd -S $Server -U sa -P $SaPassword -C -W -s "|" -d AssessmentDelivery -i $_.FullName
}
