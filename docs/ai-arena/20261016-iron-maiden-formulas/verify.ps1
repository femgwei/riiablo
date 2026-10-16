param([string]$SourceRoot='build/iron-maiden-formulas-source')
$ErrorActionPreference='Stop'
[xml]$baseline=Get-Content "$PSScriptRoot/baseline.xml" -Raw
if([int]$baseline.testsuite.tests -ne 15 -or [int]$baseline.testsuite.failures -ne 2 -or [int]$baseline.testsuite.errors -ne 0){throw 'Baseline mismatch'}
Write-Output 'Baseline: 15 tests, 2 expected Iron Maiden formula-selection failures.'
foreach($pass in @('final','repeat')){
  $count=0
  foreach($taskFile in Get-ChildItem "$PSScriptRoot/$pass/TEST-*.xml"){
    [xml]$taskXml=Get-Content -LiteralPath $taskFile.FullName -Raw
    if([int]$taskXml.testsuite.failures -ne 0 -or [int]$taskXml.testsuite.errors -ne 0 -or [int]$taskXml.testsuite.skipped -ne 0){throw "Failed/skipped: $taskFile"}
    $count += [int]$taskXml.testsuite.tests
  }
  if($count -ne 356){throw "Unexpected $pass count: $count"}
  Write-Output "${pass}: $count tests, 0 failures/errors/skips."
}
$samplePattern='NovaResistance seed=\d+ raw=[\d.]+ resisted=[\d.]+'
$firstSamples=[regex]::Matches((Get-Content "$PSScriptRoot/final/TEST-com.riiablo.engine.server.SorceressNovaIntegrationTest.xml" -Raw),$samplePattern) | ForEach-Object {$_.Value}
$repeatSamples=[regex]::Matches((Get-Content "$PSScriptRoot/repeat/TEST-com.riiablo.engine.server.SorceressNovaIntegrationTest.xml" -Raw),$samplePattern) | ForEach-Object {$_.Value}
if($firstSamples.Count -ne 64 -or $repeatSamples.Count -ne 64 -or (Compare-Object $firstSamples $repeatSamples -SyncWindow 0)){throw 'Paired sample mismatch'}
Write-Output '64 paired fixed-point samples agree in both final-source runs.'
foreach($taskPath in @('core/src/main/java/com/riiablo/engine/server/StateUpdater.java',
  'core/src/main/java/com/riiablo/engine/server/skill/NecromancerSkills.java',
  'core/src/test/java/com/riiablo/engine/server/NativeNecromancerCurseDataTest.java',
  'core/src/test/java/com/riiablo/engine/server/NecromancerCurseIntegrationTest.java',
  'core/src/test/java/com/riiablo/engine/server/NativeNecromancerExplosionDataTest.java')){
  $currentHash=(Get-FileHash -LiteralPath $taskPath).Hash
  $testedHash=(Get-FileHash -LiteralPath "$SourceRoot/$taskPath").Hash
  if($currentHash -ne $testedHash){throw "Source mismatch: $taskPath"}
  Write-Output "SHA256 $currentHash $taskPath"
}
Write-Output 'PARTIAL: Iron Maiden formula branch selection verified; other resolvers, native RNG and complete death-mode/callback parity remain outside acceptance.'
