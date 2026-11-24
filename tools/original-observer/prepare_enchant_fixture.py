"""Build an isolated level-one Sorceress with Enchant at level one and a fixed plain right-hand javelin.

The worker reads the repository-owned CXSorc save as a donor and installs a
separate CXEnchant save. It never overwrites the donor or a pre-existing test
fixture unless the existing file is byte-for-byte a compatible fixture.
"""
import hashlib
import importlib.util
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parent


def canonical_items(data, skill_offset):
    """Ignore only the unused equipped item's grid coordinates normalized by D2."""
    section = bytearray(data[skill_offset + 32:])
    if section[:6] != b"JM\x01\0JM" or len(section) != 38:
        raise ValueError("Expected exactly the fixed single-item inventory")
    header = int.from_bytes(section[11:14], "little")
    if (header >> 2) & 7 != 1 or (header >> 5) & 15 != 4:
        raise ValueError("Expected the right-hand equipped fixture item")
    header &= ~(0xFF << 9)  # Only gridX/gridY; preserve location, slot, item and stats.
    section[11:14] = header.to_bytes(3, "little")
    return bytes(section)


def build(save):
    spec = importlib.util.spec_from_file_location("enchant_character", ROOT / "test-character.py")
    fixture = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(fixture)
    source = (save / "CXSorc.d2s").read_bytes()
    data = bytearray(fixture.create(source, "CXEnchant", class_id=1, skill_level=0))
    _, offset = fixture.read_stats(data)
    # Sorceress skills start at Fire Bolt (36); Enchant is skill 52.
    data[offset + 2 + 52 - 36] = 1
    struct.pack_into("<4I", data, 120, 0, 52, 0, 52)
    from prepare_poison_javelin_fixture import JAVELIN
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
