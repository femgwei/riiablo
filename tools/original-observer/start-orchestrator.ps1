param(
  [Parameter(Mandatory=$true)][string]$Job,
  [string]$Output,
  [string]$Python = 'F:\3rd_src\emsdk\python\3.9.2-nuget_64bit\python.exe',
  [string]$D2Home = 'G:\BaiduNetdiskDownload\Diablo II 1.10F',
  [switch]$NoPause
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$script = Join-Path $root 'orchestrator.py'
$Job = (Resolve-Path -LiteralPath $Job).Path
if (!$Output) { $Output = Join-Path $root ('runs\batch-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff')) }
$Output = [IO.Path]::GetFullPath($Output)
if (Test-Path -LiteralPath $Output) { throw "Output must be new: $Output" }
$arguments = @($script, '--job', $Job, '--home', $D2Home, '--output', $Output, '--batch-timeout', '900')
$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if ($admin) {
  & $Python @arguments
  $code = $LASTEXITCODE
  Write-Host "orchestrator exit code: $code"
  if ($code -ne 0) { Write-Host "See orchestrator.json under: $Output" }
  if (!$NoPause) { Read-Host 'Press Enter to close this window' }
  return
}
# Start-Process flattens arrays. Quote EACH argument for Windows argv parsing,
# including spaces and trailing backslashes (the Diablo home contains spaces).
function Quote-WindowsArgument([string]$value) {
  if ($value.Contains('"')) { throw 'Quotes are not supported in launcher paths' }
  '"' + [regex]::Replace($value, '(\\+)$', '$1$1') + '"'
}
$quoted = ($arguments | ForEach-Object { Quote-WindowsArgument $_ }) -join ' '
$child = Start-Process -FilePath $Python -ArgumentList $quoted -Verb RunAs -WindowStyle Hidden -WorkingDirectory $root -Wait -PassThru
Write-Host "orchestrator exit code: $($child.ExitCode)"
if (!$NoPause) { Read-Host 'Press Enter to close this window' }
