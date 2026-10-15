param([string]$Evidence = $PSScriptRoot)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$baseline = @(Get-Content "$Evidence/d0-baseline.jsonl" | ForEach-Object { $_ | ConvertFrom-Json })
if ($baseline.Count -ne 22 -or @($baseline | Where-Object { !$_.fallback -or $_.actualAi -ne 'GenericMonster' }).Count) {
  throw 'Expected twenty-two original fallback scenes'
}
$executions = 0
foreach ($difficulty in 0..2) {
  $firstHash = (Get-FileHash "$Evidence/d$difficulty-first.jsonl").Hash
  if ($firstHash -ne (Get-FileHash "$Evidence/d$difficulty-repeat.jsonl").Hash) { throw 'Repeat differs' }
  foreach ($pass in @('first', 'repeat')) {
    $key = "d$difficulty-$pass"
    $lines = @(Get-Content "$Evidence/$key.jsonl")
    $rows = @($lines | ForEach-Object { $_ | ConvertFrom-Json })
    if ($rows.Count -ne 22) { throw "Expected 22 executions: $key" }
    $zip = [IO.Compression.ZipFile]::OpenRead((Resolve-Path "$Evidence/$key-scenes.zip"))
    try {
      $journal = $zip.GetEntry('results.jsonl')
      $reader = [IO.StreamReader]::new($journal.Open())
      try { $raw = @($reader.ReadToEnd() -split '\r?\n' | Where-Object { $_ }) }
      finally { $reader.Dispose() }
      $selected = @($raw | Where-Object { ($_ | ConvertFrom-Json).ai -eq 'Minion' })
      if (($selected -join "`n") -cne ($lines -join "`n")) { throw 'Extract differs from raw journal' }
      $unselected = @($raw | ForEach-Object { $_ | ConvertFrom-Json } | Where-Object ai -ne Minion)
      if ($raw.Count -ne 1180 -or $unselected.Count -ne 1158 -or
          @($unselected | Where-Object status -ne NOT_RUN).Count) { throw 'Unexpected catalog selection' }
      foreach ($r in $rows) {
        if ($r.ai -ne 'Minion' -or $r.actualAi -ne 'Minion' -or $r.fallback -or
            $r.seed -ne 1 -or $r.difficulty -ne $difficulty -or $r.ticks -ne 500 -or
            $r.warnings -ne 0 -or $r.errors -ne 0) {
          throw "Unexpected diagnostic: $key $($r.monster)/$($r.scenario)"
        }
        $reader = [IO.StreamReader]::new($zip.GetEntry($r.log).Open())
        try { $log = $reader.ReadToEnd() } finally { $reader.Dispose() }
        if ($r.scenario -eq 'near') {
          if ($r.status -ne 'ACTION_OBSERVED' -or $r.actions -le 0 -or
              $r.damageEvents -le 0 -or $r.damage -le 0 -or
              $log -notmatch 'SkillDo subject=\d+ skill=0 target=0 srvdofunc=1' -or
              $log -notmatch 'Damage subject=\d+ victim=0 amount=') { throw 'Missing actual near ordinary attack damage' }
        } elseif ($r.scenario -eq 'far') {
          # Record the unresolved observation; this is not a behavior acceptance oracle.
          if ($r.status -ne 'NO_ACTION' -or $r.actions -ne 0 -or
              $r.damageEvents -ne 0 -or $r.moves -ne 0) { throw 'Far observation changed; investigate' }
        }
      }
      foreach ($id in 1..11) {
        foreach ($scenario in @('near', 'far')) {
          if (@($rows | Where-Object { $_.monster -eq "minion$id" -and $_.scenario -eq $scenario }).Count -ne 1) {
            throw 'Missing or duplicated monster/scenario'
          }
        }
      }
      $executions += $rows.Count
    } finally { $zip.Dispose() }
  }
  "PASS difficulty=$difficulty first/repeat SHA256=$firstHash"
}
"PASS journal/repeat checks: $executions executions; 66 near ordinary attack/damage observations."
"PARTIAL: 66 far NO_ACTION observations unresolved, not behavior acceptance; unselected NOT_RUN excluded."
