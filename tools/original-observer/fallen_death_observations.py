"""Find death/flee candidates in asynchronous samples; never assert AI parity."""
import argparse
import csv
import hashlib
import json
from pathlib import Path


def summarize(rows, fallen_classes=(19,)):
    previous = {}
    deaths, reactions = [], []
    player_modes = set()
    for row in rows:
        current = {}
        for unit in row['units']:
            key = (unit['type'], unit['unit_id'], unit['class_id'])
            current[key] = (row, unit)
            if unit['type'] == 0:
                player_modes.add(unit['mode'])
            old = previous.get(key)
            if not old or unit['type'] != 1:
                continue
            old_row, old_unit = old
            interval = [old_row['frame_before'], row['frame_after']]
            if old_unit['mode'] not in (0, 12) and unit['mode'] == 0:
                deaths.append({'unit_id': unit['unit_id'], 'class_id': unit['class_id'],
                               'frame_bracket': interval, 'position': [unit.get('x'), unit.get('y')]})
            old_param = old_unit.get('ai_param')
            param = unit.get('ai_param')
            if (unit['class_id'] in fallen_classes and old_param and param
                    and old_param[0] != 1 and param[0] == 1):
                reactions.append({'unit_id': unit['unit_id'], 'frame_bracket': interval,
                                  'mode': unit['mode'],
                                  'interpretation': 'AI parameter 0 became 1; command and flee both set it'})
        # Require consecutive observations: disappearance/reappearance is not
        # a demonstrated mode transition or a stable entity lifetime.
        previous = current
    eligible = bool(deaths)
    return {'status': 'death_observed_needs_controlled_replay' if eligible else 'incomplete_no_death_observed',
            'ai_parity': 'untested', 'atomic': False, 'samples': len(rows),
            'player_modes': sorted(player_modes), 'deaths': deaths,
            'fallen_parameter_candidates': reactions,
            'limitations': ['Frame brackets are observation bounds, not exact event frames.',
                            'No room death-ring, room adjacency, target or collision proof is captured.',
                            'AI parameter 0 or walking alone does not prove a flee reaction.',
                            'GUID reuse between samples cannot be ruled out; transitions are candidates.',
                            'No observed death cannot prove that no death occurred between samples.']}


def analyze(samples):
    samples = Path(samples)
    summary = json.loads(samples.with_name('summary.json').read_text(encoding='utf-8-sig'))
    if not (summary.get('status') == 'passed' and summary.get('detached') is True
            and summary.get('cleanup_confirmed') is True):
        raise ValueError('collector cleanup/detach was not confirmed')
    raw = samples.read_bytes()
    rows = [json.loads(line) for line in raw.decode('utf-8').splitlines() if line.strip()]
    if not rows:
        raise ValueError('empty server samples')
    if summary.get('server_samples') != len(rows):
        raise ValueError('sample count does not match collector summary')
    result = summarize(rows)
    result.update(source=str(samples.resolve()), source_sha256=hashlib.sha256(raw).hexdigest())
    return result


def attack_damage_observations(events, callbacks, observer_id, player_id):
    attack_frames = {e['callback_frame'] for e in callbacks if e['observed'] and e['type'] == 0}
    entries, returned = {}, set()
    for event in events:
        if event['observer_id'] != observer_id or event['frame'] not in attack_frames:
            raise ValueError('damage trace outside observed attack callback')
        if event['fixture_attacker'] != (event['attacker_id'] == observer_id and event['attacker_type'] == 1):
            raise ValueError('damage attacker identity mismatch')
        if event['player_victim'] != (event['victim_id'] == player_id and event['victim_type'] == 0):
            raise ValueError('damage victim identity mismatch')
        key = (event['frame'], event['attacker_type'], event['attacker_id'], event['victim_type'], event['victim_id'])
        if event['stage'] == 'settlement_entry':
            if key in entries:
                raise ValueError('duplicate settlement entry')
            entries[key] = event
        elif event['stage'] == 'settlement_return':
            if key not in entries or key in returned:
                raise ValueError('unmatched settlement return')
            returned.add(key)
        elif event['stage'] != 'melee_entry':
            raise ValueError('unknown damage trace stage')
    if set(entries) != returned:
        raise ValueError('settlement return missing')
    fixture = [e for e in entries.values() if e['fixture_attacker'] and e['player_victim']]
    return {'status': 'observed' if fixture else 'incomplete',
            'fixture_player_settlements': len(fixture),
            'successful_hit_flag': sum(bool(e['result_flags'] & 1) for e in fixture),
            'unsuccessful_hit_flag': sum(not e['result_flags'] & 1 for e in fixture),
            'block_flag': sum(bool(e['result_flags'] & 0x10) for e in fixture),
            'dodge_avoid_evade_flags': sum(bool(e['result_flags'] & 0x380) for e in fixture),
            'entries': fixture,
            'scope': 'One-shot near-melee/settlement probes inside actual observer callback; entry flags are native combat results, not a same-seed damage parity claim.'}


