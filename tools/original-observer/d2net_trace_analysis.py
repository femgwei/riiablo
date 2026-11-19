"""Summarize passive D2Net wrapper traces.

The trace records are working-buffer snapshots, not declared network packets.
This tool keeps that distinction explicit while finding fields that vary across
calls to the same wrapper.  It is intentionally read-only and accepts only a
saved ``trace.jsonl`` path.
"""
from __future__ import annotations

import argparse
import collections
import json
import struct
from pathlib import Path


def load(path: Path):
    groups = collections.defaultdict(list)
    for line in path.read_text(encoding="utf8").splitlines():
        if not line.strip():
            continue
        record = json.loads(line)
        raw = record.get("candidate_bytes_hex")
        if not raw:
            continue
        groups[record.get("eip_rva", "unknown")].append(record | {"bytes": bytes.fromhex(raw)})
    return groups


def byte_fields(samples):
    width = min(len(item["bytes"]) for item in samples)
    variable, stable = [], []
    for offset in range(width):
        values = sorted({item["bytes"][offset] for item in samples})
        (variable if len(values) > 1 else stable).append({
            "offset": offset,
            "values": values,
        })
    return width, stable, variable


def dword_fields(samples):
    width = min(len(item["bytes"]) for item in samples) & ~3
    result = []
    for offset in range(0, width, 4):
        values = sorted({struct.unpack_from("<I", item["bytes"], offset)[0]
                         for item in samples})
        if len(values) > 1:
            result.append({"offset": offset, "values": values})
    return result


def analyze(path: Path):
    groups = load(path)
    report = {
        "schema": 1,
        "source": str(path),
        "scope": "passive D2Net working-buffer snapshots",
        "groups": {},
        "limitations": [
            "The 512-byte argument is the observed wrapper buffer size, not a proven payload length.",
            "Pointer-like values and allocator state can vary even when gameplay fields do not.",
            "No packet was sent or modified by this analysis.",
        ],
    }
    for entry, samples in sorted(groups.items()):
        width, stable, variable = byte_fields(samples)
        report["groups"][entry] = {
            "samples": len(samples),
            "buffer_bytes_compared": width,
            "stable_byte_count": len(stable),
            "variable_byte_count": len(variable),
            "variable_byte_offsets": [item["offset"] for item in variable],
            "variable_dwords": dword_fields(samples),
            "first_variable_bytes": variable[:64],
        }
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("trace", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = analyze(args.trace)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n",
                           encoding="utf8")
    print(json.dumps({"status": "written", "output": str(args.output),
                      "groups": len(report["groups"])}, ensure_ascii=False))


if __name__ == "__main__":
    main()
