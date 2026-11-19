"""Compare two headless scene manifests by native identity and type counts."""
import argparse
import collections
import json
import re
from pathlib import Path


def fields(line):
    return dict(re.findall(r"(\w+)=([^ ]+)", line))


def read(path):
    groups = collections.defaultdict(list)
    for raw in Path(path).read_text(encoding="utf-8-sig").splitlines():
        line = raw.strip()
        if not line or line.startswith("["):
            continue
        kind = line.split(" ", 1)[0]
        values = fields(line)
        # Entity IDs and live positions are runtime-specific. Keep them in a
        # separate evidence stream; identity/count comparison uses native class
        # and gameplay metadata.
        if kind == "RUNTIME_MONSTER":
            key = tuple(values.get(k, "") for k in
                       ("id", "hcIdx", "rank", "champion", "affixes", "super", "minionOwner"))
        elif kind == "GROUND_ITEM":
            key = tuple(values.get(k, "") for k in ("code", "ilvl", "quality", "qualityId"))
        elif kind == "NATIVE_MONSTER":
            key = tuple(values.get(k, "") for k in
                       ("room", "class", "mode", "rank", "super", "group"))
        elif kind == "NATIVE_OBJECT":
            key = tuple(values.get(k, "") for k in
                       ("room", "class", "raw", "mode", "source"))
        elif kind == "ROOM":
            key = tuple(values.get(k, "") for k in
                       ("id", "x", "y", "width", "height", "preset", "adjacent"))
        else:
            continue
        groups[kind].append(key)
    return {kind: collections.Counter(values) for kind, values in groups.items()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("expected", type=Path)
    parser.add_argument("actual", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    expected, actual = read(args.expected), read(args.actual)
    kinds = sorted(set(expected) | set(actual))
    differences = {}
    for kind in kinds:
        missing = expected.get(kind, collections.Counter()) - actual.get(kind, collections.Counter())
        extra = actual.get(kind, collections.Counter()) - expected.get(kind, collections.Counter())
        if missing or extra:
            differences[kind] = {
                "missing": [{"key": list(key), "count": count} for key, count in missing.items()],
                "extra": [{"key": list(key), "count": count} for key, count in extra.items()],
            }
    report = {"status": "passed" if not differences else "failed",
              "expected": str(args.expected), "actual": str(args.actual),
              "compared_kinds": kinds, "differences": differences,
              "position_and_entity_ids": "excluded from identity comparison; retained in scene files"}
    text = json.dumps(report, ensure_ascii=False, indent=2)
    if args.output:
        args.output.write_text(text, encoding="utf-8")
    print(text)
    return 0 if report["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
