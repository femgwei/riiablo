param([string]$SourceRoot='build/nova-fixed8-source')
$ErrorActionPreference='Stop'
[xml]$baseline=Get-Content "$PSScriptRoot/baseline-nova.xml" -Raw
if([int]$baseline.testsuite.tests -ne 8 -or [int]$baseline.testsuite.failures -ne 1){throw 'Baseline mismatch'}
Write-Output 'Baseline: 8 tests, 1 expected precision failure (seed 0, 1.5 vs 1.0 HP).'
foreach($pass in @('final','repeat')){
  $count=0
  foreach($taskFile in Get-ChildItem "$PSScriptRoot/$pass/TEST-*.xml"){
    [xml]$taskXml=Get-Content -LiteralPath $taskFile.FullName -Raw
    if([int]$taskXml.testsuite.failures -ne 0 -or [int]$taskXml.testsuite.errors -ne 0 -or [int]$taskXml.testsuite.skipped -ne 0){throw "Failed/skipped: $taskFile"}
    $count += [int]$taskXml.testsuite.tests
  }
  if($count -ne 233){throw "Unexpected $pass count: $count"}
  Write-Output "${pass}: $count tests, 0 failures/errors/skips."
}
$samplePattern='NovaResistance seed=\d+ raw=[\d.]+ resisted=[\d.]+'
$firstSamples=[regex]::Matches((Get-Content "$PSScriptRoot/final/TEST-com.riiablo.engine.server.SorceressNovaIntegrationTest.xml" -Raw),$samplePattern) | ForEach-Object {$_.Value}
$repeatSamples=[regex]::Matches((Get-Content "$PSScriptRoot/repeat/TEST-com.riiablo.engine.server.SorceressNovaIntegrationTest.xml" -Raw),$samplePattern) | ForEach-Object {$_.Value}
if($firstSamples.Count -ne 64 -or $repeatSamples.Count -ne 64 -or (Compare-Object $firstSamples $repeatSamples -SyncWindow 0)){throw 'Paired sample mismatch'}
Write-Output '64 paired fixed-point samples agree in both final-source runs.'
foreach($taskPath in @('core/src/main/java/com/riiablo/engine/server/combat/CombatSystem.java',
  'core/src/main/java/com/riiablo/engine/server/MissileCollisionSystem.java',
  'core/src/main/java/com/riiablo/engine/server/event/DamageEvent.java',
  'core/src/test/java/com/riiablo/engine/server/SorceressNovaIntegrationTest.java',
  'core/src/test/java/com/riiablo/engine/server/combat/NovaFixedPointDamageTest.java')){
  $currentHash=(Get-FileHash -LiteralPath $taskPath).Hash
  $testedHash=(Get-FileHash -LiteralPath "$SourceRoot/$taskPath").Hash
  if($currentHash -ne $testedHash){throw "Source mismatch: $taskPath"}
  Write-Output "SHA256 $currentHash $taskPath"
}
Write-Output 'PARTIAL: precision verified; native mitigation ordering, caps and RNG remain unverified.'
