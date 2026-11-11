# characterization/replay.ps1 [--target legacy|modern --base-url URL] [scenario globs...]  (Windows twin of characterization/replay)
$ErrorActionPreference = 'Stop'
$py = if ($env:PYTHON) { $env:PYTHON } elseif (Get-Command python -ErrorAction SilentlyContinue) { 'python' } else { 'py' }
$env:PYTHONUTF8 = '1'
Push-Location $PSScriptRoot
try { & $py -m harness replay @args; exit $LASTEXITCODE } finally { Pop-Location }
