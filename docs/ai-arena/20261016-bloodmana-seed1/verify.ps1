param([string]$Evidence = $PSScriptRoot, [string]$SourceRoot = 'build/bloodmana-source')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
function Require($value,[string]$message) { if (!$value) { throw $message } }
function ReadEntry($zip,$name) {
  $reader = [System.IO.StreamReader]::new($zip.GetEntry($name).Open())
  try { $reader.ReadToEnd() } finally { $reader.Dispose() }
}
function CheckSpend($log,[bool]$repaired) {
  $spends = @($log -split '\r?\n' | Where-Object { $_ -match 'BloodManaSpend player=' })
  Require ($spends.Count -eq 1) 'Expected one ordinary player cast'
  Require ($spends[0] -match 'hp=([0-9.]+)->([0-9.]+) mana=([0-9.]+)->([0-9.]+) casting=true') 'Resource observation format'
  $life = [double]$Matches[1]-[double]$Matches[2]
  $mana = [double]$Matches[3]-[double]$Matches[4]
  if ($repaired) { Require ($life -eq 7 -and $mana -eq 0) 'Blood Mana payment' }
  else { Require ($life -eq 0 -and $mana -eq 7) 'Baseline defect not reproduced' }
  Require ($log -match 'SuccubusCurse subject=1 target=0 skill=310 state=114 applied=true') 'Natural Blood Mana state missing'
  Require ($log -match 'BloodManaPlayerSkillDo player=0 skill=40 srvdofunc=18') 'Player animation did not execute skill'
}
$baseline = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path "$Evidence/d0-baseline-scenes.zip"))
try {
  $rows = @(Get-Content "$Evidence/d0-baseline.jsonl" | ConvertFrom-Json)
  Require ($rows.Count -eq 21) 'Baseline count'
  foreach ($row in $rows | Where-Object scenario -eq 'blood-mana') { CheckSpend (ReadEntry $baseline $row.log) $false }
} finally { $baseline.Dispose() }
$executions=0; $payments=0
foreach ($difficulty in 0..2) {
  $hash = (Get-FileHash "$Evidence/d$difficulty-first.jsonl").Hash
  Require ($hash -eq (Get-FileHash "$Evidence/d$difficulty-repeat.jsonl").Hash) 'Repeat JSON differs'
  Write-Output "d$difficulty JSON SHA256=$hash repeat=IDENTICAL"
  $first = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path "$Evidence/d$difficulty-first-scenes.zip"))
  $repeat = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path "$Evidence/d$difficulty-repeat-scenes.zip"))
  try {
    foreach ($pass in @('first','repeat')) {
      $zip=if($pass -eq 'first'){$first}else{$repeat}
      $rows=@(Get-Content "$Evidence/d$difficulty-$pass.jsonl" | ConvertFrom-Json)
      Require ($rows.Count -eq 21) 'Selected scene count'
      $journal=@((ReadEntry $zip 'results.jsonl') -split '\r?\n' | Where-Object { $_ } | ConvertFrom-Json)
      Require ($journal.Count -eq 1187) 'Journal count'
      Require (@($journal | Where-Object status -eq 'NOT_RUN').Count -eq 1166) 'Unselected count'
      foreach ($row in $rows) {
        Require ($row.actualAi -eq 'Succubus' -and !$row.fallback -and $row.warnings -eq 0 -and $row.errors -eq 0) 'Scene diagnostics'
        Require ($row.coverage -eq 'BASELINE_ONLY' -and !$row.specialPassed) 'No full AI acceptance'
        $log=ReadEntry $zip $row.log
        Require ($log -ceq (ReadEntry $first $row.log)) 'Repeat log differs'
        if ($row.scenario -eq 'blood-mana') { CheckSpend $log $true; $payments++ }
        $executions++
        Write-Output "d$difficulty $pass $($row.monster) $($row.scenario) actions=$($row.actions) damage=$($row.damage)"
      }
    }
  } finally { $first.Dispose(); $repeat.Dispose() }
}
$tests=0
foreach ($file in Get-ChildItem "$Evidence/TEST-*.xml") {
  [xml]$xml=Get-Content $file
  Require ($xml.testsuite.failures -eq 0 -and $xml.testsuite.errors -eq 0 -and $xml.testsuite.skipped -eq 0) 'Test failure'
  $tests += [int]$xml.testsuite.tests
}
foreach ($path in @('core/src/main/java/com/riiablo/engine/server/skill/BloodMana.java',
    'core/src/main/java/com/riiablo/engine/server/ServerSkillSystem.java',
    'core/src/main/java/com/riiablo/engine/server/AuraEcsSystem.java',
    'core/src/test/java/com/riiablo/engine/server/BloodManaIntegrationTest.java',
    'server/d2gs/src/main/java/com/riiablo/server/d2gs/MonsterAiArena.java')) {
  $hash=(Get-FileHash $path).Hash
  Require ($hash -eq (Get-FileHash "$SourceRoot/$path").Hash) 'Source mismatch'
  Write-Output "source=$path SHA256=$hash snapshot=IDENTICAL"
}
Require ($tests -eq 121 -and $executions -eq 126 -and $payments -eq 42) 'Final coverage'
Write-Output "tests=$tests naturalExecutions=$executions verifiedLifePayments=$payments overall=PARTIAL"
