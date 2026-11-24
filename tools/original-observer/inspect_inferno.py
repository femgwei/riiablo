"""Hash-pinned offline Inferno disassembly; does not load or execute the DLLs."""
import argparse
import hashlib
from pathlib import Path
import struct
import capstone
import pefile


def inspect(home):
    lines = ["Local static Capstone disassembly; not runtime or REA Evidence.",
             "Skill CltDo24 and missile pCltDo1 are different callback tables."]
    targets = [
        ("D2Client.dll", "94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282",
         [("Inferno client skill helper (A/B allocation and animation-rate override)", 0x2A1D0, 0x1DA),
          ("Client skill helper wrapper", 0x2A3B0, 0x13),
          ("Missile pCltDo1", 0x9D880, 0x318)]),
        ("D2Game.dll", "2bd92fb25e3e4b8269930712620ba9db00cdc1aa44569d87dc4aba76553272b6",
         [("SKILLS_DoInferno", 0xE56F0, 0x248),
          ("SKILLS_StartInferno", 0xE5940, 0x163)])]
    decoder = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_32)
    for dll, expected, sections in targets:
        data = (home / dll).read_bytes()
        if hashlib.sha256(data).hexdigest() != expected:
            raise ValueError("Unsupported original " + dll)
        pe = pefile.PE(data=data)
        lines += [dll + " sha256=" + expected, "ImageBase=" + hex(pe.OPTIONAL_HEADER.ImageBase)]
        if dll == "D2Client.dll":
            table = data.find(struct.pack("<II", pe.OPTIONAL_HEADER.ImageBase + 0x906F0,
                                         pe.OPTIONAL_HEADER.ImageBase + 0x90820))
            assert table >= 20
            assert struct.unpack_from("<I", data, table - 16)[0] == pe.OPTIONAL_HEADER.ImageBase + 0x9D880
            lines.append("Missile callbacks 5/6 identify table; slot1 RVA=9D880")
            assert pe.get_data(0x2A1D0, 6).hex() == "83ec70538bda"
        for label, start, size in sections:
            lines.append(label + " RVA=" + hex(start) + " size=" + hex(size))
            for i in decoder.disasm(pe.get_data(start, size), pe.OPTIONAL_HEADER.ImageBase + start):
                lines.append(("%08X %s %s" % (i.address - pe.OPTIONAL_HEADER.ImageBase,
                                               i.mnemonic, i.op_str)).rstrip())
        pe.close()
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("home", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    args.output.write_text(inspect(args.home), encoding="utf8")
