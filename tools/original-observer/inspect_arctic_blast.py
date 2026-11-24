"""Offline Arctic Blast callback identity and shared CltDo24 disassembly."""
import argparse
import hashlib
from pathlib import Path
import struct
import pefile
from inspect_inferno import inspect


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("home", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    data = (args.home / "D2Client.dll").read_bytes()
    assert hashlib.sha256(data).hexdigest() == "94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282"
    pe = pefile.PE(data=data)
    assert struct.unpack("<I", pe.get_data(0xD75A8 + 24*4, 4))[0] == pe.OPTIONAL_HEADER.ImageBase + 0x2A3B0
    pe.close()
    prefix = ("Arctic Blast skill 230: Skills.txt cltdofunc24, cltmissilea/b arcticblast1/2.\n"
        "Pinned skill callback table RVA0xD75A8, slot24 -> wrapper RVA0x2A3B0 -> helper RVA0x2A1D0.\n"
        "Shared helper consumes one caster RNG roll for A/B, allocates 0x8020 with calc1 lifetime, overrides rate (AnimLen<<8)/life.\n"
        "This is offline static disassembly, not a runtime stack or DCC submission trace.\n")
    args.output.write_text(prefix + inspect(args.home), encoding="utf8")
