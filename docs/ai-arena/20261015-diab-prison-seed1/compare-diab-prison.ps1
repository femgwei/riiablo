param([string]$New='server/d2gs/build/monster-ai-arena/20261015-diab-prison-full-final')
$old=@{};Get-Content docs/ai-arena/20261015-diab-run-seed1/results.jsonl | ForEach-Object {$r=$_|ConvertFrom-Json;$old["$($r.monster)/$($r.scenario)"]=$r}
$same=0;$newCount=0;$changed=@();$rows=@(Get-Content "$New/results.jsonl" | ForEach-Object {$_|ConvertFrom-Json})
foreach($r in $rows){$key="$($r.monster)/$($r.scenario)";if(!$old.ContainsKey($key)){$newCount++;continue};$b=$old[$key];$fields=@();foreach($p in $r.PSObject.Properties){if(($b.($p.Name)|ConvertTo-Json -Compress) -cne ($p.Value|ConvertTo-Json -Compress)){$fields+="$($p.Name):$($b.($p.Name))->$($p.Value)"}};if($fields.Count){$changed+="$key $($fields -join '; ')"}else{$same++};$old.Remove($key)}
"same=$same changed=$($changed.Count) added=$newCount missing=$($old.Count) total=$($rows.Count)"
$rows|Group-Object status|Sort-Object Name|ForEach-Object {"$($_.Name)=$($_.Count)"}
"specialPassed=$(@($rows|Where-Object specialPassed).Count)"
$changed
