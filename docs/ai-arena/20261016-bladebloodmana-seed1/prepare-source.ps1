param([string]$SourceRoot='build/bladebloodmana-source',[switch]$Repaired)
$ErrorActionPreference='Stop'
if(!$Repaired){
  if((Test-Path $SourceRoot)-or(Test-Path "$SourceRoot.zip")){throw 'Source exists'}
  $base=(Get-Content "$PSScriptRoot/source-base-sha.txt" -Raw).Trim()
  & git archive --format=zip "--output=$SourceRoot.zip" $base
  if($LASTEXITCODE -ne 0){throw 'Archive failed'}
  Expand-Archive -LiteralPath "$SourceRoot.zip" -DestinationPath $SourceRoot
}
foreach($path in @('core/src/test/java/com/riiablo/engine/server/BladeFuryBloodManaIntegrationTest.java',
    'server/d2gs/src/main/java/com/riiablo/server/d2gs/MonsterAiArena.java')){Copy-Item $path "$SourceRoot/$path"}
if($Repaired){foreach($name in @('ServerSkillSystem.java','Actioneer.java')){Copy-Item "core/src/main/java/com/riiablo/engine/server/$name" "$SourceRoot/core/src/main/java/com/riiablo/engine/server/"}}
New-Item -ItemType Directory -Force "$SourceRoot/docs/ai-arena/20261016-bladebloodmana-seed1" | Out-Null
Copy-Item "$PSScriptRoot/isolated.init.gradle" "$SourceRoot/docs/ai-arena/20261016-bladebloodmana-seed1/"
