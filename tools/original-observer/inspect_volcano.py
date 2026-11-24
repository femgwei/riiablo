"""Reproducible hash-pinned Volcano disassembly. Never loads or executes original DLLs."""
import argparse
import hashlib
from pathlib import Path
import struct
import capstone
import pefile


def inspect(home):
    lines = ["Local static Capstone observation; not REA Evidence.",
             "All excerpts start at identified function boundaries; calls use original virtual addresses."]
    targets = [
        ("D2Client.dll", "94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282", [
            ("Missile CltDo50", 0x95110, 0x2B0), ("Missile CltHit55", 0x99150, 0xE0),
            ("Missile CltDo1", 0x9D880, 0x318), ("Client constructor", 0x9E070, 0x810),
            ("CltDo50 creation helper", 0x9EBE0, 0x160),
            ("Graphics auxiliary pointer", 0xBBA40, 0x40),
            ("Graphics update", 0xBFE00, 0x250), ("Graphics arc initializer", 0xC0080, 0x70)]),
        ("D2Common.dll", "b9d1a27c6a35992932c7c47438d02e5269f878e899f6e614d507902cecd1beab", [
            ("Countdown getter ordinal11124", 0x7A140, 0x20),
            ("Elapsed getter ordinal11125 despite misleading source name", 0x7A160, 0x30)])]
    decoder = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_32)
    for dll, digest, excerpts in targets:
        data = (home / dll).read_bytes()
        assert hashlib.sha256(data).hexdigest() == digest, "Unsupported " + dll
        pe = pefile.PE(data=data)
        lines.append(dll + " sha256=" + digest)
        base = pe.OPTIONAL_HEADER.ImageBase
        if dll == "D2Client.dll":
            assert struct.unpack("<I", pe.get_data(0xF1260 + 50 * 4, 4))[0] == base + 0x95110
            assert struct.unpack("<I", pe.get_data(0xF13D0 + 55 * 4, 4))[0] == base + 0x99150
        imports = {entry.address: "%s#%s" % (group.dll.decode(), entry.ordinal)
                   for group in getattr(pe, "DIRECTORY_ENTRY_IMPORT", []) for entry in group.imports}
        for label, rva, length in excerpts:
            lines.append(label + " RVA=" + hex(rva) + " length=" + hex(length))
            for ins in decoder.disasm(pe.get_data(rva, length), base + rva):
                annotation = ""
                if ins.mnemonic == "call" and ins.op_str.startswith("dword ptr [0x"):
                    address = int(ins.op_str[11:-1], 16)
                    if address in imports: annotation = " ; " + imports[address]
                lines.append(("%08X %s %s%s" % (ins.address-base, ins.mnemonic, ins.op_str, annotation)).rstrip())
        pe.close()
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("home", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    args.output.write_text(inspect(args.home), encoding="utf8")
