"""Freeze compact storm observations with provenance and explicit non-atomic rejects."""
import argparse
from collections import Counter, defaultdict
import hashlib
import json
from pathlib import Path


def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()


def export(run, output):
    report=json.loads((run/'orchestrator.json').read_text(encoding='utf8'))
    assert report['status']=='passed' and report['worker']['cleanup_confirmed']
    assert report['input_desktop_before']==report['input_desktop_after']=='Default'
    assert report['private_desktop_not_input_checks']>0 and not report['switched_desktop']
    assert report['screen_before']==report['screen_after']
    worker=run/'worker'
    result=dict(schema=1,run_id=run.parent.name,background_verified=True,owned_game_closed=True,
        orchestrator_sha256=sha(run/'orchestrator.json'),fixture=report['worker']['fixture'],cases={},
        limitations=['Read-only polling is non-atomic; no runtime call stacks, DCC calls or pixels',
            'Client frame global stayed zero; equal endpoint values do not identify atomic scans',
            'Filtered animation-field races remain in rejected_samples and original raw JSONL',
            'Lifetime/birth boundaries may be missed; state refresh inferred from extended active interval',
            'No combat, all-direction, map-boundary or multiple-owner parity asserted'])
    for skill in (250,249):
        directory=worker/str(skill)
        summary=json.loads((directory/'summary.json').read_text(encoding='utf8'))
        assert summary['status']=='passed' and summary['final_units']==0
        caster=json.loads((directory/'caster.json').read_text(encoding='utf8'))
        states=[dict(elapsed_ms=c['elapsed_ms'],active=c['active']) for i,c in enumerate(caster)
            if not i or c['active']!=caster[i-1]['active']]
        selected=defaultdict(list); rejected=[]; counts=Counter()
        for line in (directory/'missiles.jsonl').open(encoding='utf8'):
            s=json.loads(line); cls=s['class_id']; age=s['total_frames']-s['current_frame']; counts[cls]+=1
            item=dict(unit_id=s['unit_id'],class_id=cls,age=age,lifetime=s['total_frames'],
                elapsed_ms=s['elapsed_ms'],x_fixed=s['x_fixed'],y_fixed=s['y_fixed'],
                direction=s['direction'],velocity=s['velocity'],animation=s['animation'],graphics=s['graphics_aux'])
            if skill==249:
                expected=age%16 if cls in (578,579) else age
                # A per-unit countdown guard cannot make the animation and graphics reads atomic.
                if s['animation'][0]!=expected*256:
                    rejected.append(dict(reason='animation_countdown_mismatch',**item)); continue
                selected[(cls,s['unit_id'])].append(item)
            else:
                # Keep at most three representative full paths per resource; raw capture stays intact.
                key=(cls,s['unit_id'])
                if key in selected or sum(1 for c,_ in selected if c==cls)<3: selected[key].append(item)
        result['cases'][str(skill)]=dict(summary=summary,state_transitions=states,
            events=json.loads((directory/'events.json').read_text(encoding='utf8')),
            source_sha256={n:sha(directory/n) for n in ('summary.json','missiles.jsonl','caster.json','events.json')},
            raw_sample_counts=dict(counts),samples=[s for values in selected.values() for s in values],
            rejected_samples=rejected)
    output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
    print(json.dumps({k:dict(samples=len(c['samples']),rejected=len(c['rejected_samples']),state=c['state_transitions'])
        for k,c in result['cases'].items()},indent=2))


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('run',type=Path);parser.add_argument('output',type=Path)
    args=parser.parse_args();export(args.run,args.output)
