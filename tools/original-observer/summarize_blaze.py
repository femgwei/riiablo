"""Export a background Blaze capture into a compact, independently checked oracle.

No game is launched. Retains every submitted frame, including the reverse tail;
phase is inferred from the observed first loop frame, not from an assumed RNG.
"""
import argparse
from collections import defaultdict
import csv
import hashlib
import json
from pathlib import Path


def export(run):
    report = json.loads((run / "orchestrator.json").read_text(encoding="utf8"))
    assert report["status"] == "passed" and not report["switched_desktop"]
    assert report["input_desktop_before"] == report["input_desktop_after"] == "Default"
    assert report["screen_before"] == report["screen_after"]
    assert report["worker"]["owned_game_closed"]
    capture = run / "worker" / "capture"
    summary = json.loads((capture / "summary.json").read_text(encoding="utf8"))
    assert summary["cleanup_confirmed"] and summary["detached"]
    raw = (capture / "sprite-submissions.jsonl").read_bytes()
    records = [json.loads(line) for line in raw.splitlines()]
    returned = {r["index"] for r in records if r["kind"] == "sprite_call_returned"}
    units = defaultdict(list)
    for r in records:
        if r["kind"] == "sprite_submission":
            assert r["index"] in returned
            units[r["unit_id"]].append(r)
    trails = []
    for uid, rows in units.items():
        first = rows[0]
        assert [r["game_frame"] for r in rows] == list(range(
            first["game_frame"], first["game_frame"] + len(rows)))
        assert [r["frame"] for r in rows[:12]] == list(range(12))
        assert [r["frame"] for r in rows[-11:]] == list(range(10, 0, -1)) + [1]
        phase = rows[12]["frame"] - 12
        for age, r in enumerate(rows):
            expected = (age if age < 12 else max(1, len(rows) - age - 1)
                        if age >= len(rows) - 11 else 12 + (age - 12 + phase) % 24)
            assert r["frame"] == expected, (uid, age, r["frame"], expected)
            assert r["animation_cursor"] >> 8 == r["frame"]
            assert r["direction"] == 0
            if r["selection_observed"]:
                assert r["dcc_direction"] == 0 and r["dcc_directions"] == 1
                assert r["dcc_frames"] == 37
        trails.append(dict(unit_id=uid, class_id=first["class_id"],
            cell_name=first["cell_name"], first_frame=first["game_frame"],
            x_fixed=first["x_fixed"], y_fixed=first["y_fixed"],
            lifetime_frames=len(rows), observed_phase=phase,
            selection_count=sum(r["selection_observed"] for r in rows),
            frames=[r["frame"] for r in rows]))
    server = defaultdict(list)
    missiles_raw = (capture / "missiles.jsonl").read_bytes()
    for line in missiles_raw.splitlines():
        r = json.loads(line)
        if r["side"] == "server":
            server[r["unit_id"]].append(r)
    segments = [dict(unit_id=uid, first_frame=rs[0]["game_frame"],
        lifetime_frames=len(rs), x_fixed=rs[0]["x_fixed"], y_fixed=rs[0]["y_fixed"])
        for uid, rs in server.items()]
    for s in segments:
        matched = [t for t in trails if all(t[k] == s[k] for k in
                   ("first_frame", "x_fixed", "y_fixed"))]
        assert len(matched) == 1, s
        s["client_unit_id"] = matched[0]["unit_id"]
    with (capture / "frames.csv").open(encoding="utf8", newline="") as f:
        frames = list(csv.DictReader(f))
    movement = int(frames[-1]["server_x_fixed"]) - int(frames[0]["server_x_fixed"])
    assert movement != 0 and len(segments) > 1
    # Current Riiablo gate, supplied the same segment births and positions.
    # A one-subtile x displacement projects to (16,8) pixels.
    accepted = []
    for s in segments:
        if accepted:
            prev = accepted[-1]
            dx = (s["x_fixed"] - prev["x_fixed"]) / 65536
            dy = (s["y_fixed"] - prev["y_fixed"]) / 65536
            pixels2 = (16 * (dx - dy)) ** 2 + (8 * (dx + dy)) ** 2
            if (s["first_frame"] - prev["first_frame"]) / 25 < .1 - .00001 or pixels2 < 196:
                continue
        accepted.append(s)
    return dict(schema=2, run_id=run.parent.name,
        source_capture=str(capture.resolve()),
        source_sha256=hashlib.sha256(raw).hexdigest(),
        missile_source_sha256=hashlib.sha256(missiles_raw).hexdigest(),
        background_verified=True, owned_game_closed=True,
        dcc_directions=1, dcc_frames=37, dcc_rate_8_8=256,
        loop_start=12, loop_stop_exclusive=36, shrink_frames=11,
        sprite_calls=sum(len(rs) for rs in units.values()),
        selections_observed=sum(t["selection_count"] for t in trails),
        player_movement_fixed_x=movement, client_trails=trails,
        server_segments=segments,
        riiablo_gate_replay=dict(input_segments=len(segments),
            accepted_segments=len(accepted),
            accepted_server_unit_ids=[s["unit_id"] for s in accepted]),
        limitations=["Single level-1 walking cast; no damage or pixel parity assertion",
            "Gate replay feeds native births into current policy, not a live Riiablo run",
            "Observed phase fitted from frames; native RNG consumption is not reconstructed"])


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("run", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    data = export(args.run)
    args.output.write_text(json.dumps(data, indent=2) + "\n", encoding="utf8")
    print(json.dumps({k: data[k] for k in ("run_id", "sprite_calls",
        "selections_observed", "riiablo_gate_replay")}))
