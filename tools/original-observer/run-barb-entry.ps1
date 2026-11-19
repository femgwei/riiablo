param(
  [Parameter(Mandatory=$true)][int]$TargetPid,
  [string]$Output = 'F:\3rd_src\riiablo\tools\original-observer\runs\cxbarb-entry-20261114',
  [string]$Python = 'F:\3rd_src\emsdk\python\3.9.2-nuget_64bit\python.exe',
  [string]$D2Home = 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
)
$ErrorActionPreference = 'Stop'
if (Test-Path -LiteralPath $Output) { throw "Output must be new: $Output" }
$log = $Output + '.launcher.log'
try { Start-Transcript -LiteralPath $log -Force | Out-Null } catch {}
& $Python (Join-Path $PSScriptRoot 'menu-driver.py') --pid $TargetPid --home $D2Home --output $Output --seconds 120 --auto-single-player --character CXBarb --visible-run --walk-to-level 2 --capture-layouts
exit $LASTEXITCODE
