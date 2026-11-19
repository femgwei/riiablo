param(
  [Parameter(Mandatory=$true)][string]$Python,
  [Parameter(Mandatory=$true)][string]$RunDirectory,
  [string]$D2Home = 'G:\BaiduNetdiskDownload\Diablo II 1.10F',
  [switch]$AutomateEntry,
  [switch]$HiddenRun,
  [string]$Character
)
$ErrorActionPreference = 'Stop'
# This bounded helper requires elevation, but does not install a service/task,
# change compatibility/registry settings, or terminate any game process.
$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $admin) { throw 'Run this bounded helper as administrator.' }
New-Item -ItemType Directory -Path $RunDirectory -Force | Out-Null
$record = @{ elevated=$admin; startedGame=$false; inputsSent=$false }
try {
  $loader = Join-Path $D2Home 'D2v110各版本免CD全功能启动.exe'
  $matches = @(Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'D2v110各版本免CD全功能启动.exe' -and $_.ExecutablePath -eq $loader })
  if ($matches.Count -gt 1) { throw 'Multiple matching clients: refusing to choose one.' }
  if ($matches.Count -eq 1) {
    $game = Get-Process -Id $matches[0].ProcessId
  } else {
    # Normal creation is intentional: the user requested this exact launch
    # followed immediately by minimization after hidden startup failed.
    $game = Start-Process -FilePath $loader -WorkingDirectory $D2Home -ArgumentList '-w','-res800' -WindowStyle Normal -PassThru
    $record.startedGame = $true
    Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; public static class D2Minimize { [DllImport("user32.dll")] public static extern bool ShowWindowAsync(IntPtr h, int cmd); [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr h); }'
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    $sent = $false
    while ([DateTime]::UtcNow -lt $deadline) {
      $game.Refresh()
      if ($game.HasExited) { throw "Loader exited with $($game.ExitCode)" }
      if ($game.MainWindowHandle -ne 0) {
        if ([D2Minimize]::IsIconic($game.MainWindowHandle)) { break }
        if (-not $sent) {
          [D2Minimize]::ShowWindowAsync($game.MainWindowHandle,7) | Out-Null
          $sent = $true
        }
      }
      Start-Sleep -Milliseconds 50
    }
  }
  $record.pid = $game.Id
  $record.process = Get-CimInstance Win32_Process -Filter "ProcessId=$($game.Id)" | Select-Object ProcessId,ParentProcessId,ExecutablePath,CommandLine,CreationDate
  $record | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $RunDirectory 'launch.json') -Encoding UTF8
  if ($AutomateEntry) {
    $driverArgs = @((Join-Path $PSScriptRoot 'menu-driver.py'), '--pid', [string]$game.Id, '--home', $D2Home,
      '--seconds', '45', '--output', (Join-Path $RunDirectory 'menu'), '--auto-single-player')
    if ($Character) { $driverArgs += @('--character', $Character) }
    if ($HiddenRun) { $driverArgs += '--hidden-run' }
    & $Python @driverArgs 1> (Join-Path $RunDirectory 'observer.stdout') 2> (Join-Path $RunDirectory 'observer.stderr')
    $record.observerExitCode = $LASTEXITCODE
    $driverSummary = Join-Path $RunDirectory 'menu\summary.json'
    if (Test-Path -LiteralPath $driverSummary) {
      $result = Get-Content -LiteralPath $driverSummary -Raw | ConvertFrom-Json
      $record.inputsSent = @($result.actions).Count -gt 0
      $record.driverStatus = $result.status
      $record.driverError = $result.error
    }
  } else {
    if ($HiddenRun) { throw '-HiddenRun requires -AutomateEntry.' }
    & $Python (Join-Path $PSScriptRoot 'live-observer.py') --pid $game.Id --home $D2Home --seconds 12 --output (Join-Path $RunDirectory 'live') 1> (Join-Path $RunDirectory 'observer.stdout') 2> (Join-Path $RunDirectory 'observer.stderr')
    $record.observerExitCode = $LASTEXITCODE
  }
} catch {
  $record.error = $_.Exception.Message
} finally {
  $record | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $RunDirectory 'helper-summary.json') -Encoding UTF8
}
if ($record.error) { exit 1 }
if ($record.observerExitCode -ne 0) { exit $record.observerExitCode }
