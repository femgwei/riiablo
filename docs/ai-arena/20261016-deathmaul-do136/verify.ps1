param(
  [string]$Baseline = "$PSScriptRoot/../20261016-deathmauler-monseq/results.jsonl",
  [string]$Result = "$PSScriptRoot/results.jsonl",
  [string]$Repeat = "$PSScriptRoot/repeat-results.jsonl"
)
$ErrorActionPreference = 'Stop'
function Require($ok, $message) { if (-not $ok) { throw $message } }
$old = @{}
Get-Content $Baseline | ForEach-Object { $v = $_ | ConvertFrom-Json; $old[$v.monster+'|'+$v.scenario] = $v }
$new = @(Get-Content $Result | ForEach-Object { $_ | ConvertFrom-Json })
Require ($old.Count -eq 1174 -and $new.Count -eq 1180) 'Full directory scene count changed'
Require (@($new.monster | Sort-Object -Unique).Count -eq 557) 'Monster directory changed'
Require ((Get-FileHash $Result).Hash -eq (Get-FileHash $Repeat).Hash) 'Independent repeat differs'
$changed = 0; $added = 0
foreach ($b in $new) {
  $a = $old[$b.monster+'|'+$b.scenario]
  if ($null -eq $a) {
    $added++
    Require ($b.monster -match '^deathmauler[1-6]$' -and $b.scenario -eq 'deathmaul-cycle') 'Unexpected new scene'
    Require ($b.specialAssert -and $b.specialPassed -and $b.special -eq 'DEATH_MAUL_CYCLE_OBSERVED') 'Natural DeathMaul oracle failed'
    Require ($b.damageEvents -gt 0 -and $b.damage -gt 0 -and $b.warnings -eq 0) 'Natural skill damage missing'
    continue
  }
  $fields = @($a.PSObject.Properties | Where-Object { $_.Value -cne $b.($_.Name) })
  if ($fields.Count -eq 0) { continue }
  $changed++
  Require ($b.monster -match '^deathmauler[3-6]$' -and $b.scenario -eq 'far') 'Unrelated existing scene changed'
  Require (($fields.Name -join ',') -eq 'status,damageEvents,warnings,damage') 'Unexpected changed fields'
  Require ($b.status -eq 'ACTION_OBSERVED' -and $b.warnings -eq 0 -and $b.damageEvents -gt 0 -and $b.damage -gt 0) 'Ranged effect missing'
}
Require ($changed -eq 4 -and $added -eq 6) 'Expected four changed and six new scenes'
Require (@($new | Where-Object { $_.errors -ne 0 -or $_.status -in @('RUNTIME_ERROR','NOT_RUN','SPECIAL_NOT_TRIGGERED') }).Count -eq 0) 'Runtime or special failure'
Require (@($new | Where-Object { $_.specialAssert }).Count -eq 66) 'Dedicated assertions lost'
Require (@($new | Where-Object { $_.specialAssert -and -not $_.specialPassed }).Count -eq 0) 'Dedicated assertion failed'
Require (@($new | Where-Object { $_.monster -match '^deathmauler[1-6]$' -and $_.scenario -eq 'near' -and $_.damageEvents -gt 0 -and $_.warnings -eq 0 }).Count -eq 6) 'Melee regression'
Write-Output "PASS: 557 rows / 1180 scenes; six natural DeathMaul oracles; 66 dedicated assertions; only four prior scene changes; SHA $((Get-FileHash $Result).Hash)"
