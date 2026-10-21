param([string]$Output = 'build/reports/m1c-network-inventory.tsv')
$ErrorActionPreference = 'Stop'
& python (Join-Path $PSScriptRoot 'migration_boundaries.py') --mode inventory --output $Output
exit $LASTEXITCODE
