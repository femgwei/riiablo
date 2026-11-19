"""Compare observed behavior coverage, never infer decision parity from unlike fixtures."""
import argparse
import json
import re
from pathlib import Path


def features(states):
    modes = sorted({s["mode"] for s in states})
    return {"samples": len(states), "observed_modes": modes,
            "movement_observed": len({(s["x"], s["y"]) for s in states}) > 1,
            "attack_mode_observed": bool(set(modes) & {4, 5}),
            "skill2_mode_observed": 9 in modes,
            "neutral_mode_observed": 1 in modes,
            "scope": "observed state coverage; absence is not proof of missing behavior"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--original", type=Path, required=True)
    parser.add_argument("--original-snapshot", type=Path, required=True)
    parser.add_argument("--port", type=Path, required=True)
    parser.add_argument("--monster", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    snapshot = json.loads(args.original_snapshot.read_text(encoding="utf-8"))
    map_seed = snapshot["scene"]["map_seed"]
    results = [json.loads(line) for line in
               (args.port / "results.jsonl").read_text(encoding="utf-8").splitlines()]
    cases = [r for r in results if r["monster"] == args.monster]
    if not cases or any("classId" not in c for c in cases):
        raise ValueError("Need port results containing an explicit classId")
    class_ids = {c["classId"] for c in cases}
    if len(class_ids) != 1:
        raise ValueError("Port class identity is inconsistent")
    class_id = class_ids.pop()
    if any(c["seed"] != map_seed for c in cases):
        raise ValueError("Port fixture seed must match the recorded original map seed")
    original = []
    guids = set()
    for line in args.original.read_text(encoding="utf-8").splitlines():
        row = json.loads(line)
        for unit in row["units"]:
            if unit["type"] == 1 and unit["class_id"] == class_id and unit["path_valid"]:
                original.append(unit)
                guids.add(unit["unit_id"])
    if not original:
        raise ValueError("No original units match the port class identity")
    # Pooling multiple monsters would falsely count their different spawn
    # positions as movement. Evaluate each GUID separately.
    by_guid = {guid: features([u for u in original if u["unit_id"] == guid])
               for guid in sorted(guids)}
    port = []
    for case in cases:
        states = []
        for line in (args.port / case["log"]).read_text(encoding="utf-8").splitlines():
            if " State " not in line:
                continue
            fields = dict(re.findall(r"(\w+)=([^\s]+)", line))
            if int(fields["class_id"]) != class_id:
                raise ValueError("Port state identity mismatch")
            states.append({"mode": int(fields["mode"]), "x": float(fields["x"]),
                           "y": float(fields["y"])})
        if not states:
            raise ValueError("Run port arena with --state-trace")
        port.append({"scenario": case["scenario"], "status": case["status"],
                     "coverage": features(states)})
    result = {"status": "coverage_compared", "ai_parity": "not_evaluated",
              "class_id": class_id, "monster": args.monster,
              "fixture_seed": map_seed, "unit_rng_aligned": False,
              "limitations": ["Original asynchronous samples may miss short modes",
                              "Original pack and port single-monster arena differ",
                              "Unit RNG, input timeline and collision are not aligned",
                              "Mode coverage does not establish AI decision timing"],
              "original_units": by_guid, "port_cases": port,
              "sources": {"original": str(args.original.resolve()),
                          "original_snapshot": str(args.original_snapshot.resolve()),
                          "port": str(args.port.resolve())}}
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
