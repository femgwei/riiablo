"""Freeze server/client Raven lifecycle evidence; never infer damage from A1."""
import argparse
from collections import defaultdict
import csv
import hashlib
import json
from pathlib import Path


def read(path):
    return json.loads(path.read_text(encoding="utf8"))


def export(run):
    orchestrator = read(run / "orchestrator.json")
    worker = orchestrator["worker"]
    capture = worker["capture"]
    native = worker["native_target"]
    assert orchestrator["status"] == worker["status"] == native["status"] == capture["status"] == "passed"
    assert not orchestrator["switched_desktop"] and worker["owned_game_closed"]
    assert native["detached"] and native["cleanup_confirmed"] and native["fixtures_removed"]
    level = worker["fixture"]["hard_points"]["221"]
    assert capture["selected_skill_before_cast"] == [221, level]
    assert native["memory_writes"] and native["remote_execution"]
    evidence = run / "worker"
    rows = defaultdict(list)
    for line in (evidence / "native-target/ravens.jsonl").read_text(encoding="utf8").splitlines():
        item = json.loads(line)
        rows[item["unit_id"]].append(item)
    with (evidence / "native-target/frames.csv").open() as stream:
        target = [{k: int(v) for k, v in row.items()} for row in csv.DictReader(stream)]
    ravens = []
    for unit_id, poses in rows.items():
        transitions = [p for i, p in enumerate(poses) if i == 0 or p["mode"] != poses[i-1]["mode"]]
        attacks = [p["frame"] for p in transitions if p["mode"] == 4]
        death = next((p["frame"] for p in transitions if p["mode"] == 0), None)
        corpse = next((p["frame"] for p in transitions if p["mode"] == 12), None)
        complete = (len(attacks) == 12 + level - 1 and bool(attacks)
                    and death is not None and corpse is not None
                    and attacks[-1] < death < corpse)
        ravens.append(dict(unit_id=unit_id, attack_entries=len(attacks), attack_frames=attacks,
            death_frame=death, corpse_frame=corpse, lifecycle_complete=complete,
            client_attack_entries=capture["raven_attack_entries"].get(str(unit_id)),
            mode_transitions=[dict(frame=p["frame"], mode=p["mode"]) for p in transitions]))
    sources = ("summary.json", "capture/summary.json", "capture/raven-samples.json",
               "native-target/summary.json", "native-target/identity.json",
               "native-target/ravens.jsonl", "native-target/frames.csv", "CXDruRaven-input.d2s")
    return dict(level=level, run=str(run), background_verified=True,
        owned_game_closed=True, selected_skill_before_cast=capture["selected_skill_before_cast"],
        expected_attacks=12 + level - 1, ravens=ravens,
        target_identity=worker["native_target_identity"],
        target_alive_through_observation=all(r["target_hp_fixed"] > 0 and r["target_mode"] not in (0, 12) for r in target),
        target_hp_first=target[0]["target_hp_fixed"], target_hp_last=target[-1]["target_hp_fixed"],
        target_hp_min=min(r["target_hp_fixed"] for r in target),
        native_summary=native, collector_summary=dict(status=capture["status"], seconds=capture["seconds"],
            nearby_target_unit_ids=capture["nearby_target_unit_ids"]),
        sha256={name: hashlib.sha256((evidence / name).read_bytes()).hexdigest() for name in sources},
        limitations=["Target HP/AI are experimental interventions; Raven actions and death are native.",
            "A1 entries count attack attempts, not successful hits or damage settlements.",
            "Unchanged fixed-target HP cannot establish which other nearby monster was attacked.",
            "Server modes are sampled on the stopped update thread; client polling is non-atomic."])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("runs", type=Path, nargs="+")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    cases = [export(run) for run in args.runs]
    result = dict(schema=1, scope="Raven attack-attempt budget and DT/DD lifecycle, not damage or pixel parity",
                  cases={str(case["level"]): case for case in cases})
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf8")
    print(json.dumps({str(c["level"]): [(r["attack_entries"], r["lifecycle_complete"]) for r in c["ravens"]] for c in cases}))
