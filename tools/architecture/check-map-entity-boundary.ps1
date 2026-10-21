param(
  [string[]]$Root = @('core/src/main/java/com/riiablo/map', 'core/src/main/java/com/riiablo/drlg'),
  [string]$Output = 'build/reports/m1b-map-entity-boundary.tsv',
  [string]$Baseline = 'tools/architecture/m1b-map-entity-boundary-baseline.tsv',
  [switch]$FailOnDrift
)
$ErrorActionPreference = 'Stop'
# These are runtime adapters, never dependencies of map generation.
$adapters = @(
  'core/src/main/java/com/riiablo/map/MapManager.java',
  'core/src/main/java/com/riiablo/map/Box2DPhysics.java',
  'core/src/main/java/com/riiablo/map/RenderSystem.java'
)
$patterns = [ordered]@{
  'entity-population' = '\b(?:\w*EntityFactory)\b|\bfactory\.(createMonster|createWarp|createObject|createStaticObjectByClassId)'
  'rules-data' = '\bNativeDataTables\b'
  'server-component' = 'com\.riiablo\.engine\.server\.component\.'
  'ecs-runtime' = 'com\.artemis\.'
  'transport' = 'com\.badlogic\.gdx\.net\.Socket|@Wire\(name = "client\.socket"'
}
$files = @(rg --files @Root -g '*.java' | Sort-Object)
if ($LASTEXITCODE -ne 0) { throw 'Unable to enumerate map sources' }
$rows = @("file`tcategory`tline`ttext")
$generationFindings = @()
foreach ($file in $files) {
  $file = $file.Replace('\', '/')
  $lines = @(Get-Content -LiteralPath $file)
  for ($i = 0; $i -lt $lines.Count; $i++) {
    $code = $lines[$i].Trim()
    if ($code -match '^(//|/\*|\*)') { continue }
    foreach ($category in $patterns.Keys) {
      if ($code -match $patterns[$category]) {
        $row = "$file`t$category`t$($i + 1)`t$code"
        $rows += $row
        if ($file -notin $adapters) { $generationFindings += $row }
        break
      }
    }
  }
}
New-Item -ItemType Directory -Force (Split-Path $Output) | Out-Null
$rows | Set-Content -Encoding UTF8 $Output
$currentKeys = @($rows | Select-Object -Skip 1 | ForEach-Object { $p = $_ -split "`t",4; "$($p[0])`t$($p[1])`t$($p[3])" })
Write-Output "M1-B findings: $($currentKeys.Count) (owned runtime adapters)"
Write-Output "M1-B generation findings: $($generationFindings.Count)"
# A baseline may record adapter debt; it can never authorize dependencies in generation.
if ($generationFindings.Count -gt 0) {
  throw ("M1-B generation boundary violation:`n{0}" -f ($generationFindings -join "`n"))
}
if ($FailOnDrift) {
  if (!(Test-Path $Baseline)) { throw "Missing baseline: $Baseline" }
  $baselineKeys = @(Get-Content $Baseline | Select-Object -Skip 1 | ForEach-Object { $p = $_ -split "`t",4; "$($p[0].Replace('\', '/'))`t$($p[1])`t$($p[3])" })
  $added = @($currentKeys | Where-Object { $_ -notin $baselineKeys })
  if ($added.Count -gt 0) {
    throw ("M1-B boundary drift: {0} new findings`n{1}" -f $added.Count, ($added -join "`n"))
  }
  Write-Output 'M1-B boundary drift: none'
}
