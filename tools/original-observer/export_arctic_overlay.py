"""Freeze read-only ColdWindOverlay observations; no draw/stack/GPU claim."""
import argparse
import hashlib
import json
from pathlib import Path
from export_arctic_blast import export

if __name__ == '__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('run',type=Path);p.add_argument('output',type=Path)
    args=p.parse_args()
    export(args.run,args.output)
    base=json.loads(args.output.read_text(encoding='utf8'))
    captures=[]
    for case in base['captures']:
        folder=args.run/'orchestrator/worker'/case['name']
        summary=json.loads((folder/'summary.json').read_text(encoding='utf8'))
        metadata=summary['progress_overlay']
        assert metadata==dict(id=196,filename='Expansion\\ColdWindOverlay',frames=21,rate=16,trans=3)
        assert not any(o['overlay_id']==196 for o in summary['baseline_overlays'])
        raw=(folder/'overlays.json').read_bytes();rows=json.loads(raw)
        unique={}
        for snapshot in rows:
            for o in snapshot['overlays']:
                if o['overlay_id']!=196:continue
                key=(o['pointer'],o['cursor'],o['value_08'],o['total_cursor'],o['frame_delta'])
                unique.setdefault(key,dict(o,elapsed_ms=snapshot['elapsed_ms'],held=snapshot['held'],mode=snapshot['mode']))
        states=list(unique.values())
        assert len({s['pointer'] for s in states})==1
        assert {s['cursor'] for s in states}==set(range(0,5377,256))
        assert all(s['overlay_type']==2 and s['frame_delta']==256 and s['total_cursor']==5376 for s in states)
        assert not any(o['overlay_id']==196 for o in rows[-1]['overlays'])
        base['source_files'][str((folder/'overlays.json').relative_to(args.run))]=hashlib.sha256(raw).hexdigest()
        captures.append(dict(name=case['name'],identity=case['identity'],metadata=metadata,
            release_ms_actual=case['release_ms_actual'],raw_overlay_samples=sum(len(s['overlays']) for s in rows),
            states=states,final_target_overlays=0,rejected_reads=summary['overlay_rejected_reads']))
    base['captures']=captures
    save=args.run/'orchestrator/worker'/f"{base['loadout']['name']}-input.d2s"
    base['loadout']['character_level']=save.read_bytes()[43]
    inventory=Path('tools/original-observer/class-skill-batch-2026-11-21/class-skill-inventory.json')
    source=json.loads(inventory.read_text(encoding='utf8'))
    base['resource']=source['resources']['data/global/overlays/Expansion\\ColdWindOverlay.dcc']
    assert base['resource']['directions']==1 and base['resource']['frames']==21
    base['scope']='One attached ONCE overlay per cast, native clock/counter polling; no actual draw submission, audio playback or GPU evidence'
    args.output.write_text(json.dumps(base,indent=2)+'\n',encoding='utf8')
    print('Retained overlay states:',[len(c['states']) for c in captures])
