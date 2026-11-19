param(
  [Parameter(Mandatory=$true)][string]$Fixture,
  [string]$SavePath = 'G:\BaiduNetdiskDownload\Diablo II\Save',
  [string]$Name = 'CXBarb',
  [int]$ExistingPid = 0,
  [switch]$StartLoader,
  [string]$D2Home = 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
)
$ErrorActionPreference = 'Stop'
$log = Join-Path ([IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($Fixture))) ('install-fixture-' + $PID + '.log')
try { Start-Transcript -LiteralPath $log -Force | Out-Null } catch {}
if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)) {
  throw 'Run this bounded installer as administrator.'
}
$fixturePath = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $Fixture).Path)
$save = [IO.Path]::GetFullPath($SavePath)
if (-not (Test-Path -LiteralPath $fixturePath -PathType Leaf)) { throw "Fixture missing: $fixturePath" }
if (-not (Test-Path -LiteralPath $save -PathType Container)) { throw "Save path missing: $save" }
if ($ExistingPid -gt 0) {
  $existing = Get-CimInstance Win32_Process -Filter "ProcessId=$ExistingPid"
  Write-Output ("existing_count=" + @($existing).Count + "; existing_name=" + (@($existing)[0].Name))
  if (-not $existing -or @($existing).Count -ne 1 -or @($existing)[0].Name.Trim() -notlike 'D2v110*.exe') {
    throw "ExistingPid is not the pinned original loader: $ExistingPid"
  }
  Stop-Process -Id $ExistingPid -Force
  Wait-Process -Id $ExistingPid -Timeout 15 -ErrorAction SilentlyContinue
}
if ($Name -notmatch '^[A-Za-z][A-Za-z-]{1,14}$') { throw 'Invalid original-compatible character name.' }
$target = Join-Path $save ($Name + '.d2s')
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$backup = Join-Path $save ('.codex-backup-' + $stamp)
New-Item -ItemType Directory -Path $backup | Out-Null
Get-ChildItem -LiteralPath $save -File | Copy-Item -Destination $backup
Copy-Item -LiteralPath $fixturePath -Destination $target -Force
Write-Output (ConvertTo-Json @{status='installed'; fixture=$fixturePath; target=$target; backup=$backup} -Compress)
if ($StartLoader) {
  $loaderCandidates = @(Get-ChildItem -LiteralPath $D2Home -Filter 'D2v110*.exe' -File)
  if ($loaderCandidates.Count -ne 1) { throw "Expected exactly one D2v110 loader in $D2Home" }
  $loader = $loaderCandidates[0].FullName
  Start-Process -FilePath $loader -WorkingDirectory $D2Home -ArgumentList @('-w','-res800') | Out-Null
}
try { Stop-Transcript | Out-Null } catch {}
