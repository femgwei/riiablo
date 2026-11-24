"""Compare observed surface clocks separately from unresolved layout/projectile behavior."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path

ROWS = {"fissure": ("erruption crack 1", "erruption smoke 1"), "volcano": ("volcano", "volcano overlay fire")}


def read(path):
    return json.loads(path.read_text(encoding="utf8"))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def clocks(original, production, row):
    births = [b for b in production["births"] if b["presentation_only"] and b["missile"] == row]
    observed = [u for u in original["units"] if u["side"] == "client" and u["missile"] == row]
    samples = [s for s in original["samples"] if s["missile"] == row]
    poses = {s["age"]: s["frame"] for s in (births[0]["states"] if births else []) if s["loaded"]}
    paired = [s for s in samples if s["native_age"] in poses]
    native_births = sorted(u["states"][0]["game_frame"] - u["states"][0]["total_frames"] + u["states"][0]["current_frame"] for u in observed)
    port_births = sorted(b["tick"] for b in births)
    return dict(original_count=len(observed), production_count=len(births),
        original_lifetimes=sorted({u["states"][0]["total_frames"] for u in observed}),
        production_lifetimes=sorted({b["lifetime"] for b in births}),
        native_birth_offsets=[b - native_births[0] for b in native_births],
        production_birth_offsets=[b - port_births[0] for b in port_births],
        actual_original_dcc_samples=len(samples), paired_samples=len(paired),
        missing_age_samples=len(samples) - len(paired), frame_mismatches=sum(poses[s["native_age"]] != s["frame"] for s in paired))


def compare(directory):
    summary = read(directory / "runtime-surface-batch-summary.json")
    records = []
    for name in ROWS:
        original_path = directory / ("druid-" + name + "-replay-input-1.10f.json")
        production_path = directory / ("druid-" + name + "-production-1.10f.json")
        before_path = directory / ("druid-" + name + "-production-before-native-clock-1.10f.json")
        original, replay, baseline = read(original_path), read(production_path), read(before_path)
        assert original["run_id"] == replay["source_run_id"] == summary["run_id"]
        assert replay["production"]["hard_points"] == summary["fixture"]["hard_points"]
        assert replay["fixture_sha256"] == summary["fixture"]["sha256"]
        after = {row: clocks(original, replay["production"], row) for row in ROWS[name]}
        before = {row: clocks(original, baseline["production"], row) for row in ROWS[name]}
        checks = {}
        for row, result in after.items():
            checks[row + ":count"] = result["original_count"] == result["production_count"]
            checks[row + ":lifetime"] = result["original_lifetimes"] == result["production_lifetimes"]
            checks[row + ":cadence"] = result["native_birth_offsets"] == result["production_birth_offsets"]
            checks[row + ":frames"] = result["paired_samples"] > 0 and result["frame_mismatches"] == result["missing_age_samples"] == 0
        native_counts = dict(Counter(u["missile"] for u in original["units"] if u["side"] == "client"))
        remaining = (["Vent positions/RNG and crack direction sequence are not matched; current scatter remains a reference fallback",
                      "Server vent count/cadence differs in an empty-map replay; controlled collision and damage not verified"] if name == "fissure" else
                     ["Native flight gate, distance lifetime, fp16 movement, fp11 arc and DCC selection reconstructed; global allocator RNG remains unmatched",
                      "Empty-map replay lands all 62 flights; exact native early-removal/active-room conditions remain unresolved",
                      "Server emission count differs; controlled collision and damage remain to be reconstructed"])
        result = dict(schema=1, source_run_id=summary["run_id"], skill_id=original["skill_id"],
            comparison_status="compared_with_remaining_differences", fixture=summary["fixture"],
            fixed_scope="Four audited surface-row lifetimes, emission pairing and intro/loop/outro frame clocks",
            checks=checks, before=before, after=after, remaining_differences=remaining,
            original_client_counts=native_counts, production_visual_counts=replay["production"]["visual_counts"],
            original_sha256=sha(original_path), replay_sha256=sha(production_path), baseline_sha256=sha(before_path),
            limitations=["One level-one cast, recorded two-skill loadout; no GPU, target damage or full caster input sequence comparison",
                         "Clock samples paired by row and age, not a claim of global RNG, positions or all projectile parity"])
        flight = directory / "druid-volcano-flight-comparison-1.10f.json"
        if name == "volcano" and flight.exists():
            result["flight_evidence"] = dict(file=flight.name, sha256=sha(flight), counts=read(flight)["counts"])
            result["fixed_scope"] += "; conditioned client flying-rock stream, arc and landing child clocks"
        path = directory / ("druid-" + name + "-comparison-1.10f.json")
        path.write_text(json.dumps(result, indent=2) + "\n", encoding="utf8")
        records.append(dict(source_run_id=summary["run_id"], skill_id=original["skill_id"],
            comparison_status=result["comparison_status"], file=path.name, sha256=sha(path)))
        print(name, "fixed checks", sum(checks.values()), "/", len(checks),
              "DCC samples", sum(s["paired_samples"] for s in after.values()),
              "mismatches", sum(s["frame_mismatches"] for s in after.values()))
    path = directory / "batch-comparison-summary.json"
    existing = read(path) if path.exists() else dict(schema=1, captures=[])
    existing["captures"] = [c for c in existing["captures"] if c["skill_id"] not in (234, 244)] + records
    path.write_text(json.dumps(existing, indent=2) + "\n", encoding="utf8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    compare(parser.parse_args().directory)
