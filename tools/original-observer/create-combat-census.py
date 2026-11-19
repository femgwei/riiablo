"""Create a softcore Sorceress fixture for long-running map census walks.

The file is deliberately separate from the user's fixtures.  It keeps the
validated 1.10f header/item format, raises survivability, and grants only the
Sorceress skills needed by the census driver (Nova, Lightning and Teleport).
"""
import argparse
import importlib.util
import struct
from pathlib import Path


def load_fixture():
    path = Path(__file__).with_name("test-character.py")
    spec = importlib.util.spec_from_file_location("test_character", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def create(source: Path, output: Path, name: str, level: int = 40):
    fixture = load_fixture()
    # Start from the repository's known-valid, softcore, level-99 Sorceress;
    # create() rewrites the stats/skills sections with a valid checksum.
    data = bytearray(fixture.create(source.read_bytes(), name=name,
                                    class_id=1, skill_level=20))
    stats, skill_offset = fixture.read_stats(data)
    stats.update({
        1: 200,                 # energy
        3: 200,                 # vitality
        6: 8000 << 8,           # current life (8.8 fixed point)
        7: 8000 << 8,           # maximum life
        8: 8000 << 8,           # current mana
        9: 8000 << 8,           # maximum mana
        10: 8000 << 8,          # current stamina
        11: 8000 << 8,          # maximum stamina
        12: level,
    })
    encoded = fixture.write_stats(stats)
    # All saved-stat fields have fixed widths, so this replacement preserves
    # the validated if/JM boundary.
    data[765:skill_offset] = encoded
    data[43] = level
    # 1.10f stores sixteen hotkeys as {skill_id:uint16, reserved:uint16}.
    # Bind F1 to Nova and F2 to Teleport so the background driver can post
    # ordinary window-local key messages without touching process memory.
    for index in range(16):
        struct.pack_into("<HH", data, 56 + index * 4, 0xFFFF, 0)
    struct.pack_into("<HH", data, 56 + 0 * 4, 48, 0x70)  # F1: Nova
    struct.pack_into("<HH", data, 56 + 1 * 4, 54, 0x71)  # F2: Teleport
    # Sorceress skills occupy IDs 36..65 in the 30-byte 1.10f skill block.
    # Nova=48, Lightning=49, Teleport=54; grant prerequisites as well.
    data[skill_offset + 2:skill_offset + 32] = b"\0" * 30
    for skill_id in (42, 43, 48, 49, 54):
        data[skill_offset + 2 + (skill_id - 36)] = 20
    struct.pack_into("<I", data, 8, len(data))
    struct.pack_into("<I", data, 12, fixture.checksum(data))
    output.write_bytes(data)
    for suffix in (".key", ".map", ".ma0"):
        side = source.with_suffix(suffix)
        target = output.with_suffix(suffix)
        if side.exists() and not target.exists():
            target.write_bytes(side.read_bytes())
    return {
        "name": name, "level": level, "class_id": 1,
        "skills": {"Nova": 20, "Lightning": 20, "Teleport": 20},
        "life": 8000, "mana": 8000, "output": str(output),
        "sha256": __import__("hashlib").sha256(data).hexdigest(),
    }


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("source", type=Path)
    p.add_argument("output", type=Path)
    p.add_argument("--name", default="CensusSorc")
    p.add_argument("--level", type=int, default=40)
    args = p.parse_args()
    import json
    print(json.dumps(create(args.source, args.output, args.name, args.level),
                     ensure_ascii=False, indent=2))
