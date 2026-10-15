param([string]$Output='server/d2gs/build/monster-ai-arena/20261015-horror-refined-final')
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
$verification=Get-Content docs/ai-arena/20261015-diab-wall-seed1/verification.md -Raw
$patterns=[regex]::Matches($verification,"--tests '([^']+)'") | ForEach-Object {$_.Groups[1].Value}
$taskArgs=@('-I','docs/ai-arena/20261015-horror-seed1/horror-regression.init.gradle',':core:horrorRegression')
foreach($pattern in $patterns){if($pattern -notin @('*MonsterAiArenaTest','*MapTourAiLogTest','*MapTourProbeTest')){$taskArgs+=@('--tests',$pattern)}}
foreach($pattern in @('*DiabRun*Test','*DiabPrison*Test','*Horror*Test','*PathfinderTest','*PathfinderRoomBoundaryTest','*PlayerGroundPathTest','*VelocityAdderTest','*VelocityModeChangerTest','*PlayerVelocityUnitsTest')){$taskArgs+=@('--tests',$pattern)}
$taskArgs+=@(':server:d2gs:horrorRegression','--tests','*MonsterAiArenaTest','--tests','*MapTourAiLogTest','--tests','*MapTourProbeTest',':server:d2gs:monsterAiArena',"-ParenaOutput=$Output",'--max-workers=1','--console=plain')
& ./gradlew.bat @taskArgs
exit $LASTEXITCODE
