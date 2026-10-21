param(
  [string]$Root = 'core/src/main/java/com/riiablo/map',
  [string]$Output = 'build/reports/m1b-map-entity-boundary.tsv',
  [string]$Baseline = 'tools/architecture/m1b-map-entity-boundary-baseline.tsv',
  [switch]$FailOnDrift
)
$patterns = @(
  'import com\.riiablo\.engine\.EntityFactory;',
  'import com\.riiablo\.engine\.server\.NativeDataTables;',
  'import com\.riiablo\.engine\.server\.component\.',
  '\bEntityFactory\b',
  '\bNativeDataTables\.',
  '\b(factory|map\.factory)\.(createMonster|createWarp|createObject|createStaticObjectByClassId)'
)
$files = rg --files $Root -g '*.java'
$rows = @('file`tcategory`tline`ttext')
foreach ($file in $files) {
  $lines = Get-Content $file
  for ($i = 0; $i -lt $lines.Count; $i++) {
    foreach ($pattern in $patterns) {
      if ($lines[$i] -match $pattern) {
        $category = if ($pattern -match 'EntityFactory|createMonster|createWarp|createObject|factory') {'entity-population'} elseif ($pattern -match 'NativeDataTables') {'rules-data'} else {'server-component'}
        $rows += "$file`t$category`t$($i + 1)`t$($lines[$i].Trim())"
        break
      }
    }
  }
}
New-Item -ItemType Directory -Force (Split-Path $Output) | Out-Null
$rows | Set-Content $Output
$currentKeys = @($rows | Select-Object -Skip 1 | ForEach-Object { $p = $_ -split "`t",4; "$($p[0])`t$($p[1])`t$($p[3])" })
Write-Output "M1-B findings: $($currentKeys.Count)"
if ($FailOnDrift) {
  if (!(Test-Path $Baseline)) { Write-Error "Missing baseline: $Baseline"; exit 2 }
  $baselineKeys = @(Get-Content $Baseline | Select-Object -Skip 1 | ForEach-Object { $p = $_ -split "`t",4; "$($p[0])`t$($p[1])`t$($p[3])" })
  $added = @($currentKeys | Where-Object { $_ -notin $baselineKeys })
  if ($added.Count -gt 0) { Write-Error ("M1-B boundary drift: {0} new findings`n{1}" -f $added.Count, ($added -join "`n")); exit 1 }
  Write-Output 'M1-B boundary drift: none'
}
