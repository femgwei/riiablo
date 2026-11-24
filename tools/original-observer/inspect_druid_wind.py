"""Hash-pinned offline client wind missile clocks and direction selection, no target execution."""
import argparse
import hashlib
from pathlib import Path
import struct
import capstone
import pefile


def inspect(home):
    path = home / "D2Client.dll"
    data = path.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    assert digest == "94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282"
    pe = pefile.PE(data=data)
    base = pe.OPTIONAL_HEADER.ImageBase
    assert struct.unpack("<I", pe.get_data(0xF1260 + 4, 4))[0] == base + 0x9D880
    assert struct.unpack("<I", pe.get_data(0xD75A8 + 68*4, 4))[0] == base + 0x22B30
    assert struct.unpack("<I", pe.get_data(0xD75A8 + 69*4, 4))[0] == base + 0x22BE0
    imports = {entry.address: "%s#%s" % (group.dll.decode(), entry.ordinal)
               for group in pe.DIRECTORY_ENTRY_IMPORT for entry in group.imports}
    decoder = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_32)
    lines = ["Local static Capstone observation; not REA Evidence.", "D2Client.dll sha256=" + digest,
             "Missile callback table slot1 verified; skill cltdofunc68 is a separate callback table."]
    for label, rva, size in [("Skill CltDo68: spread animation rate0x100", 0x22B30, 0xB0),
                              ("Skill CltDo69: spread animation rate0x200", 0x22BE0, 0xB0),
                              ("Shared client spread constructor", 0x22900, 0x230),
                              ("Client charged-bolt initializer", 0x29E80, 0x60),
                              ("Missile CltDo1", 0x9D880, 0x318),
                              ("Missile animation update", 0x9DBB0, 0x4C0),
                              ("Exploratory renderer excerpt; not a function entry", 0xBAD60, 0x3E0)]:
        lines.append(label + " RVA=" + hex(rva))
        for ins in decoder.disasm(pe.get_data(rva, size), base+rva):
            annotation = ""
            if ins.mnemonic == "call" and ins.op_str.startswith("0x"):
                target = int(ins.op_str, 16)-base
                thunk = pe.get_data(target, 6)
                if thunk[:2] == b"\xff\x25":
                    name = imports.get(struct.unpack("<I", thunk[2:])[0])
                    if name: annotation = " ; " + name
            lines.append(("%08X %s %s%s" % (ins.address-base, ins.mnemonic, ins.op_str, annotation)).rstrip())
    pe.close()
    common_path = home / "D2Common.dll"
    common_data = common_path.read_bytes()
    common_digest = hashlib.sha256(common_data).hexdigest()
    assert common_digest == "b9d1a27c6a35992932c7c47438d02e5269f878e899f6e614d507902cecd1beab"
    common = pefile.PE(data=common_data)
    common_base = common.OPTIONAL_HEADER.ImageBase
    exported = {entry.ordinal: entry.address for entry in common.DIRECTORY_ENTRY_EXPORT.symbols}
    lines.append("D2Common.dll sha256=" + common_digest)
    for ordinal, expected, size in [(10374, 0x7EFF0, 0x11), (10375, 0x7F020, 0x30), (11124, 0x7A140, 0x20),
                                    (10175, 0x69DE0, 0xD), (10457, 0x7D520, 0x47)]:
        assert exported[ordinal] == expected
        lines.append("D2Common ordinal" + str(ordinal) + " RVA=" + hex(expected))
        for ins in decoder.disasm(common.get_data(expected, size), common_base+expected):
            lines.append(("%08X %s %s" % (ins.address-common_base, ins.mnemonic, ins.op_str)).rstrip())
    common.close()
    for dll,digest,excerpts in [
        ("D2Game.dll","2bd92fb25e3e4b8269930712620ba9db00cdc1aa44569d87dc4aba76553272b6",
         [("Server charged-bolt initializer",0xE5E50,0x60)]),
        ("D2CMP.dll","e725036e1118d34b98d2b739525ff75a225e6964b425469e4773c93f0c9cf882",
         [("DCC direction selector",0x11F0,0x20),("Direction-count bit index",0x10346,0x1B),
          ("DCC dispatch missile branch excerpt",0xBFAC,0x3B)])]:
        data=(home/dll).read_bytes();assert hashlib.sha256(data).hexdigest()==digest
        binary=pefile.PE(data=data);base=binary.OPTIONAL_HEADER.ImageBase
        lines.append(dll+" sha256="+digest)
        for label,rva,size in excerpts:
            lines.append(label+" RVA="+hex(rva))
            for ins in decoder.disasm(binary.get_data(rva,size),base+rva):
                lines.append(("%08X %s %s"%(ins.address-base,ins.mnemonic,ins.op_str)).rstrip())
        if dll=="D2CMP.dll":
            values=struct.unpack("<64I",binary.get_data(0x1A3D0,256))
            assert values==tuple(((i+8)&63)>>4 for i in range(64))
            lines.append("Static 4-direction lookup RVA0x1A3D0="+str(values))
        binary.close()
    return "\n".join(lines)+"\n"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("home", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    args.output.write_text(inspect(args.home), encoding="utf8")
