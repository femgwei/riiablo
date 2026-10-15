param([string]$Evidence = $PSScriptRoot)
$ErrorActionPreference = 'Stop'
function Require($value, [string]$message) { if (!$value) { throw $message } }
$baseline = @(Get-Content "$Evidence/d0-baseline.jsonl" | ConvertFrom-Json)
Require ($baseline.Count -eq 2) 'Baseline scene count'
foreach ($row in $baseline) { Require ($row.fallback -and $row.actualAi -eq 'GenericMonster') 'Baseline fallback' }
$executions = 0
foreach ($difficulty in 0..2) {
  $first = "$Evidence/d$difficulty-fixed-first.jsonl"
  $repeat = "$Evidence/d$difficulty-repeat.jsonl"
  $hash = (Get-FileHash $first -Algorithm SHA256).Hash
  Require ($hash -eq (Get-FileHash $repeat -Algorithm SHA256).Hash) "Repeat differs d$difficulty"
  Write-Output "d$difficulty SHA256=$hash repeat=IDENTICAL"
  foreach ($pass in @('fixed-first', 'repeat')) {
    $rows = @(Get-Content "$Evidence/d$difficulty-$pass.jsonl" | ConvertFrom-Json)
    Require ($rows.Count -eq 2) 'Selected scene count'
    foreach ($row in $rows) {
      Require ($row.actualAi -eq 'HellMeteor' -and !$row.fallback) 'Dedicated AI'
      Require ($row.warnings -eq 0 -and $row.errors -eq 0) 'Scene diagnostic failure'
      Require ($row.actions -gt 0 -and $row.moves -eq 0) 'Natural cast without movement'
      Require ($row.coverage -eq 'BASELINE_ONLY' -and !$row.specialPassed) 'No full behavior claim'
      if ($difficulty -eq 2 -and $row.scenario -eq 'near') {
        Require ($row.damageEvents -eq 1 -and $row.damage -eq 26) 'Hell near observed damage'
      } else {
        Require ($row.damageEvents -eq 0 -and $row.damage -eq 0) 'Other scenes observed zero damage'
      }
      $executions++
      Write-Output "d$difficulty $pass $($row.scenario) actions=$($row.actions) damageEvents=$($row.damageEvents) damage=$($row.damage)"
    }
    $temporary = Join-Path ([System.IO.Path]::GetTempPath()) ([guid]::NewGuid().ToString())
    Expand-Archive "$Evidence/d$difficulty-$pass-scenes.zip" $temporary
    $journal = @(Get-Content "$temporary/results.jsonl" | ConvertFrom-Json)
    Require ($journal.Count -eq 1180) 'Raw journal size'
    Require (@($journal | Where-Object status -eq 'NOT_RUN').Count -eq 1178) 'Unselected count'
    foreach ($scene in @('near', 'far')) {
      $events = @(Get-Content "$temporary/hellmeteor-$scene.log" | Where-Object { $_ -match 'SkillDo subject=1' })
      $row = $rows | Where-Object scenario -eq $scene
      Require ($events.Count -eq $row.actions) 'Event count'
      foreach ($event in $events) { Require ($event -match 'skill=213 target=-1 srvdofunc=28') 'Natural null-target HellMeteor event' }
    }
    # Keep extracted verification input available for inspection; no cleanup of computed paths.
  }
}
$tests = 0
foreach ($file in Get-ChildItem "$Evidence/TEST-*.xml") {
  [xml]$xml = Get-Content $file
  Require ($xml.testsuite.failures -eq 0 -and $xml.testsuite.errors -eq 0 -and $xml.testsuite.skipped -eq 0) 'Directed suite failure'
  $tests += [int]$xml.testsuite.tests
}
Write-Output "Directed tests=$tests; natural executions=$executions; overall=PARTIAL; damage observed=Hell-near-only"
