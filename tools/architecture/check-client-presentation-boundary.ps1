param(
  [string]$Root = 'core/src/main/java/com/riiablo/engine/client',
  [string]$Output = 'build/reports/m1a-client-presentation-boundary.tsv',
  [string]$Baseline = 'tools/architecture/m1a-client-presentation-boundary-baseline.tsv',
  [switch]$FailOnDrift
)
$patterns = @(
  'com\.riiablo\.screen', 'com\.riiablo\.widget',
  'com\.riiablo\.audio', 'com\.riiablo\.graphics',
  '\bMonsterAudio\b', '\bAudio\.'
)
$files = rg --files $Root -g '*.java'
$rows = @('file`tcategory`tline`ttext')
foreach ($file in $files) {
  $lines = Get-Content $file
  for ($i = 0; $i -lt $lines.Count; $i++) {
    foreach ($pattern in $patterns) {
      if ($lines[$i] -match $pattern) {
        $category = if ($pattern -match 'screen|widget') {'ui'} elseif ($pattern -match 'audio|MonsterAudio|Audio') {'audio'} else {'graphics'}
        $rows += "$file`t$category`t$($i + 1)`t$($lines[$i].Trim())"
        break
      }
    }
  }
}
New-Item -ItemType Directory -Force (Split-Path $Output) | Out-Null
$rows | Set-Content $Output
$currentKeys = @($rows | Select-Object -Skip 1 | ForEach-Object { $p = $_ -split "`t",4; "$($p[0])`t$($p[1])`t$($p[3])" })
Write-Output "M1-A findings: $($currentKeys.Count)"
if ($FailOnDrift) {
  if (!(Test-Path $Baseline)) { Write-Error "Missing baseline: $Baseline"; exit 2 }
  $baselineKeys = @(Get-Content $Baseline | Select-Object -Skip 1 | ForEach-Object { $p = $_ -split "`t",4; "$($p[0])`t$($p[1])`t$($p[3])" })
  $added = @($currentKeys | Where-Object { $_ -notin $baselineKeys })
  if ($added.Count -gt 0) { Write-Error ("M1-A boundary drift: {0} new findings`n{1}" -f $added.Count, ($added -join "`n")); exit 1 }
  Write-Output 'M1-A boundary drift: none'
}
