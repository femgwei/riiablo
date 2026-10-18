# Shared independent audit. Dot-source from runners; no game-state mutation.
function Assert-AiTourEvidence {
  param([string[]]$Lines, [string[]]$Summary, [int]$ObserveMillis = 8000)
  if (-not ($Lines -match '^\[MAP_TOUR\] BEGIN .* coverage=AI_RUNTIME_OBSERVATION ')) {
    throw 'Missing explicit AI observation coverage'
  }
  if (-not ($Summary -match '^\[AI_TOUR\] SUMMARY run=COMPLETED coverage=OBSERVED_RUNTIME_ONLY oracle=NONE correctness=NOT_ASSERTED warnings=\d+ errors=0 fallbackRecords=0$')) {
    throw 'Missing clean AI observation summary (not an AI correctness PASS)'
  }
  if (-not ($Summary -match '^\[AI_TOUR\] MONSTER .* nativeCombatant=true evidence=BEHAVIOR_OBSERVED$')) {
    throw 'No actual AI behavior evidence; initialization alone is insufficient'
  }
  $required = [long][Math]::Ceiling($ObserveMillis / 40.0)
  $loaded = @($Lines | ForEach-Object {
    if ($_ -match '^\[MAP_TOUR\] LOAD level=(\d+) ') { [int]$Matches[1] }
  })
  if (@($loaded | Where-Object { $_ -notin @(1,40,75,103,109) }).Count -eq 0) {
    throw 'No non-town level observation evidence'
  }
  foreach ($level in $loaded) {
    if ($level -in @(1,40,75,103,109)) { continue }
    $begin = @($Lines -match "^\[MAP_TOUR\] AI_OBSERVE_BEGIN level=$level reason=LEVEL_ENTRY firstTick=\d+ requiredTicks=$required playerAttacks=0$")
    $end = @($Lines | Where-Object {
      $_ -match "^\[MAP_TOUR\] AI_OBSERVE_END level=$level reason=LEVEL_ENTRY firstTick=(\d+) lastTick=(\d+) observedTicks=(\d+) playerAttacks=0 correctness=NOT_ASSERTED$"
    })
    if ($begin.Count -ne 1 -or $end.Count -ne 1) { throw "Missing unique AI entry window level=$level" }
    $null = $end[0] -match 'firstTick=(\d+) lastTick=(\d+) observedTicks=(\d+)'
    $first = [long]$Matches[1]; $last = [long]$Matches[2]; $observed = [long]$Matches[3]
    if ($observed -lt $required -or $last - $first -ne $observed `
        -or $begin[0] -notmatch " firstTick=$first ") {
      throw "Invalid authoritative tick evidence level=$level"
    }
  }
  # Encounter and blocker windows carry the same contract as level-entry
  # windows. Extra evidence must never bypass the no-attacks/tick checks.
  $begins = @($Lines -match '^\[MAP_TOUR\] AI_OBSERVE_BEGIN ')
  $ends = @($Lines -match '^\[MAP_TOUR\] AI_OBSERVE_END ')
  if ($begins.Count -ne $ends.Count) { throw 'Unpaired AI observation windows' }
  foreach ($line in $begins) {
    if ($line -notmatch '^\[MAP_TOUR\] AI_OBSERVE_BEGIN level=(\d+) reason=(.+) firstTick=(\d+) requiredTicks=(\d+) playerAttacks=0$') {
      throw 'Invalid AI observation begin record'
    }
    $level = $Matches[1]; $reason = [regex]::Escape($Matches[2])
    $first = [long]$Matches[3]; $ticks = [long]$Matches[4]
    $matching = @($ends -match "^\[MAP_TOUR\] AI_OBSERVE_END level=$level reason=$reason firstTick=$first lastTick=\d+ observedTicks=\d+ playerAttacks=0 correctness=NOT_ASSERTED$")
    if ($ticks -ne $required -or $matching.Count -ne 1) { throw 'Invalid AI observation pair' }
    $null = $matching[0] -match 'lastTick=(\d+) observedTicks=(\d+)'
    if ([long]$Matches[2] -lt $ticks -or [long]$Matches[1] - $first -ne [long]$Matches[2]) {
      throw 'Invalid AI observation duration'
    }
  }
}

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
  Assert-NativeSeamEvidence -Lines $Lines
}

function Assert-NativeSeamEvidence {
  param([string[]]$Lines, [int[]]$Acts = @(1,2,3,4,5))
  if ($Lines -match '^\[MAP_TOUR\] (SEAM_FAIL|FAIL|DIAGNOSTIC_FAIL|DEATH_RECOVERY) ') {
    throw 'Native seam run contains failures'
  }
  $expected = @{}
  $passed = @{}
  $outbound = @{}
  $returned = @{}
  $staged = @{}
  foreach ($act in $Acts) {
    if (@($Lines -match "^\[MAP_TOUR\] SEAM_MANIFEST_START act=$act source=D2MOO_VIS_WARP_MINUS_ONE$").Count -ne 1) {
      throw "Missing unique native seam manifest act=$act"
    }
  }
  foreach ($line in $Lines) {
    if ($line -match '^\[MAP_TOUR\] SEAM_EXPECTED act=(\d+) from=(\d+) to=(\d+) slot=(\d+) warp=-1$') {
      if ([int]$Matches[1] -notin $Acts) { throw 'Unexpected seam act' }
      $key = "$($Matches[1]):$($Matches[2]):$($Matches[3]):$($Matches[4])"
      if ($expected.ContainsKey($key)) { throw "Duplicate expected edge $key" }
      $expected[$key] = @([int]$Matches[2], [int]$Matches[3])
    } elseif ($line -match '^\[MAP_TOUR\] SEAM_STAGE act=(\d+) from=(\d+) to=(\d+) slot=(\d+) level=(\d+) role=SOURCE_ONLY$') {
      $key = "$($Matches[1]):$($Matches[2]):$($Matches[3]):$($Matches[4])"
      if (-not $expected.ContainsKey($key) -or $staged.ContainsKey($key) -or $Matches[5] -ne $Matches[2]) { throw "Invalid source staging $key" }
      $staged[$key] = $true
    } elseif ($line -match '^\[MAP_TOUR\] SEAM_DIRECTION_PASS act=(\d+) from=(\d+) to=(\d+) slot=(\d+) direction=(OUTBOUND|RETURN) walkedFrom=(\d+) walkedTo=(\d+) .* clientLevel=(\d+) warpRequests=0 movement=PRODUCTION_TCP$') {
      $key = "$($Matches[1]):$($Matches[2]):$($Matches[3]):$($Matches[4])"
      if (-not $expected.ContainsKey($key) -or -not $staged.ContainsKey($key)) { throw "Unplanned seam direction $key" }
      $edge = $expected[$key]
      if ($Matches[5] -eq 'OUTBOUND') {
        if ($outbound.ContainsKey($key) -or [int]$Matches[6] -ne $edge[0] -or [int]$Matches[7] -ne $edge[1]) { throw "Invalid outbound $key" }
        $outbound[$key] = $true
      } else {
        if (-not $outbound.ContainsKey($key) -or $returned.ContainsKey($key) -or [int]$Matches[6] -ne $edge[1] -or [int]$Matches[7] -ne $edge[0]) { throw "Invalid return $key" }
        $returned[$key] = $true
      }
      if ($Matches[8] -ne $Matches[7]) { throw "Wrong client level $key" }
    } elseif ($line -match '^\[MAP_TOUR\] SEAM_EDGE_PASS act=(\d+) from=(\d+) to=(\d+) slot=(\d+) directions=2 warpRequests=0 destinationStages=0 movement=PRODUCTION_TCP$') {
      $key = "$($Matches[1]):$($Matches[2]):$($Matches[3]):$($Matches[4])"
      if (-not $outbound.ContainsKey($key) -or -not $returned.ContainsKey($key) -or $passed.ContainsKey($key)) { throw "Incomplete/duplicate seam pass $key" }
      $passed[$key] = $true
    } elseif ($line -match '^\[MAP_TOUR\] SEAM_(EXPECTED|STAGE|DIRECTION_PASS|EDGE_PASS) ') { throw 'Malformed seam evidence' }
  }
  if ($expected.Count -eq 0 -or $expected.Count -ne $passed.Count) { throw 'Incomplete expected native seam coverage' }
  $count = $expected.Count
  if (@($Lines -match "^\[MAP_TOUR\] SEAM_SUMMARY planned=$count passed=$count failures=0 coverage=ALL_ADJACENT_NATIVE_VIS_MINUS_ONE result=PASS$").Count -ne 1) { throw 'Invalid native seam summary' }
}
