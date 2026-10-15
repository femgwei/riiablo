param(
  [string]$Arena = 'server/d2gs/build/monster-ai-arena/20261016-viledog-full',
  [string]$Repeat = 'server/d2gs/build/monster-ai-arena/20261016-viledog-repeat',
  [string]$Baseline = 'docs/ai-arena/20261015-megademon-seed1/results.jsonl'
)
$ErrorActionPreference = 'Stop'
$rows = @(Get-Content "$Arena/results.jsonl" | ForEach-Object { $_ | ConvertFrom-Json })
if ($rows.Count -ne 1174) { throw "Expected 1174 scenes, got $($rows.Count)" }
if (@($rows | Where-Object { $_.errors -gt 0 -or $_.status -in @('RUNTIME_ERROR', 'ERROR_LOG', 'NOT_RUN', 'SPECIAL_NOT_TRIGGERED') }).Count) {
  throw 'Arena has errors or unexecuted/failed dedicated scenes'
}
if (@($rows | Where-Object specialPassed).Count -ne 60) { throw 'Expected sixty dedicated assertions' }
$dogs = @($rows | Where-Object ai -eq 'VileDog')
if ($dogs.Count -ne 20) { throw 'Expected ten VileDog rows, two scenes each' }
foreach ($row in $dogs) {
  if ($row.fallback -or $row.actualAi -ne 'VileDog') { throw "Fallback: $($row.monster)" }
  if ($row.scenario -ne 'near') { continue }
  $log = Get-Content (Join-Path $Arena $row.log) -Raw
  if ($log -notmatch '(?m)^tick=1 sample subject=\d+ ai=VileDog state=IDLE .*casting=false') {
    throw "Missing initial idle sample: $($row.monster)"
  }
  if ($log -notmatch 'SkillDo subject=\d+ skill=0 target=0 srvdofunc=1' -or $row.damageEvents -le 0) {
    throw "Missing natural ordinary attack/damage: $($row.monster)"
  }
}
$old = @{}
Get-Content $Baseline | ForEach-Object { $r = $_ | ConvertFrom-Json; $old[$r.monster+'/'+$r.scenario] = $_ }
$changes = 0
Get-Content "$Arena/results.jsonl" | ForEach-Object {
  $r = $_ | ConvertFrom-Json
  $key = $r.monster+'/'+$r.scenario
  if (!$old.ContainsKey($key)) { throw "Unexpected new scene $key" }
  if ($old[$key] -cne $_) {
    if ($r.ai -ne 'VileDog') { throw "Unrelated result changed: $key" }
    $changes++
  }
}
$hash = (Get-FileHash "$Arena/results.jsonl" -Algorithm SHA256).Hash
if ($changes -ne 20) { throw "Expected twenty changed scenes, got $changes" }
if ($hash -ne (Get-FileHash "$Repeat/results.jsonl" -Algorithm SHA256).Hash) { throw 'Repeat differs' }
"PASS: ten natural near-scene initial-idle/attack/damage checks; $changes changed VileDog scenes; repeat SHA256=$hash"
$rows | Group-Object status | ForEach-Object { "$($_.Name)=$($_.Count)" }