def attack_callback_observations(events, observer_id, rows=None):
    seen = set()
    for event in events:
        if event['observer_id'] != observer_id or event['type'] not in (0, 1):
            raise ValueError('callback identity/type mismatch')
        key = (event['type'], event['expected_frame'])
        if key in seen:
            raise ValueError('duplicate callback observation')
        seen.add(key)
        if event['observed'] and (event['callback_frame'] != event['expected_frame']
                                  or event['return_frame'] != event['callback_frame']):
            raise ValueError('callback frame mismatch')
    if rows is not None:
        expected = set()
        for row in rows[:-1]:
            if row['observer_mode'] not in (4, 5):
                continue
            for kind, field in ((0, 'event_modechange_frame'), (1, 'event_endanim_frame')):
                if row.get(field) == row['frame'] + 1:
                    expected.add((kind, row[field]))
        if seen != expected:
            raise ValueError('callback trace does not cover pending attack events')
    observed = [e for e in events if e['observed']]
    attacks = [e for e in observed if e['type'] == 0]
    bracketed = [e for e in attacks if 'player_hp_before_callback' in e
                 and 'player_hp_after_callback' in e]
    decreases = [e for e in bracketed if e['player_hp_after_callback'] < e['player_hp_before_callback']]
    return {'status': 'observed' if events and len(observed) == len(events) else 'incomplete',
            'scheduled_callbacks': len(events), 'observed_callbacks': len(observed),
            'attack_callbacks': len(attacks), 'end_callbacks': sum(e['type'] == 1 for e in observed),
            'attack_callbacks_with_hp_bracket': len(bracketed),
            'callback_bracket_hp_decreases': decreases,
            'scope': 'Actual per-mode callback entry and dispatcher return. HP bracket covers only this callback plus dispatcher epilogue. No RNG/damage-formula parity or client rendering claim.'}


def attack_animation_observations(rows):
    """Pre-update snapshots of animation state, not damage or client frames."""
    required = ('anim_frame_fp8', 'anim_length_fp8', 'anim_speed', 'anim_action')
    if not rows or not all(key in rows[0] for key in required):
        return {'status': 'not_captured'}
    if any(not all(key in row for key in required) for row in rows):
        raise ValueError('partial animation capture')
    if any(b['frame'] != a['frame'] + 1 for a, b in zip(rows, rows[1:])):
        raise ValueError('animation samples are not consecutive')
    spans, start = [], 0
    for end in range(1, len(rows) + 1):
        if end < len(rows) and rows[end]['observer_mode'] == rows[start]['observer_mode']:
            continue
        group = rows[start:end]
        if group[0]['observer_mode'] in (4, 5):
            spans.append({
                'mode': group[0]['observer_mode'], 'start_step': group[0]['step'],
                'duration_ticks': len(group),
                'complete': start > 0 and end < len(rows),
                'frame_state_fp8': [r['anim_frame_fp8'] for r in group],
                'valid_relative_cursor': all(0 <= r['anim_frame_fp8'] < r['anim_length_fp8'] for r in group),
                'length_fp8': sorted({r['anim_length_fp8'] for r in group}),
                'speed': sorted({r['anim_speed'] for r in group}),
                'action_samples': [{'step': r['step'], 'offset': i,
                                    'cursor_fp8': r['anim_frame_fp8'], 'action': r['anim_action']}
                                   for i, r in enumerate(group) if r['anim_action']],
                'scheduled_events': [
                    {'step': r['step'], 'modechange_frame': r['event_modechange_frame'],
                     'endanim_frame': r['event_endanim_frame'],
                     'modechange_id': r['event_modechange_id'],
                     'modechange_offset': r['event_modechange_frame'] - group[0]['frame']
                         if r['event_modechange_frame'] else None,
                     'endanim_offset': r['event_endanim_frame'] - group[0]['frame']
                         if r['event_endanim_frame'] else None}
                    for r in group if 'event_modechange_frame' in r],
            })
        start = end
    return {'status': 'captured', 'spans': spans,
            'scope': 'Server pre-update state and planned events, not callback execution, damage or client presentation. Frame state is not assumed to be a relative animation cursor.'}


