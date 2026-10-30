[CmdletBinding()]
param([Parameter(Mandatory)][string]$Run, [switch]$RequireRecovery)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'map-acceptance-audit.ps1')
$root = (Resolve-Path -LiteralPath $Run).Path
$manifest = Get-Content (Join-Path $root 'acceptance.json') -Raw | ConvertFrom-Json
if ($manifest.natural -ne 'OBSERVED_RUNTIME_ONLY') { throw 'Natural acceptance must pass first' }
$lines = @(Get-Content (Join-Path $root 'natural/map-tour.log'))
Assert-AiTourEvidence -Lines $lines -Summary (Get-Content (Join-Path $root 'natural/ai-summary.txt'))
if (@(Get-MapLogIssues -Lines (Get-Content (Join-Path $root 'natural-console.log'))).Count) { throw 'Strict console audit failed' }
$number = '-?\d+(?:\.\d+)?'
$culture = [Globalization.CultureInfo]::InvariantCulture
function Number([string]$value) {
  if ($value -notmatch "^$number`$") { throw 'Malformed numeric evidence' }
  [double]::Parse($value, $culture)
}
function Point([string]$value) {
  if ($value -notmatch "^\(($number),($number)\)`$") { throw 'Malformed position evidence' }
  @((Number $Matches[1]), (Number $Matches[2]))
}
function Distance($a, $b) { [Math]::Sqrt([Math]::Pow($a[0]-$b[0],2)+[Math]::Pow($a[1]-$b[1],2)) }
$blocks = @{}; $chains = @{}; $moves = @(); $recoveries = @(); $passages = @()
foreach ($line in $lines) {
  if ($line -notmatch '^\[MAP_TOUR\] (MONSTER_NARROW_MOVE|MONSTER_NARROW_BLOCK|MONSTER_NARROW_RECOVER) (.+)$') { continue }
  $kind = $Matches[1]; $body = $Matches[2]; $fields = @{}
  foreach ($token in $body.Split(' ')) {
    $pair = $token.Split('=',2)
    if ($pair.Count -ne 2 -or $fields.ContainsKey($pair[0])) { throw 'Malformed/duplicate evidence field' }
    $fields[$pair[0]] = $pair[1]
  }
  foreach ($key in @('entity','target','level','lifetime','targetLifetime','footprint','firstTick','lastTick','beforeSpan','afterSpan','allowedDisplacement')) {
    if (!$fields.ContainsKey($key)) { throw "Missing field $key" }; $null = Number $fields[$key]
  }
  if ($fields.identity -ne 'SAME_LIFETIME' -or $fields.movement -ne 'PRODUCTION_AI' `
      -or $fields.coverage -ne 'SAMPLED_LOCAL_PROGRESS' -or $fields.bound -ne '3' `
      -or $fields.context -notin @('WALK','OBSERVE') -or $fields.playerAttacks -ne '0' `
      -or $fields.targetSource -notin @('LIVE_PATH','BOUNDED_TOWARD_REQUEST') `
      -or [int]$fields.footprint -lt 1 -or [long]$fields.lifetime -lt 1 -or [long]$fields.targetLifetime -lt 1 `
      -or [int]$fields.beforeSpan -notin 1..5 -or [int]$fields.afterSpan -notin 1..5) { throw 'Invalid provenance or geometry' }
  $from = Point $fields.from; $to = Point $fields.to
  $first = [long]$fields.firstTick; $last = [long]$fields.lastTick
  if ($last -le $first) { throw 'Invalid tick order' }
  $dx = $to[0]-$from[0]; $dy = $to[1]-$from[1]
  switch ($fields.crossAxis) {
    X { $along=[Math]::Abs($dy); $across=[Math]::Abs($dx) }
    Y { $along=[Math]::Abs($dx); $across=[Math]::Abs($dy) }
    DPLUS { $along=[Math]::Abs($dx-$dy)/[Math]::Sqrt(2); $across=[Math]::Abs($dx+$dy)/[Math]::Sqrt(2) }
    DMINUS { $along=[Math]::Abs($dx+$dy)/[Math]::Sqrt(2); $across=[Math]::Abs($dx-$dy)/[Math]::Sqrt(2) }
    default { throw 'Invalid cross-section axis' }
  }
  $key = @('entity','target','level','lifetime','targetLifetime','targetSource','footprint','crossAxis' | ForEach-Object { $fields[$_] }) -join ':'
  if ($kind -eq 'MONSTER_NARROW_BLOCK') {
    if ($last-$first -lt 5 -or (Distance $from $to) -ge .1 -or $fields.queryWasBlocked -ne 'true') { throw 'Invalid stationary dynamic hold' }
    $null = Point $fields.query
    $blocks["${key}:$first`:$last"] = $fields
    continue
  }
  if ($last-$first -gt 10 -or $along -lt 1 -or $across -gt .75 `
      -or (Distance $from $to) -gt (Number $fields.allowedDisplacement) + .001) { throw 'Discontinuous/non-narrow movement' }
  if ($kind -eq 'MONSTER_NARROW_RECOVER') {
    $blockedFirst = [long]$fields.blockedFirstTick; $blockedLast = [long]$fields.blockedLastTick
    $block = $blocks["${key}:$blockedFirst`:$blockedLast"]
    if (!$block -or $first -lt $blockedLast -or $last -le $blockedLast -or $last-$blockedLast -gt 250 `
        -or $fields.queryWasBlocked -ne 'true' -or $fields.queryNowFree -ne 'true' `
        -or $block.query -ne $fields.query -or (Distance (Point $fields.blockedPosition) (Point $block.to)) -gt .001 `
        -or (Distance (Point $block.to) $to) -lt 1) { throw 'Recovery lacks matching prior hold or cleared query' }
    $recoveries += $fields
  } else {
    $moves += $fields
    $chain = $chains[$key]
    if (!$chain -or $chain.last -ne $first -or (Distance $chain.to $from) -gt .001) {
      $chain = @{ first=$first; start=$from; count=0 }
    }
    $chain.last=$last; $chain.to=$to; $chain.count++
    $chains[$key]=$chain
    if ($chain.count -ge 2 -and (Distance $chain.start $to) -ge 3) {
      $passages += [ordered]@{ entity=$fields.entity; target=$fields.target; level=$fields.level
        lifetime=$fields.lifetime; targetLifetime=$fields.targetLifetime; crossAxis=$fields.crossAxis
        targetSource=$fields.targetSource
        firstTick=$chain.first; lastTick=$last; from=$chain.start; to=$to; consecutiveRecords=$chain.count }
      $chains.Remove($key)
    }
  }
}
if (!$moves.Count -or !$passages.Count) { throw 'No continuous sampled monster progress of at least 3 subtiles' }
if ($RequireRecovery -and !$recoveries.Count) { throw 'No observed dynamic-block recovery; movement alone is insufficient' }
[ordered]@{ revision=$manifest.revision; seed=$manifest.seed; levels=$manifest.levels
  coverage='SAMPLED_LOCAL_MONSTER_NARROW_PROGRESS'; correctness='NOT_ASSERTED'
  moveCount=$moves.Count; blockCount=$blocks.Count; recoveryCount=$recoveries.Count
  passageCount=$passages.Count; passages=$passages; recoveries=$recoveries
} | ConvertTo-Json -Depth 10
