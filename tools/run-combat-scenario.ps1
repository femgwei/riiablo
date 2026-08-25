param(
  [switch]$Logs
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

# The scenario is a JUnit/headless test. It does not start DesktopLauncher,
# GameScreen, LWJGL, or any graphical window.
$gradleArgs = @(
  ':core:test',
  '--tests', 'com.riiablo.engine.server.combat.CombatScenarioTest',
  '--tests', 'com.riiablo.engine.server.combat.CombatEcsEventScenarioTest',
  '--no-daemon'
)
if ($Logs) {
  $gradleArgs += '-PcombatLogs'
}

& "$repoRoot\gradlew.bat" @gradleArgs
if ($LASTEXITCODE -ne 0) {
  throw "Combat scenario failed with exit code $LASTEXITCODE"
}

Write-Host 'Combat scenario passed (headless, no window).' -ForegroundColor Green
