[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)] [string] $AuditLog,
  [Parameter(Mandatory = $true)] [string] $MaFile,
  [int] $Layer = 0
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $AuditLog -PathType Leaf)) { throw "Audit log not found: $AuditLog" }
if (-not (Test-Path -LiteralPath $MaFile -PathType Leaf)) { throw "MA file not found: $MaFile" }

$samplePattern = 'position (-?\d+),(-?\d+) ma=(-?\d+),(-?\d+) cells=\[([^\]]+)\]'
$matches = [System.Collections.Generic.List[object]]::new()
foreach ($line in (Get-Content -LiteralPath $AuditLog)) {
  foreach ($m in [regex]::Matches($line, $samplePattern)) {
    $matches.Add([pscustomobject]@{
      WorldX = [int]$m.Groups[1].Value; WorldY = [int]$m.Groups[2].Value
      MaX = [int]$m.Groups[3].Value; MaY = [int]$m.Groups[4].Value
      Cells = @($m.Groups[5].Value -split ',' | ForEach-Object { [int]$_.Trim() })
    })
  }
}
if ($matches.Count -eq 0) { throw 'No conflict samples found in audit log' }

$fs = [IO.File]::OpenRead((Resolve-Path -LiteralPath $MaFile))
$br = [IO.BinaryReader]::new($fs)
$records = [System.Collections.Generic.HashSet[string]]::new()
try {
  $br.BaseStream.Position = $Layer * 4
  $offset = $br.ReadInt32()
  if ($offset -eq 0) {
    Write-Output "layer=$Layer status=MISSING file=$MaFile"
    exit 2
  }
  $br.BaseStream.Position = $offset + 16
  $sizes = @($br.ReadInt32(), $br.ReadInt32(), $br.ReadInt32(), $br.ReadInt32())
  $br.BaseStream.Position = $offset + 32
  foreach ($size in $sizes) {
    for ($cursor = 0; $cursor -lt $size; $cursor += 6) {
      $cell = $br.ReadUInt16(); $x = $br.ReadInt16(); $y = $br.ReadInt16()
      [void]$records.Add("$cell,$x,$y")
    }
  }
} finally {
  $br.Dispose(); $fs.Dispose()
}

Write-Output "layer=$Layer status=FOUND file=$MaFile records=$($records.Count)"
$found = 0
foreach ($candidate in $matches) {
  $hits = @($candidate.Cells | Where-Object { $records.Contains("$_,$($candidate.MaX),$($candidate.MaY)") })
  if ($hits.Count -gt 0) { $found++ }
  Write-Output ("world={0},{1} ma={2},{3} candidates=[{4}] fixtureHits=[{5}]" -f
    $candidate.WorldX, $candidate.WorldY, $candidate.MaX, $candidate.MaY,
    ($candidate.Cells -join ','), ($hits -join ','))
}
Write-Output "conflicts=$($matches.Count) conflictsWithFixtureCell=$found"
if ($found -eq 0) { exit 2 }
exit 0
