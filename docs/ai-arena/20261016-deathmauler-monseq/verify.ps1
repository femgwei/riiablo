param(
  [string]$Baseline = "$PSScriptRoot/../20261016-deathmauler-seed1/results.jsonl",
  [string]$Result = "$PSScriptRoot/results.jsonl",
  [string]$Repeat = "$PSScriptRoot/repeat-results.jsonl"
)
$ErrorActionPreference = 'Stop'
function Require($ok, $message) { if (-not $ok) { throw $message } }
$old = @(Get-Content $Baseline | ForEach-Object { $_ | ConvertFrom-Json })
$new = @(Get-Content $Result | ForEach-Object { $_ | ConvertFrom-Json })
Require ($old.Count -eq 1174 -and $new.Count -eq 1174) 'Expected full 1174-scene directory'
Require (@($new.monster | Sort-Object -Unique).Count -eq 557) 'Expected 557 monster rows'
Require ((Get-FileHash $Result).Hash -eq (Get-FileHash $Repeat).Hash) 'Independent repeat differs'
$changed = 0
for ($i = 0; $i -lt $new.Count; $i++) {
  $a = $old[$i]; $b = $new[$i]
  Require ($a.monster -eq $b.monster -and $a.scenario -eq $b.scenario) 'Scene order changed'
  $fields = @($a.PSObject.Properties | Where-Object { $_.Value -cne $b.($_.Name) })
  if ($fields.Count -eq 0) { continue }
  $changed++
  Require ($b.monster -match '^deathmauler[3-6]$' -and $b.scenario -eq 'far') 'Unrelated scene changed'
  Require (($fields.Name -join ',') -eq 'actions,warnings') 'Unexpected changed fields'
  Require ($b.actions -eq 14 -and $b.warnings -eq 14) 'Expected full sequence cadence and preserved Do136 warnings'
  Require ($b.status -eq 'WARNING' -and $b.damageEvents -eq 0 -and $b.damage -eq 0) 'Unsupported effect must stay visible'
}
Require ($changed -eq 4) 'Expected four ranged scene differences'
Require (@($new | Where-Object { $_.errors -ne 0 }).Count -eq 0) 'Runtime errors'
Require (@($new | Where-Object { $_.specialAssert }).Count -eq 60) 'Lost existing dedicated assertions'
Require (@($new | Where-Object { $_.specialAssert -and -not $_.specialPassed }).Count -eq 0) 'Existing dedicated assertion failed'
Require (@($new | Where-Object { $_.monster -match '^deathmauler[1-6]$' -and $_.scenario -eq 'near' -and $_.damageEvents -gt 0 -and $_.warnings -eq 0 }).Count -eq 6) 'Lost melee damage observations'
Write-Output "PASS: 557 rows / 1174 scenes; only four ranged cadence changes; 60 existing assertions; repeat SHA $((Get-FileHash $Result).Hash)"
