param([string]$Pass = 'first', [int]$Difficulty = 0,
  [string]$Evidence = $PSScriptRoot, [string]$SourceRoot = 'build/bloodmana-source')
$ErrorActionPreference = 'Stop'
$env:D2_HOME = 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
$output = Join-Path (Get-Location) "server/d2gs/build/monster-ai-arena/20261016-bloodmana-d$Difficulty-$Pass"
$key = "d$Difficulty-$Pass"
if ((Test-Path $output) -or (Test-Path "$Evidence/$key.log")) { throw 'Existing evidence/output' }
& ./gradlew.bat -p $SourceRoot --project-cache-dir build/bloodmana-gradle-cache `
  -I docs/ai-arena/20261016-bloodmana-seed1/isolated.init.gradle `
  :server:d2gs:monsterAiArena '-ParenaMonsters=succubus1,succubus2,succubus3,succubus4,succubus5,succubus6,succubus7' `
  "-ParenaOutput=$output" "-ParenaDifficulty=$Difficulty" '-ParenaSeed=1' `
  --console=plain --max-workers=1 *> "$Evidence/$key.log"
if ($LASTEXITCODE -ne 0) { throw "Arena failed: $key" }
Get-Content "$output/results.jsonl" | Where-Object { ($_ | ConvertFrom-Json).ai -eq 'Succubus' } | Set-Content "$Evidence/$key.jsonl"
Compress-Archive -Path "$output/*.log", "$output/results.jsonl" -DestinationPath "$Evidence/$key-scenes.zip"
