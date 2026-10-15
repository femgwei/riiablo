param([string]$Pass = 'first', [int]$Difficulty = 0,
  [string]$RunName = '20261016-minion', [string]$Evidence = $PSScriptRoot)
$ErrorActionPreference = 'Stop'
$output = "server/d2gs/build/monster-ai-arena/$RunName-d$Difficulty-$Pass"
$key = "d$Difficulty-$Pass"
if ((Test-Path $output) -or (Test-Path "$Evidence/$key.log")) { throw 'Existing evidence/output' }
$monsters = (1..11 | ForEach-Object { "minion$_" }) -join ','
& ./gradlew.bat --project-cache-dir build/minion-gradle-cache `
  -I docs/ai-arena/20261016-minion-seed1/isolated.init.gradle `
  :server:d2gs:monsterAiArena "-ParenaMonsters=$monsters" `
  "-ParenaOutput=$output" "-ParenaDifficulty=$Difficulty" '-ParenaSeed=1' `
  --console=plain --max-workers=1 *> "$Evidence/$key.log"
if ($LASTEXITCODE -ne 0) { throw "Arena failed: $key" }
Get-Content "$output/results.jsonl" | Where-Object {
  ($_ | ConvertFrom-Json).ai -eq 'Minion'
} | Set-Content "$Evidence/$key.jsonl"
Compress-Archive -Path "$output/*.log", "$output/results.jsonl" -DestinationPath "$Evidence/$key-scenes.zip"
