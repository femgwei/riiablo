"""Hash-pinned current target resolution in CltDo24, without executing target DLLs."""
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
    lines=['Local static Capstone/PE inspection; not a native runtime stack or REA Evidence.']
    for name,sha,regions in [
        ('D2Client.dll','94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282',[
            ('CltDo24 calls current-target helper before allocation',0x2A24A,0x16),
            ('Current target unit coordinates or current path target',0x1A950,0xAC)]),
        ('D2Common.dll','b9d1a27c6a35992932c7c47438d02e5269f878e899f6e614d507902cecd1beab',[
            ('PATH target X ordinal10175 reads path+10',0x69DE0,0xD),
            ('PATH target Y ordinal10176 reads path+12',0x69DF0,0xD)])]:
        data=(args.home/name).read_bytes();assert hashlib.sha256(data).hexdigest()==sha
        pe=pefile.PE(data=data);base=pe.OPTIONAL_HEADER.ImageBase;lines += [name+' sha256='+sha]
        if name=='D2Client.dll':
            imports={x.address:(e.dll.lower(),x.ordinal) for e in pe.DIRECTORY_ENTRY_IMPORT for x in e.imports}
            for thunk,ordinal in [(0xCABAE,10175),(0xCABA8,10176)]:
                assert imports[struct.unpack_from('<I',pe.get_data(thunk,6),2)[0]]==(b'd2common.dll',ordinal)
        for label,rva,size in regions:
            lines.append(label+' RVA='+hex(rva));lines += ['%08X %s %s'%(i.address-base,i.mnemonic,i.op_str) for i in decoder.disasm(pe.get_data(rva,size),base+rva)]
        pe.close()
    args.output.write_text('\n'.join(lines)+'\n',encoding='utf8')
