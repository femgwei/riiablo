param(
  [Parameter(Mandatory = $true)]
  [string] $OriginalDir,
  [Parameter(Mandatory = $true)]
  [string] $RiiabloDir,
  [string] $OutputPath = (Join-Path (Get-Location) 'skill-visual-diff.tsv'),
  [int] $Threshold = 8,
  [double] $FrameRate = 25
)

$ErrorActionPreference = 'Stop'
if ($Threshold -lt 0 -or $Threshold -gt 255) {
  throw 'Threshold must be between 0 and 255'
}
if ($FrameRate -le 0) {
  throw 'FrameRate must be greater than zero'
}
foreach ($dir in @($OriginalDir, $RiiabloDir)) {
  if (-not (Test-Path -LiteralPath $dir -PathType Container)) {
    throw "Capture directory not found: $dir"
  }
}

Add-Type -AssemblyName System.Drawing

function Get-FrameFiles([string] $dir) {
  $files = @(Get-ChildItem -LiteralPath $dir -File -Filter '*.png' |
    Sort-Object Name)
  $items = for ($index = 0; $index -lt $files.Count; $index++) {
    $file = $files[$index]
    # Prefer an explicit frame/fN token. This avoids pairing frame-10 with
    # frame-2 by lexicographic order when capture tools name files differently.
    $match = [regex]::Match(
      $file.BaseName,
      '(?i)(?:^|[._-])(?:frame|f)[._-]?(\d+)(?:$|[._-])')
    if ($match.Success) {
      $frameKey = [int64]$match.Groups[1].Value
      $hasExplicitFrame = $true
    } else {
      $frameKey = "index:$index"
      $hasExplicitFrame = $false
    }
    [pscustomobject]@{
      File = $file
      Name = $file.Name
      FrameKey = "$frameKey"
      HasExplicitFrame = $hasExplicitFrame
      Index = $index
    }
  }
  @($items)
}

function Get-ChangedPixels([System.Drawing.Bitmap] $left, [System.Drawing.Bitmap] $right, [int] $threshold) {
  $changed = 0L
  $total = [int64]$left.Width * [int64]$left.Height
  for ($y = 0; $y -lt $left.Height; $y++) {
    for ($x = 0; $x -lt $left.Width; $x++) {
      $a = $left.GetPixel($x, $y)
      $b = $right.GetPixel($x, $y)
      if ([Math]::Abs($a.R - $b.R) -gt $threshold -or
          [Math]::Abs($a.G - $b.G) -gt $threshold -or
          [Math]::Abs($a.B - $b.B) -gt $threshold -or
          [Math]::Abs($a.A - $b.A) -gt $threshold) {
        $changed++
      }
    }
  }
  [pscustomobject]@{
    changed_pixels = $changed
    total_pixels = $total
    changed_ratio = if ($total -eq 0) { 0.0 } else { [double]$changed / $total }
  }
}

$original = @(Get-FrameFiles $OriginalDir)
$riiablo = @(Get-FrameFiles $RiiabloDir)
$originalByKey = @{}
$riiabloByKey = @{}
foreach ($item in $original) {
  if ($originalByKey.ContainsKey($item.FrameKey)) {
    throw "Duplicate frame key '$($item.FrameKey)' in original capture directory: $OriginalDir"
  }
  $originalByKey[$item.FrameKey] = $item
}
foreach ($item in $riiablo) {
  if ($riiabloByKey.ContainsKey($item.FrameKey)) {
    throw "Duplicate frame key '$($item.FrameKey)' in riiablo capture directory: $RiiabloDir"
  }
  $riiabloByKey[$item.FrameKey] = $item
}

# Preserve numeric frame order, then append fallback index keys. The fallback
# keeps old captures usable while making explicit frame-number pairing the norm.
$keys = @($original.FrameKey) + @($riiablo.FrameKey) |
  Sort-Object -Unique -Property @{
    Expression = {
      $number = 0L
      if ([int64]::TryParse($_, [ref]$number)) { "0:{0:D20}" -f $number }
      else { "1:{0}" -f $_ }
    }
  }
