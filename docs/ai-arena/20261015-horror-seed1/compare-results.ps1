param([string]$New='server/d2gs/build/monster-ai-arena/20261015-horror-refined-final')
$old=@{}
Get-Content docs/ai-arena/20261015-diab-prison-seed1/results.jsonl | ForEach-Object {$r=$_|ConvertFrom-Json;$old["$($r.monster)/$($r.scenario)"]=$r}
$same=0;$added=0;$changes=@()
$rows=@(Get-Content "$New/results.jsonl" | ForEach-Object {$_|ConvertFrom-Json})
foreach($r in $rows){
  $key="$($r.monster)/$($r.scenario)"
  if(!$old.ContainsKey($key)){$added++;continue}
  $before=$old[$key];$fields=@()
  foreach($p in $r.PSObject.Properties){
    if(($before.($p.Name)|ConvertTo-Json -Compress) -cne ($p.Value|ConvertTo-Json -Compress)){
      $fields+="$($p.Name):$($before.($p.Name))->$($p.Value)"
    }
  }
  if($fields.Count){$changes+="$key $($fields -join '; ')"}else{$same++}
  $old.Remove($key)
}
"same=$same changed=$($changes.Count) added=$added missing=$($old.Count) total=$($rows.Count)"
$rows|Group-Object status|Sort-Object Name|ForEach-Object {"$($_.Name)=$($_.Count)"}
"specialPassed=$(@($rows|Where-Object specialPassed).Count)"
$changes
