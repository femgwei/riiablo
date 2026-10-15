param(
  [string]$Arena = 'server/d2gs/build/monster-ai-arena/20261016-deathmauler-full',
  [string]$Repeat = 'server/d2gs/build/monster-ai-arena/20261016-deathmauler-repeat',
  [string]$Baseline = 'docs/ai-arena/20261016-doomknight-seed1/results.jsonl'
)
$ErrorActionPreference = 'Stop'
$rows = @(Get-Content "$Arena/results.jsonl" | ForEach-Object { $_ | ConvertFrom-Json })
if ($rows.Count -ne 1174) { throw 'Expected 1174 scenes' }
if (@($rows | Where-Object { $_.errors -gt 0 -or $_.status -in @('RUNTIME_ERROR', 'ERROR_LOG', 'NOT_RUN', 'SPECIAL_NOT_TRIGGERED') }).Count) {
  throw 'Errors or unsuccessful dedicated scenes'
}
if (@($rows | Where-Object specialPassed).Count -ne 60) { throw 'Expected sixty existing dedicated assertions' }
$maulers = @($rows | Where-Object ai -eq 'DeathMauler')
if ($maulers.Count -ne 12) { throw 'Expected six DeathMauler rows, near/far each' }
foreach ($r in $maulers) {
  if ($r.fallback -or $r.actualAi -ne 'DeathMauler') { throw "Fallback: $($r.monster)" }
  $log = Get-Content (Join-Path $Arena $r.log) -Raw
  if ($r.scenario -eq 'near') {
    if ($log -notmatch 'SkillDo subject=\d+ skill=0 target=0 srvdofunc=1' -or $r.damageEvents -le 0) {
      throw "Missing natural melee attack and damage: $($r.monster)"
    }
    if ($log -match 'skill=308 target=') { throw "Unexpected ranged skill in melee scene: $($r.monster)" }
  }
  if ($log -match 'SkillDo subject=\d+ skill=308 target=') {
    if ($log -notmatch 'Unsupported srvdofunc\(136\)') { throw 'Missing unsupported DeathMaul warning' }
  }
}
$old = @{}
Get-Content $Baseline | ForEach-Object { $r = $_ | ConvertFrom-Json; $old[$r.monster+'/'+$r.scenario] = $_ }
$changes = 0
Get-Content "$Arena/results.jsonl" | ForEach-Object {
  $r = $_ | ConvertFrom-Json
  $key = $r.monster+'/'+$r.scenario
  if (!$old.ContainsKey($key)) { throw "Unexpected scene: $key" }
  if ($old[$key] -cne $_) {
    if ($r.ai -ne 'DeathMauler') { throw "Unrelated result changed: $key" }
    $changes++
  }
}
if ($changes -ne 12) { throw "Expected twelve changed scenes, got $changes" }
$hash = (Get-FileHash "$Arena/results.jsonl" -Algorithm SHA256).Hash
if ($hash -ne (Get-FileHash "$Repeat/results.jsonl" -Algorithm SHA256).Hash) { throw 'Repeat differs' }
"PASS: six natural near-scene melee/damage checks; twelve changed DeathMauler scenes; unsupported Do136 remains visible; repeat SHA256=$hash"
$rows | Group-Object status | ForEach-Object { "$($_.Name)=$($_.Count)" }
