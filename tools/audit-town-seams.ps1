param(
  [Parameter(Mandatory = $true)][string]$Directory,
  [Parameter(Mandatory = $true)][string]$Console,
  [int[]]$Towns = @(1,40,75,103,109)
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'map-acceptance-audit.ps1')
$report = Join-Path $Directory 'map-tour.log'
if (-not (Test-Path -LiteralPath $report -PathType Leaf) `
    -or -not (Test-Path -LiteralPath $Console -PathType Leaf)) {
  throw 'Both movement evidence and full console log are required'
}
$issues = @(Get-MapLogIssues -Lines (Get-Content -LiteralPath $Console))
ConvertTo-Json -InputObject $issues -Depth 4 |
  Set-Content -LiteralPath (Join-Path $Directory 'log-issues.json') -Encoding UTF8
Assert-TownRoundTripEvidence -Lines (Get-Content -LiteralPath $report) -Towns $Towns
if ($issues.Count) {
  throw "Movement evidence passed, but $($issues.Count) unresolved diagnostics block acceptance; inspect log-issues.json"
}
Write-Output "PASS town acceptance: $($Towns.Count) bidirectional native seams and independently audited logs"
