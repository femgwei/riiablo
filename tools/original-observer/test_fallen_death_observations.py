import json
from pathlib import Path
import tempfile
import unittest

from fallen_death_observations import analyze, summarize, controlled_frames, recovery_observations, attack_animation_observations
from fallen_death_observations import attack_callback_observations
from fallen_death_observations import attack_damage_observations
from fallen_death_observations import attack_hit_roll_observations
from fallen_death_observations import attack_physical_observations
from fallen_death_observations import intermediate_rng_observations


def row(frame, mode=1, param=0, present=True):
    return {'frame_before': frame, 'frame_after': frame + 1, 'units': [
        {'type': 1, 'unit_id': 14, 'class_id': 19, 'mode': mode,
         'ai_param': [param, 0, 0], 'x': 10, 'y': 10}] if present else []}


class FallenDeathObservationsTest(unittest.TestCase):
    def test_intermediate_rng_requires_known_consumer_and_both_seed_links(self):
        physical = dict(frame=108, observer_id=3, seed_after_low=10, seed_after_high=20,
                        critical_seed_low=30, critical_seed_high=40)
        event = dict(frame=108, observer_id=3, fixture_seed=True, function_rva=0x1E4D0,
                     caller_rva=0x8F1AF, bound=1, value=0, seed_low=10, seed_high=20,
                     seed_after_low=30, seed_after_high=40)
        self.assertEqual('observed', intermediate_rng_observations([event], [physical])['status'])
        for change in (dict(caller_rva=0), dict(bound=2), dict(seed_after_high=41), dict(fixture_seed=False)):
            with self.assertRaises(ValueError):
                intermediate_rng_observations([dict(event, **change)], [physical])
        self.assertEqual('incomplete', intermediate_rng_observations([], [physical])['status'])

    def test_physical_requires_complete_critical_and_matching_callback(self):
        event = dict(frame=108, observer_id=3, target_id=1, stages=2, critical_stages=2,
                     min_fixed=256, max_fixed=512, roll=100, critical_chance=5, critical_roll=50)
        hits = [dict(frame=108, hit_result=1)]
        damage, _ = self.damage_fixture(1)
        damage[0].update(physical_fixed=356, total_fixed=356)
        callbacks = [dict(callback_frame=108, type=0, observed=True,
                          player_hp_before_callback=1000, player_hp_after_callback=644,
                          defender_flat_physical_reduction=0, defender_physical_resistance=0)]
        stats = dict(observer_id=3, target_id=1, attacker={})
        report = attack_physical_observations([event], hits, damage, callbacks, stats, 3, 1)
        self.assertEqual(1, report['hp_matches_settlement'])
        for change in (dict(critical_stages=0), dict(target_id=2), dict(roll=256)):
            with self.assertRaises(ValueError):
                attack_physical_observations([dict(event, **change)], hits, damage, callbacks, stats, 3, 1)
        with self.assertRaises(ValueError):
            attack_physical_observations([event], hits, damage, [], stats, 3, 1)
        missing = attack_physical_observations([], hits, damage, callbacks, stats, 3, 1)
        self.assertEqual('incomplete', missing['status'])
        self.assertEqual([108], missing['successful_settlements_without_trace'])

    def test_hit_roll_rejects_partial_identity_and_false_result(self):
        event = dict(observer_id=3, target_id=1, stages=5, chance=5, roll=53, hit_result=0)
        self.assertEqual(1, attack_hit_roll_observations([event], 3, 1)['misses'])
        for change in (dict(stages=0), dict(target_id=2), dict(hit_result=1), dict(roll=100)):
            with self.assertRaises(ValueError):
                attack_hit_roll_observations([dict(event, **change)], 3, 1)
        self.assertEqual(0, attack_hit_roll_observations([], 3, 1)['samples'])

    def test_direct_attack_profile_remains_a_finite_collector_contract(self):
        from collector_specs import validate_collector
        self.assertEqual('d2game_fallen_death.exe',
                         validate_collector('fallen_death', {'profile': 'attack-direct'}).name)
        self.assertEqual('d2game_fallen_death.exe',
                         validate_collector('fallen_death', {'profile': 'attack-repeat'}).name)
        with self.assertRaises(ValueError):
            validate_collector('fallen_death', {'profile': 'attack-direct', 'function': 123})

    def damage_fixture(self, flags=0):
        entry = dict(frame=108, observer_id=3, attacker_id=3, attacker_type=1,
                     victim_id=1, victim_type=0, fixture_attacker=True, player_victim=True,
                     stage='settlement_entry', result_flags=flags)
        returned = dict(entry, stage='settlement_return')
        callbacks = [dict(callback_frame=108, type=0, observed=True)]
        return [entry, returned], callbacks

    def test_damage_success_block_and_unsuccessful_are_distinct(self):
        for flags, success, block in [(0, 0, 0), (1, 1, 0), (0x10, 0, 1)]:
            events, callbacks = self.damage_fixture(flags)
            report = attack_damage_observations(events, callbacks, 3, 1)
            self.assertEqual(success, report['successful_hit_flag'])
            self.assertEqual(1-success, report['unsuccessful_hit_flag'])
            self.assertEqual(block, report['block_flag'])

    def test_damage_return_and_identity_are_required(self):
        events, callbacks = self.damage_fixture()
        with self.assertRaises(ValueError):
            attack_damage_observations(events[:1], callbacks, 3, 1)
        events[0]['attacker_id'] = 4
        with self.assertRaises(ValueError):
            attack_damage_observations(events, callbacks, 3, 1)

    def test_no_damage_entry_is_incomplete_not_a_proven_miss(self):
        self.assertEqual('incomplete', attack_damage_observations([], [], 3, 1)['status'])

    def test_callback_bracket_separates_hp_loss_from_whole_update(self):
        event = dict(observer_id=3, type=0, expected_frame=108, callback_frame=108,
                     return_frame=108, observed=True, player_hp_before_update=100,
                     player_hp_after_update=90, player_hp_before_callback=95,
                     player_hp_after_callback=95)
        report = attack_callback_observations([event], 3)
        self.assertEqual(1, report['attack_callbacks'])
        self.assertEqual([], report['callback_bracket_hp_decreases'])
        event['player_hp_after_callback'] = 94
        self.assertEqual(1, len(attack_callback_observations([event], 3)['callback_bracket_hp_decreases']))

    def test_callback_identity_duplicate_and_frame_mismatch_rejected(self):
        event = dict(observer_id=3, type=0, expected_frame=108, callback_frame=108,
                     return_frame=108, observed=True)
        with self.assertRaises(ValueError):
            attack_callback_observations([event], 4)
        with self.assertRaises(ValueError):
            attack_callback_observations([event, event], 3)
        event['return_frame'] = 109
        with self.assertRaises(ValueError):
            attack_callback_observations([event], 3)

    def test_missing_callback_is_incomplete_not_negative_execution_proof(self):
        event = dict(observer_id=3, type=0, expected_frame=108, observed=False)
        self.assertEqual('incomplete', attack_callback_observations([event], 3)['status'])
        self.assertEqual('incomplete', attack_callback_observations([], 3)['status'])

    def test_callback_trace_must_cover_all_due_events(self):
        rows = [dict(observer_mode=4, frame=107, event_modechange_frame=108),
                dict(observer_mode=4, frame=108)]
        with self.assertRaises(ValueError):
            attack_callback_observations([], 3, rows)

    def test_animation_state_outside_length_is_not_a_relative_cursor(self):
        rows = [dict(step=i, frame=100+i, observer_mode=mode, anim_frame_fp8=25600,
                     anim_length_fp8=2560, anim_speed=256, anim_action=0,
                     event_modechange_frame=108, event_endanim_frame=111,
                     event_modechange_id=1) for i, mode in enumerate([1, 4, 4, 1])]
        span = attack_animation_observations(rows)['spans'][0]
        self.assertTrue(span['complete'])
        self.assertFalse(span['valid_relative_cursor'])
        self.assertEqual([], span['action_samples'])
        self.assertEqual(7, span['scheduled_events'][0]['modechange_offset'])
        self.assertEqual(10, span['scheduled_events'][0]['endanim_offset'])

    def test_animation_capture_missing_or_partial_is_not_silently_inferred(self):
        self.assertEqual('not_captured', attack_animation_observations([{}])['status'])
        with self.assertRaises(ValueError):
            attack_animation_observations([dict(anim_frame_fp8=0, anim_length_fp8=256,
                                               anim_speed=256, anim_action=0), {}])

    def test_animation_edge_span_is_truncated_and_frame_gaps_rejected(self):
        rows = [dict(step=i, frame=100+i, observer_mode=4, anim_frame_fp8=i*256,
                     anim_length_fp8=2560, anim_speed=256, anim_action=0) for i in range(2)]
        self.assertFalse(attack_animation_observations(rows)['spans'][0]['complete'])
        rows[1]['frame'] += 1
        with self.assertRaises(ValueError):
            attack_animation_observations(rows)

    def test_path_change_separates_flee_from_later_walking(self):
        rows = [dict(step=i, observer_mode=2, ai_param0=1, observer_x=22 if i else 10,
                     observer_y=10, path_target_x=22 if i == 0 else 23, path_target_y=10,
                     path_velocity=1600 if i == 0 else 960, player_x=9, player_y=10) for i in range(3)]
        report = recovery_observations(rows)
        self.assertEqual(1, report['first_movement_phase_change_step'])
        self.assertTrue(report['at_initial_target_on_phase_change'])
        self.assertEqual('unobserved', report['attack_recovery'])
        rows[-1]['observer_mode'] = 4
        self.assertEqual(2, recovery_observations(rows)['first_attack_step'])

    def test_incomplete_movement_is_not_reported_as_complete(self):
        rows = [dict(step=0, observer_mode=2, ai_param0=1, observer_x=10, observer_y=10,
                     path_target_x=22, path_target_y=10, path_velocity=1600, player_x=9, player_y=10)]
        report = recovery_observations(rows)
        self.assertIsNone(report['first_movement_phase_change_step'])
        self.assertIsNone(report['at_initial_target_on_phase_change'])

    def test_recovery_requires_path_evidence(self):
        with self.assertRaises(ValueError): recovery_observations([{'step': 0}])

    def controlled_rows(self):
        return [dict(step=i, frame=100+i, corpse_mode=1 if i == 0 else 0 if i < 25 else 12,
                     observer_mode=2 if 2 <= i < 53 else 1, observer_x=10 if i < 4 else 11,
                     observer_y=10, ai_param0=0 if i < 2 else 1,
                     ring0=22 if i else 0, ring1=0, ring2=0, ring3=0) for i in range(121)]

    def test_controlled_death_and_delayed_displacement(self):
        report = controlled_frames(self.controlled_rows(), {'corpse_id': 22})
        self.assertEqual(1, report['death_step'])
        self.assertEqual(25, report['corpse_dd_step'])
        self.assertEqual(4, report['first_displacement_step'])
        self.assertEqual('untested', report['ai_parity'])

    def test_controlled_trace_rejects_missing_frame(self):
        rows = self.controlled_rows()
        rows[20]['frame'] += 1
        with self.assertRaises(ValueError): controlled_frames(rows, {'corpse_id': 22})

    def test_controlled_trace_requires_native_ring(self):
        with self.assertRaises(ValueError): controlled_frames(self.controlled_rows(), {'corpse_id': 999})

    def test_fifth_death_excludes_first_guid(self):
        rows = self.controlled_rows()
        for item in rows: item['ring0'] = 99
        report = controlled_frames(rows, {'corpse_id': 22, 'death_count': 5})
        self.assertEqual('untested', report['ai_parity'])

    def test_fifth_death_must_not_keep_first_guid(self):
        with self.assertRaises(ValueError):
            controlled_frames(self.controlled_rows(), {'corpse_id': 22, 'death_count': 5})

    def test_finite_profiles_reject_arbitrary_parameters(self):
        from collector_specs import validate_collector
        for profile in ('distance-14', 'distance-15', 'ring-4', 'ring-5', 'adjacent-room'):
            self.assertEqual('d2game_fallen_death.exe', validate_collector('fallen_death', {'profile': profile}).name)
        for scenario in ({'profile': 'arbitrary'}, {'address': 1234}):
            with self.assertRaises(ValueError): validate_collector('fallen_death', scenario)

    def test_distance_fixture_requires_exact_coordinates(self):
        identity = dict(corpse_id=22, requested_distance=14, observer_x=10,
                        observer_y=10, corpse_x=23, corpse_y=13)
        controlled_frames(self.controlled_rows(), identity)
        identity['requested_distance'] = 15
        with self.assertRaises(ValueError): controlled_frames(self.controlled_rows(), identity)

    def test_adjacent_fixture_requires_distinct_room_in_native_list(self):
        identity = dict(corpse_id=22, adjacent_room_fixture=True, room=100,
                        death_room=200, adjacent_rooms=[100, 200])
        controlled_frames(self.controlled_rows(), identity)
        identity['adjacent_rooms'] = [100]
        with self.assertRaises(ValueError): controlled_frames(self.controlled_rows(), identity)
        identity.update(death_room=100)
        with self.assertRaises(ValueError): controlled_frames(self.controlled_rows(), identity)

    def test_no_response_during_dt_remains_an_observation(self):
        rows = self.controlled_rows()
        for item in rows: item['ai_param0'] = 0
        report = controlled_frames(rows, {'corpse_id': 22})
        self.assertEqual([], report['walking_with_param0_one_steps'])
        self.assertIsNone(report['first_displacement_step'])
        self.assertEqual('untested', report['ai_parity'])

    def test_idle_does_not_pass_as_death_reaction(self):
        report = summarize([row(10), row(20)])
        self.assertEqual('incomplete_no_death_observed', report['status'])
        self.assertEqual('untested', report['ai_parity'])

    def test_observed_death_has_uncertainty_bracket(self):
        report = summarize([row(10), row(20, mode=0)])
        self.assertEqual([10, 21], report['deaths'][0]['frame_bracket'])
        self.assertEqual('untested', report['ai_parity'])

    def test_initial_corpse_is_not_new_death(self):
        self.assertEqual([], summarize([row(10, mode=0), row(20, mode=12)])['deaths'])

    def test_disappearance_does_not_create_transition(self):
        self.assertEqual([], summarize([row(10), row(15, present=False), row(20, mode=0)])['deaths'])

    def test_command_parameter_is_not_classified_as_flee(self):
        report = summarize([row(10), row(20, param=1)])
        self.assertEqual('incomplete_no_death_observed', report['status'])
        self.assertEqual(1, len(report['fallen_parameter_candidates']))

    def test_cleanup_and_sample_count_required(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'server-ai-samples.jsonl'
            path.write_text(json.dumps(row(10)), encoding='utf-8')
            summary = {'status': 'passed', 'detached': False, 'cleanup_confirmed': True, 'server_samples': 1}
            path.with_name('summary.json').write_text(json.dumps(summary), encoding='utf-8')
            with self.assertRaises(ValueError): analyze(path)
            summary.update(detached=True, server_samples=2)
            path.with_name('summary.json').write_text(json.dumps(summary), encoding='utf-8')
            with self.assertRaises(ValueError): analyze(path)
            summary['server_samples'] = 1
            path.with_name('summary.json').write_text(json.dumps(summary), encoding='utf-8')
            self.assertEqual(64, len(analyze(path)['source_sha256']))


if __name__ == '__main__':
    unittest.main()
