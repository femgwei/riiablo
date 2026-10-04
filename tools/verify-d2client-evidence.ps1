param(
  [string] $Path = (Join-Path $PSScriptRoot '..\docs\d2client-static-skill-evidence.tsv'),
  [switch] $RequireComplete
)

$ErrorActionPreference = 'Stop'
$expectedIds = @(0, 36, 40, 50, 60, 45, 47, 54, 55, 48, 52, 66, 70, 75,
  80, 85, 90, 94, 95, 72, 98, 99, 100, 103, 104, 105, 108, 109, 110,
  115, 120, 124, 125, 251, 256, 257, 261, 262, 266, 271, 272, 276, 277)

if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
  throw "Evidence file not found: $Path"
}

$rows = @(Get-Content -LiteralPath $Path |
  Where-Object { $_ -and -not $_.StartsWith('#') } |
  ConvertFrom-Csv -Delimiter "`t")
if ($rows.Count -ne $expectedIds.Count) {
  throw "Expected $($expectedIds.Count) evidence rows, found $($rows.Count)"
}

$actualIds = @($rows | ForEach-Object { [int]$_.skill_id })
$duplicates = @($actualIds | Group-Object | Where-Object Count -gt 1)
if ($duplicates.Count -gt 0) {
  throw "Duplicate skill IDs: $($duplicates.Name -join ', ')"
}
$missing = @($expectedIds | Where-Object { $_ -notin $actualIds })
$unexpected = @($actualIds | Where-Object { $_ -notin $expectedIds })
if ($missing.Count -gt 0 -or $unexpected.Count -gt 0) {
  throw "Manifest mismatch; missing=[$($missing -join ',')] unexpected=[$($unexpected -join ',')]"
}

foreach ($row in $rows) {
  if ($row.dll_version -ne '1.10f') {
    throw "skill_id=$($row.skill_id) must target DLL version 1.10f"
  }
  if ($row.status -notin @('pending-binary', 'verified')) {
    throw "skill_id=$($row.skill_id) has invalid status '$($row.status)'"
  }
  if ($RequireComplete -and $row.status -ne 'verified') {
    throw "skill_id=$($row.skill_id) is not verified (status=$($row.status))"
  }
  if ($row.status -eq 'verified') {
    foreach ($field in @('dll_sha256', 'client_role', 'function_address',
        'caller_xrefs', 'keyframe_evidence')) {
      if ([string]::IsNullOrWhiteSpace($row.$field)) {
        throw "skill_id=$($row.skill_id) verified row is missing $field"
      }
    }
  }
}

$verified = @($rows | Where-Object status -eq 'verified').Count
Write-Output "d2client evidence rows=$($rows.Count) verified=$verified pending=$($rows.Count - $verified)"
if ($RequireComplete) { Write-Output 'd2client evidence status=complete' }
