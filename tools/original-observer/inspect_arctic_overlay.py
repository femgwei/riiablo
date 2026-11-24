"""Hash-pinned CltSt15 and shared overlay creation clock; offline observations only."""
import argparse
import hashlib
from pathlib import Path
import struct
import capstone
import pefile

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('home',type=Path);p.add_argument('output',type=Path);args=p.parse_args()
    data=(args.home/'D2Client.dll').read_bytes();sha=hashlib.sha256(data).hexdigest()
    assert sha=='94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282'
    pe=pefile.PE(data=data);base=pe.OPTIONAL_HEADER.ImageBase
    assert struct.unpack('<I',pe.get_data(0xD7488+15*4,4))[0]==base+0x2A050
    assert pe.get_data(0x2A050,4)==bytes.fromhex('565785d2')
    cs=capstone.Cs(capstone.CS_ARCH_X86,capstone.CS_MODE_32)
    lines=['Local static Capstone output; not REA Evidence, an observed runtime stack or audio capture.',
        'D2Client.dll sha256='+sha,
        'Client start table RVA0xD7488, slot15 -> RVA0x2A050.',
        'When INFERNO is absent: skill+10C progress overlay -> create type2 (ONCE); skill+10E -> sound dispatcher.',
        'The private-desktop overlay list polling independently observes one type2 instance per cast.']
    for label,start,size in [('CltSt15',0x2A050,0x130),('Shared native overlay clock setup excerpt',0xBBE10,0x65)]:
        lines.append(label+' RVA='+hex(start))
        for ins in cs.disasm(pe.get_data(start,size),base+start):
            lines.append('%08X %s %s'%(ins.address-base,ins.mnemonic,ins.op_str))
    pe.close();args.output.write_text('\n'.join(lines)+'\n',encoding='utf8')
