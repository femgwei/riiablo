param([string]$SourceRoot='build/static-absorb-source',[switch]$Repaired)
$ErrorActionPreference='Stop'
if(Test-Path $SourceRoot){throw 'Source exists'}
$base=(Get-Content "$PSScriptRoot/source-base-sha.txt" -Raw).Trim()
if(Test-Path "$SourceRoot.zip"){throw 'Archive exists'}
& git archive --format=zip "--output=$SourceRoot.zip" $base
if($LASTEXITCODE -ne 0){throw 'Archive failed'}
Expand-Archive -LiteralPath "$SourceRoot.zip" -DestinationPath $SourceRoot
Copy-Item core/src/test/java/com/riiablo/engine/server/SorceressStaticFieldIntegrationTest.java "$SourceRoot/core/src/test/java/com/riiablo/engine/server/"
if($Repaired){Copy-Item core/src/main/java/com/riiablo/engine/server/ServerSkillSystem.java "$SourceRoot/core/src/main/java/com/riiablo/engine/server/"}
