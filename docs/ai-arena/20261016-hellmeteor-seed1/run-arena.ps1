param([string]$Pass = 'first', [int]$Difficulty = 0,
  [string]$RunName = '20261016-hellmeteor', [string]$Evidence = $PSScriptRoot)
$ErrorActionPreference = 'Stop'
$env:D2_HOME = 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
$output = "server/d2gs/build/monster-ai-arena/$RunName-d$Difficulty-$Pass"
$key = "d$Difficulty-$Pass"
if ((Test-Path $output) -or (Test-Path "$Evidence/$key.log")) { throw 'Existing evidence/output' }
& ./gradlew.bat --project-cache-dir build/hellmeteor-gradle-cache `
  -I docs/ai-arena/20261016-hellmeteor-seed1/isolated.init.gradle `
  :server:d2gs:monsterAiArena '-ParenaMonsters=hellmeteor' `
  "-ParenaOutput=$output" "-ParenaDifficulty=$Difficulty" '-ParenaSeed=1' `
  --console=plain --max-workers=1 *> "$Evidence/$key.log"
if ($LASTEXITCODE -ne 0) { throw "Arena failed: $key" }
Get-Content "$output/results.jsonl" | Where-Object {
  ($_ | ConvertFrom-Json).ai -eq 'HellMeteor'
} | Set-Content "$Evidence/$key.jsonl"
Compress-Archive -Path "$output/*.log", "$output/results.jsonl" -DestinationPath "$Evidence/$key-scenes.zip"
