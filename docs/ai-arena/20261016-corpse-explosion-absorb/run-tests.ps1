param([string]$Pass='first',[string]$SourceRoot='build/corpse-explosion-absorb-source')
$ErrorActionPreference='Stop'
if((Test-Path "$PSScriptRoot/$Pass.log")-or(Test-Path "$PSScriptRoot/$Pass")){throw 'Evidence exists'}
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
& ./gradlew.bat -p $SourceRoot --project-cache-dir build/corpse-explosion-absorb-cache `
  -I "$PSScriptRoot/isolated.init.gradle" `
  :core:cleanTest :server:d2gs:cleanTest :core:test `
  --tests '*NecromancerExplosionIntegrationTest' --tests '*NativeNecromancerExplosionDataTest' --tests '*DiabCold*Test' --tests '*SorceressTelekinesisIntegrationTest' --tests '*TelekinesisGoldenDamageTest' --tests '*SorceressStaticFieldIntegrationTest' --tests '*StaticFieldGoldenDamageTest' --tests '*SorceressFireAreaIntegrationTest' --tests '*FireWallGoldenDamageTest' --tests '*BlazeGoldenDamageTest' --tests '*DruidFirestormIntegrationTest' --tests '*NovaFixedPointDamageTest' --tests '*CombatSystemTest' `
  --tests '*BladeFuryBloodManaIntegrationTest' --tests '*BloodManaIntegrationTest' `
  --tests '*AssassinSkillSpecializationTest' --tests '*Succubus*Test' --tests '*NecromancerCurse*Test' `
  --tests '*SorceressNovaIntegrationTest' --tests '*Aura*Test' --tests '*ServerSkillSystemTest' `
  :server:d2gs:test --tests '*MonsterAiArenaTest' --console=plain --max-workers=1 *> "$PSScriptRoot/$Pass.log"
$testExit=$LASTEXITCODE
New-Item -ItemType Directory "$PSScriptRoot/$Pass" | Out-Null
Copy-Item "$SourceRoot/build/corpse-explosion-absorb-isolated/core/test-results/test/TEST-*.xml" "$PSScriptRoot/$Pass/"
Copy-Item "$SourceRoot/build/corpse-explosion-absorb-isolated/server_d2gs/test-results/test/TEST-*.xml" "$PSScriptRoot/$Pass/" -ErrorAction SilentlyContinue
if($testExit -ne 0){throw 'Joint suite failed'}
