$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'map-acceptance-audit.ps1')
$valid = @('[MAP_TOUR] BEGIN seed=1 contract=2 planned=132')
foreach ($level in 1..132) {
  $valid += "[MAP_TOUR] LOAD level=$level nativeTerrain=true"
  $valid += "[MAP_TOUR] WALK_PASS sequence=$level mode=LOCAL_WALK level=$level final=(1,1)"
}
foreach ($edge in @('1 to=2', '40 to=41', '75 to=76', '103 to=104', '109 to=110')) {
  $valid += "[MAP_TOUR] TRANSITION_PASS from=$edge mode=BOUNDARY_WALK warpRequests=0 movement=PRODUCTION_TCP"
}
$valid += '[MAP_TOUR] SUMMARY planned=132 visited=132 failures=0 result=PASS'
Assert-MapTourEvidence $valid
foreach ($invalid in @(
    @($valid -replace 'mode=BOUNDARY_WALK', 'mode=WARP_WALK'),
    @($valid -replace 'contract=2', 'contract=1'),
    @($valid -replace 'warpRequests=0', 'warpRequests=1'),
    @($valid | Where-Object { $_ -notmatch 'LOAD level=132 ' }),
    @($valid + '[MAP_TOUR] FAIL level=109 reason=bad_seam'))) {
  $rejected = $false
  try { Assert-MapTourEvidence $invalid } catch { $rejected = $true }
  if (-not $rejected) { throw 'False PASS: independent evidence audit accepted a defective fixture' }
}
$issues = @(Get-MapLogIssues @(
    '[D2Log][WARNING] sub_6FD84780: Invalid style 0, expected 48 or 49',
    'WARN  [AI] [AI_FALLBACK] className=Catapult',
    'com.badlogic.gdx.utils.GdxRuntimeException: #iterator() cannot be used nested.',
    '[Map] D2MOO apply: failedResolve=3',
    '[Map] WARNING: missing resource',
    '[Map] failedFloor=1',
    '[D2Log][WARNING] ACT1_D2MOO_LEVELDEF skip id=0: unsupported DrlgType=0',
    'DEBUG [Logger] saved as "WARN"'))
if ($issues.Count -ne 6) { throw "Incorrect warning/error classification: $($issues.Count)" }
$overflow = @(Get-MapLogIssues @(
    '[D2Log][WARNING] Memory pool not provided; using Java fallback allocation (object)',
    '[D2Log][WARNING] Memory pool not provided; using Java fallback allocation (object)'))
if ($overflow.Count -ne 1) { throw 'Expected-warning count budget must be enforced' }
Write-Output 'PASS acceptance-audit: valid evidence accepted; teleport, stale contract, coverage gaps and unreviewed diagnostics rejected'
