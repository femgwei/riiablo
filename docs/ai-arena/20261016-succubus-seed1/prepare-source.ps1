param([string]$SourceRoot = 'build/succubus-source', [switch]$Repaired)
$ErrorActionPreference = 'Stop'
if (!$Repaired) {
  if (Test-Path $SourceRoot) { throw 'Source snapshot already exists' }
  $base = (Get-Content "$PSScriptRoot/source-base-sha.txt" -Raw).Trim()
  $archive = "$SourceRoot.zip"
  if (Test-Path $archive) { throw 'Source archive already exists' }
  & git archive --format=zip "--output=$archive" $base
  if ($LASTEXITCODE -ne 0) { throw 'git archive failed' }
  Expand-Archive -LiteralPath $archive -DestinationPath $SourceRoot
}
$tests = @('core/src/test/java/com/riiablo/engine/server/ai/SuccubusAiIntegrationTest.java',
  'core/src/test/java/com/riiablo/engine/server/SuccubusCurseIntegrationTest.java')
$production = @('core/src/main/java/com/riiablo/engine/server/ai/Succubus.java',
  'core/src/main/java/com/riiablo/engine/server/skill/NecromancerSkills.java',
  'core/src/main/java/com/riiablo/engine/server/state/StateId.java',
  'core/src/main/java/com/riiablo/engine/server/state/StateFlags.java',
  'server/d2gs/src/main/java/com/riiablo/server/d2gs/MonsterAiArena.java')
foreach ($path in $tests) { Copy-Item $path "$SourceRoot/$path" }
if ($Repaired) { foreach ($path in $production) { Copy-Item $path "$SourceRoot/$path" } }
New-Item -ItemType Directory -Force "$SourceRoot/docs/ai-arena/20261016-succubus-seed1" | Out-Null
Copy-Item "$PSScriptRoot/isolated.init.gradle" "$SourceRoot/docs/ai-arena/20261016-succubus-seed1/"
