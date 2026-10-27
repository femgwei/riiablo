param(
  [string]$Output = 'build/reports/unbound-rng.tsv',
  [string]$Baseline = 'tools/architecture/rng-ownership-baseline.tsv',
  [switch]$FailOnDrift
)

$ErrorActionPreference = 'Stop'
$roots = @(
  'core/src/main/java',
  'server-domain/src/main/java',
  'server-systems/src/main/java',
  'quests/src/main/java',
  'shared/src/main/java',
  'server'
)
$patterns = @(
  @{ Name = 'MathUtils'; Regex = 'MathUtils\.random(?:Boolean)?(?:\b|\.)' },
  @{ Name = 'MathRandom'; Regex = '\bMath\.random\s*\(' },
  @{ Name = 'JavaRandom'; Regex = '\bnew\s+Random\s*\(' },
  @{ Name = 'RandomXS128'; Regex = '\bRandomXS128\b' },
  @{ Name = 'ThreadLocalRandom'; Regex = '\bThreadLocalRandom\b' }
)

$rows = @('file`tcategory`tline`ttext')
foreach ($root in $roots) {
  if (!(Test-Path $root)) { continue }
  foreach ($file in (rg --files $root -g '*.java' | Where-Object {
    $_ -notmatch '(^|[/\\])(build|gen|src[/\\]test)[/\\]'
  })) {
    $lines = Get-Content $file
    for ($i = 0; $i -lt $lines.Count; $i++) {
      foreach ($pattern in $patterns) {
        if ($lines[$i] -match $pattern.Regex) {
          $text = $lines[$i].Trim() -replace "`t", ' '
          $rows += "$file`t$($pattern.Name)`t$($i + 1)`t$text"
          break
        }
      }
    }
  }
}

$rows = @($rows[0]) + @($rows | Select-Object -Skip 1 | Sort-Object)
New-Item -ItemType Directory -Force (Split-Path $Output) | Out-Null
$rows | Set-Content $Output
$currentKeys = @($rows | Select-Object -Skip 1 | ForEach-Object {
  $p = $_ -split "`t", 4
  "$($p[0])`t$($p[1])`t$($p[3])"
})
Write-Output "Unbound RNG findings: $($currentKeys.Count)"

if ($FailOnDrift) {
  if (!(Test-Path $Baseline)) { Write-Error "Missing RNG baseline: $Baseline"; exit 2 }
  $baselineKeys = @(Get-Content $Baseline | Select-Object -Skip 1 | ForEach-Object {
    $p = $_ -split "`t", 4
    "$($p[0])`t$($p[1])`t$($p[3])"
  })
  $added = @($currentKeys | Where-Object { $_ -notin $baselineKeys })
  if ($added.Count -gt 0) {
    Write-Error ("Unbound RNG drift: {0} new findings`n{1}" -f $added.Count, ($added -join "`n"))
    exit 1
  }
  Write-Output 'Unbound RNG drift: none'
}
