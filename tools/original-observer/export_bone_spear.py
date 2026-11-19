"""Export background original Bone Spear paths and actual DCC selections."""
import argparse
from collections import defaultdict
import hashlib
import json
from pathlib import Path

NAMES = {192: "bonespear", 216: "bonespearexplode", 248: "bonespeartrail"}


def export(run):
    outer = json.loads((run / "orchestrator.json").read_text(encoding="utf8"))
    assert outer["status"] == "passed" and not outer["switched_desktop"]
    assert outer["input_desktop_before"] == outer["input_desktop_after"] == "Default"
    assert outer["screen_before"] == outer["screen_after"]
    assert outer["worker"]["owned_game_closed"]
    capture = run / "worker/capture"
    native = json.loads((capture / "summary.json").read_text(encoding="utf8"))
    assert native["status"] == "passed" and native["detached"] and native["cleanup_confirmed"]
    identity = json.loads((capture / "identity.json").read_text(encoding="utf8"))
    raw = (capture / "missiles.jsonl").read_bytes()
    by_unit = defaultdict(list)
    client_states = {}
    for line in raw.splitlines():
        row = json.loads(line)
        by_unit[row["side"], row["class_id"], row["unit_id"]].append(row)
        if row["side"] == "client":
            client_states[row["unit_id"], row["game_frame"]] = row
    units = []
    keys = ("game_frame", "x_fixed", "y_fixed", "velocity_x", "velocity_y", "path_direction",
            "total_frames", "current_frame", "unit_44", "unit_48", "unit_4c")
    for (side, cls, uid), states in sorted(by_unit.items()):
        units.append(dict(side=side, missile=NAMES[cls], unit_id=uid,
            states=[{k: row[k] for k in keys} for row in states]))
    draw_raw = (capture / "sprite-submissions.jsonl").read_bytes()
    draws, pending = [], None
    for line in draw_raw.splitlines():
        row = json.loads(line)
        if row["kind"] == "sprite_submission":
            assert pending is None
            pending = row
        else:
            assert row["kind"] == "sprite_call_returned" and pending["index"] == row["index"]
            draws.append(pending)
            pending = None
    assert pending is None
    assert len(draws) == native["spear_sprite_submissions"] == native["spear_sprite_calls_returned"]
    selected = []
    for row in draws:
        if not row["selection_observed"]:
            continue
        assert row["frame"] == row["animation_cursor"] >> 8
        assert 0 <= row["dcc_direction"] < row["dcc_directions"]
        assert 0 <= row["frame"] < row["dcc_frames"]
        state = client_states[row["unit_id"], row["game_frame"]]
        assert state["unit_44"] == row["animation_cursor"]
        selected.append(dict(missile=NAMES[row["class_id"]],
            native_age=state["total_frames"] - state["current_frame"],
            **{k: row[k] for k in (
            "index", "unit_id", "game_frame", "x_fixed", "y_fixed", "velocity_x", "velocity_y",
            "path_direction", "animation_cursor", "frame", "dcc_direction", "dcc_directions",
            "dcc_frames", "cell_name")}))
    assert len(selected) == native["spear_dcc_selections_observed"]
    return dict(schema=1, run_id=run.parent.name, identity=identity,
        background_verified=True, owned_game_closed=True,
        source_capture=str(capture.resolve()), state_sha256=hashlib.sha256(raw).hexdigest(),
        draw_sha256=hashlib.sha256(draw_raw).hexdigest(), paired_draws=len(draws),
        selections_observed=len(selected), selections_unobserved=len(draws)-len(selected),
        units=units, samples=selected,
        limitations=["Single level-one, no-synergy cast in one direction; not all directions or skill levels",
            "Original client and server updates are asynchronous; sample game_frame is the server clock",
            "current_frame retains native field +0x10, which counts down in these observed missiles",
            "Cache-miss draws without the selected breakpoint are retained raw, excluded from selection oracle",
            "No controlled targets/damage/collision or pixel equivalence demonstrated"])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    data = export(args.run)
    args.output.write_text(json.dumps(data, indent=2) + "\n", encoding="utf8")
    print(json.dumps({k: data[k] for k in ("run_id", "paired_draws", "selections_observed")}))
