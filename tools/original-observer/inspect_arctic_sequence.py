"""Hash-pinned offline SQ table and client rewind branch; no execution of target DLLs."""
import argparse
import hashlib
from pathlib import Path
import struct
import capstone
import pefile

if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('home',type=Path)
    parser.add_argument('output',type=Path)
    args=parser.parse_args()
    lines=['Local static Capstone/PE data, not REA Evidence or a runtime stack trace.']
    data=(args.home/'D2Common.dll').read_bytes()
    digest=hashlib.sha256(data).hexdigest()
    assert digest=='b9d1a27c6a35992932c7c47438d02e5269f878e899f6e614d507902cecd1beab'
    pe=pefile.PE(data=data)
    table=pe.get_data(0x9FE10,90)
    expected=[0,1,2,3,4,5,6,7,8,9,9,10,11,12,13]
    assert table==b''.join(struct.pack('<H4B',0,13,f,0,int(i==10)) for i,f in enumerate(expected))
    lines += ['D2Common.dll sha256='+digest,'SQ table RVA0x9FE10 sha256='+hashlib.sha256(table).hexdigest()]
    lines += ['point=%d mode=S1 source=%d event=%d'%(i,f,int(i==10)) for i,f in enumerate(expected)]
    pe.close()
    data=(args.home/'D2Client.dll').read_bytes()
    digest=hashlib.sha256(data).hexdigest()
    assert digest=='94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282'
    pe=pefile.PE(data=data);base=pe.OPTIONAL_HEADER.ImageBase
    lines += ['D2Client.dll sha256='+digest,'CltDo24: INFERNO state check, seek SQ point8 and reset remaining to0x500.']
    decoder=capstone.Cs(capstone.CS_ARCH_X86,capstone.CS_MODE_32)
    for ins in decoder.disasm(pe.get_data(0x2A358,0x40),base+0x2A358):
        lines.append('%08X %s %s'%(ins.address-base,ins.mnemonic,ins.op_str))
    pe.close()
    args.output.write_text('\n'.join(lines)+'\n',encoding='utf8')
