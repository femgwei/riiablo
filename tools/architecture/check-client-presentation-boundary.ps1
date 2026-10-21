param(
  [string]$Root = 'core/src/main/java/com/riiablo/engine/client',
  [string]$Output = 'build/reports/m1a-client-presentation-boundary.tsv',
  [switch]$FailOnFindings
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
Write-Output "M1-A findings: $($rows.Count - 1)"
if ($FailOnFindings -and $rows.Count -gt 1) { exit 1 }
