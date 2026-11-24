"""Compare actual client births at matched release boundaries; retain phase limitations."""
import argparse
import hashlib
import json
from pathlib import Path

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    for name in ('original','production','elapsed_reference','output'):p.add_argument(name,type=Path)
    args=p.parse_args()
    original=json.loads(args.original.read_text(encoding='utf8'))
    production=json.loads(args.production.read_text(encoding='utf8'))
    elapsed=json.loads(args.elapsed_reference.read_text(encoding='utf8'))
    assert production['source_run_id']==original['run_id']
    assert production['elapsed_interval_reference_run']==elapsed['run_id']
    records=[]
    for native,local in zip(original['captures'],production['cases']):
        n=native['identity']['release_after_births'];release=local['release_tick']
        assert n==local['release_after_births']
        tail=[b for b in local['births'] if b['tick']>release]
        ticks=[b['tick']-release for b in tail]
        assert len(native['births_before_release'])==n
        assert len(native['births_after_release'])==len(tail)==3 and ticks==[2,4,6]
        assert len(native['units'])==len(local['births'])==n+3
        assert local['final_streams']==0 and local['final_mode']==1
        native_ms=[b['elapsed_ms']-native['release_ms_actual'] for b in native['births_after_release']]
        records.append(dict(release_after_births=n,total_native=len(native['units']),total_local=len(local['births']),
            native_tail_births=3,local_tail_births=3,local_tail_ticks=ticks,native_tail_observed_ms=native_ms,
            max_observed_time_difference_ms=max(abs(ms-t*40) for ms,t in zip(native_ms,ticks)),
            native_logical_point_at_release=native['release_boundary']['sequence_cursor']//256,
            local_logical_point_at_release=local['boundary']['sequence_cursor']//256,
            source_frame_at_release=local['boundary']['source_frame'],local_final_streams=0))
    timed=[]
    for native,local in zip(elapsed['captures'],production['elapsed_interval_cases']):
        hold=native['identity']['hold_ms_requested']//40
        assert hold==local['hold_elapsed_intervals']
        assert len(native['units'])==len(local['births'])
        timed.append(dict(hold_elapsed_intervals=hold,native_count=len(native['units']),local_count=len(local['births']),
            reference='initialized S1 source frame0; the zero pose is not counted as an elapsed interval',
            local_time_zero_tick=local['time_zero_tick'],local_release_tick=local['release_tick']))
    result=dict(schema=1,skill_id=230,source_run_id=original['run_id'],
        comparison_status='matched_release_boundary_counts_without_production_count_compensation',
        boundary_comparisons=records,calibrated_elapsed_comparisons=timed,
        production_gameplay_changed=False,
        evidence_sha256={str(x):hashlib.sha256(x.read_bytes()).hexdigest() for x in (args.original,args.production,args.elapsed_reference)},
        limitations=['Native births are hash-list observations; local births are actual Artemis component insertions, not a runtime CltDo24 stack trace',
            'The native release logical point is9 versus local8, although both source frames are9; absolute frame/packet/update-phase parity is not claimed',
            'Elapsed-time calibration removes the initialization-sample offset but does not establish native message processing timestamps',
            'No claim about native server damage/mana, all input phases/FCR/levels, GPU or actual audio playback'])
    args.output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
    print(json.dumps(dict(boundaries=records,calibrated_elapsed=timed)))
