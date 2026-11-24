"""Hash-pinned offline D2Game SrvDo124/SrvActive146 disassembly."""
import argparse
import hashlib
from pathlib import Path
import capstone
import pefile


HASH = "2bd92fb25e3e4b8269930712620ba9db00cdc1aa44569d87dc4aba76553272b6"


def inspect(home):
    path = home / "D2Game.dll"
    data = path.read_bytes()
    assert hashlib.sha256(data).hexdigest() == HASH
    pe = pefile.PE(data=data)
    base = pe.OPTIONAL_HEADER.ImageBase
    assert base == 0x6FC30000
    decoder = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_32)
    lines = [
        "Local static Capstone observation; not REA Evidence or a runtime call trace.",
        "D2Game.dll sha256=" + HASH,
        "D2MOO 1.10f source comment maps SKILLS_SrvDo124 to absolute 0x6FCFF870, "
        "which is RVA 0xCF870 for this image base.",
        "SrvDo124: pUnit seed is advanced at RVA0xCF919; the saved cast Param1 "
        "is the low 16 bits observed at RVA0xCF933-0xCF938.",
        "SrvActive146 source is at RVA0xCFAB0; D2MOO documents SEED_InitLowSeed "
        "from Param1, then ITEMS_RollRandomNumber % 1000000 before five x/y attempts.",
    ]
    for label, rva, size in [
        ("SKILLS_SrvDo124_Armageddon_Hurricane", 0xCF870, 0x140),
        ("SKILLS_SrvActive146_PeriodicRandomMissile", 0xCFAB0, 0x1A0),
    ]:
        lines.append(label + " RVA=" + hex(rva) + " length=" + hex(size))
        for ins in decoder.disasm(pe.get_data(rva, size), base + rva):
            lines.append(("%08X %s %s" % (ins.address - base, ins.mnemonic, ins.op_str)).rstrip())
    pe.close()
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("home", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    args.output.write_text(inspect(args.home), encoding="utf8")
