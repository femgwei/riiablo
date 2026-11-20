"""Read-only, hash-pinned disassembly of the captured 1.10f animation branches."""
import argparse
import hashlib
from pathlib import Path
import capstone
import pefile

HASHES = {
    "D2Client.dll": "94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282",
    "D2Common.dll": "b9d1a27c6a35992932c7c47438d02e5269f878e899f6e614d507902cecd1beab",
}


def inspect(home):
    lines = []
    decoder = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_32)
    for name, expected in HASHES.items():
        data = (home / name).read_bytes()
        actual = hashlib.sha256(data).hexdigest()
        if actual != expected:
            raise ValueError("Unsupported original DLL: " + name)
        pe = pefile.PE(data=data)
        lines.append(name + " sha256=" + actual)
        ranges = [(0x902E0, 0x190), (0x9D90E, 0x67), (0x9DB1E, 0x44),
                  (0xCAE6C, 6), (0xCA926, 6), (0xCA920, 6),
                  (0xCB4C6, 6), (0xCB538, 6)]
        imports = {}
        if name == "D2Common.dll":
            entry = next(e.address for e in pe.DIRECTORY_ENTRY_EXPORT.symbols if e.ordinal == 10375)
            ranges = [(entry, 0x30)]
            lines.append("Export #10375: advance 8.8 cursor, wrap at exclusive SubStop")
        else:
            imports = {e.address: d.dll.decode() + "#" + str(e.ordinal)
                       for d in pe.DIRECTORY_ENTRY_IMPORT for e in d.imports}
        for start, size in ranges:
            for insn in decoder.disasm(pe.get_data(start, size), pe.OPTIONAL_HEADER.ImageBase + start):
                suffix = ""
                if insn.mnemonic == "jmp":
                    for address, label in imports.items():
                        if hex(address) in insn.op_str:
                            suffix = " " + label
                lines.append("%08x %s %s%s" % (insn.address - pe.OPTIONAL_HEADER.ImageBase,
                                               insn.mnemonic, insn.op_str, suffix))
        pe.close()
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("home", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    args.output.write_text(inspect(args.home), encoding="utf8")