def controlled_frames(rows, identity):
    if identity.get('requested_distance', 0):
        dx = abs(identity['observer_x'] - identity['corpse_x'])
        dy = abs(identity['observer_y'] - identity['corpse_y'])
        if max(dx, dy) + min(dx, dy) // 2 != identity['requested_distance']:
            raise ValueError('fixture coordinates do not match requested native distance')
    if identity.get('adjacent_room_fixture'):
        if identity['room'] == identity['death_room'] or identity['death_room'] not in identity.get('adjacent_rooms', []):
            raise ValueError('adjacent-room fixture lacks native room-list evidence')
    count = identity.get('observation_frames', 120) + 1
    if count not in (121, 601, 1041) or len(rows) != count or any(row['step'] != index for index, row in enumerate(rows)):
        raise ValueError('unexpected observation length or non-consecutive steps')
    if any(row['frame'] != rows[0]['frame'] + index for index, row in enumerate(rows)):
        raise ValueError('non-contiguous game frames')
    death = next((row for row in rows if row['corpse_mode'] == 0), None)
    if death is None:
        raise ValueError('no native DT observed')
    present = identity['corpse_id'] in [death['ring' + str(i)] for i in range(4)]
    if present != (identity.get('death_count', 1) <= 4):
        raise ValueError('corpse GUID ring membership does not match death count')
    # This is a per-update snapshot, not instrumentation of every think call.
    # Param0 can also mean an attack order; retain facts rather than declaring parity.
    active = [row for row in rows if row['step'] >= death['step'] and row['corpse_mode'] == 0]
    marked = [row for row in active if row['ai_param0'] == 1]
    walking = [row for row in marked if row['observer_mode'] == 2]
    departed = [row for row in walking if (row['observer_x'], row['observer_y']) !=
                (death['observer_x'], death['observer_y'])]
    done = next((row for row in rows if row['step'] > death['step'] and row['corpse_mode'] == 12), None)
    return {'status': 'controlled_death_observed', 'ai_parity': 'untested',
            'samples': len(rows), 'identity': identity, 'death_step': death['step'],
            'death_frame': death['frame'], 'corpse_dd_step': done['step'] if done else None,
            'param0_one_during_dt_steps': [row['step'] for row in marked],
            'walking_with_param0_one_steps': [row['step'] for row in walking],
            'first_displacement_step': departed[0]['step'] if departed else None,
            'limitations': ['Native DT is forced through the original mode-change functions; damage and kill attribution are not exercised.',
                            'No exact think-call, target, escape-path or RNG parity with riiablo is established.',
                            'No response is inconclusive when no AI think occurs during DT.']}


