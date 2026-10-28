param(
  [string]$Root = (Join-Path $PSScriptRoot '..\..'),
  [string]$Output = 'build/reports/m8d-network-transport-owners.tsv'
)

$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path -LiteralPath $Root).Path
$serverRoot = Join-Path $Root 'server'
$files = Get-ChildItem -LiteralPath $serverRoot -Recurse -File -Include *.java,*.kt |
  Where-Object { $_.FullName -notmatch '[\\/]build[\\/]' }

$rows = [System.Collections.Generic.List[object]]::new()
foreach ($file in $files) {
  $relative = $file.FullName.Substring($Root.Length).TrimStart('\','/')
  $text = Get-Content -LiteralPath $file.FullName -Raw
  foreach ($owner in @('com.riiablo.server.d2gs_netty', 'com.riiablo.nnet', 'com.riiablo.onet')) {
    $count = ([regex]::Matches($text, [regex]::Escape($owner))).Count
    if ($count -gt 0) {
      $rows.Add([pscustomobject]@{
        Owner = $owner
        File = $relative.Replace('\','/')
        SourceSet = if ($relative -match '[\\/]src[\\/]test[\\/]') { 'test' } else { 'main' }
        References = $count
      })
    }
  }
}

$parent = Split-Path -Parent $Output
if ($parent -and -not (Test-Path -LiteralPath $parent)) {
  New-Item -ItemType Directory -Path $parent -Force | Out-Null
}
"owner`tfile`tsourceSet`treferences" | Set-Content -LiteralPath $Output -Encoding UTF8
$rows | Sort-Object Owner,File | ForEach-Object {
  "$($_.Owner)`t$($_.File)`t$($_.SourceSet)`t$($_.References)" | Add-Content -LiteralPath $Output -Encoding UTF8
}

# d2gs_netty is a retired server implementation. It may reference itself, but
# no other production source is allowed to depend on it.
$leaks = $rows | Where-Object {
  $_.Owner -eq 'com.riiablo.server.d2gs_netty' -and
  $_.File -notmatch '^server/netty/src/main/java/com/riiablo/server/d2gs_netty/'
}
if ($leaks) {
  $leaks | Format-Table -AutoSize | Out-String | Write-Error
  exit 1
}

# The current authoritative D2GS transport is nnet.  onet is retained only as
# a legacy implementation and must not be imported by production code outside
# its own package tree.  Keep this check textual and deterministic so it can
# run before compilation in the architecture gate.
$legacyLeaks = $rows | Where-Object {
  $_.Owner -eq 'com.riiablo.onet' -and
  $_.File -notmatch '^server/netty/src/main/java/com/riiablo/onet/' -and
  $_.File -notmatch '^server/netty/src/test/'
}
if ($legacyLeaks) {
  $legacyLeaks | Format-Table -AutoSize | Out-String | Write-Error
  exit 1
}

# nnet may only be consumed by the authoritative D2GS server family.  This
# prevents a second session/transport owner from appearing in another server.
$nnetLeaks = $rows | Where-Object {
  $_.Owner -eq 'com.riiablo.nnet' -and
  $_.File -notmatch '^server/netty/src/main/java/com/riiablo/server/d2gs/' -and
  $_.File -notmatch '^server/netty/src/main/java/com/riiablo/nnet/'
}
if ($nnetLeaks) {
  $nnetLeaks | Format-Table -AutoSize | Out-String | Write-Error
  exit 1
}

$summary = $rows | Group-Object Owner | Sort-Object Name |
  ForEach-Object { "{0}={1}" -f $_.Name, $_.Count }
Write-Output ("network transport owner audit passed: {0} references ({1})" -f
  $rows.Count, ($summary -join ', '))
