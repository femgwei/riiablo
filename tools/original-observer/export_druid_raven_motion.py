"""Export native Raven AI decisions and motion stages without inferring exact path parity."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path


def read(path):
    return json.loads(path.read_text(encoding="utf8"))


def lines(path):
    return [json.loads(x) for x in path.read_text(encoding="utf8").splitlines()]


def path_evidence(base):
    """Summarize the native cursor and room input without claiming route parity."""
    motion = lines(base / "native-target/raven-motion.jsonl")
    grid = read(base / "native-target/room-grid.json")
    rooms = grid.get("rooms", [grid])
    assert rooms
    for room in rooms:
        width, height = room["width"], room["height"]
        assert 0 < width <= 512 and 0 < height <= 512
        assert len(room["flags"]) == height and all(len(row) == width for row in room["flags"])
    points = [point for row in motion for point in row.get("path_points", [])]
    assert all(0 <= point["x"] <= 65535 and 0 <= point["y"] <= 65535 for point in points)
    nonempty = [row for row in motion if row.get("path_points")]
    signatures = []
    for row in nonempty:
        signature = (row["frame"], row["path_type"], row["path_count"],
                     row["path_index"], tuple((p["x"], p["y"]) for p in row["path_points"]))
        if not signatures or signature != signatures[-1]:
            signatures.append(signature)
    return dict(room_grid=dict(source_room=grid.get("source_room"), room_count=len(rooms),
        rooms=[dict(room_pointer=room["room_pointer"], grid_x=room["grid_x"],
            grid_y=room["grid_y"], width=room["width"], height=room["height"],
            unique_flags=sorted({flag for row in room["flags"] for flag in row})) for room in rooms],
        sha256=hashlib.sha256((base / "native-target/room-grid.json").read_bytes()).hexdigest()),
        path_point_rows=len(nonempty), path_point_total=len(points),
        path_point_signatures=[dict(frame=f, path_type=t, path_count=c, path_index=i,
            points=[dict(x=x, y=y) for x, y in ps]) for f, t, c, i, ps in signatures[:128]])


def export(run, town):
    report = read(run / "orchestrator.json")
    worker = report["worker"]
    native, capture = worker["native_target"], worker["capture"]
    assert report["status"] == worker["status"] == native["status"] == capture["status"] == "passed"
    assert not report["switched_desktop"] and worker["owned_game_closed"]
    assert native["detached"] and native["cleanup_confirmed"] and native["fixtures_removed"]
    assert capture["selected_skill_before_cast"] == [221, 1]
    base = run / "worker"
    identity = read(base / "native-target/identity.json")
    if town:
        assert identity["town_orbit"] and not identity["target_created"] and identity["target_id"] == 0
    motion = lines(base / "native-target/raven-motion.jsonl")
    calls = lines(base / "native-target/raven-decisions.jsonl")
    assert len({r["unit_id"] for r in motion}) == 1 and len(calls) % 2 == 0
    decisions = []
    for before, after in zip(calls[::2], calls[1::2]):
        assert before["stage"] == "entry" and after["stage"] == "return"
        assert before["frame"] == after["frame"] and before["unit_id"] == after["unit_id"]
        attack = after["mode"] == 4
        if attack:
            assert before["attack_due"] < before["frame"] and before["in_combat"]
            assert after["attack_due"] - after["frame"] == 50
            assert after["ai_pecks"] == before["ai_pecks"] - 1
        else:
            assert before["ai_pecks"] == -1 and after["ai_pecks"] == 12 or before["ai_pecks"] == after["ai_pecks"]
        decisions.append(dict(frame=before["frame"], target=before["decision_target_id"],
            target_distance=before["target_distance"], in_combat=before["in_combat"],
            budget_before=before["ai_pecks"], budget_after=after["ai_pecks"],
            attack_due_before=before["attack_due"], attack_due_after=after["attack_due"],
            attack=attack, mode_after=after["mode"], path_type_after=after["path_type"],
            circle_side_after=after["circle_side"], path_target_type=after["path_target_type"],
            path_target_id=after["path_target_id"], direction_after=after["new_direction"]))
    transitions = [r for i, r in enumerate(motion) if i == 0 or r["mode"] != motion[i-1]["mode"]]
    attacks = [r["frame"] for r in transitions if r["mode"] == 4]
    durations = [next((r["frame"] - frame for r in transitions if r["frame"] > frame), None) for frame in attacks]
    positions = len({(r["x_fixed"], r["y_fixed"]) for r in motion})
    if town:
        assert not attacks and positions > 30 and all(d["budget_after"] == 12 for d in decisions)
        assert any(d["path_type_after"] in (5, 6) for d in decisions)
    else:
        assert attacks and any(x == 25 for x in durations) and all(x in (25, None) for x in durations)
        assert any(not d["attack"] and d["frame"] <= d["attack_due_before"] and d["mode_after"] == 2 for d in decisions)
    path = path_evidence(base)
    sources = ["orchestrator.json", "worker/summary.json", "worker/native-target/identity.json",
        "worker/native-target/summary.json", "worker/native-target/raven-stats.jsonl",
        "worker/native-target/raven-decisions.jsonl", "worker/native-target/raven-motion.jsonl",
        "worker/native-target/room-grid.json",
        "worker/capture/summary.json", "worker/capture/raven-samples.json",
        "worker/provenance/d2game_raven_target.cpp", "worker/provenance/d2game_raven_target.exe"]
    return dict(run=str(run), town_orbit=town, background_verified=True, owned_game_closed=True,
        native_cleanup=native, target_intervention=identity,
        raven_stats=lines(base / "native-target/raven-stats.jsonl")[0],
        decisions=decisions, attack_frames=attacks, attack_durations=durations,
        attack_intervals=[b-a for a, b in zip(attacks, attacks[1:])],
        path_evidence=path,
        distinct_positions=positions, path_types=dict(Counter(r["path_type"] for r in motion)),
        mode_transitions=[dict(frame=r["frame"], mode=r["mode"]) for r in transitions],
        client_modes=capture["raven_mode_values"],
        sha256={name: hashlib.sha256((run/name).read_bytes()).hexdigest() for name in sources})


if __name__ == "__main__":
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("combat", type=Path)
    p.add_argument("town", type=Path)
    p.add_argument("--output", type=Path, required=True)
    args = p.parse_args()
    result = dict(schema=2, scope="Raven AI stages plus native waypoint and room-grid evidence",
        cases=dict(combat=export(args.combat, False), town=export(args.town, True)),
        limitations=["Selected Fn107 calls and stopped-thread path snapshots, not all movement helper calls.",
            "A 50-tick attack deadline is not a fixed attack interval: pathing, chance and decision cadence add delay.",
            "Town capture spawns no target and modifies no monster HP; the combat capture uses a durable experimental target.",
            "Client polling establishes received modes, not exact facing/resource frame or rendered pixels.",
            "PathPoints and room flags are now exported, but the two runtimes have not yet replayed the same room in one process; identical inputs are still required before claiming coordinate parity."])
    args.output.write_text(json.dumps(result, indent=2)+"\n", encoding="utf8")
    print(json.dumps({k: dict(attacks=len(c["attack_frames"]), intervals=c["attack_intervals"],
        positions=c["distinct_positions"]) for k,c in result["cases"].items()}))
