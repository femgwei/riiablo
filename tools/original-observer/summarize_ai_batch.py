"""Summarize paired observations without equating unlike fixtures to AI parity."""
import argparse
import csv
import json
import re
from pathlib import Path

MONSTERS = {'zombie1': 5, 'sk_archer1': 170, 'fallen1': 19,
            'fallenshaman1': 58, 'goatman1': 53}


def coverage(rows):
    if not rows:
        return {'samples': 0}
    modes = sorted({int(r['mode']) for r in rows})
    positions = {(float(r['x']), float(r['y'])) for r in rows}
    return dict(samples=len(rows), modes=modes, movement=len(positions) > 1,
                attack_mode=bool(set(modes) & {4, 5}),
                skill_modes=sorted(set(modes) & {8, 9, 10, 11}),
                first_action_tick=next((int(r['step']) for r in rows
                                       if int(r['mode']) not in (0, 1, 12)), None))


def summarize(native, port):
    results = [json.loads(s) for s in (port / 'results.jsonl').read_text(encoding='utf8').splitlines()]
    cases = []
    for name, klass in MONSTERS.items():
        matches = list(native.glob('*-' + name))
        original = {'status': 'not_collected'}
        if len(matches) == 1 and (matches[0] / 'summary.json').exists():
            directory = matches[0]
            summary = json.loads((directory / 'summary.json').read_text(encoding='utf8'))
            original = dict(summary=summary, path=str(directory.resolve()))
            if (directory / 'identity.json').exists():
                identity = json.loads((directory / 'identity.json').read_text(encoding='utf8'))
                if identity['class_id'] != klass:
                    raise ValueError('native class mismatch: ' + name)
                original['identity'] = identity
            if (directory / 'frames.csv').exists():
                rows = list(csv.DictReader((directory / 'frames.csv').open(encoding='utf8')))
                original['coverage'] = coverage(rows)
        host = []
        for result in results:
            if result['monster'] != name or result['status'] == 'NOT_RUN':
                continue
            if result['classId'] != klass:
                raise ValueError('port class mismatch: ' + name)
            states = []
            for line in (port / result['log']).read_text(encoding='utf8').splitlines():
                if ' State ' not in line:
                    continue
                fields = dict(re.findall(r'(\w+)=([^\s]+)', line))
                if int(fields['class_id']) != klass:
                    raise ValueError('port state class mismatch')
                fields['step'] = fields['tick']
                states.append(fields)
            host.append(dict(scenario=result['scenario'], status=result['status'],
                             coverage=coverage(states), special=result['special'],
                             log=str((port / result['log']).resolve())))
        cases.append(dict(monster=name, class_id=klass, original=original, riiablo=host,
                          ai_parity='not_evaluated'))
    return dict(status='first_pass_observations', cases=cases,
                limitations=['Original native Blood Moor and port synthetic arena differ',
                             'Player class, initial distance, unit seeds and helper populations are not aligned',
                             'Map seed equality alone does not align monster RNG',
                             'No forced actions; missing observed behavior is not proof of an AI defect',
                             'Original first pass does not stimulate death/flee/resurrection branches'])


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--native', type=Path, required=True)
    parser.add_argument('--port', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    value = summarize(args.native, args.port)
    args.output.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf8')
    print(json.dumps({'status': value['status'], 'cases': len(value['cases'])}))
