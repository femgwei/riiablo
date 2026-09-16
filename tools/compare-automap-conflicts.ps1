[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)] [string] $AuditLog,
  [Parameter(Mandatory = $true)] [string] $MaFile,
  [Parameter(Mandatory = $true)] [string] $CellCsv,
  [int] $Layer = 0,
  [switch] $RequireConflictHit
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $AuditLog -PathType Leaf)) { throw "Audit log not found: $AuditLog" }
if (-not (Test-Path -LiteralPath $MaFile -PathType Leaf)) { throw "MA file not found: $MaFile" }
if (-not (Test-Path -LiteralPath $CellCsv -PathType Leaf)) { throw "Cell CSV not found: $CellCsv" }

$samplePattern = 'position (-?\d+),(-?\d+) ma=(-?\d+),(-?\d+) cells=\[([^\]]+)\]'
$conflicts = [System.Collections.Generic.List[object]]::new()
foreach ($line in (Get-Content -LiteralPath $AuditLog)) {
  foreach ($m in [regex]::Matches($line, $samplePattern)) {
    $conflicts.Add([pscustomobject]@{
      WorldX = [int]$m.Groups[1].Value; WorldY = [int]$m.Groups[2].Value
      MaX = [int]$m.Groups[3].Value; MaY = [int]$m.Groups[4].Value
      Cells = @($m.Groups[5].Value -split ',' | ForEach-Object { [int]$_.Trim() })
    })
  }
}
if ($conflicts.Count -eq 0) { throw 'No conflict samples found in audit log' }

$nativeRecords = [System.Collections.Generic.List[object]]::new()
$nativeRecordKeys = [System.Collections.Generic.HashSet[string]]::new()
$nativeByCategoryCell = @{}
$nativeByPosition = @{}
$fs = [IO.File]::OpenRead((Resolve-Path -LiteralPath $MaFile))
$br = [IO.BinaryReader]::new($fs)
try {
  $br.BaseStream.Position = $Layer * 4
  $offset = $br.ReadInt32()
  if ($offset -eq 0) {
    Write-Output "layer=$Layer status=MISSING file=$MaFile"
    exit 2
  }
  $br.BaseStream.Position = $offset + 16
  $sizes = @($br.ReadInt32(), $br.ReadInt32(), $br.ReadInt32(), $br.ReadInt32())
  $categories = @('floors', 'walls', 'objects', 'extras')
  $br.BaseStream.Position = $offset + 32
  for ($categoryIndex = 0; $categoryIndex -lt $categories.Count; $categoryIndex++) {
    $category = $categories[$categoryIndex]
    $size = $sizes[$categoryIndex]
    if ($size -lt 0 -or $size % 6 -ne 0) { throw "Invalid $category byte size: $size" }
    for ($cursor = 0; $cursor -lt $size; $cursor += 6) {
      $cell = [int]$br.ReadUInt16()
      $x = [int]$br.ReadInt16()
      $y = [int]$br.ReadInt16()
      $record = [pscustomobject]@{ Category = $category; CellNo = $cell; X = $x; Y = $y }
      $nativeRecords.Add($record)
      [void]$nativeRecordKeys.Add("$category,$cell,$x,$y")
      $indexKey = "$category,$cell"
      if (-not $nativeByCategoryCell.ContainsKey($indexKey)) {
        $nativeByCategoryCell[$indexKey] = [System.Collections.Generic.List[object]]::new()
      }
      $nativeByCategoryCell[$indexKey].Add($record)
      $positionKey = "$x,$y"
      if (-not $nativeByPosition.ContainsKey($positionKey)) {
        $nativeByPosition[$positionKey] = [System.Collections.Generic.List[object]]::new()
      }
      $nativeByPosition[$positionKey].Add($record)
    }
  }
} finally {
  $br.Dispose(); $fs.Dispose()
}

$javaCells = @(Import-Csv -LiteralPath $CellCsv | ForEach-Object {
  $category = if ($_.category -eq 'roads') { 'floors' } else { $_.category }
  [pscustomobject]@{
    Category = $category
    CellNo = [int]$_.cellNo
    WorldX = [int]$_.worldX
    WorldY = [int]$_.worldY
    MaX = [int]$_.maX
    MaY = [int]$_.maY
  }
})
if ($javaCells.Count -eq 0) { throw 'Cell CSV contains no records' }

