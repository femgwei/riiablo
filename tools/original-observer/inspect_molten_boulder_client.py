"""Hash-pinned, read-only Molten Boulder CltDo47/CltHit52/53 disassembly; never loads or executes the DLL."""
import argparse
import hashlib
from pathlib import Path
import capstone
import pefile

CLIENT_SHA256 = "94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282"


def inspect(home):
    data = (home / "D2Client.dll").read_bytes()
    if hashlib.sha256(data).hexdigest() != CLIENT_SHA256:
        raise ValueError("Unsupported original D2Client.dll")
    pe = pefile.PE(data=data)
    cs = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_32)
    lines = ["D2Client.dll sha256=" + CLIENT_SHA256,
             "Static Capstone disassembly only; not a new REA Evidence record."]
    imports = {e.address: d.dll.decode() + ("#" + str(e.ordinal) if e.ordinal else "!" + e.name.decode())
               for d in pe.DIRECTORY_ENTRY_IMPORT for e in d.imports}
    # Bounds established by RET/tail-JMP and following function alignment;
    # CltDo6's entry also matches the earlier retained Fire Wall evidence.
    for label, start, size in [("CltDo47 table[47] at RVA F131C", 0x94C30, 0x90), ("CltDo6 shared flame selector", 0x90820, 0x2B0), ("CltHit52 shared fragments/fire fan-out", 0x989A0, 0x250), ("CltHit52 table[52] at RVA F14A0", 0x98D20, 0x1E0), ("CltHit53 table[53] at RVA F14A4", 0x98F00, 0x150)]:
        lines.append(label)
        for i in cs.disasm(pe.get_data(start, size), pe.OPTIONAL_HEADER.ImageBase + start):
            suffix = ""
            if i.mnemonic == "call":
                try:
                    target = int(i.op_str, 16) - pe.OPTIONAL_HEADER.ImageBase
                    thunk = next(cs.disasm(pe.get_data(target, 6), target), None)
                    if thunk and thunk.mnemonic == "jmp":
                        for address, name in imports.items():
                            if hex(address) in thunk.op_str:
                                suffix = " ; " + name
                except (ValueError, pefile.PEFormatError):
                    pass
            lines.append("%08X %s %s%s" % (i.address - pe.OPTIONAL_HEADER.ImageBase,
                                               i.mnemonic, i.op_str, suffix))
    import struct
    for table, index, expected in [(0xF1260, 47, 0x94C30), (0xF13D0, 52, 0x98D20), (0xF13D0, 53, 0x98F00)]:
        assert struct.unpack("<I", pe.get_data(table + index*4, 4))[0] == pe.OPTIONAL_HEADER.ImageBase + expected
    pe.close()
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("home", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    args.output.write_text(inspect(args.home), encoding="utf8")
