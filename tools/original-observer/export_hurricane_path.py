"""Freeze all captured Hurricane trajectories; retain countdown/animation inconsistencies separately."""
import argparse
from collections import Counter,defaultdict
import hashlib
import json
from pathlib import Path
import struct
import pefile

X=(7,5,0,-5,-7,-5,0,5)
Y=(0,5,7,5,0,-5,-7,-5)


def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()


def export(run,home,output):
    report=json.loads((run/'orchestrator.json').read_text(encoding='utf8'))
    assert report['status']=='passed' and report['worker']['cleanup_confirmed']
    assert report['private_desktop_not_input_checks'] and not report['switched_desktop']
    directory=run/'worker/250'
    summary=json.loads((directory/'summary.json').read_text(encoding='utf8'))
    assert summary['status']=='passed' and summary['final_units']==0
    cmp=home/'D2CMP.dll'
    assert sha(cmp)=='e725036e1118d34b98d2b739525ff75a225e6964b425469e4773c93f0c9cf882'
    p=pefile.PE(str(cmp));table=list(struct.unpack('<64I',p.get_data(0x1A4D0,256)));p.close()
    assert table==[(4,0,5,1,6,2,7,3)[((i+4)&63)>>3] for i in range(64)]
    units=defaultdict(list)
    for line in (directory/'missiles.jsonl').open(encoding='utf8'):
        row=json.loads(line);units[row['unit_id']].append(row)
    result=dict(schema=1,run_id=run.parent.name,background_verified=True,original_launched_by_export=False,
        fixture=report['worker']['fixture'],direction_table=table,direction_evidence='Static D2CMP table applied to sampled facing; no DCC submission trace',
        original_summary=summary,source_sha256={n:sha(directory/n) for n in ('missiles.jsonl','caster.json','summary.json')},
        cmp_sha256=sha(cmp),columns=['age','x_fixed','y_fixed','velocity_x','velocity_y','path_direction','cursor','length','rate','height','elapsed_ms'],
        units=[],rejected_samples=[],limitations=[
            'Non-atomic polling: reject only observed animation/countdown inconsistency; original records retained',
            '25 units first seen at age1: birth position inferred by subtracting their observed first-step velocity',
            'Birth choices and height are observed replay inputs, not proof of matching caster RNG across engines',
            'No callback stack, GPU pixels, collision boundary, combat or multiplayer parity'])
    for uid,rows in units.items():
        first=rows[0];age=first['total_frames']-first['current_frame']
        assert age in (0,1)
        center=struct.unpack_from('<2i',bytes.fromhex(first['missile_data_hex']),40)
        bx,by=first['x_fixed']-first['velocity'][0]*age,first['y_fixed']-first['velocity'][1]*age
        assert bx&65535==by&65535==32768
        delta=((bx>>16)-center[0],(by>>16)-center[1]);lane=list(zip(X,Y)).index(delta)
        unit=dict(unit_id=uid,class_id=first['class_id'],center=list(center),lane=lane,
            lifetime=first['total_frames'],height=first['graphics_aux'][3]>>11,
            birth_position_evidence='direct_age0' if age==0 else 'inferred_from_first_step_age1',states=[])
        assert 0<=unit['height']<75
        for s in rows:
            assert struct.unpack_from('<2i',bytes.fromhex(s['missile_data_hex']),40)==center
            a=s['total_frames']-s['current_frame']
            state=[a,s['x_fixed'],s['y_fixed'],*s['velocity'],s['direction'],*s['animation'],s['graphics_aux'][3]>>11,s['elapsed_ms']]
            if s['animation'][0]!=a*s['animation'][2]:
                result['rejected_samples'].append(dict(unit_id=uid,snapshot=s['snapshot'],reason='animation_countdown_inconsistent',state=state))
            else:unit['states'].append(state)
        result['units'].append(unit)
    result['counts']=dict(units=len(units),accepted_samples=sum(len(u['states']) for u in result['units']),
        rejected_samples=len(result['rejected_samples']),by_class=dict(Counter(u['class_id'] for u in result['units'])),
        inferred_births=sum(u['birth_position_evidence']!='direct_age0' for u in result['units']))
    # Keep each captured state on one line; retain readable unit metadata without a huge line per file.
    text=json.dumps(result,indent=2)
    import re
    text=re.sub(r'\[\s*(-?\d+(?:\s*,\s*-?\d+){10})\s*\]',lambda m:'['+', '.join(v.strip() for v in m.group(1).split(','))+']',text)
    output.write_text(text+'\n',encoding='utf8')
    print(json.dumps(result['counts'],indent=2))


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('run',type=Path);p.add_argument('home',type=Path);p.add_argument('output',type=Path)
    args=p.parse_args();export(args.run,args.home,args.output)
