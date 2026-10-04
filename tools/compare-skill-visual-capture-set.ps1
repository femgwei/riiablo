param(
  [string] $PlanPath = (Join-Path $PSScriptRoot '..\docs\dark-magic-visual-capture-plan.tsv'),
  [string] $CaptureRoot = (Join-Path (Get-Location) 'captures\dark-magic'),
  [string] $SummaryPath = (Join-Path (Get-Location) 'captures\dark-magic\reports\summary.tsv'),
  [string] $CompareScript = (Join-Path $PSScriptRoot 'compare-skill-visual-frames.ps1'),
  [int[]] $ExpectedSkillIds = @(251, 256, 257, 261, 262, 266, 271, 272, 276, 277),
  [switch] $RequireMetadata,
  [switch] $RequireComparableCaptures
)

$ErrorActionPreference = 'Stop'
foreach ($path in @($PlanPath, $CompareScript)) {
  if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
    throw "Required file not found: $path"
  }
}

$plan = @(Import-Csv -LiteralPath $PlanPath -Delimiter "`t")
if ($plan.Count -eq 0) {
  throw "Capture plan is empty: $PlanPath"
}

$requiredColumns = @(
  'skill_id', 'skill_name', 'slug', 'priority', 'frame_rate', 'map_seed',
  'skill_level', 'original_dir', 'riiablo_dir', 'scenarios')
foreach ($column in $requiredColumns) {
  if ($column -notin $plan[0].PSObject.Properties.Name) {
    throw "Capture plan is missing column '$column': $PlanPath"
  }
}

function Test-CaptureMetadata {
  param(
    [string] $Directory,
    [string] $ExpectedSource,
    [int] $ExpectedSkillId,
    [int] $ExpectedSkillLevel,
    [int] $ExpectedMapSeed,
    [double] $ExpectedFrameRate
  )

  $metadataPath = Join-Path $Directory 'capture.json'
  if (-not (Test-Path -LiteralPath $metadataPath -PathType Leaf)) {
    return [pscustomobject]@{
      Valid = $false
      Status = 'missing'
      Message = "Missing metadata: $metadataPath"
    }
  }
  try {
    $metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
    $required = @('source', 'mpq', 'skill_id', 'skill_level', 'map_seed', 'frame_rate', 'resolution', 'scale')
    foreach ($field in $required) {
      if ($field -notin $metadata.PSObject.Properties.Name) {
        return [pscustomobject]@{
          Valid = $false
          Status = 'invalid'
          Message = "Metadata missing '$field': $metadataPath"
        }
      }
    }
    if ([string]$metadata.source -ne $ExpectedSource) {
      return [pscustomobject]@{ Valid = $false; Status = 'mismatch'; Message = "source mismatch in $metadataPath" }
    }
    if ([string]$metadata.mpq -ne '1.10f') {
      return [pscustomobject]@{ Valid = $false; Status = 'mismatch'; Message = "mpq must be 1.10f in $metadataPath" }
    }
    if ([int]$metadata.skill_id -ne $ExpectedSkillId) {
      return [pscustomobject]@{ Valid = $false; Status = 'mismatch'; Message = "skill_id mismatch in $metadataPath" }
    }
    if ([int]$metadata.skill_level -ne $ExpectedSkillLevel) {
      return [pscustomobject]@{ Valid = $false; Status = 'mismatch'; Message = "skill_level mismatch in $metadataPath" }
    }
    if ([int]$metadata.map_seed -ne $ExpectedMapSeed) {
      return [pscustomobject]@{ Valid = $false; Status = 'mismatch'; Message = "map_seed mismatch in $metadataPath" }
    }
    if ([Math]::Abs([double]$metadata.frame_rate - $ExpectedFrameRate) -gt 0.001) {
      return [pscustomobject]@{ Valid = $false; Status = 'mismatch'; Message = "frame_rate mismatch in $metadataPath" }
    }
    if ([string]::IsNullOrWhiteSpace([string]$metadata.resolution) -or [double]$metadata.scale -le 0) {
      return [pscustomobject]@{ Valid = $false; Status = 'invalid'; Message = "resolution/scale invalid in $metadataPath" }
    }
    return [pscustomobject]@{ Valid = $true; Status = 'valid'; Message = '' }
  } catch {
    return [pscustomobject]@{ Valid = $false; Status = 'invalid'; Message = $_.Exception.Message }
  }
}

