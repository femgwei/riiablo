param([string]$Evidence=$PSScriptRoot,[string]$SourceRoot='build/bladebloodmana-source')
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
function Require($value,[string]$message){if(!$value){throw $message}}
function ReadEntry($zip,$name){$r=[System.IO.StreamReader]::new($zip.GetEntry($name).Open());try{$r.ReadToEnd()}finally{$r.Dispose()}}
function CheckBlade($log,[bool]$fixed){
  Require ($log -match 'SuccubusCurse subject=1 target=0 skill=310 state=114 applied=true') 'Natural curse missing'
  Require ($log -match 'BloodManaPlayerSkillDo player=0 skill=266 srvdofunc=48') 'Ordinary animation SkillDo missing'
  $start=@($log -split '\r?\n' | Where-Object {$_ -match 'BladeBloodManaStart player='})
  Require ($start.Count -eq 1) 'Expected one player input'
  Require ($start[0] -match 'hp=([0-9.]+)->([0-9.]+) mana=([0-9.]+)->([0-9.]+) casting=true') 'Start observation'
  Require ($Matches[1] -eq $Matches[2] -and $Matches[3] -eq $Matches[4]) 'Premature start payment'
  $spends=@($log -split '\r?\n' | Where-Object {$_ -match 'BladeBloodManaSpend player='})
  Require ($spends.Count -eq 1) 'Expected one blade payment'
  Require ($spends[0] -match 'hp=([0-9.]+)->([0-9.]+) mana=([0-9.]+)->([0-9.]+) otherDamage=([0-9.]+) newBlades=1') 'Blade observation'
  $life=[double]$Matches[1]-[double]$Matches[2]-[double]$Matches[5]
  $mana=[double]$Matches[3]-[double]$Matches[4]
  if($fixed){Require ($life -eq 1 -and $mana -eq 0) 'Blade Blood Mana payment'}
  else{Require ($life -eq 0 -and $mana -eq 1) 'Baseline defect not reproduced'}
}
$baseline=[System.IO.Compression.ZipFile]::OpenRead((Resolve-Path "$Evidence/d0-baseline-scenes.zip"))
try{
  $rows=@(Get-Content "$Evidence/d0-baseline.jsonl" | ConvertFrom-Json)
  Require ($rows.Count -eq 28) 'Baseline count'
  foreach($row in $rows | Where-Object scenario -eq 'blade-blood-mana'){
    CheckBlade (ReadEntry $baseline $row.log) $false
    Require ($row.warnings -eq 1) 'Baseline SrvDo48 routing warning'
  }
}finally{$baseline.Dispose()}
$executions=0;$payments=0
foreach($difficulty in 0..2){
  $hash=(Get-FileHash "$Evidence/d$difficulty-first.jsonl").Hash
  Require ($hash -eq (Get-FileHash "$Evidence/d$difficulty-repeat.jsonl").Hash) 'Repeat JSON differs'
  Write-Output "d$difficulty JSON SHA256=$hash repeat=IDENTICAL"
  $first=[System.IO.Compression.ZipFile]::OpenRead((Resolve-Path "$Evidence/d$difficulty-first-scenes.zip"))
  $repeat=[System.IO.Compression.ZipFile]::OpenRead((Resolve-Path "$Evidence/d$difficulty-repeat-scenes.zip"))
  try{foreach($pass in @('first','repeat')){
    $zip=if($pass -eq 'first'){$first}else{$repeat}
    $rows=@(Get-Content "$Evidence/d$difficulty-$pass.jsonl" | ConvertFrom-Json)
    Require ($rows.Count -eq 28) 'Scene count'
    $journal=@((ReadEntry $zip 'results.jsonl') -split '\r?\n' | Where-Object {$_} | ConvertFrom-Json)
    Require ($journal.Count -eq 1194) 'Journal count'
    Require (@($journal | Where-Object status -eq 'NOT_RUN').Count -eq 1166) 'Unselected count'
    foreach($row in $rows){
      Require ($row.actualAi -eq 'Succubus' -and !$row.fallback -and $row.warnings -eq 0 -and $row.errors -eq 0) 'Scene diagnostics'
      Require ($row.coverage -eq 'BASELINE_ONLY' -and !$row.specialPassed) 'No full AI claim'
      $log=ReadEntry $zip $row.log
      Require ($log -ceq (ReadEntry $first $row.log)) 'Repeat log differs'
      if($row.scenario -eq 'blade-blood-mana'){CheckBlade $log $true;$payments++}
      $executions++
      Write-Output "d$difficulty $pass $($row.monster) $($row.scenario) actions=$($row.actions) damage=$($row.damage)"
    }
  }}finally{$first.Dispose();$repeat.Dispose()}
}
$tests=0
foreach($file in Get-ChildItem "$Evidence/TEST-*.xml"){
  [xml]$xml=Get-Content $file
  Require ($xml.testsuite.failures -eq 0 -and $xml.testsuite.errors -eq 0 -and $xml.testsuite.skipped -eq 0) 'Test failure'
  $tests += [int]$xml.testsuite.tests
}
foreach($path in @('core/src/main/java/com/riiablo/engine/server/ServerSkillSystem.java',
    'core/src/main/java/com/riiablo/engine/server/Actioneer.java',
    'core/src/test/java/com/riiablo/engine/server/BladeFuryBloodManaIntegrationTest.java',
    'server/d2gs/src/main/java/com/riiablo/server/d2gs/MonsterAiArena.java')){
  $hash=(Get-FileHash $path).Hash
  Require ($hash -eq (Get-FileHash "$SourceRoot/$path").Hash) 'Source mismatch'
  Write-Output "source=$path SHA256=$hash snapshot=IDENTICAL"
}
Require ($tests -eq 181 -and $executions -eq 168 -and $payments -eq 42) 'Coverage'
Write-Output "tests=$tests naturalExecutions=$executions bladeLifePayments=$payments overall=PARTIAL"
