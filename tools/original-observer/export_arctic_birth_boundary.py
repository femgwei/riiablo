"""Retain a release boundary defined by observed native client missile births."""
import argparse
import hashlib
import json
from pathlib import Path
from export_arctic_blast import export

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('run',type=Path);p.add_argument('output',type=Path);args=p.parse_args()
    export(args.run,args.output)
    result=json.loads(args.output.read_text(encoding='utf8'))
    for case in result['captures']:
        folder=args.run/'orchestrator/worker'/case['name']
        report=json.loads((folder/'summary.json').read_text(encoding='utf8'))
        boundary=report['release_boundary'];requested=case['identity']['release_after_births']
        assert boundary['observed_births']==requested and case['identity']['hold_ms_requested'] is None
        before=[];after=[]
        for unit in case['units']:
            first=unit['states'][0]
            assert first['total_frames']==first['current_frame']==17,'A missile birth was not directly observed'
            birth=dict(first)
            (before if first['snapshot_index']<=boundary['snapshot_index'] else after).append(birth)
        assert len(before)==requested
        case['release_boundary']=boundary
        case['births_before_release']=before;case['births_after_release']=after
        caster=case['caster_states']
        case['neutral_after_release_ms']=next(s['elapsed_ms']-case['release_ms_actual'] for s in caster
            if s['elapsed_ms']>case['release_ms_actual'] and s['mode']==1)
    save=args.run/'orchestrator/worker'/(result['loadout']['name']+'-input.d2s')
    result['loadout']['character_level']=save.read_bytes()[43]
    result['scope']='Client memory polling; release is sent immediately after N observed missile births, not an assumed wall-clock duration'
    result['limitations'] += ['A native hash-list birth is an observation boundary, not a traced CltDo24 invocation',
        'The native release snapshot has logical point9 while local creation may be published at point8; phase/first-update parity remains separate']
    args.output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
    print([(c['name'],len(c['births_before_release']),len(c['births_after_release'])) for c in result['captures']])
