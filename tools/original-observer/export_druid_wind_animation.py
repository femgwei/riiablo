"""Freeze observed client wind cursors, preserving their non-atomic/non-DCC scope."""
import argparse
import hashlib
import json
from pathlib import Path


def export(directory, output):
    summary_path = directory / "runtime-wind-polling-summary.json"
    summary = json.loads(summary_path.read_text(encoding="utf8"))
    assert summary["background_verified"] and summary["owned_game_closed"]
    result = dict(schema=1, scope="Observed unsuspended client animation cursor states; not actual DCC selections, pixels, server or damage",
        run_id=summary["run_id"], background_verified=True, fixture=summary["fixture"], cases={},
        summary_sha256=hashlib.sha256(summary_path.read_bytes()).hexdigest())
    for capture in summary["captures"]:
        source = directory / capture["file"]
        assert hashlib.sha256(source.read_bytes()).hexdigest() == capture["sha256"]
        data = json.loads(source.read_text(encoding="utf8"))
        name = "twister" if data["skill_id"] == 240 else "tornado"
        assert data["selections_observed"] == data["paired_draws"] == 0
        result["cases"][name] = dict(skill_id=data["skill_id"], identity=data["identity"], fixture=data["fixture"],
            source_sha256=capture["sha256"], raw_state_sha256=data["state_sha256"],
            samples=[dict(unit_id=u["unit_id"], age=s["total_frames"]-s["current_frame"],
                lifetime=s["total_frames"], cursor=s["unit_44"], rate=s["unit_4c"],
                path_direction=s["path_direction"], x_fixed=s["x_fixed"], y_fixed=s["y_fixed"],
                velocity_x=s["velocity_x"], velocity_y=s["velocity_y"])
                for u in data["units"] for s in u["states"]], limitations=data["limitations"])
    output.write_text(json.dumps(result, indent=2)+"\n", encoding="utf8")
    print({name:len(case["samples"]) for name,case in result["cases"].items()})


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    export(args.directory, args.output)
