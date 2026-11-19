"""Summarize original Frozen Orb runtime states without claiming draw parity."""
import argparse
from collections import Counter, defaultdict
import hashlib
import json
from pathlib import Path

NAMES = {260: "frozenorb", 261: "frozenorbbolt", 262: "frozenorbnova", 263: "frozenorbexplode"}


def summarize(capture):
    source = capture / "missiles.jsonl"
    data = source.read_bytes()
    native = json.loads((capture / "summary.json").read_text(encoding="utf8"))
    rows = [json.loads(line) for line in data.decode("utf8").splitlines()]
    groups = defaultdict(list)
    for row in rows:
        if row["side"] not in ("server", "client") or row["class_id"] not in NAMES:
            raise ValueError("Unexpected side or missile class")
        groups[row["side"], row["class_id"]].append(row)
    result = dict(schema=1, source=str(source.resolve()), source_sha256=hashlib.sha256(data).hexdigest(),
                  capture_status=native["status"], cleanup_confirmed=native["cleanup_confirmed"],
                  scope="sampled client path directions and raw animation cursors; final draw arguments/resource layout unresolved",
                  groups=[])
    for (side, cls), samples in sorted(groups.items()):
        by_id = defaultdict(list)
        for sample in samples:
            by_id[sample["unit_id"]].append(sample)
        turns = Counter()
        changes = 0
        for sequence in by_id.values():
            for a, b in zip(sequence, sequence[1:]):
                if b["step"] <= a["step"]:
                    raise ValueError("Non-increasing per-unit sample steps")
                if b["path_direction"] != a["path_direction"]:
                    turns[(b["path_direction"] - a["path_direction"]) % 64] += 1
                changes += b["unit_44"] != a["unit_44"]
        result["groups"].append(dict(
            side=side, missile=NAMES[cls], class_id=cls, samples=len(samples),
            unique_units_observed=len(by_id), first_step=min(s["step"] for s in samples),
            last_step=max(s["step"] for s in samples),
            path_direction_codes=sorted({s["path_direction"] for s in samples}),
            direction_change_code_deltas=dict(sorted(turns.items())),
            animation_cursor_raw_values=sorted({s["unit_44"] for s in samples}),
            animation_length_raw_values=sorted({s["unit_48"] for s in samples}),
            animation_cursor_changes=changes,
            raw_gfx_blocks_read=sum(bool(s.get("gfx_words")) for s in samples)))
    if sum(len(v) for (side, _), v in groups.items() if side == "client") != native["client_samples"]:
        raise ValueError("Client sample count disagrees with native summary")
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("capture", type=Path)
    args = parser.parse_args()
    result = summarize(args.capture)
    output = args.capture / "animation-state-summary.json"
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf8")
    print(json.dumps({"output": str(output), "status": result["capture_status"],
                      "groups": [{k: g[k] for k in ("side", "missile", "samples", "unique_units_observed", "animation_cursor_changes")}
                                 for g in result["groups"]]}, indent=2))
