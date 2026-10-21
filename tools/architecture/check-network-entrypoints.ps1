param([string]$Output='build/reports/m1c-network-entrypoints.tsv')
$files=rg --files server core quests server-domain server-systems shared game-data -g '*.java'
$rows=@('family`tfile`tline`tclassification')
foreach($f in $files){ if($f -like '*d2gs_test*' -or $f -like '*d2gs_netty*'){ continue }; $lines=Get-Content $f;
New-Item -ItemType Directory -Force (Split-Path $Output) | Out-Null; $rows | Set-Content $Output
$d2=@($rows | Where-Object {$_ -match '^d2gs_test`t'}); $netty=@($rows | Where-Object {$_ -match '^d2gs_netty`t'}); Write-Output "d2gs_test_refs=$($d2.Count) d2gs_netty_refs=$($netty.Count) total=$($rows.Count-1)"

