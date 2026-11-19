"""Export paired original sprite selections as an independent headless test oracle."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path

NAMES = {260: "frozenorb", 261: "frozenorbbolt", 262: "frozenorbnova"}


def export(capture, output):
    native = json.loads((capture / "summary.json").read_text(encoding="utf8"))
    if native["status"] != "passed" or not native["cleanup_confirmed"]:
        raise ValueError("Require a successful, safely detached capture")
    source = capture / "sprite-submissions.jsonl"
    data = source.read_bytes()
    events = [json.loads(line) for line in data.decode("utf8").splitlines()]
    rows, pending = [], None
    for event in events:
        if event["kind"] == "sprite_submission":
            if pending is not None:
                raise ValueError("Unpaired sprite call")
            pending = event
        elif event["kind"] == "sprite_call_returned":
            if pending is None or pending["index"] != event["index"]:
                raise ValueError("Sprite return identity mismatch")
            rows.append(pending)
            pending = None
        else:
            raise ValueError("Unknown sprite event")
    if pending or len(rows) != native["orb_sprite_submissions"] or len(rows) != native["orb_sprite_calls_returned"]:
        raise ValueError("Native sprite counts disagree")
    selected = []
    for row in rows:
        if row["frame"] != row["animation_cursor"] >> 8 or row["direction"] != row["path_direction"]:
            raise ValueError("Draw block does not match this unit's animation/path")
        if not row.get("selection_observed"):
            continue
        if not 0 <= row["dcc_direction"] < row["dcc_directions"] or not 0 <= row["frame"] < row["dcc_frames"]:
            raise ValueError("Invalid actual DCC selection")
        if row["dcc_frames"] != row["cell_14_raw"]:
            raise ValueError("DCC cache and submitted frame count disagree")
        selected.append(dict(missile=NAMES[row["class_id"]], **{key: row[key] for key in (
            "index", "unit_id", "game_frame", "velocity_x", "velocity_y", "path_direction",
            "animation_cursor", "frame", "dcc_direction", "dcc_directions", "dcc_frames", "cell_name")}))
    if len({r["unit_id"] for r in selected if r["missile"] == "frozenorbnova"}) != 16:
        raise ValueError("Require all 16 final-ring units")
    fixture = dict(schema=1, source=str(source.resolve()), source_sha256=hashlib.sha256(data).hexdigest(),
                   run_id=capture.parents[2].name, paired_submissions=len(rows),
                   selections_observed=len(selected), selections_unobserved=len(rows)-len(selected),
                   scope="actual D2CMP DCC selection paired with D2Gfx call and return; no pixel/damage parity",
                   samples=selected)
    output.write_text(json.dumps(fixture, indent=2) + "\n", encoding="utf8")
    print(json.dumps(dict(output=str(output), paired=len(rows), selected=len(selected),
                          counts=Counter(r["missile"] for r in selected)), indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("capture", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    export(args.capture, args.output)
