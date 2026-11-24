"""Reproducible offline 1.10f Cyclone Armor setfunc8/remfunc5 evidence."""
import argparse
import hashlib
import json
import struct
from pathlib import Path

import capstone
import pefile


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--dll', type=Path, default=Path(
        'G:/BaiduNetdiskDownload/Diablo II 1.10F/D2Client.dll'))
    parser.add_argument('--output', type=Path, default=Path(
        'tools/original-observer/cyclone-armor-static-1.10f-2026-11-23.json'))
    args = parser.parse_args()
    raw = args.dll.read_bytes()
    digest = hashlib.sha256(raw).hexdigest()
    if digest != '94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282':
        raise SystemExit('Offsets require the recorded 1.10f D2Client hash')
    pe = pefile.PE(data=raw)
    base = pe.OPTIONAL_HEADER.ImageBase
    mem = pe.get_memory_mapped_image()
    assert struct.unpack_from('<I', mem, 0x6FB93EE0-base+8*4)[0] == 0x6FB4C560
    assert struct.unpack_from('<I', mem, 0x6FB93F60-base+5*4)[0] == 0x6FB4C3D0
    imports = {i.address: ('%s#%s' % (d.dll.decode(), i.ordinal) if i.name is None
                          else '%s!%s' % (d.dll.decode(), i.name.decode()))
               for d in pe.DIRECTORY_ENTRY_IMPORT for i in d.imports}
    md = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_32)

    def instructions(start, end):
        out = []
        for ins in md.disasm(mem[start-base:end-base], start):
            row = {'va': hex(ins.address), 'rva': hex(ins.address-base),
                   'bytes': ins.bytes.hex(), 'mnemonic': ins.mnemonic, 'operands': ins.op_str}
            if ins.mnemonic == 'call' and ins.op_str.startswith('0x'):
                target = int(ins.op_str, 16)
                stub = mem[target-base:target-base+6]
                if stub[:2] == b'\xff\x25':
                    row['import'] = imports.get(int.from_bytes(stub[2:6], 'little'))
            out.append(row)
        return out

    evidence = {
        'schema': 1, 'evidence_type': 'offline local PE/Capstone, NOT live REA evidence',
        'binary': str(args.dll.resolve()), 'sha256': digest, 'image_base': hex(base),
        'target_executed': False, 'foreground_used': False,
        'set_dispatch': {'table': '0x6fb93ee0', 'index': 8, 'target': '0x6fb4c560'},
        'remove_dispatch': {'table': '0x6fb93f60', 'index': 5, 'target': '0x6fb4c3d0'},
        'set_function': instructions(0x6FB4C560, 0x6FB4C855),
        'remove_function': instructions(0x6FB4C3D0, 0x6FB4C557),
        'interpretation': {
            'stats': {'132': 'bonearmor', '133': 'bonearmormax', '355': 'shortparam1 (previous visual count)'},
            'third': 'signed integer maximum / 3 (truncation toward zero)',
            'count': '3; if current < third then 2; if current < 2*third then decrement once',
            'creation': 'For each enabled overlay1..3, create its numeric ID and ID+1, mode 3',
            'increase': 'When count > previous, remfunc5 clears all pairs, then recreate enabled pairs',
            'decrease': 'Remove higher disabled pairs, preserve surviving lower animation objects',
            'retail_lowest_group_quirk': 'At 0x6fb4c7fb compares first overlay numeric ID, not count, against 1; positive pool always retains at least group one',
            'remove': 'For all three configured IDs and ID+1, remove the 1ofN range returned by D2Common#10680'},
        'limits': ['Static behavior only, no original gameplay or GPU frames captured',
                   'Retail 1ofN=1 overlays are the supported reconstruction scope',
                   'Riiablo treats zero/missing pool as no visual; exhausted authoritative armor removes the state']}
    args.output.write_text(json.dumps(evidence, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'output': str(args.output), 'sha256': digest, 'setfunc8_rva': '0xac560'}))


if __name__ == '__main__':
    main()
