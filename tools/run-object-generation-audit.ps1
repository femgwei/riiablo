[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)] [string] $D2Home,
  [Parameter(Mandatory = $true)] [string] $SavesDir,
  [string] $Character = '',
  [string] $Output = 'build/visual-tests/object-audit'
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$gradleArgs = @(
  ':desktop:offscreenObjectAudit', "-Pd2Home=$D2Home", "-PsavesDir=$SavesDir",
  "-PvisualOutput=$Output", '--no-daemon'
)
if ($Character) { $gradleArgs += "-PoffscreenCharacter=$Character" }

$ErrorActionPreference = 'Continue'
& "$repoRoot\gradlew.bat" @gradleArgs
$exitCode = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
if ($exitCode -ne 0) {
  throw "Object-generation audit failed with exit code $exitCode"
}

$report = Join-Path "$repoRoot\desktop" $Output
$manifest = Join-Path $report 'rogue-encampment-manifest.txt'
$csv = Join-Path $report 'act1-town-bloodmoor-objects.csv'
if (!(Test-Path -LiteralPath $manifest) -or !(Test-Path -LiteralPath $csv)) {
  throw "Object-generation audit did not produce its manifest and CSV under $report"
}

Get-Content -LiteralPath $manifest | Where-Object { $_ -like 'objectAudit*' }
Write-Host "Object-generation audit passed: $csv" -ForegroundColor Green