# Matching category/cell-number pairs vote for the translation between the generated level
# origin and the native .ma coordinate origin. The true translation is shared by many cells.
$translationVotes = @{}
foreach ($javaCell in $javaCells) {
  $indexKey = "$($javaCell.Category),$($javaCell.CellNo)"
  if (-not $nativeByCategoryCell.ContainsKey($indexKey)) { continue }
  foreach ($nativeCell in $nativeByCategoryCell[$indexKey]) {
    $dx = $nativeCell.X - $javaCell.MaX
    $dy = $nativeCell.Y - $javaCell.MaY
    $translationKey = "$dx,$dy"
    if ($translationVotes.ContainsKey($translationKey)) {
      $translationVotes[$translationKey]++
    } else {
      $translationVotes[$translationKey] = 1
    }
  }
}
if ($translationVotes.Count -eq 0) { throw 'No category/cell matches available to infer translation' }

$rankedTranslations = @($translationVotes.GetEnumerator() | Sort-Object `
    @{Expression = 'Value'; Descending = $true}, @{Expression = 'Name'; Descending = $false})
$best = $rankedTranslations[0]
$bestParts = $best.Name -split ','
$bestDx = [int]$bestParts[0]
$bestDy = [int]$bestParts[1]

Write-Output "layer=$Layer status=FOUND file=$MaFile nativeRecords=$($nativeRecords.Count) javaRecords=$($javaCells.Count)"
$nativeSameCategoryPositionConflicts = @($nativeRecords |
    Group-Object Category, X, Y | Where-Object { $_.Count -gt 1 }).Count
$nativeCrossCategoryPositionConflicts = @($nativeRecords | Group-Object X, Y | Where-Object {
    @($_.Group | Select-Object -ExpandProperty Category -Unique).Count -gt 1
  }).Count
Write-Output "nativeSameCategoryPositionConflicts=$nativeSameCategoryPositionConflicts nativeCrossCategoryPositionConflicts=$nativeCrossCategoryPositionConflicts"
Write-Output "translationCandidates=$($translationVotes.Count)"
foreach ($candidate in ($rankedTranslations | Select-Object -First 10)) {
  Write-Output "translation=$($candidate.Name) votes=$($candidate.Value)"
}
Write-Output "selectedTranslation=$bestDx,$bestDy votes=$($best.Value)"

$conflictsWithFixtureCell = 0
foreach ($conflict in $conflicts) {
  $translatedX = $conflict.MaX + $bestDx
  $translatedY = $conflict.MaY + $bestDy
  $positionKey = "$translatedX,$translatedY"
  $fixtureCellsAtPosition = if ($nativeByPosition.ContainsKey($positionKey)) {
    @($nativeByPosition[$positionKey] | ForEach-Object { "$($_.Category):$($_.CellNo)" })
  } else {
    @()
  }
  Write-Output ("conflict world={0},{1} javaMa={2},{3} translatedMa={4},{5} fixtureCellsAtPosition=[{6}]" -f
    $conflict.WorldX, $conflict.WorldY, $conflict.MaX, $conflict.MaY,
    $translatedX, $translatedY, ($fixtureCellsAtPosition -join ','))
  $hitCount = 0
  foreach ($cellNo in $conflict.Cells) {
    $candidateCells = @($javaCells | Where-Object {
      $_.WorldX -eq $conflict.WorldX -and $_.WorldY -eq $conflict.WorldY -and $_.CellNo -eq $cellNo
    })
    $candidateCategories = @($candidateCells | Select-Object -ExpandProperty Category -Unique)
    if ($candidateCategories.Count -eq 0) { $candidateCategories = @('unknown') }
    foreach ($category in $candidateCategories) {
      $fixtureHit = $nativeRecordKeys.Contains("$category,$cellNo,$translatedX,$translatedY")
      if ($fixtureHit) { $hitCount++ }
      Write-Output ("world={0},{1} javaMa={2},{3} translatedMa={4},{5} cell={6} category={7} fixtureHit={8}" -f
        $conflict.WorldX, $conflict.WorldY, $conflict.MaX, $conflict.MaY,
        $translatedX, $translatedY, $cellNo, $category, $fixtureHit)
    }
  }
  if ($hitCount -gt 0) { $conflictsWithFixtureCell++ }
}
Write-Output "conflicts=$($conflicts.Count) conflictsWithFixtureCell=$conflictsWithFixtureCell"
if ($RequireConflictHit -and $conflictsWithFixtureCell -eq 0) { exit 2 }
exit 0
