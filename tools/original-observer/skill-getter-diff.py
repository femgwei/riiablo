"""Compare live original Fire Bolt getters with production riiablo probe output.

This gate checks complete inputs, debugger cleanup and actual frame sequences.
It does not claim casts, final damage settlement, acceleration equivalence or
client visual coverage.
"""
import argparse
import csv
import json
from pathlib import Path
import re

VALUE_FIELDS = ("min_fixed", "max_fixed", "min_integer", "max_integer")
EXPECTED_KEYS = {(36, level) for level in range(1, 21)}


def native_values(path):
    result = {}
    with path.open(encoding="utf8", newline="") as stream:
        for row in csv.DictReader(stream):
            key = (int(row["skill"]), int(row["level"]))
            if key in result:
                raise ValueError("Duplicate original skill/level")
            if int(row["owner_class"]) != 0 or int(row["mastery"]) != 0:
                raise ValueError("This base-only fixture requires Amazon owner, mastery disabled")
            values = {k: int(row[k]) for k in VALUE_FIELDS}
            if any(values[k] < 0 for k in VALUE_FIELDS):
                raise ValueError("Invalid negative getter output")
            if values["min_fixed"] >> 8 != values["min_integer"] or values["max_fixed"] >> 8 != values["max_integer"]:
                raise ValueError("Native fixed/integer output is inconsistent")
            result[key] = values
    if set(result) != EXPECTED_KEYS:
        raise ValueError("Original must contain exactly Fire Bolt levels 1..20")
    return result


def port_values(path):
    result = {}
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        if not line.startswith("SKILL_FORMULA "):
            continue
        row = dict(re.findall(r"(\w+)=([^\s]+)", line))
        key = (int(row["skill"]), int(row["level"]))
        if key in result:
            raise ValueError("Duplicate port skill/level")
        if row["synergy"] != "0" or row["mastery"] != "0":
            raise ValueError("Port fixture is not zero synergy/mastery")
        result[key] = {k: int(row[k]) for k in VALUE_FIELDS}
    if set(result) != EXPECTED_KEYS:
        raise ValueError("Port must contain exactly Fire Bolt levels 1..20")
    return result


def frame_gate(folder):
    summary = json.loads((folder / "summary.json").read_text(encoding="utf8"))
    if summary.get("status") != "passed" or not summary.get("cleanup_confirmed") or not summary.get("detached"):
        raise ValueError("Original controller did not pass with confirmed debugger cleanup")
    with (folder / "frames.csv").open(encoding="utf8", newline="") as stream:
        rows = [{k: int(v) for k, v in row.items() if k in ("step", "before", "after")}
                for row in csv.DictReader(stream)]
    if len(rows) != 12 or summary["frames_executed"] != 12:
        raise ValueError("Missing native frame evidence")
    frame = summary["first_frame"]
    for index, row in enumerate(rows, 1):
        if row != dict(step=index, before=frame, after=(frame + 1) & 0xFFFFFFFF):
            raise ValueError("Native frame sequence skipped, duplicated or discontinuous")
        frame = row["after"]
    if frame != summary["last_frame"]:
        raise ValueError("Native last frame does not match its evidence")
    return summary


def compare(run, manifest):
    control = json.loads((run / "summary.json").read_text(encoding="utf8"))
    if control.get("status") != "passed" or control.get("cleanup_attention_required"):
        raise ValueError("Controller wrapper did not finish successfully")
    phases = {name: frame_gate(run / name) for name in ("native-cadence", "burst")}
    original = native_values(run / "native-cadence/skill-values.csv")
    burst = native_values(run / "burst/skill-values.csv")
    if original != burst:
        raise ValueError("Native getter results changed between cadence and burst probes")
    actual = port_values(manifest)
    mismatches = [dict(skill=key[0], level=key[1], original=original[key], riiablo=actual[key])
                  for key in sorted(EXPECTED_KEYS) if original[key] != actual[key]]
    return dict(schema=1, status="failed" if mismatches else "passed",
        scope="Fire Bolt base elemental getters only, levels 1..20, zero synergy/mastery",
        skill_count=1, levels=20, getter_returns=40, fixed_and_integer_fields_compared=80,
        mismatches=mismatches, frame_control=phases,
        native_run=str(run.resolve()), port_manifest=str(manifest.resolve()),
        not_tested=["actual casting", "hit resolution and HP loss", "synergy/mastery/equipment variants",
                    "accelerated vs normal full-state equivalence", "client frames", "other 209 class skills"])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("native_run", type=Path)
    parser.add_argument("port_manifest", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = compare(args.native_run, args.port_manifest)
    except (OSError, ValueError, KeyError) as error:
        result = dict(status="invalid_comparison", error=str(error))
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf8")
    print(json.dumps(result, ensure_ascii=False, indent=2))
    raise SystemExit(0 if result["status"] == "passed" else 1)
