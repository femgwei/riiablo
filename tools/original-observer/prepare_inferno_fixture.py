"""Build an isolated level-one Sorceress with Inferno at level one.

The worker reads the repository-owned CXSorc save as a donor and installs a
separate CXInferno save. It never overwrites the donor or a pre-existing test
fixture unless the existing file is byte-for-byte a compatible fixture.
"""
import hashlib
import importlib.util
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parent


def build(save):
    spec = importlib.util.spec_from_file_location("inferno_character", ROOT / "test-character.py")
    fixture = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(fixture)
    source = (save / "CXSorc.d2s").read_bytes()
    data = bytearray(fixture.create(source, "CXInferno", class_id=1, skill_level=0))
    _, offset = fixture.read_stats(data)
    # Sorceress skills start at Fire Bolt (36); Inferno is skill 41.
    data[offset + 2 + 41 - 36] = 1
    struct.pack_into("<4I", data, 120, 0, 41, 0, 41)
    struct.pack_into("<I", data, 12, fixture.checksum(data))
    return data, fixture, source


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("save", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    data, _, _ = build(args.save)
    with args.output.open("xb") as stream:
        stream.write(data)
    print(hashlib.sha256(data).hexdigest())
