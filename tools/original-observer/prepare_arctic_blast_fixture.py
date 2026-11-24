"""Separate naked level-one Druid with only Arctic Blast learned."""
import importlib.util
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parent


def build(save):
    spec = importlib.util.spec_from_file_location("arctic_character", ROOT / "test-character.py")
    fixture = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(fixture)
    source = (save / "CXSorc.d2s").read_bytes()
    data = bytearray(fixture.create(source, "CXArctic", class_id=5, skill_level=0))
    _, offset = fixture.read_stats(data)
    data[offset + 2 + 230 - 221] = 1
    struct.pack_into("<4I", data, 120, 0, 230, 0, 230)
    struct.pack_into("<I", data, 12, fixture.checksum(data))
    return data, fixture, source
