param([string]$Pass='first',[int]$Difficulty=0,[string]$SourceRoot='build/bladebloodmana-source')
$ErrorActionPreference='Stop'
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
$output=Join-Path (Get-Location) "server/d2gs/build/monster-ai-arena/20261016-bladebloodmana-d$Difficulty-$Pass"
$key="d$Difficulty-$Pass"
if((Test-Path $output)-or(Test-Path "$PSScriptRoot/$key.log")){throw 'Existing evidence/output'}
& ./gradlew.bat -p $SourceRoot --project-cache-dir build/bladebloodmana-gradle-cache `
  -I docs/ai-arena/20261016-bladebloodmana-seed1/isolated.init.gradle `
  :server:d2gs:monsterAiArena '-ParenaMonsters=succubus1,succubus2,succubus3,succubus4,succubus5,succubus6,succubus7' `
  "-ParenaOutput=$output" "-ParenaDifficulty=$Difficulty" -ParenaSeed=1 --console=plain --max-workers=1 *> "$PSScriptRoot/$key.log"
if($LASTEXITCODE -ne 0){throw 'Arena failed'}
Get-Content "$output/results.jsonl" | Where-Object {($_|ConvertFrom-Json).ai -eq 'Succubus'} | Set-Content "$PSScriptRoot/$key.jsonl"
Compress-Archive -Path "$output/*.log","$output/results.jsonl" -DestinationPath "$PSScriptRoot/$key-scenes.zip"
