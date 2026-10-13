# Shared independent audit. Dot-source from runners; no game-state mutation.
function Get-MapLogIssues {
  param([string[]]$Lines)
  $knownCounts = @{}
  for ($index = 0; $index -lt $Lines.Count; $index++) {
    $line = $Lines[$index]
    if ($line -match '^\[D2Log\]\[WARNING\] ACT1_D2MOO_LEVELDEF skip id=0: unsupported DrlgType=0$') {
      $key = 'inert_level_zero'; $limit = 64
    } elseif ($line -match '^\[D2Log\]\[WARNING\] Memory pool not provided; using Java fallback allocation \(object\)$') {
      $key = 'java_pool_fallback'; $limit = 1
    } else { $key = $null }
    if ($key) {
      $knownCounts[$key] = 1 + [int]$knownCounts[$key]
      if ($knownCounts[$key] -le $limit) { continue }
    }
    $rule = $null
    if ($line -match 'Exception\b|OutOfMemoryError|StackOverflowError|Error during|Animating tiles missing') {
      $rule = 'runtime_error'
    } elseif ($line -match '^\s*(ERROR|WARN)\s+\[|^\[D2Log\]\[(WARNING|ERROR)\]|^\[[^\]]+\]\s+(WARN|WARNING|ERROR):') {
      $rule = 'unreviewed_warning_or_error'
    } elseif ($line -match 'failedResolve=[1-9]\d*|failedWalls?=[1-9]\d*|failedFloors?=[1-9]\d*') {
      $rule = 'resource_resolution_failure'
    }
    if ($rule) {
      [pscustomobject]@{ line = $index + 1; rule = $rule; text = $line }
    }
  }
}

function Assert-TownRoundTripEvidence {
  param([string[]]$Lines, [int[]]$Towns = @(1,40,75,103,109))
  if (-not ($Lines -match '^\[MAP_TOUR\] BEGIN .* contract=2(?: |$)') `
      -or ($Lines -match '^\[MAP_TOUR\] (FAIL|DIAGNOSTIC_FAIL|DEATH_RECOVERY) ')) {
    throw 'Missing contract or failed town diagnostic'
  }
  foreach ($town in $Towns) {
    $outdoor = $town + 1
    foreach ($edge in @("$town to=$outdoor", "$outdoor to=$town")) {
      if (@($Lines -match "^\[MAP_TOUR\] TRANSITION_PASS from=$edge mode=BOUNDARY_WALK warpRequests=0 movement=PRODUCTION_TCP$").Count -ne 1) {
        throw "Missing unique genuine walking evidence $edge"
      }
    }
    if (-not ($Lines -match "^\[MAP_TOUR\] TOWN_ROUND_TRIP_PASS town=$town outdoor=$outdoor$")) {
      throw "Missing round trip $town"
    }
    if ($Lines -match "^\[MAP_TOUR\] ENTER level=$outdoor ") {
      throw "Outdoor staging cannot count as a town round trip $town"
    }
  }
  if (-not ($Lines -match '^\[MAP_TOUR\] SUMMARY .*failures=0 result=PASS$')) {
    throw 'Missing successful short summary'
  }
}

function Assert-MapTourEvidence {
  param([string[]]$Lines)
  if (-not ($Lines -match '^\[MAP_TOUR\] BEGIN .* contract=2(?: |$)')) {
    throw 'Missing acceptance contract=2; older PASS results are not equivalent'
  }
  if ($Lines -match '^\[MAP_TOUR\] (FAIL|DIAGNOSTIC_FAIL|DEATH_RECOVERY) ') {
    throw 'Tour contains explicit failures or death recovery'
  }
  if (-not ($Lines -match '^\[MAP_TOUR\] SUMMARY planned=132 visited=132 .*failures=0 result=PASS$')) {
    throw 'Missing complete successful tour summary'
  }
  $loads = @($Lines | ForEach-Object {
    if ($_ -match '^\[MAP_TOUR\] LOAD level=(\d+) ') { [int]$Matches[1] }
  })
  $walks = @($Lines | ForEach-Object {
    if ($_ -match '^\[MAP_TOUR\] WALK_PASS .*mode=LOCAL_WALK level=(\d+) ') { [int]$Matches[1] }
  })
  if ($loads.Count -ne 132 -or @($loads | Sort-Object -Unique).Count -ne 132 `
      -or @(1..132 | Where-Object { $_ -notin $loads -or $_ -notin $walks }).Count) {
    throw 'Incomplete unique load and local movement coverage'
  }
  foreach ($edge in @('1 to=2', '40 to=41', '75 to=76', '103 to=104', '109 to=110')) {
    if (-not ($Lines -match "^\[MAP_TOUR\] TRANSITION_PASS from=$edge mode=BOUNDARY_WALK warpRequests=0 movement=PRODUCTION_TCP$")) {
      throw "Missing genuine town walking exit $edge; Warp/staging does not count"
    }
    if ($Lines -match "^\[MAP_TOUR\] TRANSITION_PASS from=$edge mode=(?!BOUNDARY_WALK)") {
      throw "Contradictory town exit evidence $edge"
    }
  }
}
