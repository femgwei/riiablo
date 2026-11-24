"""Extract native turn behavior at the observed path-target change, not posted-input time."""
import argparse
import hashlib
import json
from pathlib import Path

def read(path):return json.loads(path.read_text(encoding='utf8'))

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('run',type=Path);parser.add_argument('output',type=Path);args=parser.parse_args()
    report=read(args.run/'orchestrator.json');worker=args.run/'worker';capture=worker/'birth-15'
    assert report['status']=='passed' and not report['switched_desktop'] and report['worker']['owned_game_closed']
    assert report['input_desktop_before']==report['input_desktop_after']!=report['private_desktop']
    summary=read(capture/'summary.json');identity=read(capture/'identity.json')
    assert summary['status']=='passed' and summary['client_game_type']==0
    assert summary['turn_boundary']['phase']['action_frame']==1
    scans=read(capture/'publication.json');stable={s['snapshot_index']:s for s in scans if s['endpoint_markers_equal'] and s['before']==s['after']}
    rows=[json.loads(line) for line in (capture/'missiles.jsonl').read_text(encoding='utf8').splitlines() if line.strip()]
    grouped={}
    for row in rows:
        if row['snapshot_index'] in stable:grouped.setdefault(row['unit_id'],[]).append(row)
    units=[]
    for uid,states in grouped.items():
        first=states[0];assert first['total_frames']==first['current_frame']==17
        velocity=(first['velocity_x'],first['velocity_y'])
        assert all((s['velocity_x'],s['velocity_y'])==velocity for s in states),'Published stream changed its own velocity'
        for s in states:
            age=17-s['current_frame']
            assert s['x_fixed']-first['x_fixed']==age*velocity[0]
            assert s['y_fixed']-first['y_fixed']==age*velocity[1]
        marker=stable[first['snapshot_index']]['after']
        units.append(dict(unit_id=uid,first=first,velocity=list(velocity),
            current_path_target=[marker['target_x'],marker['target_y']],states=len(states)))
    units.sort(key=lambda u:(u['first']['snapshot_index'],u['unit_id']))
    assert len(units)==18
    initial=[identity['target_x'],identity['target_y']];turned=identity['turn_target']
    assert initial!=turned
    first_changed=next(s for s in stable.values() if [s['after']['target_x'],s['after']['target_y']]==turned)
    before=[u for u in units if u['first']['snapshot_index']<first_changed['snapshot_index']]
    after=[u for u in units if u['first']['snapshot_index']>=first_changed['snapshot_index']]
    assert len(before)==4 and len(after)==14
    assert all(u['current_path_target']==initial for u in before)
    assert all(u['current_path_target']==turned for u in after)
    assert all(u['velocity']==[36864,0] for u in before) and all(u['velocity']==[0,36864] for u in after)
    overlay_nodes={o['pointer'] for s in read(capture/'overlays.json') for o in s['overlays'] if o['overlay_id']==196}
    assert len(overlay_nodes)==1
    assert all(s['after']['source_cursor']==9*256 for s in stable.values()
        if summary['turn_boundary']['snapshot_index']<=s['snapshot_index']<=summary['release_boundary']['snapshot_index'])
    assert summary['final_client_units']==0
    release=summary['release_boundary']['snapshot_index']
    assert sum(u['first']['snapshot_index']<=release for u in units)==15
    files=[args.run/'orchestrator.json',worker/'summary.json']+list((worker/'provenance').glob('*.py'))
    files += [capture/f for f in ['summary.json','identity.json','publication.json','missiles.jsonl','overlays.json']]
    result=dict(schema=1,run_id=args.run.parent.name,background_verified=True,owned_game_closed=True,
        identity=identity,turn_posted=summary['turn_boundary'],first_observed_changed_path_target=first_changed,
        release_boundary=summary['release_boundary'],units=units,old_direction_births=4,new_direction_births=14,
        post_release_births=3,overlay_instances=1,source_frame_held_at_turn=9,
        all_published_streams_keep_their_velocity=True,
        observation_clock=summary['observation_clock'],
        hashes={p.as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in files},
        limitations=['Posted WM_MOUSEMOVE time is not the path-target-change or native skill consumption time.',
            'Snapshots are non-atomic; equal endpoint markers exclude detected transitions only.',
            'Free-flight point aiming in this sample; target-monster disappearance, all angles, DCC/GPU orientation remain unverified.'])
    args.output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');print('native turn births:4 east,14 north; tail3,overlay1')
