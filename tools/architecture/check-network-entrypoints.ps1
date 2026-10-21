param([string]$Output = 'build/reports/m1c-network-entrypoints.tsv')
$ErrorActionPreference = 'Stop'
& python (Join-Path $PSScriptRoot 'migration_boundaries.py') --mode references --output $Output
exit $LASTEXITCODE
