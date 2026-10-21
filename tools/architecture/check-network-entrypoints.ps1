param([string]$Output='build/reports/m1c-network-entrypoints.tsv')
$files=rg --files server core quests server-domain server-systems shared game-data -g '*.java'
$rows=@('family`tfile`tline`tclassification')
foreach($f in $files){
  if($f -like '*d2gs_test*' -or $f -like '*d2gs_netty*'){ continue }
  $lines=Get-Content $f
  for($i=0;$i -lt $lines.Count;$i++){
    $l=$lines[$i]
    if($l -match 'com\.riiablo\.server\.d2gs_test'){ $rows += "d2gs_test`t$f`t$($i+1)`tproduction-reference" }
    elseif($l -match 'com\.riiablo\.server\.d2gs_netty'){ $rows += "d2gs_netty`t$f`t$($i+1)`tproduction-reference" }
    elseif($l -match 'com\.riiablo\.nnet\.|com\.riiablo\.onet\.') { $rows += "legacy-transport`t$f`t$($i+1)`tproduction-reference" }
  }
}
New-Item -ItemType Directory -Force (Split-Path $Output) | Out-Null
$rows | Set-Content $Output
$d2=@($rows | Where-Object {$_ -match '^d2gs_test`t'}); $dn=@($rows | Where-Object {$_ -match '^d2gs_netty`t'}); Write-Output "d2gs_test_refs=$($d2.Count) d2gs_netty_refs=$($dn.Count) total=$($rows.Count-1)"
