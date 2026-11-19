"""Validate one original WW trace; report observed timing without claiming port parity."""
import argparse
import csv
import json
from collections import Counter
from pathlib import Path


def read_csv(path):
    with path.open(encoding="utf8", newline="") as stream:
        return [{key: int(value) for key, value in row.items()} for row in csv.DictReader(stream)]


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


def check(root):
    summary = json.loads((root / "summary.json").read_text(encoding="utf8"))
    require(summary.get("status") == "passed", "native collector did not pass")
    require(summary.get("cleanup_confirmed") and summary.get("detached")
            and summary.get("fixtures_restored"), "cleanup/restoration unconfirmed")
    identity = json.loads((root / "identity.json").read_text(encoding="utf8"))
    require(identity.get("skill") == 151 and identity.get("level") == 1
            and identity.get("class") == 4, "wrong skill fixture")
    require(identity.get("right_item_class") == 0 and identity.get("left_item_class") == 328,
            "wrong hand-axe/shield fixture")
    frames = read_csv(root / "frames.csv")
    damage = read_csv(root / "damage.csv")
    trace = [json.loads(line) for line in (root / "trace.jsonl").read_text(encoding="utf8").splitlines()]
    first = summary["first_frame"]
    require(frames and frames[0]["frame"] == first, "capture start mismatch")
    require([row["frame"] for row in frames] == list(range(first, first + len(frames))), "skipped/duplicate frame")
    require(frames[-1]["frame"] == summary["last_frame"] and frames[-1]["player_mode"] == 1,
            "cast did not finish at neutral")
    require(all(row["player_mode"] == 18 for row in frames[:-1]), "unexpected intermediate mode")
    require(len(damage) == summary["damage_events"] and damage, "damage event count mismatch")
    require(all(row["attacker"] == identity["player"] and row["victim"] == identity["target"]
                for row in damage), "damage target binding mismatch")
    require(sum(row["total_fixed"] for row in damage) == summary["damage_total_fixed"], "damage total mismatch")
    require(all(first <= row["frame"] <= frames[-1]["frame"] for row in damage), "damage outside capture")
    for row in frames:
        expected = summary["target_hp_before_fixed"] - sum(d["total_fixed"] for d in damage if d["frame"] <= row["frame"])
        require(row["target_hp_fixed"] == expected, "per-frame HP differs from damage events")
    require(frames[-1]["target_hp_fixed"] == summary["target_hp_after_fixed"], "final HP mismatch")
    counts = Counter(row["kind"] for row in trace)
    require(frames[0]["mana_fixed"] <= summary["mana_before_fixed"], "mana increased at cast entry")
    require(min(row["mana_fixed"] for row in frames) == summary["lowest_mana_fixed"],
            "lowest mana differs from frame series")
    immediate_mana_drop = summary["mana_before_fixed"] - frames[0]["mana_fixed"]
    resources = None
    if (root / "resources.json").exists():
        resources = json.loads((root / "resources.json").read_text(encoding="utf8"))
        require(resources["mana_before_fixed"] == summary["mana_before_fixed"], "resource mana binding mismatch")
        resources["observed_step_deltas_fixed"] = sorted({row["mana_fixed"] - before["mana_fixed"]
            for before, row in zip(frames, frames[1:])})
    require(counts == {"start": summary["start_calls"], "do": summary["do_calls"], "damage": len(damage)}
            and counts["start"] == 1 and counts["do"] > 1, "trace callback counts mismatch")
    start = next(row for row in trace if row["kind"] == "start")
    base = start["eip"] - 0xCBFE0
    rvas = {"start": 0xCBFE0, "do": 0xCC4C0, "damage": 0x91260}
    for row in trace:
        require(row["eip"] == base + rvas[row["kind"]] and row["ecx"] == identity["game"]
                and row["edx"] == identity["player"], "callback instruction/context mismatch")
        require(len(row["stack"]) == 16, "incomplete stack capture")
        if row["kind"] in ("start", "do"):
            require(row["stack"][1:3] == [151, 1], "callback skill/level mismatch")
        else:
            require(row["stack"][1] == identity["target"], "damage stack victim mismatch")
    require([row["frame"] for row in trace if row["kind"] == "damage"]
            == [row["frame"] for row in damage], "damage trace/CSV ordering differs")
    windows, previous = [], 0
    for row in frames:
        deadline = row["param4"]
        if deadline and deadline != previous:
            windows.append({"frame": row["frame"] - first, "next_deadline": deadline - first})
            previous = deadline
    dx = identity["destination_x"] - identity["start_x"]
    dy = identity["destination_y"] - identity["start_y"]
    require(all((row["x"]-identity["start_x"])*dy == (row["y"]-identity["start_y"])*dx
                for row in frames), "movement left straight corridor")
    require((frames[-1]["x"], frames[-1]["y"]) != (identity["start_x"], identity["start_y"]), "no displacement")
    precision = None
    if any("x_fp16" in row or "y_fp16" in row for row in frames):
        require(all(all(key in row for key in ("x_fp16", "y_fp16", "path_velocity"))
                    for row in frames), "incomplete precise path capture")
        require(all(row["x_fp16"] >> 16 == row["x"] and row["y_fp16"] >> 16 == row["y"]
                    for row in frames), "FP16/integer coordinate mismatch")
        origin = frames[0]["x_fp16"], frames[0]["y_fp16"]
        moved = [row["frame"] - first for row in frames
                 if (row["x_fp16"], row["y_fp16"]) != origin]
        require(moved, "no precise displacement")
        changed = [row["frame"] - first for before, row in zip(frames, frames[1:])
                   if (row["x_fp16"], row["y_fp16"]) != (before["x_fp16"], before["y_fp16"])]
        precision = dict(first_displacement_frame=moved[0],
                         last_displacement_frame=changed[-1],
                         path_velocities=sorted({row["path_velocity"] for row in frames}),
                         first_step_fp16=[frames[moved[0]]["x_fp16"]-origin[0],
                                          frames[moved[0]]["y_fp16"]-origin[1]])
    return dict(status="passed", source=str(root.resolve()), skill=151, level=1,
                cast_frames=frames[-1]["frame"]-first,
                do_frames=[row["frame"]-first for row in trace if row["kind"] == "do"],
                attack_timer_windows=windows,
                damage_frames=[row["frame"]-first for row in damage],
                damage_events=len(damage), hit_count=sum(row["total_fixed"] > 0 for row in damage),
                damage_total_fixed=summary["damage_total_fixed"],
                mana_net_drop_fixed=summary["mana_before_fixed"]-summary["lowest_mana_fixed"],
                mana_immediate_cost_fixed=immediate_mana_drop,
                mana_final_net_drop_fixed=summary["mana_before_fixed"]-frames[-1]["mana_fixed"],
                resources=resources,
                arrived_requested_destination=(frames[-1]["x"],frames[-1]["y"])
                    == (identity["destination_x"],identity["destination_y"]),
                precise_path=precision,
                scope="original single-hand-axe runtime/stack/HP validation only; no RNG, port timing or visual parity")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = check(args.source)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps(report, ensure_ascii=True))
