[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

& "$repoRoot\gradlew.bat" :core:test `
  --tests 'com.riiablo.engine.client.automap.AutomapWindowIntegrationTest' `
  -PvisualTests --no-daemon
if ($LASTEXITCODE -ne 0) {
  throw "Automap window smoke test failed with exit code $LASTEXITCODE"
}

Write-Host 'Automap window smoke test passed (320x240 LWJGL3, 3 frames).' -ForegroundColor Green
