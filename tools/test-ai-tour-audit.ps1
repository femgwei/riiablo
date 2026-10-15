$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'map-acceptance-audit.ps1')
$valid = @(
  '[MAP_TOUR] BEGIN seed=1 coverage=AI_RUNTIME_OBSERVATION planned=1',
  '[MAP_TOUR] LOAD level=2 nativeTerrain=true',
  '[MAP_TOUR] AI_OBSERVE_BEGIN level=2 reason=LEVEL_ENTRY firstTick=10 requiredTicks=200 playerAttacks=0',
  '[MAP_TOUR] AI_OBSERVE_END level=2 reason=LEVEL_ENTRY firstTick=10 lastTick=210 observedTicks=200 playerAttacks=0 correctness=NOT_ASSERTED')
$summary = @(
  '[AI_TOUR] SUMMARY run=COMPLETED coverage=OBSERVED_RUNTIME_ONLY oracle=NONE correctness=NOT_ASSERTED warnings=0 errors=0 fallbackRecords=0',
  '[AI_TOUR] MONSTER monster=zombie1 ai=Zombie initRecords=1 moveRecords=1 decisionRecords=0 skillCastRecords=0 meleeDamageRecords=0 nativeCombatant=true evidence=BEHAVIOR_OBSERVED')
Assert-AiTourEvidence -Lines $valid -Summary $summary
$cases = @(
  @{ lines = @($valid -replace 'lastTick=210', 'lastTick=209'); summary = $summary },
  @{ lines = @($valid -replace 'observedTicks=200', 'observedTicks=199'); summary = $summary },
  @{ lines = @($valid -replace 'firstTick=10 lastTick', 'firstTick=11 lastTick'); summary = $summary },
  @{ lines = @($valid | Where-Object { $_ -notmatch 'AI_OBSERVE_END' }); summary = $summary },
  @{ lines = @($valid -replace 'playerAttacks=0', 'playerAttacks=1'); summary = $summary },
  @{ lines = @($valid -replace 'AI_RUNTIME_OBSERVATION', 'COMPLETE_ACT_RANGE'); summary = $summary },
  @{ lines = $valid; summary = @($summary -replace 'run=COMPLETED', 'run=FAILED_OR_INTERRUPTED') },
  @{ lines = $valid; summary = @($summary -replace 'errors=0', 'errors=1') },
  @{ lines = $valid; summary = @($summary -replace 'fallbackRecords=0', 'fallbackRecords=1') },
  @{ lines = $valid; summary = @($summary -replace 'BEHAVIOR_OBSERVED', 'INIT_ONLY_NO_BEHAVIOR_EVIDENCE') },
  @{ lines = $valid; summary = @($summary -replace 'nativeCombatant=true', 'nativeCombatant=false') })
foreach ($case in $cases) {
  $rejected = $false
  try { Assert-AiTourEvidence -Lines $case.lines -Summary $case.summary } catch { $rejected = $true }
  if (-not $rejected) { throw 'False PASS: defective AI evidence accepted' }
}
Write-Output "PASS AI audit: valid window accepted; $($cases.Count) defective evidence cases rejected"
