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
$seams = @()
foreach ($act in 1..5) {
  $seams += "[MAP_TOUR] SEAM_MANIFEST_START act=$act source=D2MOO_VIS_WARP_MINUS_ONE"
  $identity = "act=$act from=1 to=2 slot=0"
  $seams += "[MAP_TOUR] SEAM_EXPECTED $identity warp=-1"
  $seams += "[MAP_TOUR] SEAM_STAGE $identity level=1 role=SOURCE_ONLY"
  $seams += "[MAP_TOUR] SEAM_DIRECTION_PASS $identity direction=OUTBOUND walkedFrom=1 walkedTo=2 before=(1,1) after=(2,1) snapshot=(2,1) clientLevel=2 warpRequests=0 movement=PRODUCTION_TCP"
  $seams += "[MAP_TOUR] SEAM_DIRECTION_PASS $identity direction=RETURN walkedFrom=2 walkedTo=1 before=(2,1) after=(1,1) snapshot=(1,1) clientLevel=1 warpRequests=0 movement=PRODUCTION_TCP"
  $seams += "[MAP_TOUR] SEAM_EDGE_PASS $identity directions=2 warpRequests=0 destinationStages=0 movement=PRODUCTION_TCP"
}
$seams += '[MAP_TOUR] SEAM_SUMMARY planned=5 passed=5 failures=0 coverage=ALL_ADJACENT_NATIVE_VIS_MINUS_ONE result=PASS'
$valid += $seams
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
$roundTrip = @('[MAP_TOUR] BEGIN seed=1 contract=2 planned=2',
  '[MAP_TOUR] TRANSITION_PASS from=40 to=41 mode=BOUNDARY_WALK warpRequests=0 movement=PRODUCTION_TCP',
  '[MAP_TOUR] TRANSITION_PASS from=41 to=40 mode=BOUNDARY_WALK warpRequests=0 movement=PRODUCTION_TCP',
  '[MAP_TOUR] TOWN_ROUND_TRIP_PASS town=40 outdoor=41',
  '[MAP_TOUR] SUMMARY planned=2 visited=2 failures=0 result=PASS')
Assert-TownRoundTripEvidence $roundTrip @(40)
foreach ($invalid in @(@($roundTrip | Where-Object { $_ -notmatch 'from=41 ' }),
    @($roundTrip + '[MAP_TOUR] ENTER level=41 mode=JUMP_SEGMENT_START'),
    @($roundTrip -replace 'warpRequests=0', 'warpRequests=1'))) {
  $rejected = $false
  try { Assert-TownRoundTripEvidence $invalid @(40) } catch { $rejected = $true }
  if (-not $rejected) { throw 'False PASS: town return/staging/warp fixture accepted' }
}
Write-Output 'PASS town-round-trip audit: missing return, outdoor staging and Warp are rejected'
Assert-NativeSeamEvidence -Lines $seams
foreach ($invalid in @(@($seams | Where-Object { $_ -notmatch 'direction=RETURN' }),
    @($seams -replace 'level=1 role=SOURCE_ONLY', 'level=2 role=SOURCE_ONLY'),
    @($seams -replace 'clientLevel=2', 'clientLevel=1'),
    @($seams -replace 'warpRequests=0', 'warpRequests=1'),
    @($seams + '[MAP_TOUR] SEAM_EXPECTED act=1 from=2 to=3 slot=1 warp=-1'),
    @($seams | Where-Object { $_ -notmatch 'SEAM_MANIFEST_START act=3 ' }))) {
  $rejected = $false
  try { Assert-NativeSeamEvidence -Lines $invalid } catch { $rejected = $true }
  if (-not $rejected) { throw 'False PASS: missing native edge/return, destination staging, wrong snapshot or Warp accepted' }
}
Write-Output 'PASS all-native-seam audit: expected edges require unique two-direction TCP evidence'
