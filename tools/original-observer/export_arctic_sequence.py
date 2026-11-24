"""Retain Arctic Blast SQ state and its hash-pinned in-process sequence table."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
from export_arctic_blast import export

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('run', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    export(args.run, args.output)
    result = json.loads(args.output.read_text(encoding='utf8'))
    save = args.run / 'orchestrator/worker' / (result['loadout']['name'] + '-input.d2s')
    result['loadout']['character_level'] = save.read_bytes()[43]
    tables = []
    for capture in result['captures']:
        summary = json.loads((args.run / 'orchestrator/worker' / capture['name'] / 'summary.json').read_text(encoding='utf8'))
        assert summary['sequence_table_rva'] == 0x9FE10
        raw = bytes.fromhex(summary['sequence_table_hex'])[:90]
        records = [dict(zip(('sequence', 'mode', 'frame', 'direction', 'event'), struct.unpack_from('<H4B', raw, i*6))) for i in range(15)]
        assert [r['frame'] for r in records] == [0,1,2,3,4,5,6,7,8,9,9,10,11,12,13]
        assert all(r['mode'] == 13 and r['event'] == (1 if i == 10 else 0) for i,r in enumerate(records))
        tables.append(dict(rva=summary['sequence_table_rva'], records=records,
            sha256=hashlib.sha256(raw).hexdigest()))
        changes = []
        previous = None
        for state in capture['caster_states']:
            signature = tuple(state[k] for k in ('mode','sequence_cursor','cursor','anim_length','inferno_state','held'))
            if signature != previous:
                changes.append(state)
                previous = signature
            if state['mode'] != 18 and changes:
                break
        capture['sequence_transitions'] = changes
        capture.pop('caster_states')
    assert tables[0] == tables[1]
    result['sequence_table'] = tables[0]
    result['scope'] = 'Read-only client SQ/INFERNO state polling and loaded sequence table; no original stack or GPU trace'
    args.output.write_text(json.dumps(result, indent=2)+'\n', encoding='utf8')
    print('Retained sequence transitions:', [len(c['sequence_transitions']) for c in result['captures']])
