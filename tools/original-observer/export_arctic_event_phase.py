"""Add local-owner/game-type checks to the QPC Arctic publication capture."""
import argparse
import json
from pathlib import Path
from export_arctic_publication import export,load


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('run',type=Path);parser.add_argument('output',type=Path)
    args=parser.parse_args();result=export(args.run)
    player=load(args.run/'worker/entry/summary.json')['player']
    assert player['name']=='CXArctic' and player['type']==0
    result['player_unit_id']=player['unit_id']
    for capture in result['captures']:
        directory=args.run/'worker'/capture['name'];summary=load(directory/'summary.json')
        assert summary['client_game_type']==0
        assert summary['observation_clock']['implementation']=='QueryPerformanceCounter()'
        rows=[json.loads(line) for line in (directory/'missiles.jsonl').read_text(encoding='utf8').splitlines() if line.strip()]
        assert all(row['owner_type']==0 and row['owner_id']==player['unit_id'] for row in rows)
        capture['client_game_type']=0;capture['owner_unit_id']=player['unit_id']
        capture['observation_clock']=summary['observation_clock']
        capture['sampling_profile']=summary['sampling_profile']
    result['scope']='Native local player/type0; action before allocation; first stable update at age2, supported by pinned CltDo1 repeat branch'
    args.output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
    print([(c['name'],c['total_births'],c['equal_endpoint_scans']) for c in result['captures']])
