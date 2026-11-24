"""Keep native observation phases separate from legacy and assembly-order local replays."""
import argparse
import json
from pathlib import Path


def local_summary(replay):
    inserted = next(s for s in replay['stages'] if s['phase'] == 'missile_subscription_inserted')
    entity, tick = inserted['subject'], inserted['tick']
    allocation = next(s for s in replay['stages'] if s['phase'] == 'factory_return_before_client_flags'
        and s['subject'] == entity and s['tick'] == tick)
    end = next(s for s in replay['stages'] if s['phase'] == 'world_process_finished' and s['tick'] == tick)
    following = next(s for s in replay['stages'] if s['phase'] == 'world_process_finished' and s['tick'] == tick+1)
    def missile(stage):
        return next(m for m in stage['missiles'] if m['entity'] == entity)
    assert missile(inserted)['native_age'] == 0
    assert allocation['sequence_cursor'] == 10*256 and inserted['sequence_cursor'] == 8*256
    assert replay['final_streams'] == 0
    return dict(allocation=allocation, insertion=inserted, end_of_birth_tick=end,
        following_tick=following, birth_tick_end_age=missile(end)['native_age'],
        following_tick_age=missile(following)['native_age'],
        allocation_and_insertion_same_tick=True, allocation_stack=replay['allocation_stack'])


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('native', type=Path)
    parser.add_argument('local', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    native = json.loads(args.native.read_text(encoding='utf8'))
    local = json.loads(args.local.read_text(encoding='utf8'))
    assert native['background_verified'] and native['owned_game_closed']
    legacy = local_summary(local['legacy_fixture_order'])
    assembly = local_summary(local['client_assembly_relative_order'])
    assert legacy['birth_tick_end_age'] == 1 and assembly['birth_tick_end_age'] == 0
    assert legacy['following_tick_age'] == 2 and assembly['following_tick_age'] == 1
    births = [u for c in native['captures'] for u in c['births']]
    assert len(births) == 24 and all(u['first_native_age']==0 for u in births)
    assert all(u['first_observed_updated_age']==2 for u in births)
    source = Path('core/src/main/java/com/riiablo/presentation/ClientSimulationAssembly.java').read_text(encoding='utf8')
    assert source.index('new InfernoPresentationSystem()') < source.index('new com.riiablo.presentation.MissileImpactPresentationSystem()')
    assert source.index('new com.riiablo.presentation.MissileImpactPresentationSystem()') < source.index('SimulationSystemGroups.sequenceAnimation()')
    result = dict(schema=1, source_run_id=native['run_id'],
        comparison_status='localized_action_allocation_and_first_update_phases_with_legacy_fixture_order_bias',
        production_gameplay_changed=False, native_units=24,
        native_birth_ages=sorted({b['first_native_age'] for b in births}),
        native_first_observed_update_ages=sorted({b['first_observed_updated_age'] for b in births}),
        native_action_marker_precedes_allocation_in_all_samples=True,
        client_assembly_relative_order=assembly, legacy_fixture_order=legacy,
        unresolved=[
            'Native action marker, CltSt15 state refresh and CltDo24 execution have not been traced as individual calls.',
            'Native first observed age2 does not prove no transient age1 update or rendering occurred.',
            'Assembly-relative ECS replay covers relevant system order, not the complete live application or GPU.',
            'No fixed one-tick delay or skipped movement should be added solely to force matching sampled cursors.'])
    args.output.write_text(json.dumps(result, indent=2)+'\n', encoding='utf8')
    print(result['comparison_status'])
