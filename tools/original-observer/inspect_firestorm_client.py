"""Hash-pinned, read-only CltDo5/6 disassembly; never loads or executes the DLL."""
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
    for label, start, size in [("CltDo5", 0x906F0, 0x130), ("CltDo6", 0x90820, 0x2B0)]:
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
    pe.close()
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("home", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    args.output.write_text(inspect(args.home), encoding="utf8")
