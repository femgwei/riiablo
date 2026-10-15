param([string]$Evidence=$PSScriptRoot,[string]$SourceRoot='build/nova-resistance-source')
$ErrorActionPreference='Stop'
function Require($value,[string]$message){if(!$value){throw $message}}
[xml]$baseline=Get-Content "$Evidence/baseline-nova.xml"
Require ($baseline.testsuite.tests -eq 7 -and $baseline.testsuite.failures -eq 1) 'Baseline Nova failure'
$reference=$null
foreach($pass in @('first','repeat')){
  $tests=0
  foreach($file in Get-ChildItem "$Evidence/$pass/TEST-*.xml"){
    [xml]$xml=Get-Content $file
    Require ($xml.testsuite.failures -eq 0 -and $xml.testsuite.errors -eq 0 -and $xml.testsuite.skipped -eq 0) 'Joint test failure'
    $tests += [int]$xml.testsuite.tests
  }
  Require ($tests -eq 182) 'Joint suite count'
  [xml]$nova=Get-Content "$Evidence/$pass/TEST-com.riiablo.engine.server.SorceressNovaIntegrationTest.xml"
  Require ($nova.testsuite.tests -eq 8) 'Nova directed test count'
  $rows=@($nova.testsuite.SelectSingleNode('system-out').InnerText -split '\r?\n' | Where-Object {$_ -match '^NovaResistance seed='})
  Require ($rows.Count -eq 64) 'Seed sample count'
  $zero=0;$positive=0
  for($seed=0;$seed -lt 64;$seed++){
    Require ($rows[$seed] -match '^NovaResistance seed=(\d+) raw=([0-9.]+) resisted=([0-9.]+)$') 'Sample format'
    $raw=[double]$Matches[2];$resisted=[double]$Matches[3]
    Require ([int]$Matches[1] -eq $seed -and $raw -ge 1 -and $raw -le 19) 'Raw missile range'
    Require ($resisted -eq [Math]::Floor($raw/2)) 'Current integer resistance contract'
    if($resisted -eq 0){$zero++;Write-Output "$pass counterexample=$($rows[$seed])"}else{$positive++}
  }
  Require ($zero -gt 0 -and $positive -gt 0) 'Missing boundary coverage'
  $joined=$rows -join "`n"
  if($null -eq $reference){$reference=$joined}else{Require ($reference -ceq $joined) 'Independent sample replay differs'}
  Write-Output "$pass jointTests=$tests samples=64 zeroAfterResistance=$zero positiveAfterResistance=$positive"
}
$path='core/src/test/java/com/riiablo/engine/server/SorceressNovaIntegrationTest.java'
$hash=(Get-FileHash $path).Hash
Require ($hash -eq (Get-FileHash "$SourceRoot/$path").Hash) 'Source mismatch'
Write-Output "source=$path SHA256=$hash snapshot=IDENTICAL"
Write-Output 'jointSuite=PASS independentReplay=IDENTICAL nativeFractionalDamage=NOT_ACCEPTED overall=PARTIAL'
