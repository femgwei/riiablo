param([string]$Evidence = $PSScriptRoot, [string]$SourceRoot = 'build/trappedsoul-source')
$ErrorActionPreference = 'Stop'
function Require($value, [string]$message) { if (!$value) { throw $message } }
$baseline = @(Get-Content "$Evidence/d0-baseline.jsonl" | ConvertFrom-Json)
Require ($baseline.Count -eq 4) 'Baseline scene count'
foreach ($row in $baseline) { Require ($row.fallback -and $row.actualAi -eq 'GenericMonster') 'Baseline fallback' }
$executions = 0
$expectedDamage = @(122,471,940)
foreach ($difficulty in 0..2) {
  $first = "$Evidence/d$difficulty-first.jsonl"
  $repeat = "$Evidence/d$difficulty-repeat.jsonl"
  $hash = (Get-FileHash $first -Algorithm SHA256).Hash
  Require ($hash -eq (Get-FileHash $repeat -Algorithm SHA256).Hash) "Repeat differs d$difficulty"
  Write-Output "d$difficulty SHA256=$hash repeat=IDENTICAL"
  foreach ($pass in @('first','repeat')) {
    $rows = @(Get-Content "$Evidence/d$difficulty-$pass.jsonl" | ConvertFrom-Json)
    Require ($rows.Count -eq 4) 'Selected scene count'
    foreach ($row in $rows) {
      Require ($row.actualAi -eq 'TrappedSoul' -and !$row.fallback) 'Dedicated AI'
      Require ($row.warnings -eq 0 -and $row.errors -eq 0 -and $row.moves -eq 0) 'Scene diagnostic/movement failure'
      Require ($row.coverage -eq 'BASELINE_ONLY' -and !$row.specialPassed) 'No full behavior claim'
      if ($row.scenario -eq 'near') {
        Require ($row.actions -eq 26 -and $row.damageEvents -eq 24 -and $row.damage -eq $expectedDamage[$difficulty]) 'Observed near events/damage'
      } else {
        Require ($row.actions -eq 0 -and $row.damageEvents -eq 0 -and $row.damage -eq 0) 'Observed inactive far scene'
      }
      $executions++
      Write-Output "d$difficulty $pass $($row.monster) $($row.scenario) events=$($row.actions) damageEvents=$($row.damageEvents) damage=$($row.damage)"
    }
    $zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path "$Evidence/d$difficulty-$pass-scenes.zip"))
    try {
      $reader = [System.IO.StreamReader]::new($zip.GetEntry('results.jsonl').Open())
      try { $journal = @($reader.ReadToEnd() -split '\r?\n' | Where-Object { $_ } | ConvertFrom-Json) }
      finally { $reader.Dispose() }
      Require ($journal.Count -eq 1180) 'Raw journal size'
      Require (@($journal | Where-Object status -eq 'NOT_RUN').Count -eq 1176) 'Unselected count'
      foreach ($row in $rows) {
        $reader = [System.IO.StreamReader]::new($zip.GetEntry($row.log).Open())
        try { $lines = $reader.ReadToEnd() -split '\r?\n' }
        finally { $reader.Dispose() }
        $events = @($lines | Where-Object { $_ -match 'SkillDo subject=1' })
        Require ($events.Count -eq $row.actions) 'Natural event count'
        foreach ($event in $events) { Require ($event -match 'skill=0 target=0 srvdofunc=1') 'Natural ordinary attack event' }
      }
    } finally { $zip.Dispose() }
  }
}
$tests = 0
foreach ($file in Get-ChildItem "$Evidence/TEST-*.xml") {
  [xml]$xml = Get-Content $file
  Require ($xml.testsuite.failures -eq 0 -and $xml.testsuite.errors -eq 0 -and $xml.testsuite.skipped -eq 0) 'Directed suite failure'
  $tests += [int]$xml.testsuite.tests
}
foreach ($path in @('core/src/main/java/com/riiablo/engine/server/ai/TrappedSoul.java',
    'core/src/main/java/com/riiablo/engine/server/ai/NativeDecisionAI.java',
    'core/src/test/java/com/riiablo/engine/server/ai/TrappedSoulAiIntegrationTest.java')) {
  $hash = (Get-FileHash $path).Hash
  Require ($hash -eq (Get-FileHash "$SourceRoot/$path").Hash) 'Snapshot/workspace source mismatch'
  Write-Output "source=$path SHA256=$hash snapshot=IDENTICAL"
}
Write-Output "Directed tests=$tests; natural executions=$executions; overall=PARTIAL; full native animation/event scheduling not accepted"
