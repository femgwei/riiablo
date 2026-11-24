"""Compare real production wind clocks against client polling; keep motion/draw limitations explicit."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path


def read(path):
    return json.loads(path.read_text(encoding="utf8"))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def clocks(original, port, name):
    births = [b for b in port["births"] if b["missile"] == name]
    visual = [b for b in births if b["presentation_only"]]
    if visual: births = visual
    frames = {}
    for b in births:
        for s in b["states"]:
            if s["loaded"]: frames.setdefault(s["age"], set()).add(s["frame"])
    counts = Counter()
    for u in original["units"]:
        for s in u["states"]:
            age = s["total_frames"]-s["current_frame"]
            counts["observed_cursor_states"] += 1
            if age not in frames:
                counts["unloaded_birth_age_samples" if age == 0 else "missing_nonbirth_age_samples"] += 1
                continue
            counts["paired_cursor_states"] += 1
            counts["frame_mismatches"] += frames[age] != {s["unit_44"] >> 8}
    return dict(counts), births


def compare(directory):
    summary = read(directory / "runtime-wind-polling-summary.json")
    records = []
    for name, skill in [("twister",240), ("tornado",245)]:
        original_path = directory / ("druid-"+name+"-replay-input-1.10f.json")
        after_path = directory / ("druid-"+name+"-production-1.10f.json")
        before_path = directory / ("druid-"+name+"-production-before-native-wind-1.10f.json")
        original, after, before = read(original_path), read(after_path), read(before_path)
        assert original["run_id"] == after["source_run_id"] == before["source_run_id"] == summary["run_id"]
        assert after["production"]["hard_points"] == summary["fixture"]["hard_points"]
        assert after["fixture_sha256"] == original["fixture"]["sha256"]
        counts, births = clocks(original, after["production"], name)
        previous, _ = clocks(original, before["production"], name)
        native_lives = sorted({s["total_frames"] for u in original["units"] for s in u["states"]})
        result = dict(schema=1, source_run_id=summary["run_id"], skill_id=skill,
            comparison_status="compared_client_clock_with_remaining_differences",
            observation_mode="unsuspended_client_memory_polling", fixed_scope="Skill-specific animation rate and countdown-controlled intro/loop/exit; no DCC call/pixel or movement parity",
            before=previous, after=counts, original_client_count=len(original["units"]), production_count=len(births),
            original_lifetimes=native_lives, production_lifetimes=sorted({b["lifetime"] for b in births}),
            original_animation_rates=sorted({s["unit_4c"] for u in original["units"] for s in u["states"]}),
            production_removed_ticks=[b["removed_tick"] for b in births],
            remaining_differences=["Original client initial path direction remains fixed while velocity bends; actual DCC direction was not captured and generic host facing remains unaudited",
                "Original movement uses native fixed-point charged-bolt steps; host float movement/turn spacing and full seeded path parity remain unresolved",
                "Native initial axis speed fp16/tick="+str(original["units"][0]["states"][0]["velocity_x"])+"; host initial world velocity="+str(births[0]["velocity"]),
                "No original server damage/collision trace, GPU output or full caster input replay"],
            limitations=original["limitations"]+["Pairing by missile row/age; age-zero units precede first resource publication and are compared only by the pure clock regression"],
            sha256=dict(original=sha(original_path), before=sha(before_path), after=sha(after_path)))
        path_evidence=directory/("druid-"+name+"-path-comparison-1.10f.json")
        if path_evidence.exists():
            path_result=read(path_evidence)
            assert path_result["sha256"]["production"]==sha(after_path)
            result["path_evidence"]=dict(file=path_evidence.name,sha256=sha(path_evidence),checks=path_result["checks"])
            result["comparison_status"]="compared_client_path_clock_with_remaining_differences"
            result["fixed_scope"]="Client skill rate, native waypoint movement and fixed-direction mapping; local authority shares path; no original server or GPU parity"
            result["remaining_differences"]=path_result["remaining_differences"]
        assert result["original_client_count"] == result["production_count"]
        assert result["original_lifetimes"] == result["production_lifetimes"]
        assert not counts.get("frame_mismatches",0) and not counts.get("missing_nonbirth_age_samples",0)
        path = directory / ("druid-"+name+"-comparison-1.10f.json")
        path.write_text(json.dumps(result,indent=2)+"\n",encoding="utf8")
        records.append(dict(source_run_id=summary["run_id"], skill_id=skill,
            comparison_status=result["comparison_status"], file=path.name, sha256=sha(path)))
        print(name, "before", previous, "after", counts)
    path = directory / "batch-comparison-summary.json"
    index = read(path)
    index["captures"] = [c for c in index["captures"] if c["skill_id"] not in (240,245)]+records
    path.write_text(json.dumps(index,indent=2)+"\n",encoding="utf8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory",type=Path)
    compare(parser.parse_args().directory)
