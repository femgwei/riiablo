"""Pinned local static allocation/rewind evidence. Does not execute target DLLs."""
import argparse
import hashlib
from pathlib import Path
import capstone
import pefile


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('home', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    data = (args.home / 'D2Client.dll').read_bytes()
    sha = hashlib.sha256(data).hexdigest()
    assert sha == '94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282'
    pe = pefile.PE(data=data)
    base = pe.OPTIONAL_HEADER.ImageBase
    decoder = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_32)
    lines = ['Local static Capstone disassembly; not runtime or REA Evidence.',
        'D2Client.dll sha256=' + sha,
        'CltSt15 has its own state-dependent point8 / remaining5 rewind. A rewind alone cannot identify CltDo24.',
        'Allocator increments global counter RVA F08FC and synchronously links the unit by type/id.',
        'This does not prove when any call ran in the collected process.']
    for label, address, size in [
        ('CltSt15, including existing-state rewind', 0x2A050, 0x114),
        ('CltDo24 create, then state-dependent rewind', 0x2A2FE, 0xA0),
        ('Client allocator counter and unit initialization call', 0x87D50, 0xBC),
        ('Allocation initializer invokes hash insertion', 0x87219, 0x49),
        ('Hash insertion selects secondary table for local units', 0x874F2, 0x7A)]:
        lines.append(label + ' RVA=' + hex(address))
        lines += ['%08X %s %s' % (i.address-base, i.mnemonic, i.op_str)
            for i in decoder.disasm(pe.get_data(address, size), base+address)]
    pe.close()
    args.output.write_text('\n'.join(lines)+'\n', encoding='utf8')
