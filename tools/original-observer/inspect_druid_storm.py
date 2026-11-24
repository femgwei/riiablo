"""Hash-pinned offline storm callback disassembly. Never executes original DLLs."""
import argparse
import hashlib
from pathlib import Path
import struct
import capstone
import pefile


def inspect(home):
    data=(home/'D2Client.dll').read_bytes()
    digest=hashlib.sha256(data).hexdigest()
    assert digest=='94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282'
    pe=pefile.PE(data=data); base=pe.OPTIONAL_HEADER.ImageBase
    for table,index,rva in ((0xD75A8,90,0x22C90),(0xD75A8,92,0x23010),
                            (0xF1260,60,0x9D380),(0xF13D0,55,0x99150)):
        assert struct.unpack('<I',pe.get_data(table+4*index,4))[0]==base+rva
    imports={e.address: g.dll.decode()+'#'+str(e.ordinal) for g in pe.DIRECTORY_ENTRY_IMPORT for e in g.imports}
    decoder=capstone.Cs(capstone.CS_ARCH_X86,capstone.CS_MODE_32)
    lines=['Local static Capstone observation; not REA Evidence or a runtime call trace.',
        'D2Client.dll sha256='+digest,
        'Hurricane X='+str(struct.unpack('<8i',pe.get_data(0xCE9E0,32))),
        'Hurricane Y='+str(struct.unpack('<8i',pe.get_data(0xCEA00,32)))]
    for label,rva,length in [('Skill90/Hurricane',0x22C90,0xF4),('Hurricane debris constructor',0x22D90,0x277),
        ('Skill92/Armageddon pair',0x23010,0x1E5),('Missile60/Hurricane motion',0x9D380,0xB3),
        ('Missile55/impact children',0x99150,0xE0),('Graphics offset initializer',0xBFC80,0x30),
        ('Graphics linear initializer',0xBFCB0,0x80),('Graphics auxiliary getter',0xBBA40,0x10),
        ('Graphics update',0xBFE00,0x250)]:
        lines.append(label+' RVA='+hex(rva)+' length='+hex(length))
        for i in decoder.disasm(pe.get_data(rva,length),base+rva):
            annotation=''
            if i.mnemonic=='call' and i.op_str.startswith('0x'):
                thunk=pe.get_data(int(i.op_str,16)-base,6)
                if thunk[:2]==b'\xff\x25': annotation=' ; '+imports.get(struct.unpack('<I',thunk[2:])[0],'unknown import')
            lines.append(('%08X %s %s%s'%(i.address-base,i.mnemonic,i.op_str,annotation)).rstrip())
    pe.close()
    return '\n'.join(lines)+'\n'


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('home',type=Path); parser.add_argument('output',type=Path)
    args=parser.parse_args(); args.output.write_text(inspect(args.home),encoding='utf8')
