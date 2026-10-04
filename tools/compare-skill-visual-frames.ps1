param(
  [Parameter(Mandatory = $true)]
  [string] $OriginalDir,
  [Parameter(Mandatory = $true)]
  [string] $RiiabloDir,
  [string] $OutputPath = (Join-Path (Get-Location) 'skill-visual-diff.tsv'),
  [int] $Threshold = 8
)

$ErrorActionPreference = 'Stop'
if ($Threshold -lt 0 -or $Threshold -gt 255) {
  throw 'Threshold must be between 0 and 255'
}
foreach ($dir in @($OriginalDir, $RiiabloDir)) {
  if (-not (Test-Path -LiteralPath $dir -PathType Container)) {
    throw "Capture directory not found: $dir"
  }
}

Add-Type -AssemblyName System.Drawing

function Get-FrameFiles([string] $dir) {
  @(Get-ChildItem -LiteralPath $dir -File -Filter '*.png' |
    Sort-Object Name)
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

$original = Get-FrameFiles $OriginalDir
$riiablo = Get-FrameFiles $RiiabloDir
$count = [Math]::Max($original.Count, $riiablo.Count)
$rows = [System.Collections.Generic.List[object]]::new()

for ($i = 0; $i -lt $count; $i++) {
  $left = if ($i -lt $original.Count) { $original[$i] } else { $null }
  $right = if ($i -lt $riiablo.Count) { $riiablo[$i] } else { $null }
  if ($null -eq $left -or $null -eq $right) {
    $rows.Add([pscustomobject]@{
      frame = $i
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

  $bitmapLeft = [System.Drawing.Bitmap]::new($left.FullName)
  $bitmapRight = [System.Drawing.Bitmap]::new($right.FullName)
  try {
    if ($bitmapLeft.Width -ne $bitmapRight.Width -or $bitmapLeft.Height -ne $bitmapRight.Height) {
      $rows.Add([pscustomobject]@{
        frame = $i
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
      frame = $i
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
Write-Output "visual frame comparison original=$($original.Count) riiablo=$($riiablo.Count) different=$different missing=$missing dimension_mismatch=$dimension output=$OutputPath"
Write-Output 'This report is assistive evidence only; it does not replace frame-by-frame semantic review.'
