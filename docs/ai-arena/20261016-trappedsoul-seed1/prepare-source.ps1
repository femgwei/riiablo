param([string]$SourceRoot = 'build/trappedsoul-source', [switch]$Repaired)
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
Copy-Item core/src/test/java/com/riiablo/engine/server/ai/TrappedSoulAiIntegrationTest.java "$SourceRoot/core/src/test/java/com/riiablo/engine/server/ai/"
if ($Repaired) {
  foreach ($name in @('TrappedSoul.java', 'NativeDecisionAI.java')) {
    Copy-Item "core/src/main/java/com/riiablo/engine/server/ai/$name" "$SourceRoot/core/src/main/java/com/riiablo/engine/server/ai/"
  }
}
New-Item -ItemType Directory -Force "$SourceRoot/docs/ai-arena/20261016-trappedsoul-seed1" | Out-Null
Copy-Item "$PSScriptRoot/isolated.init.gradle" "$SourceRoot/docs/ai-arena/20261016-trappedsoul-seed1/"
