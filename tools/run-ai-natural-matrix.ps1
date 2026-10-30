[CmdletBinding()]
param(
  [Parameter(Mandatory)][string]$D2Home,
  [Parameter(Mandatory)][string]$Output,
  [string]$Revision = 'HEAD',
  [ValidatePattern('^\d+(,\d+)*$')][string]$Levels = '1,2',
  [int[]]$Seeds = @(1, 2, 3),
  [ValidateRange(1, 86400)][int]$StageTimeoutSeconds = 600
)
$ErrorActionPreference = 'Stop'
$levelIds = @($Levels.Split(',') | ForEach-Object { [int]$_ })
if (@($levelIds | Select-Object -Unique).Count -ne $levelIds.Count `
    -or @($levelIds | Where-Object { $_ -lt 1 -or $_ -gt 132 }).Count) {
  throw 'Levels must contain unique 1.10f level IDs in 1..132'
}
$repo = Split-Path -Parent $PSScriptRoot
$commit = & git -C $repo rev-parse --verify "$Revision^{commit}"
if ($LASTEXITCODE -ne 0) { throw 'Invalid revision' }
if (!$Seeds.Count -or @($Seeds | Select-Object -Unique).Count -ne $Seeds.Count) {
  throw 'Seeds must be nonempty and unique'
}
$destination = [IO.Path]::GetFullPath($Output)
if (Test-Path -LiteralPath $destination) { throw 'Output must be a new directory' }
New-Item -ItemType Directory -Path $destination | Out-Null
$report = [ordered]@{ revision = "$commit"; levels = $Levels; seeds = $Seeds
  stageTimeoutSeconds = $StageTimeoutSeconds
  coverage = 'MULTI_SEED_RUNTIME_OBSERVATION'; correctness = 'NOT_ASSERTED'
  result = 'RUNNING'; runs = @() }
function Save-Report {
  $report | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $destination 'matrix.json') -Encoding UTF8
}
Save-Report
foreach ($seed in $Seeds) {
  $run = Join-Path $destination "seed-$seed"
  $row = [ordered]@{ seed = $seed; status = 'RUNNING'; summary = $null; error = $null }
  $report.runs += $row
  Save-Report
  try {
    & (Join-Path $PSScriptRoot 'run-ai-dependency-acceptance.ps1') -D2Home $D2Home `
      -Output $run -Revision $commit -Seed $seed -Levels $Levels -SkipGl `
      -StageTimeoutSeconds $StageTimeoutSeconds
    $acceptance = Get-Content (Join-Path $run 'acceptance.json') -Raw | ConvertFrom-Json
    if ($acceptance.natural -ne 'OBSERVED_RUNTIME_ONLY') { throw 'Natural evidence rejected' }
    $row.summary = @(Get-Content (Join-Path $run 'natural/map-tour.log') |
      Where-Object { $_ -match '^\[MAP_TOUR\] SUMMARY ' })
    $row.status = 'OBSERVED_RUNTIME_ONLY'
  } catch {
    $row.status = 'FAILED'
    $row.error = $_.Exception.Message
    $manifestPath = Join-Path $run 'acceptance.json'
    if (Test-Path -LiteralPath $manifestPath) {
      $failedAcceptance = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
      $row.error = "$($row.error); natural=$($failedAcceptance.natural)"
    }
    $tourLog = Join-Path $run 'natural/map-tour.log'
    if (Test-Path -LiteralPath $tourLog) {
      $row.summary = @(Get-Content -LiteralPath $tourLog |
        Where-Object { $_ -match '^\[MAP_TOUR\] SUMMARY ' })
    }
  }
  Save-Report
}
$report.result = if (@($report.runs | Where-Object { $_.status -ne 'OBSERVED_RUNTIME_ONLY' }).Count) {
  'FAILED'
} else { 'OBSERVED_RUNTIME_ONLY' }
Save-Report
if ($report.result -eq 'FAILED') { throw 'Natural matrix failed; see matrix.json' }