$rows = [System.Collections.Generic.List[object]]::new()
$dimensionSet = [System.Collections.Generic.HashSet[string]]::new()

foreach ($key in $keys) {
  $left = if ($originalByKey.ContainsKey($key)) { $originalByKey[$key] } else { $null }
  $right = if ($riiabloByKey.ContainsKey($key)) { $riiabloByKey[$key] } else { $null }
  if ($null -eq $left -or $null -eq $right) {
    $rows.Add([pscustomobject]@{
      frame_key = $key
      original_index = if ($null -eq $left) { '' } else { $left.Index }
      riiablo_index = if ($null -eq $right) { '' } else { $right.Index }
      original = if ($null -eq $left) { '' } else { $left.Name }
      riiablo = if ($null -eq $right) { '' } else { $right.Name }
      width = ''
      height = ''
      changed_pixels = ''
      total_pixels = ''
      changed_ratio = ''
      status = 'missing-frame'
    })
    continue
  }

  $bitmapLeft = [System.Drawing.Bitmap]::new($left.File.FullName)
  $bitmapRight = [System.Drawing.Bitmap]::new($right.File.FullName)
  try {
    $dimensionSet.Add("$($bitmapLeft.Width)x$($bitmapLeft.Height)") | Out-Null
    $dimensionSet.Add("$($bitmapRight.Width)x$($bitmapRight.Height)") | Out-Null
    if ($bitmapLeft.Width -ne $bitmapRight.Width -or $bitmapLeft.Height -ne $bitmapRight.Height) {
      $rows.Add([pscustomobject]@{
        frame_key = $key
        original_index = $left.Index
        riiablo_index = $right.Index
        original = $left.Name
        riiablo = $right.Name
        width = "$($bitmapLeft.Width)x$($bitmapLeft.Height) != $($bitmapRight.Width)x$($bitmapRight.Height)"
        height = ''
        changed_pixels = ''
        total_pixels = ''
        changed_ratio = ''
        status = 'dimension-mismatch'
      })
      continue
    }
    $diff = Get-ChangedPixels $bitmapLeft $bitmapRight $Threshold
    $rows.Add([pscustomobject]@{
      frame_key = $key
      original_index = $left.Index
      riiablo_index = $right.Index
      original = $left.Name
      riiablo = $right.Name
      width = $bitmapLeft.Width
      height = $bitmapLeft.Height
      changed_pixels = $diff.changed_pixels
      total_pixels = $diff.total_pixels
      changed_ratio = ('{0:F6}' -f $diff.changed_ratio)
      status = if ($diff.changed_pixels -eq 0) { 'identical' } else { 'different' }
    })
  } finally {
    $bitmapLeft.Dispose()
    $bitmapRight.Dispose()
  }
}

$rows | Export-Csv -LiteralPath $OutputPath -Delimiter "`t" -NoTypeInformation -Encoding UTF8
$different = @($rows | Where-Object { $_.status -eq 'different' }).Count
$missing = @($rows | Where-Object { $_.status -eq 'missing-frame' }).Count
$dimension = @($rows | Where-Object { $_.status -eq 'dimension-mismatch' }).Count
$paired = @($rows | Where-Object { $_.status -ne 'missing-frame' }).Count
$originalDuration = $original.Count / $FrameRate
$riiabloDuration = $riiablo.Count / $FrameRate
$dimensions = if ($dimensionSet.Count -eq 0) { 'unknown' } else { (@($dimensionSet) -join ',') }
Write-Output "visual frame comparison original=$($original.Count) riiablo=$($riiablo.Count) paired=$paired different=$different missing=$missing dimension_mismatch=$dimension frame_rate=$FrameRate original_duration_seconds=$('{0:F3}' -f $originalDuration) riiablo_duration_seconds=$('{0:F3}' -f $riiabloDuration) dimensions=$dimensions output=$OutputPath"
Write-Output 'This report is assistive evidence only; it does not replace frame-by-frame semantic review.'
