param([string]$Evidence = $PSScriptRoot)
$ErrorActionPreference = 'Stop'
function Require($condition, $message) { if (-not $condition) { throw $message } }
$summary = @()
$baseline = @{}
Get-Content "$PSScriptRoot/../20261016-deathmaul-do136/results.jsonl" | ForEach-Object {
  $row = $_ | ConvertFrom-Json
  if ($row.ai -eq 'DeathMauler') { $baseline[$row.monster+'|'+$row.scenario] = $_ }
}
foreach ($difficulty in @(0, 1, 2)) {
  foreach ($seed in @(1, 7, 42)) {
    $key = "d$difficulty-s$seed"
    $first = "$Evidence/$key-first.jsonl"
    $repeat = "$Evidence/$key-repeat.jsonl"
    $hash = (Get-FileHash -LiteralPath $first).Hash
    Require ($hash -eq (Get-FileHash -LiteralPath $repeat).Hash) "$key repeat differs"
    $lines = @(Get-Content -LiteralPath $first)
    $rows = @($lines | ForEach-Object { $_ | ConvertFrom-Json })
    foreach ($pass in @('first', 'repeat')) {
      $archive = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path "$Evidence/$key-$pass-scenes.zip"))
      try {
        $entry = $archive.GetEntry('results.jsonl')
        Require ($null -ne $entry) "$key missing original journal"
        $reader = [System.IO.StreamReader]::new($entry.Open())
        try { $rawLines = @($reader.ReadToEnd() -split '\r?\n' | Where-Object { $_ -ne '' }) }
        finally { $reader.Dispose() }
        $rawRows = @($rawLines | ForEach-Object { $_ | ConvertFrom-Json })
        Require ($rawRows.Count -eq 1180) "$key raw catalog count changed"
        Require (@($rawRows | Where-Object { $_.ai -ne 'DeathMauler' -and $_.status -eq 'NOT_RUN' }).Count -eq 1162) "$key unexpected execution outside selected monsters"
        $selected = @($rawLines | Where-Object { ($_ | ConvertFrom-Json).ai -eq 'DeathMauler' })
        Require (($selected -join "`n") -ceq ($lines -join "`n")) "$key scoped evidence differs from raw journal"
      } finally { $archive.Dispose() }
    }
    Require ($rows.Count -eq 18) "$key expected eighteen scenes"
    Require (@($rows.monster | Sort-Object -Unique).Count -eq 6) "$key expected six rows"
    foreach ($row in $rows) {
      Require ($row.monster -match '^deathmauler[1-6]$') "$key unexpected monster"
      Require ($row.ai -eq 'DeathMauler' -and $row.actualAi -eq 'DeathMauler' -and -not $row.fallback) "$key wrong AI"
      Require ($row.seed -eq $seed -and $row.difficulty -eq $difficulty -and $row.ticks -eq 500) "$key wrong run configuration"
      Require ($row.errors -eq 0 -and $row.warnings -eq 0 -and $row.error -eq '') "$key diagnostics failed"
      Require ($row.status -in @('ACTION_OBSERVED', 'NO_ACTION', 'MOVEMENT_ONLY')) "$key invalid scene result"
      if ($row.scenario -eq 'deathmaul-cycle') {
        Require ($row.specialAssert -and $row.specialPassed -and $row.special -eq 'DEATH_MAUL_CYCLE_OBSERVED') "$key natural skill oracle failed for $($row.monster)"
        Require ($row.damageEvents -gt 0 -and $row.damage -gt 0) "$key missing skill damage"
      } elseif ($row.scenario -eq 'near') {
        Require ($row.status -eq 'ACTION_OBSERVED' -and $row.damageEvents -gt 0 -and $row.damage -gt 0) "$key melee regression"
      } else {
        Require ($row.scenario -eq 'far') "$key unexpected scenario"
      }
    }
    foreach ($monster in @('deathmauler1','deathmauler2','deathmauler3','deathmauler4','deathmauler5','deathmauler6')) {
      Require (@($rows | Where-Object { $_.monster -eq $monster -and $_.scenario -eq 'deathmaul-cycle' }).Count -eq 1) "$key missing or duplicated natural oracle"
      Require (@($rows | Where-Object { $_.monster -eq $monster -and $_.scenario -eq 'near' }).Count -eq 1) "$key missing melee scene"
      Require (@($rows | Where-Object { $_.monster -eq $monster -and $_.scenario -eq 'far' }).Count -eq 1) "$key missing far scene"
    }
    if ($difficulty -eq 0 -and $seed -eq 1) {
      foreach ($line in $lines) {
        $row = $line | ConvertFrom-Json
        Require ($line -ceq $baseline[$row.monster+'|'+$row.scenario]) 'Normal seed1 changed from accepted Do136 baseline'
      }
    }
    $summary += [pscustomobject]@{ difficulty = $difficulty; seed = $seed; scenes = $rows.Count;
      naturalPassed = @($rows | Where-Object { $_.specialPassed }).Count; sha256 = $hash }
  }
}
$summary | ConvertTo-Json | Set-Content -LiteralPath "$Evidence/summary.json"
Write-Output 'PASS: nine configurations, 162 scenes plus 162 repeat scenes, 54 natural DeathMaul assertions per pass, no warnings/errors; Normal seed1 matches prior accepted baseline.'
