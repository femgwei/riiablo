"""Build an isolated level-one Amazon with one verified nonmagical javelin.

Reads the existing saves without changing them. The background worker installs
only the separately named CXPoison save, never overwriting an existing file.
"""
import hashlib
import importlib.util
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parent
SOURCE_HASH = "85fcbb27df5a21d02dfa101c3e165c2d4025808bf1376afe96308f6e4be25340"
JAVELIN = bytes.fromhex("4a4d10008000656417a016660782be6558d84010010154fc07")


def build(save, plague=False, firestorm=False):
    spec = importlib.util.spec_from_file_location("poison_character", ROOT / "test-character.py")
    fixture = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(fixture)
    if firestorm:
        source = (save / "CXSorc.d2s").read_bytes()
        data = bytearray(fixture.create(source, "CXFirestorm", class_id=5, skill_level=0))
        _, offset = fixture.read_stats(data)
        data[offset+2+225-221] = 1
        struct.pack_into("<4I", data, 120, 0, 225, 0, 225)
        struct.pack_into("<I", data, 12, fixture.checksum(data))
        return data, fixture, source
    donor = (save / "Census.d2s").read_bytes()
    if hashlib.sha256(donor).hexdigest() != SOURCE_HASH or donor[1549:1574] != JAVELIN:
        raise ValueError("Javelin donor changed; refusing an unverified item")
    source = (save / "CXSorc.d2s").read_bytes()
    name, skill = ("CXPlague", 25) if plague else ("CXPoison", 15)
    data = bytearray(fixture.create(source, name, class_id=0, skill_level=0))
    _, offset = fixture.read_stats(data)
    data[offset+2+skill-6] = 1  # Amazon's saved skill array starts at skill 6.
    struct.pack_into("<4I", data, 120, 0, skill, 0, skill)
    item = bytearray(JAVELIN)
    header = int.from_bytes(item[7:9], "little")
    item[7:9] = ((header & ~(15 << 5)) | (4 << 5)).to_bytes(2, "little")
    data[offset+32:] = b"JM\x01\0" + item + b"JM\0\0jfkf\0"
    struct.pack_into("<I", data, 8, len(data))
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
