"""Pinned offline target-reference validation; no original process is launched."""
import argparse
import hashlib
import struct
from pathlib import Path
import pefile
import capstone

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('home',type=Path);parser.add_argument('output',type=Path);args=parser.parse_args()
    decoder=capstone.Cs(capstone.CS_ARCH_X86,capstone.CS_MODE_32)
    data=(args.home/'D2Client.dll').read_bytes()
    sha=hashlib.sha256(data).hexdigest();assert sha=='94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282'
    pe=pefile.PE(data=data);base=pe.OPTIONAL_HEADER.ImageBase
    imports={x.address:(e.dll.lower(),x.ordinal) for e in pe.DIRECTORY_ENTRY_IMPORT for x in e.imports}
    for thunk,ordinal in [(0xCAE66,10180),(0xCB5CE,10171),(0xCAE00,10179)]:
        assert imports[struct.unpack_from('<I',pe.get_data(thunk,6),2)[0]]==(b'd2common.dll',ordinal)
    lines=['Local PE/Capstone inspection, not a native runtime stack or REA Evidence.',
        'D2Client.dll sha256='+sha,
        '896A0 validates the current target reference before returning it.',
        'ADEC0 compares the target type/GUID lookup with the stored pointer; mismatch clears the path reference.',
        'This resolver chain has no HP check. Other input/death paths may clear the reference separately.']
    for label,rva,size in [('Current target getter',0x896A0,0x35),
        ('Reference identity check and clear',0xADEC0,0x87),
        ('Target position or current path fallback',0x1A950,0xAC)]:
        lines.append(label+' RVA='+hex(rva));lines += ['%08X %s %s'%(i.address-base,i.mnemonic,i.op_str) for i in decoder.disasm(pe.get_data(rva,size),base+rva)]
    pe.close();args.output.write_text('\n'.join(lines)+'\n',encoding='utf8')
