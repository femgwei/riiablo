"""Verify and export the first same-process Druid batch, without a parity claim."""
import argparse
from collections import Counter, defaultdict
import csv
import hashlib
import json
from pathlib import Path


def read(path):
    return json.loads(path.read_text(encoding="utf8"))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write(path, data):
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf8")


def export(run, output):
    outer = read(run / "orchestrator.json")
    worker = read(run / "worker" / "summary.json")
    assert outer["status"] == worker["status"] == "passed"
    assert not outer["switched_desktop"] and outer["private_desktop_not_input_checks"] > 0
    assert outer["private_desktop"] != outer["input_desktop_before"] == outer["input_desktop_after"]
    assert outer["screen_before"] == outer["screen_after"]
    assert worker["owned_game_closed"] and not worker["cleanup_attention_required"]
    assert worker["same_process_verified"] and worker["launch_count"] == 1
    assert sha(run / "worker" / (worker["fixture"]["name"] + "-input.d2s")) == worker["fixture"]["sha256"]
    output.mkdir(parents=True, exist_ok=True)
    cases, game_address = [], None
    for capture in worker["captures"]:
        base = run / "worker" / capture["path"]
        native, identity = read(base / "summary.json"), read(base / "identity.json")
        assert native["status"] == "passed" and native["cleanup_confirmed"] and native["detached"]
        assert capture["game_pid"] == worker["pid"] and identity["skill"] == capture["skill_id"]
        executable = {225: "d2game_druid_firestorm.exe", 229: "d2game_druid_molten.exe",
                      234: "d2game_druid_fissure.exe", 244: "d2game_druid_volcano.exe",
                      240: "d2game_druid_twister.exe", 245: "d2game_druid_tornado.exe"}[capture["skill_id"]]
        assert sha(run / "worker" / "provenance" / executable) == capture["collector_sha256"]
        assert game_address is None or game_address == identity["game"]
        game_address = identity["game"]
        frames = list(csv.DictReader((base / "frames.csv").open(encoding="utf8")))
        assert len(frames) == 241
        assert [int(f["step"]) for f in frames] == list(range(241))
        first = int(frames[0]["game_frame"])
        assert [int(f["game_frame"]) for f in frames] == list(range(first, first + 241))
        samples = [json.loads(line) for line in (base / "missiles.jsonl").read_text(encoding="utf8").splitlines()]
        assert not any(s["step"] == 240 for s in samples)
        by_unit = defaultdict(list)
        keys = ("step", "game_frame", "x_fixed", "y_fixed", "velocity_x", "velocity_y",
                "path_direction", "total_frames", "current_frame", "unit_44", "unit_48", "unit_4c")
        for sample in samples:
            by_unit[sample["side"], sample["class_id"], sample["unit_id"]].append({k: sample[k] for k in keys})
        submissions, pending = [], None
        for line in (base / "sprite-submissions.jsonl").read_text(encoding="utf8").splitlines():
            event = json.loads(line)
            if event["kind"] == "sprite_submission":
                assert pending is None
                pending = event
            else:
                assert event["kind"] == "sprite_call_returned" and pending is not None
                assert pending["index"] == event["index"]
                submissions.append(pending)
                pending = None
        assert pending is None
        selected = [s for s in submissions if s["selection_observed"]]
        for s in selected:
            assert s["frame"] == s["animation_cursor"] >> 8
            assert 0 <= s["frame"] < s["dcc_frames"]
            assert 0 <= s["dcc_direction"] < s["dcc_directions"]
        prefix = "molten" if capture["skill_id"] == 229 else "poison"
        assert len(submissions) == native[prefix + "_sprite_submissions"] == native[prefix + "_sprite_calls_returned"]
        assert len(selected) == native[prefix + "_dcc_selections_observed"]
        filename = "druid-" + capture["path"] + "-batch-1.10f.json"
        result = dict(schema=1, run_id=run.parent.name, skill_id=capture["skill_id"],
                      background_verified=True, game_pid=worker["pid"], identity=identity,
                      fixture=worker["fixture"], runtime_status="captured", comparison_status="pending_matched_loadout_replay",
                      units=[dict(side=side, class_id=cls, unit_id=uid, states=states)
                             for (side, cls, uid), states in sorted(by_unit.items())],
                      submissions=submissions, native=native,
                      raw_sha256={name: sha(base / name) for name in
                                  ("summary.json", "identity.json", "missiles.jsonl", "frames.csv", "sprite-submissions.jsonl")},
                      limitations=worker["fixture"]["limitations"] + [
                          "One cast in one available direction; no all-directions or interrupted-cast coverage",
                          "Observed draw arguments and DCC selections; no pixel or controlled damage comparison"])
        write(output / filename, result)
        cases.append(dict(skill_id=capture["skill_id"], file=filename, sha256=sha(output / filename),
                          runtime_status="captured", comparison_status=result["comparison_status"],
                          server_samples=native["server_samples"], client_samples=native["client_samples"],
                          paired_draws=len(submissions), dcc_selections=len(selected), residual_at_boundary=0))
    assert len(cases) == 2 and {c["skill_id"] for c in cases} in ({225, 229}, {234, 244}, {240, 245})
    result = dict(schema=1, run_id=run.parent.name, class_code="dru", launch_count=1,
                  same_process_verified=True, same_game_address=game_address, background_verified=True,
                  owned_game_closed=True, fixture=worker["fixture"], captures=cases,
                  source=str(run.resolve()),
                  provenance_sha256={p.name: sha(p) for p in (run / "worker" / "provenance").iterdir() if p.is_file()},
                  scope="Two-skill infrastructure validation; not all 30 Druid skills or production parity")
    filename = "runtime-wind-batch-summary.json" if cases[0]["skill_id"] == 240 else "runtime-surface-batch-summary.json" if cases[0]["skill_id"] == 234 else "runtime-batch-summary.json"
    write(output / filename, result)
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    result = export(args.run, args.output)
    print(json.dumps({k: result[k] for k in ("run_id", "launch_count", "captures")}, indent=2))
