"""Export background original Inferno paths and actual DCC selections."""
import argparse
import csv
from collections import defaultdict
import hashlib
import json
from pathlib import Path

NAMES = {60: "infernoflame1", 61: "infernoflame2"}


def export(run, capture_name="capture"):
    outer = json.loads((run / "orchestrator.json").read_text(encoding="utf8"))
    assert outer["status"] == "passed" and not outer["switched_desktop"]
    assert outer["input_desktop_before"] == outer["input_desktop_after"] == "Default"
    assert outer["screen_before"] == outer["screen_after"]
    assert outer["worker"]["owned_game_closed"]
    capture = run / "worker" / capture_name
    native = json.loads((capture / "summary.json").read_text(encoding="utf8"))
    assert native["status"] == "passed" and native["detached"] and native["cleanup_confirmed"]
    provenance = {}
    for name, key in (("d2game_inferno.exe", "native_sha256"),
                      ("d2game_inferno.cpp", "native_source_sha256"),
                      ("d2game_controller.cpp", "controller_source_sha256"),
                      ("inferno_background.py", "runner_source_sha256")):
        actual = hashlib.sha256((run / "worker/provenance" / name).read_bytes()).hexdigest()
        assert actual == outer["worker"][key]
        provenance[name] = actual
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
            states=[dict({k: row[k] for k in keys}, seed_low=row["unit_words"][8],
                         seed_high=row["unit_words"][9]) for row in states]))
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
    assert len(draws) == native["inferno_sprite_submissions"] == native["inferno_sprite_calls_returned"]
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
            cell_name=row.get("cell_name"), cell_name_observed="cell_name" in row,
            **{k: row[k] for k in (
            "index", "unit_id", "game_frame", "x_fixed", "y_fixed", "velocity_x", "velocity_y",
            "path_direction", "animation_cursor", "frame", "dcc_direction", "dcc_directions",
            "dcc_frames")}))
    assert len(selected) == native["inferno_dcc_selections_observed"]
    caster_states = []
    with (capture / "frames.csv").open(encoding="utf8", newline="") as stream:
        for row in csv.DictReader(stream):
            # The first captured harness used semantic labels for raw fields.
            # In particular +0x48 must not be interpreted as animation speed.
            renamed = {"server_anim_cursor": "server_unit44", "client_anim_cursor": "client_unit44",
                       "server_anim_speed": "server_unit48", "client_anim_speed": "client_unit48"}
            caster_states.append({renamed.get(k, k): int(v) for k, v in row.items()})
    return dict(schema=1, run_id=run.parent.name, identity=identity,
        background_verified=True, owned_game_closed=True,
        source_capture=str(capture.resolve()), provenance_sha256=provenance,
        state_sha256=hashlib.sha256(raw).hexdigest(),
        draw_sha256=hashlib.sha256(draw_raw).hexdigest(), paired_draws=len(draws),
        selections_observed=len(selected), selections_unobserved=len(draws)-len(selected),
        units=units, samples=selected, caster_states=caster_states,
        limitations=["One level-one, no-synergy held cast (40 ticks) in one direction; not all directions or skill levels",
            "Original client and server updates are asynchronous; sample game_frame is the server clock",
            "current_frame retains native field +0x10, which counts down in these observed missiles",
            "Cache-miss draws without the selected breakpoint are retained raw, excluded from selection oracle",
            "No controlled targets/damage/collision or pixel equivalence demonstrated"])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--capture", default="capture")
    args = parser.parse_args()
    data = export(args.run, args.capture)
    args.output.write_text(json.dumps(data, indent=2) + "\n", encoding="utf8")
    print(json.dumps({k: data[k] for k in ("run_id", "paired_draws", "selections_observed")}))
