[CmdletBinding()]
param(
  [Parameter(Mandatory)][string]$D2Home,
  [Parameter(Mandatory)][string]$Output,
  [string]$Revision = 'HEAD',
  [int]$Seed = 1,
  [string]$CompareWith
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'map-acceptance-audit.ps1')
$homePath = (Resolve-Path -LiteralPath $D2Home).Path
$destination = [IO.Path]::GetFullPath($Output)
if (Test-Path -LiteralPath $destination) { throw 'Output must be a new directory' }
$commit = & git -C $repo rev-parse --verify "$Revision^{commit}"
if ($LASTEXITCODE -ne 0) { throw 'Revision must resolve to a commit' }
New-Item -ItemType Directory -Path $destination | Out-Null
$archive = Join-Path $destination 'source.zip'
& git -C $repo archive --format=zip "--output=$archive" $commit
if ($LASTEXITCODE -ne 0) { throw 'Source export failed' }
$source = Join-Path $destination 'source'
Expand-Archive -LiteralPath $archive -DestinationPath $source
$mpqs = @(Get-ChildItem -LiteralPath $homePath -File -Filter '*.mpq' |
  Sort-Object Name | ForEach-Object {
    [ordered]@{ name = $_.Name.ToLowerInvariant(); bytes = $_.Length
      sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash }
  })
if ($mpqs.Count -eq 0) { throw 'No MPQ files found' }
$manifest = [ordered]@{
  contract = 1; revision = "$commit"; sourceSha256 = (Get-FileHash $archive).Hash
  profile = '1.10f'; seed = $Seed; glSeed = 0x110FCA4D; rate = 25; levels = '1,2'; observeMillis = 8000
  machine = [Environment]::MachineName; os = [Environment]::OSVersion.ToString()
  java = ((& java -version 2>&1 | ForEach-Object { "$_" }) -join "`n")
  resources = $mpqs; startedUtc = [DateTime]::UtcNow.ToString('o')
  coverage = 'FOCUSED_LOCAL_OBSERVATION'; correctness = 'NOT_ASSERTED'
  natural = 'NOT_RUN'; gl = 'NOT_RUN'; crossComputer = 'NOT_RUN'; result = 'PARTIAL'
}
$manifestPath = Join-Path $destination 'acceptance.json'
function Save-Manifest {
  $manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
}
Save-Manifest
if ($CompareWith) {
  $other = Get-Content -LiteralPath $CompareWith -Raw | ConvertFrom-Json
  $same = $other.contract -eq 1 -and $other.revision -eq $manifest.revision `
      -and $other.sourceSha256 -eq $manifest.sourceSha256 -and $other.profile -eq $manifest.profile `
      -and $other.seed -eq $Seed -and $other.rate -eq 25 -and $other.levels -eq '1,2' `
      -and $other.observeMillis -eq 8000
  $same = $same -and $other.glSeed -eq $manifest.glSeed
  $expected = @($other.resources | ForEach-Object { "$($_.name):$($_.bytes):$($_.sha256)" })
  $actual = @($mpqs | ForEach-Object { "$($_.name):$($_.bytes):$($_.sha256)" })
  $same = $same -and (($expected -join '|') -eq ($actual -join '|'))
  $manifest.crossComputer = if (-not $same) { 'INPUT_MISMATCH' }
    elseif ($other.machine -eq $manifest.machine) { 'SAME_HOST_NOT_CROSS_COMPUTER' }
    else { 'INPUTS_MATCH_OTHER_HOST_RESULTS_REQUIRE_REVIEW' }
  Save-Manifest
}
Push-Location $source
try {
  $common = @("-Pd2Home=$homePath", '--no-daemon', '--max-workers=1', '--console=plain')
  $natural = Join-Path $destination 'natural'
  & .\gradlew.bat :server:d2gs:headlessMapTour @common "-PtourSeed=$Seed" `
      '-PtourLevels=1,2' -PtourRate=25 -PtourAiObserve -PtourAiObserveMillis=8000 `
      "-PtourOutput=$natural" *> (Join-Path $destination 'natural-console.log')
  $exitCode = $LASTEXITCODE
  try {
    if ($exitCode -ne 0) { throw "Gradle exited $exitCode" }
    $lines = Get-Content -LiteralPath (Join-Path $natural 'map-tour.log')
    Assert-AiTourEvidence -Lines $lines -Summary (Get-Content (Join-Path $natural 'ai-summary.txt'))
    $issues = @(Get-MapLogIssues -Lines (Get-Content (Join-Path $destination 'natural-console.log')))
    $issues | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $destination 'natural-issues.json')
    if ($issues.Count) { throw "Unreviewed log issues: $($issues.Count)" }
    if ($lines -match '^\[MAP_TOUR\] (FAIL|DIAGNOSTIC_FAIL|DEATH_RECOVERY) ' `
        -or -not ($lines -match '^\[MAP_TOUR\] SUMMARY planned=2 visited=2 .*failures=0 result=PASS$')) {
      throw 'Focused tour did not complete cleanly'
    }
    $manifest.natural = 'OBSERVED_RUNTIME_ONLY'
  } catch { $manifest.natural = "FAILED: $($_.Exception.Message)" }
  Save-Manifest
  $gl = Join-Path $destination 'gl'
  & .\gradlew.bat :desktop:offscreenCamp @common -PoffscreenLevel=2 `
      -PoffscreenWidth=854 -PoffscreenHeight=480 '-Pd2Version=1.10f' `
      "-PsavesDir=$(Join-Path $destination 'saves')" "-PvisualOutput=$gl" `
      *> (Join-Path $destination 'gl-console.log')
  $exitCode = $LASTEXITCODE
  try {
    if ($exitCode -ne 0) { throw "Gradle exited $exitCode" }
    $report = Get-Content (Join-Path $gl 'rogue-encampment-manifest.txt')
    if ('window=854x480' -notin $report -or 'result=PASS' -notin $report `
        -or -not ($report -match '^playerDrawableLayers=[1-9]\d*$') `
        -or -not (Test-Path (Join-Path $gl 'rogue-encampment-smoke.png'))) {
      throw 'Missing production-size GL evidence'
    }
    $manifest.gl = 'CAPTURED_REQUIRES_VISUAL_REVIEW'
  } catch { $manifest.gl = "FAILED: $($_.Exception.Message)" }
} finally {
  Pop-Location
  $manifest.completedUtc = [DateTime]::UtcNow.ToString('o')
  if ($manifest.natural -like 'FAILED:*' -or $manifest.gl -like 'FAILED:*') {
    $manifest.result = 'FAILED'
  }
  Save-Manifest
}
Write-Output "Evidence: $manifestPath"
Write-Output "Natural: $($manifest.natural); GL: $($manifest.gl)"
Write-Output "Overall $($manifest.result): no original-game oracle, complete route/skill coverage or cross-computer visual acceptance."
if ($manifest.natural -like 'FAILED:*' -or $manifest.gl -like 'FAILED:*') {
  throw 'Acceptance diagnostics found failures; evidence was preserved'
}