def recovery_observations(rows):
    required = ('path_velocity', 'path_target_x', 'path_target_y', 'player_x', 'player_y')
    if not rows or any(key not in row for row in rows for key in required):
        raise ValueError('recovery trace lacks path/player evidence')
    flight = next((row for row in rows if row['observer_mode'] == 2 and row['ai_param0'] == 1), None)
    if flight is None:
        return {'status': 'incomplete_no_initial_flee_walk', 'attack_recovery': 'unobserved'}
    target = (flight['path_target_x'], flight['path_target_y'])
    changed = next((row for row in rows if row['step'] > flight['step'] and (
        row['observer_mode'] != 2 or row['path_velocity'] != flight['path_velocity'] or
        (row['path_target_x'], row['path_target_y']) != target)), None)
    attack = next((row for row in rows if row['step'] > flight['step'] and row['observer_mode'] in (4, 5)), None)
    stationary = len({(row['player_x'], row['player_y']) for row in rows}) == 1
    return {'status': 'movement_phase_observed', 'initial_walk_step': flight['step'],
            'initial_target': list(target), 'initial_path_velocity_raw': flight['path_velocity'],
            'first_movement_phase_change_step': changed['step'] if changed else None,
            'at_initial_target_on_phase_change': ((changed['observer_x'], changed['observer_y']) == target) if changed else None,
            'next_path_velocity_raw': changed['path_velocity'] if changed else None,
            'next_target': [changed['path_target_x'], changed['path_target_y']] if changed else None,
            'first_attack_step': attack['step'] if attack else None,
            'attack_recovery': 'observed_attack_mode' if attack else 'unobserved',
            'player_stationary': stationary,
            'scope': 'mode/path changes; a later WL segment is not automatically classified as fleeing'}


def attack_hit_roll_observations(events, observer_id, player_id):
    """Only complete native hit traces support an arithmetic/RNG comparison."""
    for event in events:
        if event.get('observer_id') != observer_id or event.get('target_id') != player_id:
            raise ValueError('hit-roll identity mismatch')
        if event.get('stages') != 5:
            raise ValueError('incomplete native hit-roll trace')
        if not 5 <= event['chance'] <= 95 or not 0 <= event['roll'] < 100:
            raise ValueError('invalid native hit-roll bounds')
        if event['hit_result'] not in (0, 1) or bool(event['hit_result']) != (event['roll'] < event['chance']):
            raise ValueError('native hit-roll result mismatch')
    return {'samples': len(events), 'hits': sum(e['hit_result'] for e in events),
            'misses': sum(1 - e['hit_result'] for e in events), 'records': events,
            'scope': 'Effective inputs and native hit draw only; not upstream stat derivation, block or damage parity.'}


def attack_physical_observations(events, hits, damage, callbacks, stats, observer_id, player_id):
    hit_by_frame = {e['frame']: e for e in hits if 'frame' in e}
    settlements = {e['frame']: e for e in damage if e['stage'] == 'settlement_entry'
                   and e['fixture_attacker'] and e['player_victim']}
    callback_by_frame = {e['callback_frame']: e for e in callbacks if e['observed'] and e['type'] == 0}
    if stats['observer_id'] != observer_id or stats['target_id'] != player_id:
        raise ValueError('physical stats identity mismatch')
    records, seen = [], set()
    for event in events:
        frame = event['frame']
        if (event['observer_id'] != observer_id or event['target_id'] != player_id
                or frame in seen or event['stages'] != 2 or event.get('critical_stages') != 2):
            raise ValueError('invalid or incomplete physical/critical trace')
        seen.add(frame)
        hit, settled, callback = hit_by_frame.get(frame), settlements.get(frame), callback_by_frame.get(frame)
        if not hit or not hit['hit_result'] or not settled or not settled['result_flags'] & 1 or not callback:
            raise ValueError('physical trace lacks matching hit/settlement/callback')
        if (event['max_fixed'] <= event['min_fixed']
                or not 0 <= event['roll'] < event['max_fixed'] - event['min_fixed']
                or not 0 < event['critical_chance'] <= 100 or not 0 <= event['critical_roll'] < 100):
            raise ValueError('invalid physical/critical roll bounds')
        required = ('defender_flat_physical_reduction', 'defender_physical_resistance',
                    'player_hp_before_callback', 'player_hp_after_callback')
        if any(key not in callback for key in required):
            raise ValueError('missing callback mitigation/HP inputs')
        record = dict(event, attacker_stats=stats['attacker'],
                      physical_fixed=settled['physical_fixed'], total_fixed=settled['total_fixed'],
                      result_flags=settled['result_flags'],
                      hp_loss_fixed=callback['player_hp_before_callback'] - callback['player_hp_after_callback'],
                      flat_reduction=callback['defender_flat_physical_reduction'],
                      resistance=callback['defender_physical_resistance'])
        records.append(record)
    successful = {frame for frame, e in settlements.items() if e['result_flags'] & 1}
    missing = sorted(successful - seen)
    return {'status': 'observed' if records and not missing else 'incomplete',
            'samples': len(records), 'successful_settlements_without_trace': missing,
            'critical_hits': sum(e['critical_roll'] < e['critical_chance'] for e in records),
            'hp_matches_settlement': sum(e['hp_loss_fixed'] == e['total_fixed'] for e in records),
            'records': records,
            'scope': 'Recorded physical endpoints/draw, monster critical draw, defense stats and callback HP. Java replay separately checks production arithmetic; no full ECS or nonzero mitigation parity claim.'}


