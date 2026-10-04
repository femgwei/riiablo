param(
  [string] $ManifestPath = 'F:\3rd_src\dark-magic\internal\content\d2legacy\manifests\skill-behavior-coverage.v1.json',
  [string] $RegistryPath = (Join-Path $PSScriptRoot '..\core\src\main\java\com\riiablo\engine\server\skill\NativeSkillBehaviorRegistry.java'),
  [string] $MatrixRoot = (Join-Path $PSScriptRoot '..\core\src\test\java\com\riiablo\engine\server'),
  [string] $ClientEvidencePath = (Join-Path $PSScriptRoot '..\docs\d2client-static-skill-evidence.tsv'),
  [switch] $RequireRegistered,
  [switch] $RequireMatrixTests
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $ManifestPath -PathType Leaf)) {
  throw "dark-magic manifest not found: $ManifestPath"
}
if (-not (Test-Path -LiteralPath $RegistryPath -PathType Leaf)) {
  throw "NativeSkillBehaviorRegistry not found: $RegistryPath"
}

$manifest = Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json
$manifestRows = @($manifest.implementations)
$manifestById = @{}
foreach ($row in $manifestRows) {
  $id = [int]$row.skill_id
  if ($manifestById.ContainsKey($id)) { throw "duplicate manifest skill_id=$id" }
  $manifestById[$id] = [string]$row.family
}

# The registry is intentionally a closed Java declaration.  Parse only the
# add*/id/family tuple; callback matching remains enforced by Java tests.
$registryText = Get-Content -LiteralPath $RegistryPath -Raw
$registeredById = @{}
$pattern = '(?m)^\s*add(?:[A-Z][A-Za-z]+)?\(\s*(\d+)\s*,\s*"([^"]+)"'
foreach ($match in [regex]::Matches($registryText, $pattern)) {
  $id = [int]$match.Groups[1].Value
  if ($registeredById.ContainsKey($id)) { throw "duplicate registry skill_id=$id" }
  $registeredById[$id] = $match.Groups[2].Value
}

$missing = @()
$familyMismatch = @()
foreach ($id in ($manifestById.Keys | Sort-Object)) {
  if (-not $registeredById.ContainsKey($id)) {
    $missing += $id
  } elseif ($registeredById[$id] -ne $manifestById[$id]) {
    $familyMismatch += "${id}:$($manifestById[$id])!=$($registeredById[$id])"
  }
}

$matrixClasses = @(
  'NativeAmazonSkillMatrixTest.java',
  'NativeSorceressSkillMatrixTest.java',
  'NativeNecromancerSkillMatrixTest.java',
  'NativePaladinSkillMatrixTest.java',
  'NativeBarbarianSkillMatrixTest.java',
  'NativeDruidSkillMatrixTest.java',
  'NativeAssassinSkillMatrixTest.java'
)
$missingMatrix = @($matrixClasses | Where-Object {
  -not (Test-Path -LiteralPath (Join-Path $MatrixRoot $_) -PathType Leaf)
})

$evidencePending = $null
if (Test-Path -LiteralPath $ClientEvidencePath -PathType Leaf) {
  $evidenceRows = @(Get-Content -LiteralPath $ClientEvidencePath |
    Where-Object { $_ -and -not $_.StartsWith('#') } |
    ConvertFrom-Csv -Delimiter "`t")
  $evidencePending = @($evidenceRows | Where-Object status -ne 'verified').Count
}

Write-Output "skill coverage manifest=$($manifestRows.Count) registry=$($registeredById.Count)"
Write-Output "skill coverage missing_registry=$($missing.Count) family_mismatch=$($familyMismatch.Count)"
if ($null -ne $evidencePending) {
  Write-Output "skill coverage d2client_pending=$evidencePending"
}
if ($missing.Count -gt 0) { Write-Output "missing_registry_ids=$($missing -join ',')" }
if ($familyMismatch.Count -gt 0) { Write-Output "family_mismatch=$($familyMismatch -join ',')" }
if ($missingMatrix.Count -gt 0) { Write-Output "missing_matrix_tests=$($missingMatrix -join ',')" }

if ($RequireRegistered -and ($missing.Count -gt 0 -or $familyMismatch.Count -gt 0)) {
  throw 'skill coverage registration is incomplete'
}
if ($RequireMatrixTests -and $missingMatrix.Count -gt 0) {
  throw 'skill coverage matrix tests are incomplete'
}
$coverageClean = $missing.Count -eq 0 -and $familyMismatch.Count -eq 0 -and $missingMatrix.Count -eq 0
if ($coverageClean) {
  Write-Output 'skill coverage status=ok'
} else {
  Write-Output 'skill coverage status=report-with-gaps'
}
