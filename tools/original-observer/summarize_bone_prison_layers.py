"""Retain matched original Bone Prison layered draw/selection/return evidence."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import struct


def export(run):
    report = json.loads((run / "orchestrator.json").read_text(encoding="utf8"))
    assert report["status"] == "passed" and not report["switched_desktop"]
    assert report["input_desktop_before"] == report["input_desktop_after"] == "Default"
    assert report["screen_before"] == report["screen_after"]
    assert report["worker"]["owned_game_closed"]
    capture = run / "worker" / "capture"
    summary = json.loads((capture / "summary.json").read_text(encoding="utf8"))
    assert summary["cleanup_confirmed"] and summary["detached"]
    raw = (capture / "bone-layer-submissions.jsonl").read_bytes()
    events = [json.loads(line) for line in raw.splitlines()]
    by_kind = {kind: {r["index"]: r for r in events if r["kind"] == kind}
               for kind in ("layer_submission", "dcc_selection", "layer_call_returned")}
    calls, selections, returns = (by_kind[k] for k in by_kind)
    assert calls.keys() == selections.keys() == returns.keys()
    assert len(calls) == summary["bone_layer_calls"] == summary["bone_layer_returns"]
    assert len(selections) == summary["bone_dcc_selections"]
    first = {}
    for line in (capture / "units.jsonl").read_bytes().splitlines():
        row = json.loads(line)
        if row["side"] == "server":
            first.setdefault(row["unit_id"], row)
    identity = json.loads((capture / "identity.json").read_text(encoding="utf8"))
    samples = []
    for index, row in calls.items():
        cell = row["cell_raw"]
        assert cell[8:10] == [1, 344]
        token, component, variant, mode, weapon = (
            struct.pack("<I", n).decode("ascii").rstrip() for n in cell[12:17])
        unit = first[row["unit_id"]]
        samples.append(dict(index=index, unit_id=row["unit_id"],
            game_frame=row["game_frame"], mode=row["mode"], frame=row["frame"],
            direction=row["direction"], component=row["component"],
            token=token, component_name=component, variant=variant, mode_name=mode,
            weapon_class=weapon,
            resource=f"data/global/monsters/{token}/{component}/{token}{component}{variant}{mode}{weapon}.dcc",
            cell_offset_x=(unit["x_fixed"] >> 16) - identity["target_x"],
            cell_offset_y=(unit["y_fixed"] >> 16) - identity["target_y"],
            server_path_direction=unit["direction"],
            **{k: selections[index][k] for k in ("dcc_direction", "dcc_directions", "dcc_frames")}))
    assert len({r["unit_id"] for r in samples}) == 12
    return dict(schema=1, run_id=run.parent.name,
        source_capture=str(capture.resolve()), source_sha256=hashlib.sha256(raw).hexdigest(),
        background_verified=True, owned_game_closed=True,
        calls_observed=len(calls), selections_observed=len(selections), returns_observed=len(returns),
        modes=dict(Counter(r["mode_name"] for r in samples)), samples=samples,
        limitations=["Only ordinary layered draw call RVA 0xBADED and its DCC selection are instrumented",
            "No proof of absence of layers on other draw branches, separate effects or DD corpse rendering",
            "Server target-mode cast; local CltDo is not exercised",
            "This captures resource/frame/direction selection, not GPU pixels or complete lifecycle parity"])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    data = export(args.run)
    args.output.write_text(json.dumps(data, indent=2) + "\n", encoding="utf8")
    print(json.dumps({k: data[k] for k in ("run_id", "calls_observed", "modes")}))
