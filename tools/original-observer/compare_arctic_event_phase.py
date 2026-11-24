"""Compare matched native/local completed-update phases after Arctic action staging."""
import argparse
import json
from pathlib import Path


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('native',type=Path);parser.add_argument('local',type=Path);parser.add_argument('output',type=Path)
    args=parser.parse_args()
    native=json.loads(args.native.read_text(encoding='utf8'))
    replay=json.loads(args.local.read_text(encoding='utf8'))['client_assembly_relative_order']
    assert native['background_verified'] and native['owned_game_closed']
    assert all(c['client_game_type']==0 for c in native['captures'])
    native_births=[b for c in native['captures'] for b in c['births']]
    assert len(native_births)==24 and all(b['first_native_age']==0 and b['first_observed_updated_age']==2 for b in native_births)
    inserted=[s for s in replay['stages'] if s['phase']=='missile_subscription_inserted']
    ends={s['tick']:s for s in replay['stages'] if s['phase']=='world_process_finished'}
    assert len(inserted)==len(replay['callback_ticks'])
    phases=[]
    for callback,birth in zip(replay['callback_ticks'],inserted):
        tick=birth['tick'];entity=birth['subject'];assert tick==callback+1
        def unit(stage):return next(m for m in stage['missiles'] if m['entity']==entity)
        at_birth=unit(ends[tick]);updated=unit(ends[tick+1])
        assert at_birth['native_age']==0 and updated['native_age']==2
        assert updated['x_fixed']-at_birth['x_fixed']==2*36864
        assert updated['y_fixed']==at_birth['y_fixed']
        assert at_birth['animation_frame']==0 and updated['animation_frame']==480>>8
        phases.append(dict(callback_tick=callback,birth_tick=tick,
            source_frame=ends[tick]['source_frame'],sequence_cursor=ends[tick]['sequence_cursor'],
            native_age_at_birth=0,native_age_at_first_update=2,
            displacement_fixed=updated['x_fixed']-at_birth['x_fixed']))
    # Persistent emission uses the same logical cursor/source pair at the
    # completed publication update; the previous insertion-stage comparison did not.
    assert phases[0]['sequence_cursor']==9*256 and phases[0]['source_frame']==9
    assert all(p['sequence_cursor']==9*256 and p['source_frame']==9 for p in phases[:-1])
    assert phases[-1]['sequence_cursor']==11*256 and phases[-1]['source_frame']==10
    assert all(b['first_published']['after']['sequence_cursor']==9*256 for c in native['captures'] for b in c['births'][:-1])
    assert all(c['births'][-1]['first_published']['after']['sequence_cursor']==11*256
        and c['births'][-1]['first_published']['after']['source_cursor']==10*256 for c in native['captures'])
    assert replay['final_streams']==0
    result=dict(schema=1,source_run_id=native['run_id'],
        comparison_status='matched_local_arctic_action_consumption_and_initial_update_phases',
        production_presentation_changed=True,server_damage_and_mana_path_changed=False,
        native_units=24,local_emissions=len(phases),local_phases=phases,
        local_allocation_stack=replay['allocation_stack'],
        native_observation_clock=native['captures'][0]['observation_clock'],
        limitations=['Native runtime call stacks/GPU draws were not captured.',
            'Native owner/type0 free-flight only; other game types, collision and all FCR/state combinations are not established.',
            'Local replay exercises the relevant actual systems in client assembly relative order, not a complete live GameScreen.',
            'Absolute origin centering and A/B allocation RNG are outside this phase comparison.'])
    args.output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
    print(result['comparison_status'],'native',len(native_births),'local',len(phases))
