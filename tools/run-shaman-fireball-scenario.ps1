param(
  [switch]$Logs
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

# This is a headless ECS test and never starts DesktopLauncher or GameScreen.
# It uses the configured Diablo II installation data (normally D2_HOME).
$gradleArgs = @(
  ':core:test',
  '--tests',
  '*ShamanFireballAuditTest.actioneerKeyframeProjectileDamageAndDeathScenario',
  '--no-daemon'
)
if ($Logs) {
  $gradleArgs += '-PcombatLogs'
}

& "$repoRoot\gradlew.bat" @gradleArgs
if ($LASTEXITCODE -ne 0) {
  throw "Shaman fireball scenario failed with exit code $LASTEXITCODE"
}

Write-Host 'Shaman fireball full-chain scenario passed (headless, no window).' -ForegroundColor Green
