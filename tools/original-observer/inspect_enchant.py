"""Hash-pinned static Enchant/overlay snippets; no DLL loading or execution."""
import argparse
import hashlib
from pathlib import Path
import capstone
import pefile


def inspect(home):
    lines = ["Local static Capstone output; not a REA Evidence record.",
             "Only the background run establishes which paths were executed."]
    targets = [
        ("D2Client.dll", "94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282",
         [("Overlay create and native clock setup", 0xBBA70, 0x40D),
          ("Verified graphics-data getter", 0x88400, 0xB),
          ("Actual FireEnchant draw caller region (call BA6E0, return BA6E5)", 0xBA6C1, 0x24),
          ("Map overlay draw path (did not observe Enchant here)", 0xB7E2A, 0x28),
          ("Graphics draw import thunk", 0xCB850, 0x6)]),
        ("D2Game.dll", "2bd92fb25e3e4b8269930712620ba9db00cdc1aa44569d87dc4aba76553272b6",
         [("SrvDo025 Enchant", 0xE7040, 0x1B9)]),
        ("D2Common.dll", "b9d1a27c6a35992932c7c47438d02e5269f878e899f6e614d507902cecd1beab",
         [("Stat list lookup by state", 0x78190, 0x6D),
          ("Native stat getter used to cross-check raw snapshots", 0x77C30, 0x60)])]
    decoder = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_32)
    for name, expected, sections in targets:
        data = (home / name).read_bytes()
        if hashlib.sha256(data).hexdigest() != expected: raise ValueError("Unsupported " + name)
        pe = pefile.PE(data=data)
        lines.append(name + " sha256=" + expected + " ImageBase=" + hex(pe.OPTIONAL_HEADER.ImageBase))
        for label, start, size in sections:
            lines.append(label + " RVA=" + hex(start) + " size=" + hex(size))
            for i in decoder.disasm(pe.get_data(start, size), pe.OPTIONAL_HEADER.ImageBase + start):
                lines.append(("%08X %s %s" % (i.address - pe.OPTIONAL_HEADER.ImageBase, i.mnemonic, i.op_str)).rstrip())
        pe.close()
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("home", type=Path); parser.add_argument("output", type=Path)
    args = parser.parse_args(); args.output.write_text(inspect(args.home), encoding="utf8")
