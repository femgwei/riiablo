"""Hash-pinned, offline Bone Armor setfunc6/remfunc3 and overlay-chain evidence."""
import argparse
import hashlib
import json
import struct
from pathlib import Path

import capstone
import pefile

CLIENT_SHA256 = "94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282"


def inspect(dll):
    raw = dll.read_bytes()
    digest = hashlib.sha256(raw).hexdigest()
    if digest != CLIENT_SHA256:
        raise ValueError("Offsets require the reviewed 1.10f D2Client.dll hash")
    pe = pefile.PE(data=raw)
    base = pe.OPTIONAL_HEADER.ImageBase
    mem = pe.get_memory_mapped_image()
    assert struct.unpack_from("<I", mem, 0xF3EE0 + 6 * 4)[0] == base + 0xAC1B0
    assert struct.unpack_from("<I", mem, 0xF3F60 + 3 * 4)[0] == base + 0xABF60
    assert struct.unpack_from("<I", mem, 0xB8144 + 3 * 4)[0] == base + 0xB8026  # mode4
    assert struct.unpack_from("<I", mem, 0xB8144 + 6 * 4)[0] == base + 0xB80F4  # mode7
    imports = {i.address: d.dll.decode() + ("#" + str(i.ordinal) if i.name is None
               else "!" + i.name.decode()) for d in pe.DIRECTORY_ENTRY_IMPORT for i in d.imports}
    cs = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_32)

    def instructions(start, end):
        result = []
        for ins in cs.disasm(mem[start:end], base + start):
            row = {"rva": hex(ins.address - base), "bytes": ins.bytes.hex(),
                   "mnemonic": ins.mnemonic, "operands": ins.op_str}
            if ins.mnemonic == "call" and ins.op_str.startswith("0x"):
                target = int(ins.op_str, 16) - base
                thunk = mem[target:target + 6]
                if thunk[:2] == b"\xff\x25":
                    row["import"] = imports.get(int.from_bytes(thunk[2:6], "little"))
            result.append(row)
        return result

    result = {
        "schema": 1, "evidence_type": "offline local PE/Capstone; NOT live REA or runtime evidence",
        "binary": str(dll.resolve()), "sha256": digest, "image_base": hex(base),
        "target_executed_by_analyzer": False,
        "set_dispatch": {"table_rva": "0xf3ee0", "index": 6, "target_rva": "0xac1b0"},
        "remove_dispatch": {"table_rva": "0xf3f60", "index": 3, "target_rva": "0xabf60"},
        "set_function": instructions(0xAC1B0, 0xAC3CD),
        "remove_function": instructions(0xABF60, 0xAC1A9),
        "overlay_update": instructions(0xB7E80, 0xB811D),
        "creation_duplicate_exception": instructions(0xBBB5D, 0xBBBE4),
        "interpretation": {
            "count": "Shared integer thirds over stats132/133, previous count in stat355; different setfunc from Cyclone Armor",
            "increase": "remfunc3; count>=3: overlay3 mode7; count>=2: overlay4 mode7; castoverlay mode4 with overlay1/2 as continuations",
            "mode4_end": "RVA B8026 retires emerge and creates configured front/back as mode3 loops",
            "mode7_end": "RVA B80F4 retires null timer and creates overlay140 mode4 with continuations141/142",
            "clock": "Overlay AnimRate <<4 in 8.8 frames at 25Hz; advance then compare against resource length",
            "duplicates": "RVA BBBAD exempts ids140..142 from generic replacement, preserving three staggered chains",
            "decrease": "Remove current head matching front and back for each lost count; linked-list newest first",
            "removal": "remfunc3 clears cast/front/back and both configured null timer ranges"
        },
        "limits": ["No target execution or observed draw calls from this script",
                   "Missing/cold Riiablo assets pause their stage; original load-delay parity unverified",
                   "Damage during emergence and exact same-id original removal selection need runtime sampling"]
    }
    pe.close()
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dll", type=Path, default=Path(
        "G:/BaiduNetdiskDownload/Diablo II 1.10F/D2Client.dll"))
    parser.add_argument("--output", type=Path, default=Path(
        "tools/original-observer/necromancer-bone-armor-static-1.10f-2026-11-26.json"))
    args = parser.parse_args()
    evidence = inspect(args.dll)
    args.output.write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"output": str(args.output), "sha256": evidence["sha256"],
                      "setfunc": 6, "removefunc": 3}))
