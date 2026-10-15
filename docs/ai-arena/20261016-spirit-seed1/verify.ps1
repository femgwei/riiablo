param([string]$Evidence = $PSScriptRoot)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$baseline = @(Get-Content "$Evidence/d0-baseline.jsonl" | ForEach-Object { $_ | ConvertFrom-Json })
if ($baseline.Count -ne 2 -or @($baseline | Where-Object { !$_.fallback -or $_.actualAi -ne 'GenericMonster' }).Count) {
  throw 'Expected two original fallback scenes'
}
foreach ($difficulty in 0..2) {
  $hash = (Get-FileHash "$Evidence/d$difficulty-first.jsonl").Hash
  if ($hash -ne (Get-FileHash "$Evidence/d$difficulty-repeat.jsonl").Hash) { throw 'Repeat differs' }
  foreach ($pass in @('first', 'repeat')) {
    $key = "d$difficulty-$pass"
    $lines = @(Get-Content "$Evidence/$key.jsonl")
    $rows = @($lines | ForEach-Object { $_ | ConvertFrom-Json })
    if ($rows.Count -ne 2) { throw "Expected two executions: $key" }
    $zip = [IO.Compression.ZipFile]::OpenRead((Resolve-Path "$Evidence/$key-scenes.zip"))
    try {
      $reader = [IO.StreamReader]::new($zip.GetEntry('results.jsonl').Open())
      try { $raw = @($reader.ReadToEnd() -split '\r?\n' | Where-Object { $_ }) }
      finally { $reader.Dispose() }
      $selected = @($raw | Where-Object { ($_ | ConvertFrom-Json).ai -eq 'Spirit' })
      if (($selected -join "`n") -cne ($lines -join "`n")) { throw 'Extract differs from raw journal' }
      $unselected = @($raw | ForEach-Object { $_ | ConvertFrom-Json } | Where-Object ai -ne Spirit)
      if ($raw.Count -ne 1180 -or $unselected.Count -ne 1178 -or
          @($unselected | Where-Object status -ne NOT_RUN).Count) { throw 'Unexpected catalog selection' }
      foreach ($scenario in @('near', 'far')) {
        $matches = @($rows | Where-Object scenario -eq $scenario)
        if ($matches.Count -ne 1) { throw 'Missing/duplicate scenario' }
        $r = $matches[0]
        if ($r.monster -ne 'mephistospirit' -or $r.ai -ne 'Spirit' -or $r.actualAi -ne 'Spirit' -or
            $r.fallback -or $r.seed -ne 1 -or $r.difficulty -ne $difficulty -or $r.ticks -ne 500 -or
            $r.warnings -ne 0 -or $r.errors -ne 0 -or $r.moves -ne 0) { throw "Unexpected diagnostics: $key/$scenario" }
        $reader = [IO.StreamReader]::new($zip.GetEntry($r.log).Open())
        try { $log = $reader.ReadToEnd() } finally { $reader.Dispose() }
        $calls = [regex]::Matches($log, 'SkillDo subject=\d+ skill=0 target=0 srvdofunc=1').Count
        if ($scenario -eq 'near') {
          if ($r.status -ne 'ACTION_OBSERVED' -or $r.actions -ne 1 -or $calls -ne 1 -or
              $log -notmatch 'tick=476 sample subject=\d+ ai=Spirit state=IDLE') {
            throw 'Expected exactly one natural ordinary attack and idle through final sample'
          }
        } elseif ($r.status -ne 'NO_ACTION' -or $r.actions -ne 0 -or $calls -ne 0) {
          throw 'Unexpected far attack'
        }
        if ($scenario -eq 'near' -and $difficulty -gt 0) {
          if ($r.damageEvents -ne 1 -or $r.damage -le 0 -or
              $log -notmatch 'Damage subject=\d+ victim=0 amount=') { throw 'Missing higher-difficulty one-shot damage' }
        } elseif ($r.damage -ne 0 -or $r.damageEvents -ne 0) {
          throw 'Normal/far damage observation changed; investigate'
        }
      }
    } finally { $zip.Dispose() }
  }
  "PASS difficulty=$difficulty one-shot decision/event checks; repeat SHA256=$hash"
}
"PASS 12 scene executions: six near one-shot actions and six far no-action observations."
"PARTIAL: four higher-difficulty near executions have one damage event; Normal near has no damage."
"No Normal damage, GL/TCP, terrain or global scheduling acceptance."
