"""Freeze observed native animation cases for Fissure and resident Volcano layers."""
import argparse
import hashlib
import json
from pathlib import Path

ROWS = {"erruption crack 1", "erruption smoke 1", "volcano", "volcano overlay fire"}


def export(directory, output):
    result = dict(schema=1, scope="Actual original DCC selections for four surface rows; not missile flight, layout, damage or GPU pixels", cases={})
    for name in ("fissure", "volcano"):
        path = directory / ("druid-" + name + "-replay-input-1.10f.json")
        data = json.loads(path.read_text(encoding="utf8"))
        units = {u["unit_id"]: u for u in data["units"] if u["side"] == "client"}
        samples = []
        for s in data["samples"]:
            if s["missile"] not in ROWS:
                continue
            samples.append(dict(missile=s["missile"], age=s["native_age"], frame=s["frame"],
                frames=s["dcc_frames"], lifetime=units[s["unit_id"]]["states"][0]["total_frames"], index=s["index"]))
        assert samples
        result["cases"][name] = dict(skill_id=data["skill_id"], run_id=data["run_id"],
            background_verified=data["background_verified"], fixture=data["fixture"], identity=data["identity"],
            source_sha256=hashlib.sha256(path.read_bytes()).hexdigest(), samples=samples,
            client_counts={row: sum(u["side"] == "client" and u["missile"] == row for u in data["units"]) for row in ROWS})
    output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf8")
    print({name: len(case["samples"]) for name, case in result["cases"].items()})


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    export(args.directory, args.output)