$ids = @{}
$slugs = @{}
$summary = [System.Collections.Generic.List[object]]::new()
foreach ($entry in ($plan | Sort-Object { [int]$_.priority })) {
  $skillId = [int]$entry.skill_id
  $slug = [string]$entry.slug
  $frameRate = [double]$entry.frame_rate
  $mapSeed = [int]$entry.map_seed
  $skillLevel = [int]$entry.skill_level
  if ($ids.ContainsKey($skillId)) { throw "Duplicate skill_id in capture plan: $skillId" }
  if ($slugs.ContainsKey($slug)) { throw "Duplicate slug in capture plan: $slug" }
  if ($frameRate -le 0) { throw "Invalid frame_rate for skill_id=$skillId" }
  if ($mapSeed -lt 0) { throw "Invalid map_seed for skill_id=$skillId" }
  if ($skillLevel -le 0) { throw "Invalid skill_level for skill_id=$skillId" }
  $ids[$skillId] = $true
  $slugs[$slug] = $true

  $originalPath = Join-Path $CaptureRoot ([string]$entry.original_dir)
  $riiabloPath = Join-Path $CaptureRoot ([string]$entry.riiablo_dir)
  $reportPath = Join-Path $CaptureRoot ("reports\$slug.tsv")
  $originalFrames = if (Test-Path -LiteralPath $originalPath -PathType Container) {
    @(Get-ChildItem -LiteralPath $originalPath -File -Filter '*.png').Count
  } else { 0 }
  $riiabloFrames = if (Test-Path -LiteralPath $riiabloPath -PathType Container) {
    @(Get-ChildItem -LiteralPath $riiabloPath -File -Filter '*.png').Count
  } else { 0 }

  if ($RequireMetadata) {
    $originalMetadata = [pscustomobject]@{ Valid = $false; Status = 'missing'; Message = "Missing capture directory: $originalPath" }
    $riiabloMetadata = [pscustomobject]@{ Valid = $false; Status = 'missing'; Message = "Missing capture directory: $riiabloPath" }
    if (Test-Path -LiteralPath $originalPath -PathType Container) {
      $originalMetadata = Test-CaptureMetadata $originalPath 'original-1.10f' $skillId $skillLevel $mapSeed $frameRate
    }
    if (Test-Path -LiteralPath $riiabloPath -PathType Container) {
      $riiabloMetadata = Test-CaptureMetadata $riiabloPath 'riiablo' $skillId $skillLevel $mapSeed $frameRate
    }
  } else {
    $originalMetadata = [pscustomobject]@{ Valid = $true; Status = 'not-required'; Message = '' }
    $riiabloMetadata = [pscustomobject]@{ Valid = $true; Status = 'not-required'; Message = '' }
  }

  $status = 'capture-pending'
  $paired = 0
  $missing = 0
  $dimensionMismatch = 0
  $different = 0
  $note = ''
  $metadataStatus = if (-not $RequireMetadata) {
    'not-required'
  } elseif (-not $originalMetadata.Valid -or -not $riiabloMetadata.Valid) {
    if ($originalMetadata.Status -eq 'missing' -or $riiabloMetadata.Status -eq 'missing') { 'missing' } else { 'invalid-or-mismatch' }
  } else { 'valid' }
  $metadataMessages = @($originalMetadata.Message, $riiabloMetadata.Message) |
    Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) }
  if ($RequireMetadata -and $originalFrames -gt 0 -and $riiabloFrames -gt 0 -and $metadataStatus -ne 'valid') {
    $status = 'metadata-pending'
    $note = ($metadataMessages -join ' ')
  } elseif ($originalFrames -gt 0 -and $riiabloFrames -gt 0) {
    $reportParent = Split-Path -Parent $reportPath
    New-Item -ItemType Directory -Force -Path $reportParent | Out-Null
    try {
      $compareOutput = @(& $CompareScript `
        -OriginalDir $originalPath `
        -RiiabloDir $riiabloPath `
        -OutputPath $reportPath `
        -FrameRate $frameRate)
      $report = @(Import-Csv -LiteralPath $reportPath -Delimiter "`t")
      $missing = @($report | Where-Object { $_.status -eq 'missing-frame' }).Count
      $dimensionMismatch = @($report | Where-Object { $_.status -eq 'dimension-mismatch' }).Count
      $different = @($report | Where-Object { $_.status -eq 'different' }).Count
      $paired = @($report | Where-Object { $_.status -ne 'missing-frame' }).Count
      if ($missing -eq 0 -and $dimensionMismatch -eq 0) {
        # Pixel comparison can make the captures reviewable, but only a human
        # can decide whether keyframe, pulse, collision, and expiry semantics pass.
        $status = 'awaiting-human-review'
      } else {
        $status = 'capture-mismatch'
      }
      $note = ($compareOutput -join ' ')
    } catch {
      $status = 'comparison-failed'
      $note = $_.Exception.Message
    }
  } elseif ($originalFrames -eq 0 -and $riiabloFrames -eq 0) {
    $note = 'Both capture sets are missing.'
  } elseif ($originalFrames -eq 0) {
    $note = 'Original 1.10f capture set is missing.'
  } else {
    $note = 'riiablo capture set is missing.'
  }

  $summary.Add([pscustomobject]@{
    priority = [int]$entry.priority
    skill_id = $skillId
    skill_name = [string]$entry.skill_name
    slug = $slug
    original_frames = $originalFrames
    riiablo_frames = $riiabloFrames
    paired_frames = $paired
    missing_frames = $missing
    dimension_mismatch = $dimensionMismatch
    different_frames = $different
    metadata_status = $metadataStatus
    status = $status
    report = if (Test-Path -LiteralPath $reportPath -PathType Leaf) { $reportPath } else { '' }
    note = $note
  })
}

