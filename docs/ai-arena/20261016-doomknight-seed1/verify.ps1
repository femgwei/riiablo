param(
  [string]$Arena = 'server/d2gs/build/monster-ai-arena/20261016-doomknight-full',
  [string]$Repeat = 'server/d2gs/build/monster-ai-arena/20261016-doomknight-repeat',
  [string]$Baseline = 'docs/ai-arena/20261016-viledog-seed1/results.jsonl'
)
$ErrorActionPreference = 'Stop'
$rows = @(Get-Content "$Arena/results.jsonl" | ForEach-Object { $_ | ConvertFrom-Json })
if ($rows.Count -ne 1174) { throw 'Expected 1174 scenes' }
if (@($rows | Where-Object { $_.errors -gt 0 -or $_.status -in @('RUNTIME_ERROR', 'ERROR_LOG', 'NOT_RUN', 'SPECIAL_NOT_TRIGGERED') }).Count) {
  throw 'Errors or unsuccessful dedicated scenes'
}
if (@($rows | Where-Object specialPassed).Count -ne 60) { throw 'Expected sixty dedicated assertions' }
$knights = @($rows | Where-Object ai -eq 'DoomKnight')
if ($knights.Count -ne 6) { throw 'Expected three DoomKnight rows with near/far scenes' }
foreach ($r in $knights) {
  if ($r.fallback -or $r.actualAi -ne 'DoomKnight') { throw "Fallback: $($r.monster)" }
  if ($r.scenario -ne 'near') { continue }
  $log = Get-Content (Join-Path $Arena $r.log) -Raw
  if ($log -notmatch 'SkillDo subject=\d+ skill=0 target=0 srvdofunc=1' -or $r.damageEvents -le 0) {
    throw "Missing natural ordinary attack and damage: $($r.monster)"
  }
}
$old = @{}
Get-Content $Baseline | ForEach-Object { $r = $_ | ConvertFrom-Json; $old[$r.monster+'/'+$r.scenario] = $_ }
$changes = 0
$helperChanges = 0
Get-Content "$Arena/results.jsonl" | ForEach-Object {
  $r = $_ | ConvertFrom-Json
  $key = $r.monster+'/'+$r.scenario
  if (!$old.ContainsKey($key)) { throw "Unexpected scene: $key" }
  if ($old[$key] -cne $_) {
    if ($r.ai -eq 'DoomKnight') {
      $changes++
    } else {
      # These six scenes instantiate a DoomKnight from MonStats.minion1/2.
      $helper = switch ($r.monster) {
        'dkmag1' { 'dkfig1:2 ' }
        'dkmag2' { 'dkfig2:2 ' }
        'doomknight3' { 'doomknight1:2 ' }
        default { throw "Unrelated result changed: $key" }
      }
      $prior = $old[$key] | ConvertFrom-Json
      if ($r.ai -ne 'OblivionKnight' -or $r.helpers -cne $helper -or $prior.helpers -cne $helper -or
          $r.warnings -ne ($prior.warnings - 1)) { throw "Unexpected helper effect: $key" }
      foreach ($p in $r.psobject.Properties) {
        if ($p.Name -notin @('damageEvents', 'damage', 'warnings') -and $prior.($p.Name) -cne $p.Value) {
          throw "Unexpected helper field change: $key/$($p.Name)"
        }
      }
      $helperChanges++
    }
  }
}
if ($changes -ne 6) { throw "Expected six changed scenes, got $changes" }
if ($helperChanges -ne 6) { throw "Expected six DoomKnight-helper effects, got $helperChanges" }
$hash = (Get-FileHash "$Arena/results.jsonl" -Algorithm SHA256).Hash
if ($hash -ne (Get-FileHash "$Repeat/results.jsonl" -Algorithm SHA256).Hash) { throw 'Repeat differs' }
"PASS: three natural near-scene attack/damage checks; six changed DoomKnight scenes plus six verified DoomKnight-helper effects; repeat SHA256=$hash"
$rows | Group-Object status | ForEach-Object { "$($_.Name)=$($_.Count)" }
