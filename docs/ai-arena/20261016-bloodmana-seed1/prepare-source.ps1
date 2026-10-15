param([string]$SourceRoot = 'build/bloodmana-source', [switch]$Repaired)
$ErrorActionPreference = 'Stop'
if (!$Repaired) {
  if (Test-Path $SourceRoot) { throw 'Source snapshot exists' }
  $base = (Get-Content "$PSScriptRoot/source-base-sha.txt" -Raw).Trim()
  if (Test-Path "$SourceRoot.zip") { throw 'Source archive exists' }
  & git archive --format=zip "--output=$SourceRoot.zip" $base
  if ($LASTEXITCODE -ne 0) { throw 'git archive failed' }
  Expand-Archive -LiteralPath "$SourceRoot.zip" -DestinationPath $SourceRoot
}
foreach ($path in @('core/src/test/java/com/riiablo/engine/server/BloodManaIntegrationTest.java',
    'server/d2gs/src/main/java/com/riiablo/server/d2gs/MonsterAiArena.java')) {
  Copy-Item $path "$SourceRoot/$path"
}
if ($Repaired) {
  foreach ($path in @('core/src/main/java/com/riiablo/engine/server/skill/BloodMana.java',
      'core/src/main/java/com/riiablo/engine/server/ServerSkillSystem.java',
      'core/src/main/java/com/riiablo/engine/server/AuraEcsSystem.java')) {
    Copy-Item $path "$SourceRoot/$path"
  }
}
New-Item -ItemType Directory -Force "$SourceRoot/docs/ai-arena/20261016-bloodmana-seed1" | Out-Null
Copy-Item "$PSScriptRoot/isolated.init.gradle" "$SourceRoot/docs/ai-arena/20261016-bloodmana-seed1/"
