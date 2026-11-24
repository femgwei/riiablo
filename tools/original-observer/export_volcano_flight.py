"""Freeze two background-only 1.10f Volcano casts, keeping draw and hit evidence separate."""
import argparse
from collections import defaultdict
import hashlib
import json
from pathlib import Path


def read(path):
    return json.loads(path.read_text(encoding="utf8"))


def rows(path):
    return [json.loads(line) for line in path.read_text(encoding="utf8").splitlines() if line]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def signed(n):
    return n if n < 0x80000000 else n - 0x100000000


def cast(directory, run_id, draws=None):
    raw_path = directory / "missiles.jsonl"
    raw = [s for s in rows(raw_path) if s["side"] == "client"]
    controller = next(s for s in raw if s["class_id"] == 479)
    birth = controller["game_frame"] - controller["total_frames"] + controller["current_frame"]
    groups = defaultdict(list)
    for s in raw:
        if s["class_id"] == 481:
            groups[s["unit_id"]].append(s)
    rocks = []
    for uid, states in groups.items():
        first = states[0]
        life = first["total_frames"]
        age = life - first["current_frame"]
        emission = first["game_frame"] - age - birth
        compact = []
        for s in states:
            state = dict(age=life-s["current_frame"], x_fixed=s["x_fixed"], y_fixed=s["y_fixed"])
            if "gfx_aux_words" in s:
                state.update(z_fixed=signed(s["gfx_aux_words"][3]), vz_fixed=signed(s["gfx_aux_words"][6]))
            compact.append(state)
        rocks.append(dict(unit_id=uid, emission_age_estimate=emission, lifetime=life,
            velocity_x=first["velocity_x"], velocity_y=first["velocity_y"], states=compact))
    result = dict(run_id=run_id, controller_target_seed=controller["missile_data_words"][10],
        origin_x=(controller["x_fixed"] >> 16), origin_y=(controller["y_fixed"] >> 16),
        source=str(directory), source_sha256=sha(raw_path), rocks=rocks)
    if draws is not None:
        lifetimes = {u["unit_id"]: u["states"][0]["total_frames"] for u in draws["units"] if u["side"] == "client"}
        result["selections"] = [dict(missile=s["missile"], age=s["native_age"], frame=s["frame"],
            lifetime=lifetimes[s["unit_id"]], unit_id=s["unit_id"]) for s in draws["samples"]
            if s["missile"] in ("volcano debris 2", "volcano explosion", "volcano small fire")]
    hit_path = directory / "client-hit55.jsonl"
    if hit_path.exists():
        result["hits"] = rows(hit_path)
        result["hit_sha256"] = sha(hit_path)
        seen = {s["unit_id"] for s in raw}
        result["created_but_unsampled"] = [s["child_unit_id"] for s in result["hits"]
            if s["kind"] == "create_return" and s["result"] and s["child_unit_id"] not in seen]
        hit_ids = {s["unit_id"] for s in result["hits"] if s["kind"] == "owner_return"}
        result["observed_rocks_without_hit"] = sorted(set(groups) - hit_ids)
    return result


def export(directory, hit_run, output):
    replay = read(directory / "druid-volcano-replay-input-1.10f.json")
    assert replay["background_verified"] and replay["owned_game_closed"]
    worker = hit_run / "orchestrator/worker"
    summary = read(worker / "summary.json")
    assert summary["status"] == "passed" and not summary["cleanup_attention_required"] and summary["owned_game_closed"]
    assert summary["desktop"].startswith("riiablo_molten_")
    assert all(c["native"]["cleanup_confirmed"] and c["native"]["detached"] for c in summary["captures"])
    result = dict(schema=1, scope="Volcano CltDo50/CltDo1/CltHit55; two distinct casts, not pixel or damage parity",
        fixture=replay["fixture"], background_verified=True,
        cases=dict(draw=cast(Path(replay["source_capture"]), replay["run_id"], replay),
                   arc_hits=cast(worker / "volcano", hit_run.name)),
        provenance=dict(replay_sha256=sha(directory / "druid-volcano-replay-input-1.10f.json"),
                        summary_sha256=sha(worker / "summary.json")),
        limitations=["Draw selections and graphics/hit trace are different casts with independent seeds",
                     "Game-frame and missile countdown sampling is not atomic; emission estimates have a one-tick phase shift",
                     "Unobserved short flights are not silently manufactured as captured units",
                     "Early removal cause and complete native room lifecycle remain unresolved",
                     "Seed replay conditions a controller only; global allocator RNG is not reproduced"])
    result["cases"]["draw"]["fixture"] = replay["fixture"]
    result["cases"]["arc_hits"]["fixture"] = summary["fixture"]
    result["cases"]["arc_hits"]["identity"] = read(worker / "volcano/identity.json")
    result["provenance"]["owned_game_closed"] = summary["owned_game_closed"]
    result["provenance"]["private_desktop"] = summary["desktop"]
    output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf8")
    for name, c in result["cases"].items():
        print(name, "rocks", len(c["rocks"]), "states", sum(len(r["states"]) for r in c["rocks"]),
              "selections", len(c.get("selections", [])), "hits", len(c.get("hits", [])))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("hit_run", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    export(args.directory, args.hit_run, args.output)
