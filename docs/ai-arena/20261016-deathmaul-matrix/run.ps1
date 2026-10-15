param(
  [string]$RunName = '20261016-deathmaul-matrix',
  [string]$EvidenceDirectory = $PSScriptRoot
)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path "$PSScriptRoot/../../..").Path
$evidenceRoot = [System.IO.Path]::GetFullPath($EvidenceDirectory)
New-Item -ItemType Directory -Force -Path $evidenceRoot | Out-Null
foreach ($pass in @('first', 'repeat')) {
  foreach ($difficulty in @(0, 1, 2)) {
    foreach ($seed in @(1, 7, 42)) {
      foreach ($suffix in @('.log', '.jsonl', '-scenes.zip')) {
        $target = "$evidenceRoot/d$difficulty-s$seed-$pass$suffix"
        if (Test-Path -LiteralPath $target) { throw "Evidence already exists: $target" }
      }
    }
  }
}
Push-Location $repoRoot
try {
  foreach ($pass in @('first', 'repeat')) {
    foreach ($difficulty in @(0, 1, 2)) {
      foreach ($seed in @(1, 7, 42)) {
        $key = "d$difficulty-s$seed-$pass"
        $output = "server/d2gs/build/monster-ai-arena/$RunName-$key"
        if (Test-Path $output) { throw "Output already exists: $output" }
        Write-Output "Running $key"
        & ./gradlew.bat --project-cache-dir build/deathmaul-matrix-gradle-cache `
          -I docs/ai-arena/20261016-deathmaul-matrix/isolated.init.gradle `
          :server:d2gs:monsterAiArena `
          '-ParenaMonsters=deathmauler1,deathmauler2,deathmauler3,deathmauler4,deathmauler5,deathmauler6' `
          "-ParenaOutput=$output" "-ParenaDifficulty=$difficulty" "-ParenaSeed=$seed" `
          --console=plain --max-workers=1 *> "$evidenceRoot/$key.log"
        if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $key" }
        # The arena also journals unselected catalog rows as NOT_RUN. Preserve
        # that raw journal in the archive; scope the comparison to the six selected rows.
        Get-Content -LiteralPath "$output/results.jsonl" | Where-Object {
          ($_ | ConvertFrom-Json).ai -eq 'DeathMauler'
        } | Set-Content -LiteralPath "$evidenceRoot/$key.jsonl"
        Compress-Archive -Path "$output/*.log", "$output/results.jsonl" -DestinationPath "$evidenceRoot/$key-scenes.zip"
      }
    }
  }
} finally { Pop-Location }