$expectedKey = @($ExpectedSkillIds | Sort-Object) -join ','
$actualKey = @($ids.Keys | ForEach-Object { [int]$_ } | Sort-Object) -join ','
if ($actualKey -ne $expectedKey) {
  throw "Capture plan exact-ID set mismatch: expected=$expectedKey actual=$actualKey"
}

$summaryParent = Split-Path -Parent $SummaryPath
if ($summaryParent) {
  New-Item -ItemType Directory -Force -Path $summaryParent | Out-Null
}
$summary | Export-Csv -LiteralPath $SummaryPath -Delimiter "`t" -NoTypeInformation -Encoding UTF8

$pending = @($summary | Where-Object { $_.status -eq 'capture-pending' }).Count
$metadataPending = @($summary | Where-Object { $_.status -eq 'metadata-pending' }).Count
$mismatch = @($summary | Where-Object { $_.status -eq 'capture-mismatch' }).Count
$failed = @($summary | Where-Object { $_.status -eq 'comparison-failed' }).Count
$reviewable = @($summary | Where-Object { $_.status -eq 'awaiting-human-review' }).Count
Write-Output "visual capture set skills=$($summary.Count) reviewable=$reviewable pending=$pending metadata_pending=$metadataPending mismatch=$mismatch failed=$failed summary=$SummaryPath"
Write-Output 'Reviewable means the frame sets can be compared; it does not mean visual semantics passed.'

if ($RequireComparableCaptures -and ($pending -gt 0 -or $metadataPending -gt 0 -or $mismatch -gt 0 -or $failed -gt 0)) {
  throw "Visual captures are incomplete: pending=$pending metadata_pending=$metadataPending mismatch=$mismatch failed=$failed"
}
