param(
  [Parameter(Mandatory = $true)]
  [ValidateSet(251, 256, 257, 261, 262, 266, 271, 272, 276, 277)]
  [int] $SkillId,
  [Parameter(Mandatory = $true)]
  [ValidateSet('original-1.10f', 'riiablo')]
  [string] $Source,
  [int] $MapSeed = 1,
  [int] $SkillLevel = 20,
  [double] $FrameRate = 25,
  [string] $Resolution = '800x600',
  [double] $Scale = 1,
  [string] $PlanPath = '',
  [string] $CaptureRoot = (Join-Path (Get-Location) 'captures\dark-magic'),
  [switch] $Force
)

$ErrorActionPreference = 'Stop'
if ($MapSeed -lt 0) { throw 'MapSeed must be non-negative' }
if ($SkillLevel -le 0) { throw 'SkillLevel must be greater than zero' }
if ($FrameRate -le 0) { throw 'FrameRate must be greater than zero' }
if ([string]::IsNullOrWhiteSpace($Resolution)) { throw 'Resolution must not be empty' }
if ($Scale -le 0) { throw 'Scale must be greater than zero' }
if ([string]::IsNullOrWhiteSpace($PlanPath)) {
  $PlanPath = Join-Path $PSScriptRoot '..\docs\dark-magic-visual-capture-plan.tsv'
}
if (-not (Test-Path -LiteralPath $PlanPath -PathType Leaf)) {
  throw "Capture plan not found: $PlanPath"
}

$plan = @(Import-Csv -LiteralPath $PlanPath -Delimiter "`t")
$entry = $plan | Where-Object { [int]$_.skill_id -eq $SkillId }
if ($null -eq $entry) {
  throw "Skill ID is not in the visual capture plan: $SkillId"
}
if (@($entry).Count -ne 1) {
  throw "Capture plan contains duplicate skill ID: $SkillId"
}

$directoryColumn = if ($Source -eq 'original-1.10f') { 'original_dir' } else { 'riiablo_dir' }
$targetDirectory = Join-Path $CaptureRoot ([string]$entry.$directoryColumn)
$metadataPath = Join-Path $targetDirectory 'capture.json'
if ((Test-Path -LiteralPath $metadataPath -PathType Leaf) -and -not $Force) {
  throw "Metadata already exists; use -Force only when replacing it: $metadataPath"
}

New-Item -ItemType Directory -Force -Path $targetDirectory | Out-Null
$metadata = [ordered]@{
  source = $Source
  mpq = '1.10f'
  skill_id = $SkillId
  skill_level = $SkillLevel
  map_seed = $MapSeed
  frame_rate = $FrameRate
  resolution = $Resolution
  scale = $Scale
  frame_prefix = 'frame-'
  recorded_at = (Get-Date).ToString('o')
  notes = 'Fill in scenario-specific notes after capture; do not change test conditions between sources.'
}
$metadata | ConvertTo-Json | Set-Content -LiteralPath $metadataPath -Encoding UTF8
Write-Output "initialized skill_id=$SkillId source=$Source directory=$targetDirectory metadata=$metadataPath"