def intermediate_rng_observations(events, physical):
    by_frame = {e['frame']: e for e in physical}
    seen = set()
    for event in events:
        frame = event['frame']
        sample = by_frame.get(frame)
        if (sample is None or frame in seen or not event['fixture_seed']
                or event['observer_id'] != sample['observer_id']):
            raise ValueError('intermediate RNG identity/coverage mismatch')
        if event['function_rva'] != 0x1E4D0 or event['caller_rva'] != 0x8F1AF or event['bound'] != 1 or event['value'] != 0:
            raise ValueError('unrecognized intermediate RNG consumer')
        if any(event['seed_' + word] != sample['seed_after_' + word]
               or event['seed_after_' + word] != sample['critical_seed_' + word] for word in ('low', 'high')):
            raise ValueError('intermediate RNG seed chain mismatch')
        seen.add(frame)
    return {'status': 'observed' if events and seen == set(by_frame) else 'incomplete',
            'samples': len(events), 'records': events,
            'consumer': 'SUNITDMG_FillDamageValues burn placeholder, limited draw bound=1 at return RVA0x8F1AF',
            'scope': 'Closes the physical-to-monster-critical seed gap for captured ordinary Fallen attacks only.'}


def analyze_controlled(samples):
    samples = Path(samples)
    summary = json.loads(samples.with_name('summary.json').read_text(encoding='utf-8-sig'))
    for key in ('detached', 'cleanup_confirmed', 'fixtures_removed', 'native_death_observed', 'room_ring_verified'):
        if summary.get(key) is not True:
            raise ValueError('controlled fixture proof missing: ' + key)
    identity = json.loads(samples.with_name('identity.json').read_text(encoding='utf-8-sig'))
    if summary.get('status') != 'passed' or summary.get('samples') != identity.get('observation_frames', 120) + 1:
        raise ValueError('controlled collector did not complete')
    with samples.open(newline='', encoding='utf-8') as stream:
        rows = [{key: int(value) for key, value in row.items()} for row in csv.DictReader(stream)]
    result = controlled_frames(rows, identity)
    result['attack_animation'] = attack_animation_observations(rows)
    callbacks_path = samples.with_name('attack-callbacks.jsonl')
    if callbacks_path.exists():
        callbacks = [json.loads(line) for line in callbacks_path.read_text(encoding='utf-8').splitlines() if line]
        result['attack_callbacks'] = attack_callback_observations(callbacks, identity['observer_id'], rows)
        result['attack_callbacks']['source_sha256'] = hashlib.sha256(callbacks_path.read_bytes()).hexdigest()
        damage_path = samples.with_name('attack-damage.jsonl')
        if damage_path.exists():
            damage = [json.loads(line) for line in damage_path.read_text(encoding='utf-8').splitlines() if line]
            result['attack_damage'] = attack_damage_observations(damage, callbacks, identity['observer_id'], identity['player_id'])
            result['attack_damage']['source_sha256'] = hashlib.sha256(damage_path.read_bytes()).hexdigest()
    hit_path = samples.with_name('attack-hit-roll.jsonl')
    if hit_path.exists():
        hits = [json.loads(line) for line in hit_path.read_text(encoding='utf-8').splitlines() if line]
        result['attack_hit_roll'] = attack_hit_roll_observations(hits, identity['observer_id'], identity['player_id'])
        result['attack_hit_roll']['source_sha256'] = hashlib.sha256(hit_path.read_bytes()).hexdigest()
    physical_path, stats_path = samples.with_name('attack-physical-roll.jsonl'), samples.with_name('combat-stats.json')
    if physical_path.exists() and stats_path.exists():
        if not hit_path.exists() or not callbacks_path.exists() or not samples.with_name('attack-damage.jsonl').exists():
            raise ValueError('physical trace requires hit, callback and settlement sources')
        physical = [json.loads(line) for line in physical_path.read_text(encoding='utf-8').splitlines() if line]
        stats = json.loads(stats_path.read_text(encoding='utf-8'))
        result['attack_physical'] = attack_physical_observations(
            physical, hits, damage, callbacks, stats, identity['observer_id'], identity['player_id'])
        result['attack_physical']['source_sha256'] = hashlib.sha256(physical_path.read_bytes()).hexdigest()
        result['attack_physical']['stats_sha256'] = hashlib.sha256(stats_path.read_bytes()).hexdigest()
        intermediate_path = samples.with_name('attack-intermediate-rng.jsonl')
        if intermediate_path.exists():
            intermediate = [json.loads(line) for line in intermediate_path.read_text(encoding='utf-8').splitlines() if line]
            result['attack_intermediate_rng'] = intermediate_rng_observations(intermediate, physical)
            result['attack_intermediate_rng']['source_sha256'] = hashlib.sha256(intermediate_path.read_bytes()).hexdigest()
    if identity.get('observation_frames') == 600:
        result['recovery'] = recovery_observations(rows)
        result['recovery']['reengage_intervention_step'] = identity.get('reengage_step') or None
        if identity.get('reengage_step'):
            intervention = json.loads(samples.with_name('intervention.json').read_text(encoding='utf-8-sig'))
            if (intervention.get('step') != identity['reengage_step']
                    or intervention.get('observer_id') != identity['observer_id']
                    or intervention.get('target_id') != identity['player_id']):
                raise ValueError('reengagement intervention identity mismatch')
            result['recovery']['intervention'] = intervention
            result['recovery']['scope'] += '; explicit native walk-to-player intervention, not spontaneous return'
    intervention_path = samples.with_name('intervention.json')
    if intervention_path.exists():
        intervention = json.loads(intervention_path.read_text(encoding='utf-8-sig'))
        if intervention.get('action') == 'native direct A1':
            if (intervention.get('observer_id') != identity['observer_id']
                    or intervention.get('target_id') != identity['player_id']
                    or intervention.get('step') != (1008 if intervention.get('repeated_actions') == 64 else 0)
                    or rows[0]['observer_mode'] != 4):
                raise ValueError('direct attack intervention identity mismatch')
            result['attack_intervention'] = intervention
            result['limitations'].append('Explicit A1 action and pending AI-think deletion; not natural AI decision evidence.')
    events_path = samples.with_name('deaths.jsonl')
    if events_path.exists():
        events = [json.loads(line) for line in events_path.read_text(encoding='utf-8').splitlines() if line]
        if len(events) != identity.get('death_count', 1):
            raise ValueError('native death event count mismatch')
        if not events or events[0]['guid'] != identity['corpse_id']:
            raise ValueError('first native death is not the observed corpse')
        previous = None
        for event in events:
            if len(event['ring']) != 4 or event['guid'] not in event['ring']:
                raise ValueError('native death GUID missing in event ring')
            if previous is not None:
                if event['cursor'] != (previous['cursor'] + 1) % 4:
                    raise ValueError('native death cursor did not advance')
                expected = list(previous['ring'])
                expected[previous['cursor']] = event['guid']
                if expected != event['ring']:
                    raise ValueError('native ring slot overwrite mismatch')
            previous = event
        result['native_death_events'] = events
    result.update(source=str(samples.resolve()), source_sha256=hashlib.sha256(samples.read_bytes()).hexdigest())
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('samples', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    result = analyze_controlled(args.samples) if args.samples.name == 'frames.csv' else analyze(args.samples)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({k: result[k] for k in ('status', 'samples', 'player_modes') if k in result}, ensure_ascii=False))
