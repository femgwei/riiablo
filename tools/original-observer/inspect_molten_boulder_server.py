"""Read-only disassembly of 1.10f's null-target Molten Boulder hit branch.

The original differs from the available D2MOO source at the null-target test.
This output is static Capstone evidence, not an observed runtime/REA record.
"""
import argparse
import hashlib
from pathlib import Path
import capstone
import pefile

SHA256 = "2bd92fb25e3e4b8269930712620ba9db00cdc1aa44569d87dc4aba76553272b6"


def inspect(home):
    data = (home / "D2Game.dll").read_bytes()
    assert hashlib.sha256(data).hexdigest() == SHA256
    pe = pefile.PE(data=data)
    decoder = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_32)
    assert pe.get_data(0x2DA80, 10).hex() == "81ec8400000053555657"
    assert pe.get_data(0x2DB08, 2) == b"\x74\x7e"  # target==NULL -> area/fan-out
    lines = ["D2Game.dll sha256=" + SHA256,
             "Static Capstone output, not runtime or REA Evidence",
             "SrvHit47 at RVA 2DA80; null target branch RVA 2DB08 -> 2DB88"]
    for i in decoder.disasm(pe.get_data(0x2DA80, 0x290), pe.OPTIONAL_HEADER.ImageBase + 0x2DA80):
        lines.append("%08X %s %s" % (i.address - pe.OPTIONAL_HEADER.ImageBase, i.mnemonic, i.op_str))
    pe.close()
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("home", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    args.output.write_text(inspect(args.home), encoding="utf8")
