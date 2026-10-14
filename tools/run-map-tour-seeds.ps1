param(
  [string]$D2Home = 'G:\BaiduNetdiskDownload\Diablo II 1.10F',
  [int[]]$Seeds = @(1, 2, 3, 12345, 54321, 127897359),
  [ValidateSet(25, 100)][int]$Rate = 25,
  [string]$Output = 'server/d2gs/build/map-tour/six-seeds',
  [switch]$SeamsOnly
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'map-acceptance-audit.ps1')
$repo = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path -LiteralPath $D2Home -PathType Container)) { throw 'D2Home directory missing' }
if ($Seeds.Count -eq 0 -or @($Seeds | Sort-Object -Unique).Count -ne $Seeds.Count) {
  throw 'Seeds must be nonempty and unique'
}
Push-Location $repo
try {
  $sourcePaths = @('core', 'server', 'D2MOO_JAVA', 'table', 'assets', 'tools',
      'build.gradle', 'gradle.properties', 'settings.gradle')
  $baseline = (& git ls-tree HEAD -- @sourcePaths) -join "`n"
  $revision = & git rev-parse HEAD
  if (& git status --porcelain -- @sourcePaths) { throw 'Commit source changes before the seed batch' }
  $resources = @(Get-ChildItem -LiteralPath $D2Home -Filter '*.mpq' | Sort-Object Name | ForEach-Object {
    $hash = Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256
    "$($_.Name) bytes=$($_.Length) sha256=$($hash.Hash)"
  })
  foreach ($seed in $Seeds) {
    if (((& git ls-tree HEAD -- @sourcePaths) -join "`n") -ne $baseline `
        -or (& git status --porcelain -- @sourcePaths)) {
      throw 'Source baseline changed during batch; do not mix revisions in one acceptance result'
    }
    $destination = Join-Path $Output "seed-$seed-rate-$Rate"
    $absolute = [IO.Path]::GetFullPath((Join-Path $repo $destination))
    if (Test-Path -LiteralPath $absolute) { throw "Refusing to overwrite existing run: $absolute" }
    New-Item -ItemType Directory -Path $absolute -Force | Out-Null
    $console = Join-Path $absolute 'console.log'
    "revision=$revision`nseed=$seed`nrate=$Rate`nhome=$D2Home`nprofile=1.10f`nseamsOnly=$SeamsOnly`n$($resources -join "`n")" |
      Set-Content -LiteralPath (Join-Path $absolute 'run-baseline.txt') -Encoding UTF8
    Write-Host "Starting complete A1-A5 tour seed=$seed rate=$Rate"
    $modeArgs = @()
    if ($SeamsOnly) { $modeArgs += '-PtourAllSeams' }
    & .\gradlew.bat :server:d2gs:headlessMapTour "-Pd2Home=$D2Home" "-PtourSeed=$seed" `
      "-PtourRate=$Rate" "-PtourOutput=$destination" @modeArgs --no-daemon --max-workers=1 *> $console
    $result = $LASTEXITCODE
    $issues = @(Get-MapLogIssues -Lines (Get-Content -LiteralPath $console))
    ConvertTo-Json -InputObject $issues -Depth 4 |
      Set-Content -LiteralPath (Join-Path $absolute 'log-issues.json') -Encoding UTF8
    if (((& git ls-tree HEAD -- @sourcePaths) -join "`n") -ne $baseline `
        -or (& git status --porcelain -- @sourcePaths)) {
      throw "Source baseline changed while seed=$seed ran; preserve logs but rerun on a fixed baseline"
    }
    $report = Join-Path $absolute 'map-tour.log'
    if ($result -ne 0 -or -not (Test-Path -LiteralPath $report)) {
      throw "Tour failed seed=$seed exit=$result; inspect $console"
    }
    $lines = Get-Content -LiteralPath $report
    if ($SeamsOnly) {
      Assert-NativeSeamEvidence -Lines $lines
      if (-not ($lines -match '^\[MAP_TOUR\] SUMMARY .* coverage=NATIVE_SEAMS_ONLY .*failures=0 result=PASS$')) {
        throw "Invalid standalone seam summary seed=$seed"
      }
      if ($issues.Count) { throw "Unresolved log issues seed=${seed}: $($issues.Count); inspect log-issues.json" }
      Write-Host "PASS seed=${seed}: all adjacent native walk seams tested out and back"
      continue
    }
    if (@($lines | Where-Object { $_ -match '^\[MAP_TOUR\] (FAIL|DIAGNOSTIC_FAIL|DEATH_RECOVERY) ' }).Count -gt 0 `
        -or -not ($lines -match '^\[MAP_TOUR\] SUMMARY planned=132 visited=132 .*failures=0 result=PASS$')) {
      throw "Invalid tour summary seed=$seed; inspect $report"
    }
    $loads = @($lines | ForEach-Object {
      if ($_ -match '^\[MAP_TOUR\] LOAD level=(\d+) ') { [int]$Matches[1] }
    })
    $local = @($lines | ForEach-Object {
      if ($_ -match '^\[MAP_TOUR\] WALK_PASS .*mode=LOCAL_WALK level=(\d+) ') { [int]$Matches[1] }
    })
    if ($loads.Count -ne 132 -or @($loads | Sort-Object -Unique).Count -ne 132 `
        -or @(1..132 | Where-Object { $_ -notin $loads -or $_ -notin $local }).Count -gt 0) {
      throw "Incomplete load/local movement coverage seed=$seed"
    }
    Assert-MapTourEvidence -Lines $lines
    $objectAudits = @($lines | ForEach-Object {
      if ($_ -match '^\[MAP_TOUR\] OBJECT_AUDIT level=(\d+) .* pending=0 .* failures=0 result=PASS$') {
        [int]$Matches[1]
      }
    })
    if ($objectAudits.Count -ne 132 -or @($objectAudits | Sort-Object -Unique).Count -ne 132) {
      throw "Incomplete per-level object loading audit seed=$seed"
    }
    if ($issues.Count) { throw "Unresolved log issues seed=${seed}: $($issues.Count); inspect log-issues.json" }
    Write-Host "PASS seed=${seed}: 132 maps and all five town exits audited"
  }
  Write-Host "PASS: all $($Seeds.Count) seed runs passed at rate=$Rate"
} finally {
  Pop-Location
}
