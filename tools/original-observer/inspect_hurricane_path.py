"""Pinned offline Hurricane path/centre accessors and eight-direction DCC lookup."""
import argparse
import hashlib
from pathlib import Path
import struct
import capstone
import pefile


def inspect(home):
    lines=['Local static Capstone observation; not REA Evidence or a runtime stack.']
    decoder=capstone.Cs(capstone.CS_ARCH_X86,capstone.CS_MODE_32)
    targets=[('D2Common.dll','b9d1a27c6a35992932c7c47438d02e5269f878e899f6e614d507902cecd1beab',
        [('Missile centre X #11141',0x7A4B0,0x1F),('Missile centre Y #11143',0x7A4F0,0x1F),
         ('Set target #10170',0x69E90,0x24),('Recompute path missile dispatch #10142',0x68600,0xF1),
         ('Direct missile path',0x68E30,0x430)]),
      ('D2CMP.dll','e725036e1118d34b98d2b739525ff75a225e6964b425469e4773c93f0c9cf882',
        [('DCC direction selector',0x11F0,0x20),('Direction-count bit index',0x10346,0x1B)])]
    for name,digest,excerpts in targets:
        data=(home/name).read_bytes();assert hashlib.sha256(data).hexdigest()==digest
        p=pefile.PE(data=data);base=p.OPTIONAL_HEADER.ImageBase
        lines.append(name+' sha256='+digest)
        if name=='D2Common.dll':
            exports={e.ordinal:e.address for e in p.DIRECTORY_ENTRY_EXPORT.symbols}
            for ordinal,rva in ((11141,0x7A4B0),(11143,0x7A4F0),(10170,0x69E90),(10142,0x68600)):
                assert exports[ordinal]==rva
        else:
            values=struct.unpack('<64I',p.get_data(0x1A4D0,256))
            expected=tuple((4,0,5,1,6,2,7,3)[((i+4)&63)>>3] for i in range(64))
            assert values==expected
            lines.append('Eight-direction table RVA0x1A4D0='+str(values))
        for label,rva,size in excerpts:
            lines.append(label+' RVA='+hex(rva)+' length='+hex(size))
            for i in decoder.disasm(p.get_data(rva,size),base+rva):
                lines.append(('%08X %s %s'%(i.address-base,i.mnemonic,i.op_str)).rstrip())
        p.close()
    return '\n'.join(lines)+'\n'


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('home',type=Path);p.add_argument('output',type=Path)
    args=p.parse_args();args.output.write_text(inspect(args.home),encoding='utf8')
