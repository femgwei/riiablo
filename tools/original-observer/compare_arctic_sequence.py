"""Compare observed caster source-frame stages, retaining phase/count differences."""
import argparse
import hashlib
import json
from pathlib import Path


def read(path):
    return json.loads(path.read_text(encoding='utf8'))


def compress(values):
    result = []
    for value in values:
        if not result or result[-1] != value:
            result.append(value)
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('original', 'production', 'output'):
        parser.add_argument(name, type=Path)
    args = parser.parse_args()
    original, production = read(args.original), read(args.production)
    assert original['run_id'] == production['source_run_id']
    assert original['loadout']['character_level'] == 99
    comparisons = []
    for observed, replay in zip(original['captures'], production['cases']):
        native = observed['sequence_transitions']
        states = replay['states']
        assert replay['hold_ticks'] * 40 == observed['identity']['hold_ms_requested']
        native_frames = compress(s['cursor'] >> 8 for s in native if s['mode'] == 18)
        local_frames = compress(s['source_frame'] for s in states if s['mode'] == 13)
        assert native_frames == local_frames == list(range(12))
        callbacks = replay['callback_ticks']
        assert all(b-a == 2 for a,b in zip(callbacks, callbacks[1:]))
        neutral = next(s['tick'] for s in states if s['mode'] == 1)
        assert states[-1]['mode'] == 1
        assert states[-1]['client_streams_alive'] == states[-1]['server_streams_alive'] == 0
        comparisons.append(dict(hold_ticks=replay['hold_ticks'], native_source_frame_stages=native_frames,
            local_source_frame_stages=local_frames, source_frame_stage_order_matches=True,
            native_observed_streams=len(observed['units']), local_callback_count=len(callbacks),
            stream_count_matches_callback_count=len(observed['units']) == len(callbacks),
            local_callback_intervals_ticks=[b-a for a,b in zip(callbacks, callbacks[1:])],
            native_release_tail_ms=native[-1]['elapsed_ms']-observed['release_ms_actual'],
            local_release_tail_ticks=neutral-replay['hold_ticks'],
            local_final_streams=0))
    result = dict(schema=1, skill_id=230, source_run_id=original['run_id'],
        comparison_status=('compared_calibrated_caster_counts_with_remaining_phase_differences'
            if production.get('time_reference')=='initialized_S1_source_frame_zero'
                and all(c['stream_count_matches_callback_count'] for c in comparisons)
            else 'compared_caster_source_frame_stages_with_remaining_phase_differences'),
        time_reference=production.get('time_reference','legacy_request_loop_includes_initialization'),
        comparisons=comparisons,
        evidence_sha256={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in (args.original,args.production)},
        limitations=['Source-frame stage order and two-tick callback intervals are compared; logical cursor/packet/absolute tick parity is not established',
            'Original release snapshots can alternate logical points9/10 while the local adapter retains its8/9 loop until state expiry; both display source frame9',
            'Native input clock is not aligned to local requests; count differences remain explicit and are not treated as passes',
            'No original server damage/mana, FCR variants, progress-overlay submissions, actual body/missile DCC directions or GPU pixels'])
    args.output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
    print(json.dumps(comparisons))
