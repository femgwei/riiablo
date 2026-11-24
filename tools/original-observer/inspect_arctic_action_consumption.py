"""Pinned offline action-consumption and local-missile repeat branches; no target execution."""
import argparse
import hashlib
from pathlib import Path
import struct
import capstone
import pefile


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('home',type=Path);parser.add_argument('output',type=Path)
    args=parser.parse_args()
    decoder=capstone.Cs(capstone.CS_ARCH_X86,capstone.CS_MODE_32)
    lines=['Local static PE/Capstone inspection, not a runtime stack or REA Evidence.']
    targets=[('D2Client.dll','94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282',[
        ('Player action marker consumption precedes sequence advance',0xA8C9D,0x85),
        ('Used-skill action dispatch enters client Do dispatcher',0x1CA30,0x3F),
        ('cltdofunc field F4 dispatch through D75A8',0x1C8CC,0x45),
        ('Main update: primary/client-only missiles before players',0x880F0,0x7F),
        ('Main update: player table processed later',0x881C3,0x27),
        ('Local-owner/game-type first-update eligibility',0x9D8D3,0x3B),
        ('Missile CltDo1 path movement and one lifetime decrement',0x9D975,0x35),
        ('After elapsed frame1, repeat from9D90E once',0x9DAE4,0x3A)]),
        ('D2Common.dll','b9d1a27c6a35992932c7c47438d02e5269f878e899f6e614d507902cecd1beab',[
        ('Ordinal11125 returns TotalFrames minus CurrentFrame',0x7A160,0x24)])]
    for dll,digest,regions in targets:
        data=(args.home/dll).read_bytes();assert hashlib.sha256(data).hexdigest()==digest
        pe=pefile.PE(data=data);base=pe.OPTIONAL_HEADER.ImageBase
        lines += [dll+' sha256='+digest]
        if dll=='D2Client.dll':
            assert struct.unpack('<I',pe.get_data(0xD75A8+24*4,4))[0]==base+0x2A3B0
            assert struct.unpack('<I',pe.get_data(0xF1260+4,4))[0]==base+0x9D880
            lookup=pe.get_data(0x9DBA4,9)
            entries=struct.unpack('<2I',pe.get_data(0x9DB9C,8))
            assert entries[lookup[0]]==base+0x9D8F4
            lines += ['Game-type eligibility bytes='+lookup.hex(),
                'Type0 selects local-owner eligibility; other type meanings were not inferred.',
                'CB4D8 imports D2Common ordinal11125; CB5BC imports ordinal10373.']
            imports={x.address:(entry.dll.lower(),x.ordinal) for entry in pe.DIRECTORY_ENTRY_IMPORT for x in entry.imports}
            for thunk,ordinal in [(0xCB4D8,11125),(0xCB5BC,10373),(0xCADE2,10147)]:
                assert imports[struct.unpack_from('<I',pe.get_data(thunk,6),2)[0]]==(b'd2common.dll',ordinal)
        for label,address,size in regions:
            lines.append(label+' RVA='+hex(address))
            lines += ['%08X %s %s'%(i.address-base,i.mnemonic,i.op_str)
                for i in decoder.disasm(pe.get_data(address,size),base+address)]
        pe.close()
    args.output.write_text('\n'.join(lines)+'\n',encoding='utf8')
