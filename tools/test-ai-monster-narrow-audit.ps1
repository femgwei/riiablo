$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$fixture = Join-Path $repo ('build/monster-narrow-audit-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path (Join-Path $fixture 'natural') | Out-Null
@{ revision='fixture'; seed=1; levels='62'; natural='OBSERVED_RUNTIME_ONLY' } |
  ConvertTo-Json | Set-Content (Join-Path $fixture 'acceptance.json')
Set-Content (Join-Path $fixture 'natural-console.log') ''
@(
  '[AI_TOUR] SUMMARY run=COMPLETED coverage=OBSERVED_RUNTIME_ONLY oracle=NONE correctness=NOT_ASSERTED warnings=0 errors=0 fallbackRecords=0',
  '[AI_TOUR] MONSTER monster=scarab2 ai=Scarab initRecords=1 moveRecords=1 decisionRecords=0 skillCastRecords=0 meleeDamageRecords=0 nativeCombatant=true evidence=BEHAVIOR_OBSERVED'
) | Set-Content (Join-Path $fixture 'natural/ai-summary.txt')
$header = @(
  '[MAP_TOUR] BEGIN seed=1 coverage=AI_RUNTIME_OBSERVATION planned=1',
  '[MAP_TOUR] LOAD level=62 nativeTerrain=true',
  '[MAP_TOUR] AI_OBSERVE_BEGIN level=62 reason=LEVEL_ENTRY firstTick=1 requiredTicks=200 playerAttacks=0',
  '[MAP_TOUR] AI_OBSERVE_END level=62 reason=LEVEL_ENTRY firstTick=1 lastTick=201 observedTicks=200 playerAttacks=0 correctness=NOT_ASSERTED')
$prefix = 'entity=4 target=5 identity=SAME_LIFETIME level=62 lifetime=1 targetLifetime=2 targetSource=LIVE_PATH crossAxis=Y footprint=3 '
$tail = ' beforeSpan=1 afterSpan=1 allowedDisplacement=2.1 bound=3 movement=PRODUCTION_AI coverage=SAMPLED_LOCAL_PROGRESS'
$context = ' context=OBSERVE playerAttacks=0'
$evidence = @(
  ('[MAP_TOUR] MONSTER_NARROW_BLOCK ' + $prefix + 'firstTick=10 lastTick=15 from=(10,10) to=(10,10)' + $tail + ' query=(11,10) queryWasBlocked=true' + $context),
  ('[MAP_TOUR] MONSTER_NARROW_MOVE ' + $prefix + 'firstTick=15 lastTick=20 from=(10,10) to=(11.5,10)' + $tail + $context),
  ('[MAP_TOUR] MONSTER_NARROW_MOVE ' + $prefix + 'firstTick=20 lastTick=25 from=(11.5,10) to=(13,10)' + $tail + $context),
  ('[MAP_TOUR] MONSTER_NARROW_RECOVER ' + $prefix + 'firstTick=20 lastTick=25 from=(11.5,10) to=(13,10)' + $tail + ' blockedFirstTick=10 blockedLastTick=15 blockedPosition=(10,10) query=(11,10) queryWasBlocked=true queryNowFree=true' + $context))
($header + $evidence) | Set-Content (Join-Path $fixture 'natural/map-tour.log')
$result = & (Join-Path $PSScriptRoot 'audit-ai-monster-narrow.ps1') -Run $fixture -RequireRecovery | ConvertFrom-Json
if ($result.passageCount -ne 1 -or $result.recoveryCount -ne 1) { throw 'Positive evidence rejected' }
$cases = @(
  @{ lines=@($evidence | Where-Object { $_ -notmatch 'MONSTER_NARROW_BLOCK' }) },
  @{ lines=@($evidence -replace 'queryNowFree=true', 'queryNowFree=false') },
  @{ lines=@($evidence -replace 'beforeSpan=1', 'beforeSpan=0') },
  @{ lines=@($evidence -replace 'firstTick=20 lastTick=25', 'firstTick=10 lastTick=25') },
  @{ lines=@($evidence -replace 'allowedDisplacement=2.1', 'allowedDisplacement=1.0') },
  @{ lines=@($evidence[0..2]) + @($evidence[3] -replace 'targetLifetime=2', 'targetLifetime=3') },
  @{ lines=@($evidence[0..1]) + @($evidence[3]) },
  @{ lines=@($evidence -replace 'playerAttacks=0', 'playerAttacks=1') })
foreach ($case in $cases) {
  ($header + $case.lines) | Set-Content (Join-Path $fixture 'natural/map-tour.log')
  $rejected = $false
  try { $null = & (Join-Path $PSScriptRoot 'audit-ai-monster-narrow.ps1') -Run $fixture -RequireRecovery }
  catch { $rejected = $true }
  if (!$rejected) { throw 'False PASS: defective monster evidence accepted' }
}
Write-Output "PASS monster narrow audit: positive accepted; $($cases.Count) defective cases rejected"
