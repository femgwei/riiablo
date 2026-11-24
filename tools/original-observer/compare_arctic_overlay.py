"""Compare native attached-overlay counters to the actual skill-start presentation pipeline."""
import argparse
import hashlib
import json
from pathlib import Path

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    for name in ('original','production','output'):p.add_argument(name,type=Path)
    args=p.parse_args()
    original=json.loads(args.original.read_text(encoding='utf8'))
    production=json.loads(args.production.read_text(encoding='utf8'))
    assert original['run_id']==production['source_run_id']
    warm=production['cases'][0];by_age={s['native_age']:s for s in warm['states'] if s['present']}
    count=errors=terminal=0
    for capture in original['captures']:
        for sample in capture['states']:
            if sample['value_08']==0:
                assert sample['cursor']==sample['total_cursor'];terminal+=1;continue
            count+=1;age=sample['cursor']//256;state=by_age.get(age)
            errors += state is None or not state['loaded'] or state['frame']!=age or state['frame_delta']!=sample['frame_delta']
    assert count==42 and terminal==2 and errors==0
    cases=[]
    for case in production['cases']:
        assert case['requests']==1
        assert [s['native_age'] for s in case['states'] if s['present']]==list(range(21))
        assert all(not s['present'] for s in case['states'][21:])
        cases.append(dict(asset_delay_updates=case['asset_delay_updates'],requests=case['requests'],
            first_loaded_age=next((s['native_age'] for s in case['states'] if s['present'] and s['loaded']),None),
            expires_at_native_age=21,late_flash=False))
    result=dict(schema=1,skill_id=230,source_run_id=original['run_id'],
        comparison_status='compared_cast_overlay_clock_with_draw_and_audio_unknowns',
        active_native_states=count,terminal_native_states=terminal,clock_mismatches=errors,
        overlay_id=196,native_type=2,lifetime_ticks=21,clock_cases=cases,
        evidence_sha256={str(x):hashlib.sha256(x.read_bytes()).hexdigest() for x in (args.original,args.production)},
        limitations=['Overlay list polling is non-atomic; no actual DCC draw selection, GPU blending, screenshot or native audio playback was captured',
            'CltSt15 sound dispatch is established by pinned static code; local once-per-action sound request is tested',
            'One native level99 naked Druid, skill1, two hold lengths; no all-level/equipment/network/cancellation parity',
            'The prior caster-loop callback-count and absolute input-phase differences are unchanged'])
    args.output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
    print(json.dumps(result))
