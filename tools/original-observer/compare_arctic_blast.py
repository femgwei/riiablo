"""Compare single-stream production replay to observed client states, without cadence/GPU claims."""
import argparse
import hashlib
import json
from pathlib import Path


def read(path):
    return json.loads(path.read_text(encoding="utf8"))


def compare(original_path, before_path, after_path, output, register_in_batch=False):
    original, before, after = map(read, (original_path, before_path, after_path))
    assert original["run_id"] == before["source_run_id"] == after["source_run_id"]
    records = {}
    for label, replay in (("before", before["production"]), ("client_after", after["client"]),
                          ("local_authority_vs_client_oracle", after["production"])):
        by_age = {s["native_age"]: s for s in replay["states"]}
        count = missing = position = clock = velocity = 0
        max_distance = 0
        for capture in original["captures"]:
            for unit in capture["units"]:
                for sample in unit["states"]:
                    count += 1
                    age = sample["total_frames"] - sample["current_frame"]
                    state = by_age.get(age)
                    if state is None:
                        missing += 1
                        continue
                    native = [(value + .5) * 65536 for value in state["position"]]
                    error = max(abs(native[i] - sample[key]) for i, key in enumerate(("x_fixed", "y_fixed")))
                    position += error > .1
                    max_distance = max(max_distance, error / 65536)
                    clock += state["frame"] != sample["unit_44"] >> 8 or state["frame_delta"] != sample["unit_4c"]
                    velocity += any(abs(replay["velocity"][i] * 65536 / 25 - sample[key]) > .1
                        for i, key in enumerate(("velocity_x", "velocity_y")))
        records[label] = dict(samples=count, missing=missing, position_mismatches=position,
            animation_mismatches=clock, velocity_mismatches=velocity, max_position_error_cells=max_distance)
        if label != "before":
            assert not (missing or position or clock or velocity), records[label]
    assert after["client"]["visual_count"] == 2 and after["client"]["server_count"] == 1
    assert after["client"]["damage_carrier_hidden"] and not after["client"]["visual_networked"]
    result = dict(schema=1, skill_id=230, source_run_id=original["run_id"],
        comparison_status="compared_client_stream_motion_clock_with_remaining_differences", comparisons=records,
        client_contract={key: after["client"][key] for key in ("lifetime", "visual_rows", "damage_carrier_hidden", "visual_networked")},
        source_sha256={str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in (original_path, before_path, after_path)},
        limitations=["One replayed stream is compared by native age; this does not reproduce the original held-input callback schedule",
            "No original server damage, actual DCC direction selection, overlay submission or GPU pixels",
            "Both local A/B resources are exercised in tests; original caster/allocator RNG is not reconstructed",
            "Level one, one direction, empty equipment; interruption, other levels and obstacles remain unverified"])
    output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf8")
    if register_in_batch:
        summary_path = output.parent / "batch-comparison-summary.json"
        summary = read(summary_path)
        summary["captures"] = [c for c in summary["captures"] if c["skill_id"] != 230]
        summary["captures"].append(dict(skill_id=230, source_run_id=original["run_id"],
            comparison_status=result["comparison_status"], file=output.name,
            sha256=hashlib.sha256(output.read_bytes()).hexdigest()))
        summary_path.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf8")
    print(json.dumps(records))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("original", "before", "after", "output"):
        parser.add_argument(name, type=Path)
    parser.add_argument("--register-in-batch", action="store_true")
    args = parser.parse_args()
    compare(args.original, args.before, args.after, args.output, args.register_in_batch)
