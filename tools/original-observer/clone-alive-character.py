"""Make a new softcore/alive 1.10f save without overwriting the source."""
import argparse
import hashlib
import importlib.util
import re
import shutil
import struct
from pathlib import Path


def load_fixture():
    path = Path(__file__).with_name("test-character.py")
    spec = importlib.util.spec_from_file_location("test_character", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def clone(source, target, name):
    if not re.fullmatch(r"[A-Za-z][A-Za-z-]{1,14}", name):
        raise ValueError("name must be 2..15 ASCII letters/hyphens")
    if target.exists():
        raise FileExistsError("refusing to overwrite " + str(target))
    data = bytearray(source.read_bytes())
    fixture = load_fixture()
    if data[:4] != bytes.fromhex("55aa55aa") or struct.unpack_from("<I", data, 4)[0] != 96:
        raise ValueError("source is not a 1.10f version-96 save")
    if struct.unpack_from("<I", data, 8)[0] != len(data) or fixture.checksum(data) != struct.unpack_from("<I", data, 12)[0]:
        raise ValueError("source checksum/length mismatch")
    data[20:36] = name.encode("ascii").ljust(16, b"\0")
    # 0x20 is expansion + softcore + alive for 1.10f saves.
    struct.pack_into("<I", data, 36, 0x20)
    struct.pack_into("<I", data, 12, fixture.checksum(data))
    target.write_bytes(data)
    copied = []
    for suffix in (".key", ".map", ".ma0"):
        side = source.with_suffix(suffix)
        dst = target.with_suffix(suffix)
        if side.exists() and not dst.exists():
            shutil.copy2(side, dst)
            copied.append(str(dst))
    return dict(name=name, source=str(source), target=str(target),
                sha256=hashlib.sha256(data).hexdigest(), sidecars=copied,
                flags=struct.unpack_from("<I", data, 36)[0],
                length=len(data))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("target", type=Path)
    parser.add_argument("--name", required=True)
    args = parser.parse_args()
    import json
    print(json.dumps(clone(args.source, args.target, args.name), ensure_ascii=False, indent=2))
