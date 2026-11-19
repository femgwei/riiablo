"""Export unit-state evidence only. This does not establish the drawn DCC cells."""
import argparse
from collections import defaultdict
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
    identity = json.loads((capture / "identity.json").read_text(encoding="utf8"))
    summary = json.loads((capture / "summary.json").read_text(encoding="utf8"))
    assert summary["cleanup_confirmed"] and summary["detached"]
    raw = (capture / "units.jsonl").read_bytes()
    groups = defaultdict(list)
    for line in raw.splitlines():
        row = json.loads(line)
        groups[row["side"], row["unit_id"]].append(row)
    units = []
    for (side, uid), rows in sorted(groups.items()):
        first = rows[0]
        assert [r["game_frame"] for r in rows] == list(range(
            first["game_frame"], rows[-1]["game_frame"] + 1))
        transitions = []
        last_mode = None
        for row in rows:
            if row["mode"] != last_mode:
                transitions.append(dict(game_frame=row["game_frame"], mode=row["mode"],
                    animation_frames_raw=row["animation_frames_raw"],
                    animation_rate_raw=row["animation_rate_raw"]))
                last_mode = row["mode"]
        units.append(dict(side=side, unit_id=uid, class_id=first["class_id"],
            first_frame=first["game_frame"], last_frame=rows[-1]["game_frame"],
            # Native paths store a cell centre with low word 0x8000.
            cell_offset_x=(first["x_fixed"] >> 16) - identity["target_x"],
            cell_offset_y=(first["y_fixed"] >> 16) - identity["target_y"],
            path_direction=first["direction"], transitions=transitions,
            death_animation_cursors=[r["animation_cursor"] for r in rows if r["mode"] == 0],
            still_present_at_capture_end=True))
    return dict(schema=1, run_id=run.parent.name, source_capture=str(capture.resolve()),
        source_sha256=hashlib.sha256(raw).hexdigest(), identity=identity,
        background_verified=True, owned_game_closed=True,
        frame_count=summary["last_frame"] - summary["first_frame"] + 1,
        server_samples=summary["server_samples"], client_samples=summary["client_samples"],
        units=units,
        limitations=["Unit-state animation clock; not an actual COF/DCC draw trace",
            "Server target-mode cast; local client skill-input callback not exercised",
            "Corpses still present at end; final corpse deletion time not measured",
            "No controlled collision/movement or damage/HP verification"])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    data = export(args.run)
    args.output.write_text(json.dumps(data, indent=2) + "\n", encoding="utf8")
    print(json.dumps({k: data[k] for k in ("run_id", "frame_count", "server_samples", "client_samples")}))
