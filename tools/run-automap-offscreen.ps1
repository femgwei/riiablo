[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)] [string] $D2Home,
  [Parameter(Mandatory = $true)] [string] $SavesDir,
  [int] $Level = 1,
  [string] $Character = '',
  [string] $LogPath = 'build/automap-offscreen.log',
  [switch] $RequireTransition
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot
$logFile = Join-Path $repoRoot $LogPath
$logParent = Split-Path -Parent $logFile
New-Item -ItemType Directory -Force -Path $logParent | Out-Null
if (Test-Path -LiteralPath $logFile) { Remove-Item -LiteralPath $logFile }

$gradleArgs = @(
  ':desktop:offscreenAutomapDc6', "-Pd2Home=$D2Home", "-PsavesDir=$SavesDir",
  "-PoffscreenLevel=$Level", '-PautomapMode=3', '--no-daemon'
)
if ($Character) { $gradleArgs += "-PoffscreenCharacter=$Character" }
& "$repoRoot\gradlew.bat" @gradleArgs *>&1 | Tee-Object -FilePath $logFile
if ($LASTEXITCODE -ne 0) { throw "Offscreen Automap client failed with exit code $LASTEXITCODE" }

powershell -NoProfile -ExecutionPolicy Bypass `
  -File "$repoRoot\tools\check-automap-log.ps1" -Path $logFile
if ($LASTEXITCODE -ne 0) { throw 'Automap log validation failed' }

if ($RequireTransition) {
  $ids = @(Select-String -Path $logFile -Pattern '\[AUTOMAP_LEVEL\].*levelId=(-?\d+)' |
    ForEach-Object { [regex]::Match($_.Line, 'levelId=(-?\d+)').Groups[1].Value } |
    Select-Object -Unique)
  if ($ids.Count -lt 2) {
    throw "Automap transition required, but only $($ids.Count) distinct levelId was observed"
  }
}

Write-Host "Automap offscreen run passed: $logFile" -ForegroundColor Green
