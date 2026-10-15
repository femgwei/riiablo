param([string]$Evidence = $PSScriptRoot, [string]$SourceRoot = 'build/succubus-source')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
function Require($value, [string]$message) { if (!$value) { throw $message } }
function ReadEntry($zip, $name) {
  $reader = [System.IO.StreamReader]::new($zip.GetEntry($name).Open())
  try { $reader.ReadToEnd() } finally { $reader.Dispose() }
}
$baseline = @(Get-Content "$Evidence/d0-baseline.jsonl" | ConvertFrom-Json)
Require ($baseline.Count -eq 14) 'Baseline count'
foreach ($row in $baseline) { Require ($row.fallback -and $row.actualAi -eq 'GenericMonster') 'Baseline fallback' }
$executions = 0; $curseObservations = 0; $damage = 0
foreach ($difficulty in 0..2) {
  foreach ($prefix in @('', 'fixed-')) {
    $hash = (Get-FileHash "$Evidence/d$difficulty-$($prefix)first.jsonl").Hash
    Require ($hash -eq (Get-FileHash "$Evidence/d$difficulty-$($prefix)repeat.jsonl").Hash) 'Repeat JSON differs'
    Write-Output "d$difficulty ${prefix}JSON SHA256=$hash repeat=IDENTICAL"
  }
  $firstZip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path "$Evidence/d$difficulty-fixed-first-scenes.zip"))
  $repeatZip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path "$Evidence/d$difficulty-fixed-repeat-scenes.zip"))
  try {
    foreach ($pass in @('fixed-first','fixed-repeat')) {
      $zip = if ($pass -eq 'fixed-first') { $firstZip } else { $repeatZip }
      $rows = @(Get-Content "$Evidence/d$difficulty-$pass.jsonl" | ConvertFrom-Json)
      Require ($rows.Count -eq 14) 'Scene count'
      $journal = @( (ReadEntry $zip 'results.jsonl') -split '\r?\n' | Where-Object { $_ } | ConvertFrom-Json)
      Require ($journal.Count -eq 1180) 'Journal count'
      Require (@($journal | Where-Object status -eq 'NOT_RUN').Count -eq 1166) 'Unselected count'
      foreach ($row in $rows) {
        Require ($row.actualAi -eq 'Succubus' -and !$row.fallback) 'Dedicated AI'
        Require ($row.warnings -eq 0 -and $row.errors -eq 0) 'Diagnostic failure'
        Require ($row.coverage -eq 'BASELINE_ONLY' -and !$row.specialPassed) 'No full acceptance claim'
        $log = ReadEntry $zip $row.log
        Require ($log -ceq (ReadEntry $firstZip $row.log)) 'Repeat scene log differs'
        $events = @($log -split '\r?\n' | Where-Object { $_ -match 'SkillDo subject=1' })
        Require ($events.Count -eq $row.actions) 'Action count'
        $curses = @($log -split '\r?\n' | Where-Object { $_ -match 'SuccubusCurse subject=' })
        Require ($curses.Count -gt 0) 'No natural curse state observation'
        Require ($curses.Count -eq @($events | Where-Object { $_ -match 'skill=309 .*srvdofunc=30' }).Count) 'Curse event/state count'
        foreach ($curse in $curses) {
          Require ($curse -match 'skill=309 state=113 applied=True duration=[1-9][0-9]* armorPercent=-[1-9][0-9]*') 'Curse state/stat missing'
        }
        Require ($row.damageEvents -gt 0 -and $row.damage -gt 0) 'No natural damage'
        $curseObservations += $curses.Count
        $damage += $row.damage
        $executions++
        Write-Output "d$difficulty $pass $($row.monster) $($row.scenario) actions=$($row.actions) damageEvents=$($row.damageEvents) damage=$($row.damage) curseStates=$($curses.Count)"
      }
    }
  } finally { $firstZip.Dispose(); $repeatZip.Dispose() }
}
$tests = 0
foreach ($file in Get-ChildItem "$Evidence/TEST-*.xml") {
  [xml]$xml = Get-Content $file
  Require ($xml.testsuite.failures -eq 0 -and $xml.testsuite.errors -eq 0 -and $xml.testsuite.skipped -eq 0) 'Test failure'
  $tests += [int]$xml.testsuite.tests
}
foreach ($path in @('core/src/main/java/com/riiablo/engine/server/ai/Succubus.java',
    'core/src/main/java/com/riiablo/engine/server/skill/NecromancerSkills.java',
    'core/src/main/java/com/riiablo/engine/server/state/StateId.java',
    'core/src/main/java/com/riiablo/engine/server/state/StateFlags.java',
    'server/d2gs/src/main/java/com/riiablo/server/d2gs/MonsterAiArena.java',
    'core/src/test/java/com/riiablo/engine/server/ai/SuccubusAiIntegrationTest.java',
    'core/src/test/java/com/riiablo/engine/server/SuccubusCurseIntegrationTest.java')) {
  $hash = (Get-FileHash $path).Hash
  Require ($hash -eq (Get-FileHash "$SourceRoot/$path").Hash) 'Source mismatch'
  Write-Output "source=$path SHA256=$hash snapshot=IDENTICAL"
}
Require ($tests -eq 86 -and $executions -eq 84 -and $curseObservations -eq 188) 'Final coverage count'
Write-Output "tests=$tests finalNaturalExecutions=$executions curseStates=$curseObservations damage=$damage overall=PARTIAL"
