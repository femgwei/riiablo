"""Compare numeric formula artifacts; never imply cast or visual parity."""
import argparse
import csv
import json
from pathlib import Path


def load(path):
    result = {}
    with path.open(encoding="utf-8-sig", newline="") as stream:
        for row in csv.DictReader(stream):
            key = (int(row["skill"]), int(row["level"]), row["field"].lower())
            if key in result:
                raise ValueError("duplicate formula: " + str(key))
            if row["profile"] != "zero-barbarian-hardpoints" or int(row["owner_class"]) != 1:
                raise ValueError("formula context does not match pinned profile")
            result[key] = row
    if {key[0] for key in result} != set(range(126, 156)):
        raise ValueError("requires all 30 skills")
    for skill in range(126, 156):
        if {key[1] for key in result if key[0] == skill} != set(range(1, 41)):
            raise ValueError("incomplete levels for skill " + str(skill))
    return result


def compare(native_dir, port_dir):
    summary = json.loads((native_dir / "summary.json").read_text(encoding="utf8"))
    if summary.get("status") != "passed" or not summary.get("cleanup_confirmed") or not summary.get("detached"):
        raise ValueError("native collector is incomplete or cleanup is unconfirmed")
    identity = json.loads((native_dir / "identity.json").read_text(encoding="utf8"))
    if identity.get("owner_class") != 1 or identity.get("profile") != "zero-barbarian-hardpoints":
        raise ValueError("native identity has a different fixture")
    native, port = load(native_dir / "formula-values.csv"), load(port_dir / "formula-values.csv")
    missing = sorted(set(port) - set(native))
    extra = sorted(set(native) - set(port))
    differences = [dict(skill=key[0], level=key[1], field=key[2],
                        original=int(native[key]["value"]), riiablo=int(port[key]["value"]),
                        expression=port[key]["expression"])
                   for key in sorted(set(native) & set(port))
                   if int(native[key]["value"]) != int(port[key]["value"])]
    return dict(status="matched" if not differences and not missing and not extra else "differences",
                skills=30, levels=40, native_rows=len(native), port_rows=len(port),
                compared=len(set(native) & set(port)), differences=differences,
                missing_native=missing, native_without_port=extra,
                scope="numeric formulas only; zero Barbarian hardpoints, Sorceress owner; casts, synergies, equipment, final damage and visuals untested")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--native", type=Path, required=True)
    parser.add_argument("--port", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = compare(args.native, args.port)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf8")
    print(json.dumps({k: v for k, v in report.items() if k not in ("differences", "missing_native", "native_without_port")}, ensure_ascii=False))
    raise SystemExit(0 if report["status"] == "matched" else 1)
