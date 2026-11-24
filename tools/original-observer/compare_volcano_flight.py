"""Compare conditioned production flight states against separately attributed native oracles."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path


def read(path):
    return json.loads(path.read_text(encoding="utf8"))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def compare(directory, fixture_path):
    fixture = read(fixture_path)
    native = fixture["cases"]["draw"]
    path = directory / "druid-volcano-production-1.10f.json"
    replay = read(path)
    assert replay["source_run_id"] == native["run_id"]
    assert replay["conditioned_controller_target_seed"] == native["controller_target_seed"]
    assert replay["fixture_sha256"] == native["fixture"]["sha256"]
    port = replay["production"]
    rocks = [b for b in port["births"] if b["presentation_only"] and b["missile"] == "volcano debris 2"]
    assert len(rocks) == len(native["rocks"]) == 62
    pairs = {n["unit_id"]: p for n, p in zip(native["rocks"], rocks)}
    counts = Counter()
    for n in native["rocks"]:
        p = pairs[n["unit_id"]]
        counts["lifetime_mismatches"] += n["lifetime"] != p["lifetime"]
        states = {s["age"]: s for s in p["states"]}
        for s in n["states"]:
            counts["native_motion_samples"] += 1
            if s["age"] not in states:
                counts["missing_motion_samples"] += 1
                continue
            actual = states[s["age"]]
            counts["motion_mismatches"] += (actual["x_fixed"], actual["y_fixed"]) != (s["x_fixed"], s["y_fixed"])
    for s in native["selections"]:
        counts["native_dcc_samples"] += 1
        candidates = [pairs[s["unit_id"]]] if s["missile"] == "volcano debris 2" else [
            b for b in port["births"] if b["presentation_only"] and b["missile"] == s["missile"]]
        frames = {a["frame"] for b in candidates for a in b["states"] if a["age"] == s["age"] and a["loaded"]}
        if not frames: counts["missing_dcc_samples"] += 1
        else: counts["dcc_mismatches"] += frames != {s["frame"]}
    # Height depends on lifetime and age, independent of each cast's RNG/target.
    heights = {(p["lifetime"], s["age"]): s["offset"][1] for p in rocks for s in p["states"]}
    for n in fixture["cases"]["arc_hits"]["rocks"]:
        for s in n["states"]:
            counts["native_arc_samples"] += 1
            key = (n["lifetime"], s["age"])
            if key not in heights: counts["missing_arc_samples"] += 1
            else: counts["arc_mismatches"] += heights[key] != s["z_fixed"] >> 11
    before = read(directory / "druid-volcano-production-before-native-flight-1.10f.json")["production"]
    result = dict(schema=1, source_run_id=native["run_id"], supplementary_run_id=fixture["cases"]["arc_hits"]["run_id"],
        status="scoped_client_flight_reconstructed_with_remaining_differences", counts=dict(counts),
        before_visual_counts=before["visual_counts"], after_visual_counts=port["visual_counts"],
        emission_ages=list(range(4, 128, 2)), production_birth_ticks=[b["tick"] for b in rocks],
        production_lifetimes=sorted({b["lifetime"] for b in rocks}),
        original_hit_parents=58, original_successful_child_creations=116,
        created_but_unsampled=fixture["cases"]["arc_hits"]["created_but_unsampled"],
        fixture_sha256=sha(fixture_path), replay_sha256=sha(path),
        remaining_differences=["Empty-map replay lands all 62 flights; native active-room removal is not fully reconstructed",
            "Original hit trace: four observed rocks disappeared before hit55; exact removal branch unresolved",
            "Server empty-map replay has 25 debris, original has 62; damage and collision were not changed",
            "Recorded TargetX seed conditions this diagnostic only; full allocator RNG and caster input are not replayed"],
        limitations=fixture["limitations"])
    output = directory / "druid-volcano-flight-comparison-1.10f.json"
    output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf8")
    print(json.dumps(result["counts"], indent=2))
    assert not any(value for key, value in counts.items() if key.endswith("mismatches") or key.startswith("missing_"))
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("fixture", type=Path)
    args = parser.parse_args()
    compare(args.directory, args.fixture)
