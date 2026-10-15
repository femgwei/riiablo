param([string]$Output='server/d2gs/build/monster-ai-arena/20261015-sentry-final')
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
& ./gradlew.bat -I docs/ai-arena/20261015-inferno-sentry-seed1/regression.init.gradle `
 :core:sentryRegression --tests '*InfernoSentrySequenceTest' --tests '*Assassin*Test' `
 --tests '*MonInferno*Test' --tests '*AnimationStepperTest' --tests '*NativeAssassin*Test' `
 :server:d2gs:sentryRegression --tests '*MonsterAiArenaTest' --tests '*MapTourAiLogTest' --tests '*MapTourProbeTest' `
 :server:d2gs:monsterAiArena "-ParenaOutput=$Output" --console=plain --max-workers=1
exit $LASTEXITCODE
