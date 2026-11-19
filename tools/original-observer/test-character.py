"""Create a separate, naked 1.10f Sorceress fixture from a validated save.

Preserves the original quest/map/level/experience header, replaces base stats,
skills and item sections. Never overwrites any save. Intended for local tests.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct

WIDTHS = (10, 10, 10, 10, 10, 8, 21, 21, 21, 21, 21, 21, 7, 32, 25, 25)
CLASS_FIRST_SKILL = {0: 0, 1: 36, 2: 66, 3: 96, 4: 126, 5: 221, 6: 251}


def checksum(data):
    value = 0
    for i, byte in enumerate(data):
        value = (((value << 1) | (value >> 31)) + (0 if 12 <= i < 16 else byte)) & 0xFFFFFFFF
    return value


def read_stats(data):
    if data[765:767] != b"gf":
        raise ValueError("Expected 1.10f stats section")
    bit = 767 * 8

    def read(count):
        nonlocal bit
        value = 0
        for n in range(count):
            value |= ((data[bit // 8] >> (bit % 8)) & 1) << n
            bit += 1
        return value

    result = {}
    while True:
        index = read(9)
        if index == 511:
            break
        if index >= len(WIDTHS) or index in result:
            raise ValueError("Unexpected/duplicate saved stat")
        result[index] = read(WIDTHS[index])
    return result, (bit + 7) // 8


def write_stats(stats):
    bits = []

    def write(value, width):
        if not 0 <= value < 1 << width:
            raise ValueError("Stat exceeds native save width")
        bits.extend((value >> n) & 1 for n in range(width))

    for index, value in sorted(stats.items()):
        write(index, 9)
        write(value, WIDTHS[index])
    write(511, 9)
    data = bytearray((len(bits) + 7) // 8)
    for n, bit in enumerate(bits):
        data[n // 8] |= bit << (n % 8)
    return b"gf" + data


def create(source, name, class_id=1, skill_level=1, item_source=None):
    if not re.fullmatch(r"[A-Za-z][A-Za-z-]{1,14}", name):
        raise ValueError("Use a distinct 2..15 letter original-compatible test name")
    if source[:4] != bytes.fromhex("55aa55aa") or struct.unpack_from("<I", source, 4)[0] != 96:
        raise ValueError("Only 1.10f version-96 saves are supported")
    if len(source) != struct.unpack_from("<I", source, 8)[0] or checksum(source) != struct.unpack_from("<I", source, 12)[0]:
        raise ValueError("Source size/checksum mismatch")
    stats, skill_offset = read_stats(source)
    if source[skill_offset:skill_offset + 2] != b"if" or source[skill_offset+32:skill_offset+34] != b"JM":
        raise ValueError("Unexpected skills/item boundary")
    if stats.get(12) != source[43] or 13 not in stats:
        raise ValueError("Source level/experience fixture is incomplete")
    result = bytearray(source[:765])
    result[20:36] = name.encode("ascii").ljust(16, b"\0")
    struct.pack_into("I", result, 36, 0x20)  # expansion, softcore, alive
    if class_id not in (0, 1, 2, 3, 4, 5, 6):
        raise ValueError("class_id must be a Diablo II class id 0..6")
    if not 0 <= skill_level <= 20:
        raise ValueError("skill_level must be 0..20")
    result[40] = class_id
    result[56:120] = b"\xff" * 64  # clear hotkeys
    first_skill = CLASS_FIRST_SKILL[class_id]
    struct.pack_into("<4I", result, 120, 0, first_skill, 0, first_skill)
    result[136:168] = b"\0" * 32  # no equipped appearance
    result[168:171] = bytes((0x80, 0, 0))  # Normal, Act I
    result[175:191] = b"\0" * 16  # no mercenary
    stats = {0: 25, 1: 35, 2: 25, 3: 100, 4: 0, 5: 0,
             6: 1000 << 8, 7: 1000 << 8, 8: 1000 << 8, 9: 1000 << 8,
             10: 1000 << 8, 11: 1000 << 8, 12: stats[12], 13: stats[13], 14: 0, 15: 0}
    result.extend(write_stats(stats))
    result.extend(b"if" + bytes((skill_level,)) + bytes(29))
    if item_source is None:
        result.extend(b"JM\0\0JM\0\0jfkf\0")  # empty inventory/corpse, no merc/golem
    else:
        item_data = Path(item_source).read_bytes()
        _, item_skill_offset = read_stats(item_data)
        if item_data[item_skill_offset:item_skill_offset + 2] != b"if" or item_data[item_skill_offset + 32:item_skill_offset + 34] != b"JM":
            raise ValueError("Item source has an unexpected 1.10f item boundary")
        # Preserve the source's valid item records (including equipped weapon)
        # while retaining this fixture's deterministic header/stats/skills.
        result.extend(item_data[item_skill_offset + 32:])
    struct.pack_into("<I", result, 8, len(result))
    struct.pack_into("<I", result, 12, checksum(result))
    return bytes(result)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--name", default="CXSorc")
    parser.add_argument("--class-id", type=int, default=1,
                        help="0 Amazon, 1 Sorceress, 2 Necromancer, 3 Paladin, 4 Barbarian, 5 Druid, 6 Assassin")
    parser.add_argument("--skill-level", type=int, default=1)
    parser.add_argument("--item-source", type=Path, default=None,
                        help="preserve the item section from a validated 1.10f save")
    args = parser.parse_args()
    original = args.source.read_bytes()
    result = create(original, args.name, args.class_id, args.skill_level, args.item_source)
    with args.output.open("xb") as stream:
        stream.write(result)
    print(json.dumps(dict(name=args.name, level=result[43], map_seed=struct.unpack_from("<I", result, 171)[0],
        bytes=len(result), source_sha256=hashlib.sha256(original).hexdigest(), output_sha256=hashlib.sha256(result).hexdigest(),
        naked=args.item_source is None, item_source=str(args.item_source) if args.item_source else None,
        class_id=args.class_id, first_skill=CLASS_FIRST_SKILL[args.class_id],
        skill_level=args.skill_level, status="created_not_yet_original_load_verified"), indent=2))
