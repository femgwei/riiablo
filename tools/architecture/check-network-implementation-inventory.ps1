param([string]$Output='build/reports/m1c-network-inventory.tsv')
$roots=@('server/netty/src/main/java','server/d2gs/src/main/java','server/bnls/src/main/java','server/bncs/src/main/java','server/mcp/src/main/java')
$rows=@('family`tpath`tpackage`tproductionSourceSet')
foreach($root in $roots){
  foreach($f in (rg --files $root -g '*.java')){
    $pkg=(Select-String -Path $f -Pattern '^package ' | Select-Object -First 1).Line.Trim()
    $family=if($pkg -match 'd2gs_test'){'d2gs_test'}elseif($pkg -match 'd2gs_netty'){'d2gs_netty'}elseif($pkg -match 'nnet'){'nnet'}elseif($pkg -match 'onet'){'onet'}elseif($pkg -match 'com\.riiablo\.net'){'net'}elseif($pkg -match 'bnls'){'bnls'}elseif($pkg -match 'bncs'){'bncs'}elseif($pkg -match 'mcp'){'mcp'}elseif($pkg -match 'server\.d2gs'){'d2gs'}else{'other'}
    $rows += "$family`t$f`t$pkg`tmain"
  }
}
New-Item -ItemType Directory -Force (Split-Path $Output) | Out-Null
$rows | Set-Content $Output
$testMain=@($rows | Where-Object {$_ -match '^d2gs_test`t'})
Write-Output "entries=$($rows.Count-1) d2gs_test_main_entries=$($testMain.Count)"
if($testMain.Count -gt 0){Write-Output 'KNOWN_DEBT: d2gs_test is currently under server/netty/src/main/java'}


