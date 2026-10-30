[CmdletBinding()]
param([Parameter(Mandatory)][string]$Run)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'map-acceptance-audit.ps1')
$root = (Resolve-Path -LiteralPath $Run).Path
$manifest = Get-Content (Join-Path $root 'acceptance.json') -Raw | ConvertFrom-Json
if ($manifest.natural -ne 'OBSERVED_RUNTIME_ONLY') { throw 'Natural acceptance must pass first' }
$lines = @(Get-Content (Join-Path $root 'natural/map-tour.log'))
Assert-AiTourEvidence -Lines $lines -Summary (Get-Content (Join-Path $root 'natural/ai-summary.txt'))
$issues = @(Get-MapLogIssues -Lines (Get-Content (Join-Path $root 'natural-console.log')))
if ($issues.Count) { throw 'Strict console audit failed' }
$number = '(-?\d+(?:\.\d+)?)'
$pattern = '^\[MAP_TOUR\] NARROW_MOVE sequence=(\d+) level=(\d+) crossAxis=([XY]) footprint=(\d+) firstTick=(\d+) lastTick=(\d+) from=\(' + $number + ',' + $number + '\) to=\(' + $number + ',' + $number + '\) beforeSpan=([1-5]) afterSpan=([1-5]) bound=3 movement=PRODUCTION_TCP coverage=SAMPLED_LOCAL_PROGRESS$'
$accepted = @()
for ($i = 0; $i -lt $lines.Count; $i++) {
  if ($lines[$i] -notmatch '^\[MAP_TOUR\] NARROW_MOVE ') { continue }
  if ($lines[$i] -notmatch $pattern) { throw 'Malformed narrow movement evidence' }
  $sequence = [long]$Matches[1]; $level = [int]$Matches[2]; $axis = $Matches[3]
  $footprint = [int]$Matches[4]; $first = [long]$Matches[5]; $last = [long]$Matches[6]
  $culture = [Globalization.CultureInfo]::InvariantCulture
  $x0 = [double]::Parse($Matches[7], $culture); $y0 = [double]::Parse($Matches[8], $culture)
  $x1 = [double]::Parse($Matches[9], $culture); $y1 = [double]::Parse($Matches[10], $culture)
  $before = [int]$Matches[11]; $after = [int]$Matches[12]
  $dx = [Math]::Abs($x1-$x0); $dy = [Math]::Abs($y1-$y0)
  $along = if ($axis -eq 'X') { $dy } else { $dx }
  $across = if ($axis -eq 'X') { $dx } else { $dy }
  if ($last -le $first -or $last-$first -gt 10 -or $along -lt 1 -or $across -gt .75 -or $footprint -lt 1) {
    throw 'Invalid narrow movement ticks or geometry'
  }
  # A narrow sample from a failed/replanned click cannot prove a successful walk.
  $completed = $false
  for ($j = $i+1; $j -lt $lines.Count; $j++) {
    if ($lines[$j] -match "^\[MAP_TOUR\] WALK_PASS sequence=$sequence mode=\S+ level=$level ") {
      $completed = $true; break
    }
  }
  if (!$completed) { continue }
  $accepted += [ordered]@{ sequence=$sequence; level=$level; crossAxis=$axis; footprint=$footprint
    firstTick=$first; lastTick=$last; from=@($x0,$y0); to=@($x1,$y1)
    beforeCenterSpan=$before; afterCenterSpan=$after; alongDisplacement=$along }
}
if (!$accepted.Count) { throw 'No narrow movement samples paired with successful walks' }
[ordered]@{ revision=$manifest.revision; seed=$manifest.seed; levels=$manifest.levels
  coverage='SAMPLED_LOCAL_NARROW_MOVEMENT'; correctness='NOT_ASSERTED'; bound=3
  samples=$accepted; sampleCount=$accepted.Count
} | ConvertTo-Json -Depth 8
