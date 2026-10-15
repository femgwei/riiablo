param([string]$SourceRoot='build/nova-fixed8-source',[switch]$Repaired)
$ErrorActionPreference='Stop'
if(Test-Path $SourceRoot){throw 'Source exists'}
$base=(Get-Content "$PSScriptRoot/source-base-sha.txt" -Raw).Trim()
if(Test-Path "$SourceRoot.zip"){throw 'Archive exists'}
& git archive --format=zip "--output=$SourceRoot.zip" $base
if($LASTEXITCODE -ne 0){throw 'Archive failed'}
Expand-Archive -LiteralPath "$SourceRoot.zip" -DestinationPath $SourceRoot
$paths=@('core/src/test/java/com/riiablo/engine/server/SorceressNovaIntegrationTest.java')
if($Repaired){
  $paths+=@('core/src/main/java/com/riiablo/engine/server/combat/CombatSystem.java',
    'core/src/main/java/com/riiablo/engine/server/MissileCollisionSystem.java',
    'core/src/main/java/com/riiablo/engine/server/event/DamageEvent.java',
    'core/src/test/java/com/riiablo/engine/server/combat/NovaFixedPointDamageTest.java')
}
foreach($taskPath in $paths){Copy-Item -LiteralPath $taskPath -Destination "$SourceRoot/$taskPath"}
