param([string]$SourceRoot='build/nova-resistance-source',[switch]$Repaired)
$ErrorActionPreference='Stop'
if(Test-Path $SourceRoot){throw 'Source exists'}
$base=(Get-Content "$PSScriptRoot/source-base-sha.txt" -Raw).Trim()
if(Test-Path "$SourceRoot.zip"){throw 'Archive exists'}
& git archive --format=zip "--output=$SourceRoot.zip" $base
if($LASTEXITCODE -ne 0){throw 'Archive failed'}
Expand-Archive -LiteralPath "$SourceRoot.zip" -DestinationPath $SourceRoot
if($Repaired){Copy-Item core/src/test/java/com/riiablo/engine/server/SorceressNovaIntegrationTest.java "$SourceRoot/core/src/test/java/com/riiablo/engine/server/"}
New-Item -ItemType Directory -Force "$SourceRoot/docs/ai-arena/20261016-nova-resistance" | Out-Null
Copy-Item "$PSScriptRoot/isolated.init.gradle" "$SourceRoot/docs/ai-arena/20261016-nova-resistance/"
