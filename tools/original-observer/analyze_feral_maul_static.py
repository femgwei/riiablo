"""Bounded offline PE/Capstone evidence for D2Client 1.10f state setfunc16."""
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
        'tools/original-observer/feral-maul-static-1.10f-2026-11-23.json'))
    args = parser.parse_args()
    raw = args.dll.read_bytes()
    digest = hashlib.sha256(raw).hexdigest()
    if digest != '94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282':
        raise SystemExit('Unrecognized D2Client: offsets are restricted to the recorded 1.10f binary')
    pe = pefile.PE(data=raw)
    base = pe.OPTIONAL_HEADER.ImageBase
    mem = pe.get_memory_mapped_image()
    unpack = lambda va: struct.unpack_from('<I', mem, va-base)[0]
    assert unpack(0x6FB93EE0 + 16*4) == 0x6FB4CDA0
    assert unpack(0x6FB93F60 + 11*4) == 0x6FB4CF50
    iat = {i.address: '{}#{}'.format(d.dll.decode(), i.ordinal)
           if i.name is None else '{}!{}'.format(d.dll.decode(), i.name.decode())
           for d in pe.DIRECTORY_ENTRY_IMPORT for i in d.imports}
    md = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_32)
    def disassemble(first, end):
        instructions = []
        for ins in md.disasm(mem[first-base:end-base], first):
            row = {'va': hex(ins.address), 'rva': hex(ins.address-base),
                   'bytes': ins.bytes.hex(), 'mnemonic': ins.mnemonic, 'operands': ins.op_str}
            if ins.mnemonic == 'call' and ins.op_str.startswith('0x'):
                target = int(ins.op_str, 16)
                stub = mem[target-base:target-base+6]
                if stub[:2] == b'\xff\x25':
                    row['import'] = iat.get(int.from_bytes(stub[2:6], 'little'))
            instructions.append(row)
        return instructions
    evidence = {
        'schema': 1, 'evidence_type': 'offline local PE/Capstone; NOT live REA evidence',
        'binary': str(args.dll.resolve()), 'sha256': digest, 'image_base': hex(base),
        'target_executed': False, 'foreground_used': False,
        'set_dispatch': {'table': '0x6fb93ee0', 'index': 16, 'target': '0x6fb4cda0',
                         'xref': disassemble(0x6FB4D986, 0x6FB4D9AC)},
        'remove_dispatch': {'table': '0x6fb93f60', 'index': 11, 'target': '0x6fb4cf50',
                            'xref': disassemble(0x6FB4DA30, 0x6FB4DA54)},
        'set_function': disassemble(0x6FB4CDA0, 0x6FB4CF4F),
        'remove_function': disassemble(0x6FB4CF50, 0x6FB4CFBF),
        'interpretation': {
            'state_stats': {'0xa9': 'skill_frenzy', '0x15e': 'modifierlist_skill',
                            '0x15f': 'modifierlist_level'},
            'skill_calc2': 'D2Common#11276 evaluates Skills.dwCalc[1], offset 0x13c',
            'front_count': 'Overlay2 - Overlay1 + 1 (numeric table indices)',
            'denominator': 'min(skill Calc2 at modifierlist_level, front_count)',
            'charge': 'min(skill_frenzy, front_count)',
            'front_index': '(charge-1) * front_count / denominator, signed integer division',
            'back_index': '(charge-1) * back_count / same denominator',
            'lifecycle': 'Remove non-selected IDs; retain an existing selected overlay; create only if missing.',
            'remfunc11': 'Remove all IDs in both inclusive overlay ranges.'},
        'limits': ['Static instructions establish the branch/selection arithmetic, not observed GPU frames.',
                   'No original game process was launched, patched, attached or stepped.',
                   'REA doctor found absent MCP registration and a native backend access failure; no configuration was changed.']}
    args.output.write_text(json.dumps(evidence, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'output': str(args.output), 'sha256': digest, 'setfunc16_rva': '0xacda0'}))


if __name__ == '__main__':
    main()
