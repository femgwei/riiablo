[CmdletBinding()]
param(
  [Parameter(Mandatory = $true, Position = 0)]
  [string] $Path,
  [switch] $Strict
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
  throw "Log file not found: $Path"
}

$lines = Get-Content -LiteralPath $Path
$errors = [System.Collections.Generic.List[string]]::new()
$warnings = [System.Collections.Generic.List[string]]::new()
$options = @()
$renders = @()
$levels = @()
$audits = @()

foreach ($line in $lines) {
  if ($line -match '\[AUTOMAP_OPTIONS\]\s+mode=(\d+)\s+fade=(True|False|true|false)\s+opacity=([0-9.]+)') {
    $opacity = [double]$Matches[3]
    $options += [pscustomobject]@{ Mode = [int]$Matches[1]; Fade = $Matches[2]; Opacity = $opacity }
    if ($opacity -lt 0 -or $opacity -gt 1) {
      $errors.Add("opacity out of range: $opacity")
    }
  }

  if ($line -match '\[AUTOMAP_NATIVE_RENDER\]\s+alpha=([0-9.]+)\s+zones=(.*)$') {
    $alpha = [double]$Matches[1]
    $zoneText = $Matches[2]
    $ids = @([regex]::Matches($zoneText, '(?<!\d)(\d+)\(') | ForEach-Object { [int]$_.Groups[1].Value })
    $renders += [pscustomobject]@{ Alpha = $alpha; ZoneText = $zoneText; Ids = $ids }
    if ($alpha -lt 0 -or $alpha -gt 1) { $errors.Add("native render alpha out of range: $alpha") }
    $duplicates = $ids | Group-Object | Where-Object Count -gt 1
    foreach ($duplicate in $duplicates) {
      $errors.Add("duplicate native layer in one frame: levelId=$($duplicate.Name)")
    }
    if ($ids.Count -eq 0) { $warnings.Add('native render reported no generated layers') }
  }

  if ($line -match '\[AUTOMAP_LEVEL\]\s+levelId=(-?\d+)\s+town=(True|False|true|false)\s+x=(-?\d+)\s+y=(-?\d+)') {
    $levels += [pscustomobject]@{ LevelId = [int]$Matches[1]; Town = $Matches[2]; X = [int]$Matches[3]; Y = [int]$Matches[4] }
  }

  if ($line -match '\[AUTOMAP_CELL_AUDIT\]\s+level=(-?\d+)\s+total=(\d+)\s+within=(\d+)\s+cross=(\d+)\s+positionConflict=(\d+)(?:\s+sameCategoryConflict=(\d+)\s+crossCategoryConflict=(\d+))?') {
    $sameCategoryConflict = if ($Matches[6]) { [int]$Matches[6] } else { [int]$Matches[5] }
    $crossCategoryConflict = if ($Matches[7]) { [int]$Matches[7] } else { 0 }
    $audit = [pscustomobject]@{
      LevelId = [int]$Matches[1]; Total = [int]$Matches[2]; Within = [int]$Matches[3]
      Cross = [int]$Matches[4]; PositionConflict = [int]$Matches[5]
      SameCategoryConflict = $sameCategoryConflict
      CrossCategoryConflict = $crossCategoryConflict
    }
    $audits += $audit
    if ($audit.Within -gt 0 -or $audit.Cross -gt 0 -or $audit.SameCategoryConflict -gt 0) {
      $warnings.Add("cell overlap level=$($audit.LevelId) within=$($audit.Within) cross=$($audit.Cross) sameCategoryConflict=$($audit.SameCategoryConflict)")
    }
  }

  if ($line -match '(?i)(Failed to save native Automap|Failed to load native Automap|Exception|crash|fatal error)') {
    $errors.Add("runtime/save failure: $line")
  }
}

if ($options.Count -eq 0) { $warnings.Add('no [AUTOMAP_OPTIONS] record found') }
if ($renders.Count -eq 0) { $warnings.Add('no [AUTOMAP_NATIVE_RENDER] record found') }

$lastOptions = if ($options.Count) { $options[-1] } else { $null }
$lastRender = if ($renders.Count) { $renders[-1] } else { $null }
Write-Output ("Automap log: {0}" -f (Resolve-Path -LiteralPath $Path))
Write-Output ("  option records: {0}, render records: {1}" -f $options.Count, $renders.Count)
Write-Output ("  level transitions: {0}" -f $levels.Count)
Write-Output ("  cell audits: {0}" -f $audits.Count)
if ($lastOptions) { Write-Output ("  last options: mode={0} fade={1} opacity={2}" -f $lastOptions.Mode, $lastOptions.Fade, $lastOptions.Opacity) }
if ($lastRender) { Write-Output ("  last render: alpha={0} layers={1}" -f $lastRender.Alpha, $lastRender.Ids.Count) }
if ($levels.Count -gt 0) {
  $distinctLevels = @($levels | Select-Object -ExpandProperty LevelId -Unique)
  Write-Output ("  distinct levels: {0}" -f ($distinctLevels -join ','))
}
foreach ($warning in $warnings) { Write-Warning $warning }
foreach ($errorText in $errors) { Write-Error $errorText }

if ($errors.Count -gt 0 -or ($Strict -and $warnings.Count -gt 0)) {
  exit 1
}
exit 0
