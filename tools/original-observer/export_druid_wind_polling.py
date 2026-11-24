"""Export private-desktop wind polling as client-state evidence, never DCC draw evidence."""
import argparse
from collections import defaultdict
import hashlib
import json
from pathlib import Path


def read(path):
    return json.loads(path.read_text(encoding="utf8"))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write(path, data):
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf8")


def export(run, directory):
    outer, worker = read(run / "orchestrator.json"), read(run / "worker/summary.json")
    assert outer["status"] == worker["status"] == "passed"
    assert not outer["switched_desktop"] and outer["private_desktop_not_input_checks"] > 0
    assert outer["private_desktop"] != outer["input_desktop_before"] == outer["input_desktop_after"]
    assert outer["screen_before"] == outer["screen_after"]
    assert worker["owned_game_closed"] and not worker["cleanup_attention_required"]
    assert worker["observation_mode"] == "unsuspended_client_memory_polling"
    assert worker["same_process_verified"] and worker["launch_count"] == 1
    fixture = worker["fixture"]
    assert fixture["hard_points"] == {"240":1, "245":1} and fixture["other_hard_points"] == 0
    assert sha(run / "worker" / (fixture["name"]+"-input.d2s")) == fixture["sha256"]
    directory.mkdir(parents=True, exist_ok=True)
    records = []
    for name, skill, count in [("twister",240,3), ("tornado",245,1)]:
        base = run / "worker" / name
        native, identity = read(base / "summary.json"), read(base / "identity.json")
        assert native["status"] == "passed" and native["cleanup_confirmed"]
        assert not native["debugger_attached"] and not native["memory_writes"] and not native["remote_execution"]
        assert native["final_client_units"] == 0 and identity["skill"] == skill
        assert identity["client_player"] == worker["same_client_player_address"]
        raw_path = base / "missiles.jsonl"
        units = defaultdict(dict)
        raw_count = 0
        for line in raw_path.read_text(encoding="utf8").splitlines():
            sample = json.loads(line); raw_count += 1
            assert sample["side"] == "client" and sample["class_id"] == (477 if skill == 240 else 478)
            key = tuple(sample[k] for k in ("current_frame", "x_fixed", "y_fixed", "unit_44", "velocity_x", "velocity_y", "path_direction"))
            units[sample["unit_id"]].setdefault(key, sample)
        assert len(units) == count
        limitations = ["Unsuspended memory polling is non-atomic and may miss ticks or contain mixed-update fields",
            "No actual DCC selection, sprite submission, GPU pixels or stack trace was captured",
            "Client state only; server damage/collision/counts remain unobserved",
            "One cast/direction at level one, two-skill recorded loadout, native synergies active"]
        data = dict(schema=1, run_id=run.parent.name, skill_id=skill, fixture=fixture,
            background_verified=True, owned_game_closed=True, identity=identity,
            observation_mode=worker["observation_mode"], runtime_status="captured_client_polling",
            comparison_status="pending_matched_loadout_replay", source_capture=str(base.resolve()),
            raw_samples=raw_count, state_sha256=sha(raw_path),
            units=[dict(side="client", missile=name, unit_id=uid, states=list(states.values())) for uid, states in units.items()],
            samples=[], selections_observed=0, paired_draws=0, limitations=limitations)
        file = "druid-"+name+"-replay-input-1.10f.json"
        write(directory / file, data)
        records.append(dict(skill_id=skill, file=file, sha256=sha(directory / file),
            runtime_status=data["runtime_status"], comparison_status=data["comparison_status"],
            observation_mode=worker["observation_mode"], unique_client_units=len(units),
            raw_samples=raw_count, unique_states=sum(len(s) for s in units.values()), dcc_selections=0))
    result = dict(schema=1, run_id=run.parent.name, source=str(run.resolve()), class_code="dru",
        background_verified=True, owned_game_closed=True, launch_count=1, same_process_verified=True,
        observation_mode=worker["observation_mode"], fixture=fixture, captures=records,
        scope="Client memory polling capture; not original server or DCC/pixel parity",
        provenance_sha256={p.name:sha(p) for p in (run / "worker/provenance").iterdir() if p.is_file()})
    write(directory / "runtime-wind-polling-summary.json", result)
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run", type=Path)
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    print(json.dumps(export(args.run, args.directory)["captures"], indent=2))
