"""Extract ordered native phase observations, without equating them to traced calls."""
import argparse
import hashlib
import json
from pathlib import Path


def load(path):
    return json.loads(path.read_text(encoding='utf8'))


def export(run):
    parent = load(run / 'orchestrator.json')
    assert parent['status'] == 'passed' and not parent['switched_desktop']
    assert parent['input_desktop_before'] == parent['input_desktop_after']
    assert parent['input_desktop_after'] != parent['private_desktop']
    assert parent['private_desktop_not_input_checks'] > 0
    assert parent['worker']['owned_game_closed']
    captures = []
    for name, count in [('birth-3', 3), ('birth-15', 15)]:
        directory = run / 'worker' / name
        summary = load(directory / 'summary.json')
        assert summary['status'] == 'passed'
        scans = load(directory / 'publication.json')
        stable = [s for s in scans if s['endpoint_markers_equal'] and s['before'] == s['after']]
        samples = [json.loads(line) for line in (directory / 'missiles.jsonl').read_text().splitlines()]
        by_snapshot = {}
        for sample in samples:
            by_snapshot[(sample['snapshot_index'], sample['unit_id'])] = sample
        units = {}
        for scan in stable:
            for unit in scan['units']:
                units.setdefault(unit['unit_id'], []).append((scan, unit))
        assert len(units) == count + 3
        births = []
        for uid, observations in units.items():
            first, unit = observations[0]
            assert unit['native_age'] == 0
            assert first['after']['client_allocation_counter'] == uid
            prior = [s for s in stable if s['snapshot_index'] < first['snapshot_index']
                and s['after']['action_frame'] == 1
                and s['after']['client_allocation_counter'] == uid - 1]
            assert prior, 'No stable pre-allocation action marker for unit %s' % uid
            previous = prior[-1]
            assert all(u['unit_id'] != uid for u in previous['units'])
            updated, next_unit = next((s, u) for s, u in observations if u['native_age'] > 0)
            assert next_unit['native_age'] == 2
            first_raw = by_snapshot[(first['snapshot_index'], uid)]
            update_raw = by_snapshot[(updated['snapshot_index'], uid)]
            # Relative motion, not an assumption about allocation-to-world origin centering.
            assert update_raw['x_fixed'] - first_raw['x_fixed'] == 2 * first_raw['velocity_x']
            assert update_raw['y_fixed'] - first_raw['y_fixed'] == 2 * first_raw['velocity_y']
            births.append(dict(unit_id=uid, class_id=unit['class_id'],
                pre_allocation_action=previous, first_published=first,
                first_observed_update=updated,
                first_native_age=unit['native_age'], first_observed_updated_age=next_unit['native_age'],
                first_position=[first_raw['x_fixed'], first_raw['y_fixed']],
                updated_position=[update_raw['x_fixed'], update_raw['y_fixed']]))
        captures.append(dict(name=name, requested_birth_boundary=count, total_births=len(units),
            scans=len(scans), equal_endpoint_scans=len(stable), rejected_endpoint_scans=len(scans)-len(stable),
            births=births, release_boundary=summary['release_boundary']))
    provenance = run / 'worker' / 'provenance'
    files = [run / 'orchestrator.json']
    for name in ['birth-3', 'birth-15']:
        files += [run / 'worker' / name / f for f in ['publication.json', 'missiles.jsonl', 'summary.json']]
    files += list(provenance.glob('*.py'))
    return dict(schema=1, run_id=run.parent.name, background_verified=True, owned_game_closed=True,
        captures=captures, source_hashes={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in files},
        limitations=[
            'Equal scan endpoints exclude detected transitions, but do not make reads atomic or exclude ABA changes.',
            'Action byte 1 is a unit animation marker, not a captured CltDo24 invocation.',
            'The first observed update at age2 does not prove that internal age1 or GPU submissions never occur.',
            'Native client allocation counter is global; this fixture checks its increment against each observed new unit ID.',
            'No original process suspension, memory write, injected call or stack capture.'])


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('run', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    result = export(args.run)
    args.output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf8')
    print([(c['name'], c['total_births'], c['equal_endpoint_scans']) for c in result['captures']])
