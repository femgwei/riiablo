"""Prepare a separate 1.10f portal-input fixture; never overwrite/install saves."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import struct

spec = importlib.util.spec_from_file_location('fixture', Path(__file__).with_name('test-character.py'))
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)


def scroll(x, y):
    # ItemWriter's compact 1.10f header: flags, version, then LSB-first fields.
    bits = []
    def put(value, width):
        bits.extend((value >> n) & 1 for n in range(width))
    for value, width in ((0, 2), (0, 3), (0, 4), (x, 4), (y, 4), (1, 3)):
        put(value, width)
    for byte in b'tsc ':
        put(byte, 8)
    put(0, 3)
    packed = bytearray((len(bits) + 7) // 8)
    for n, bit in enumerate(bits):
        packed[n // 8] |= bit << (n % 8)
    return b'JM' + struct.pack('<IB', 0x00200010, 101) + packed


def prepare(source):
    data = bytearray(fixture.create(source, 'CXPortal'))
    _, skills = fixture.read_stats(data)
    offset = skills + 32
    assert data[offset:] == b'JM\0\0JM\0\0jfkf\0'
    data[offset:] = b'JM' + struct.pack('<H', 8) + b''.join(scroll(x, 0) for x in range(8)) + b'JM\0\0jfkf\0'
    struct.pack_into('<I', data, 8, len(data))
    struct.pack_into('<I', data, 12, fixture.checksum(data))
    return bytes(data)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    source = args.source.read_bytes()
    data = prepare(source)
    args.output.mkdir(exist_ok=False)
    (args.output / 'CXPortal.d2s').write_bytes(data)
    (args.output / 'portal-scroll.d2i').write_bytes(scroll(0, 0))
    report = dict(status='prepared_not_original_load_verified', character='CXPortal', scroll_count=8,
                  source=str(args.source.resolve()), source_sha256=hashlib.sha256(source).hexdigest(),
                  save_sha256=hashlib.sha256(data).hexdigest(), original_saves_modified=False)
    (args.output / 'manifest.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps(report))
