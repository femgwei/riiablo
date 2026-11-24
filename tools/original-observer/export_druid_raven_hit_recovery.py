"""Freeze native hit-recovery calls, defender RNG and server/client modes."""
import argparse
from collections import defaultdict
import hashlib
import json
from pathlib import Path


def read(path):
    return json.loads(path.read_text(encoding="utf8"))


def lines(path):
    return [json.loads(line) for line in path.read_text(encoding="utf8").splitlines()]


def replay(entry):
    low, high = entry["seed_low"], entry["seed_high"]
    masks = []

    def draw(mask):
        nonlocal low, high
        product = low * 1791398085 + high
        low, high = product & 0xFFFFFFFF, product >> 32
        masks.append(mask)
        return low & mask

    total, maximum, kind = entry["total_fixed"], entry["maxhp_fixed"], entry["hit_class"]
    divisor = 8 if kind in (2, 6, 10, 11) else 64 if kind == 5 else 32 if kind in (4, 8) else 16
    skip = (total < 256 or bool(entry["poison_fixed"] and entry["poison_fixed"] == total)
            or total < maximum // divisor
            or total < maximum // (divisor // 2) and draw(1) == 0
            or total < maximum // (divisor // 4) and draw(3) == 0)
    return bool(skip), low, high, masks


def export(run):
    report = read(run / "orchestrator.json")
    worker = report["worker"]
    summary, capture = worker["native_target"], worker["capture"]
    assert report["status"] == worker["status"] == summary["status"] == capture["status"] == "passed"
    assert not report["switched_desktop"] and worker["owned_game_closed"]
    assert summary["detached"] and summary["cleanup_confirmed"] and summary["fixtures_removed"]
    base = run / "worker"
    identity = read(base / "native-target/identity.json")
    target = identity["target_id"]
    assert identity["target_hp_regen_after"] == 0
    if identity["natural_life"]:
        assert identity["target_hp_fixed"] == identity["target_original_hp_fixed"] == identity["target_original_maxhp_fixed"]
    assert capture["selected_skill_before_cast"] == [221, 1]
    groups = defaultdict(list)
    for item in lines(base / "native-target/recovery.jsonl"):
        if item["victim_id"] == target:
            groups[item["frame"]].append(item)
    combat = [c for c in lines(base / "native-target/combat.jsonl") if c["victim_id"] == target]
    cases = []
    for frame, rows in sorted(groups.items()):
        assert len(rows) == 2 and rows[0]["stage"] == "entry" and rows[1]["stage"] == "return"
        entry, result = rows
        expected, low, high, masks = replay(entry)
        assert expected == (result["eax"] != 0)
        assert (low, high) == (result["seed_low"], result["seed_high"])
        after = next(c for c in combat if c["frame"] == frame and c["stage"] == "return" and c["function_rva"] == 0x91260)
        assert after["target_mode"] == (1 if expected else 3)
        if expected:
            assert after["target_ai_state"] == 19
        cases.append(dict(frame=frame, attacker_id=entry["attacker_id"], victim_id=target,
            hit_class=entry["hit_class"], total_fixed=entry["total_fixed"], poison_fixed=entry["poison_fixed"],
            maxhp_fixed=entry["maxhp_fixed"], skip_hard_recovery=expected, rng_masks=masks,
            seed_before=dict(low=entry["seed_low"], high=entry["seed_high"]),
            seed_after=dict(low=low, high=high), target_mode_after=after["target_mode"],
            target_ai_state_after=after["target_ai_state"]))
    assert cases
    server = [s for s in lines(base / "native-target/target-feedback.jsonl") if s["unit_id"] == target]
    transitions = [dict(frame=s["frame"], mode=s["target_mode"], ai_state=s["target_ai_state"])
        for i, s in enumerate(server) if i == 0 or s["target_mode"] != server[i-1]["target_mode"]]
    client = [s for s in read(base / "capture/target-samples.json") if s["unit_id"] == target]
    modes = sorted({s["mode"] for s in client})
    assert bool(3 in modes) == bool(identity["natural_life"])
    sources = ["orchestrator.json", "worker/summary.json", "worker/native-target/identity.json",
        "worker/native-target/summary.json", "worker/native-target/recovery.jsonl",
        "worker/native-target/combat.jsonl", "worker/native-target/target-feedback.jsonl",
        "worker/capture/target-samples.json", "worker/capture/summary.json",
        "worker/provenance/d2game_raven_target.exe", "worker/provenance/d2game_raven_target.cpp"]
    return dict(run=str(run), natural_life=identity["natural_life"], background_verified=True,
        owned_game_closed=True, native_cleanup=summary, target_intervention=identity,
        recovery_cases=cases, server_mode_transitions=transitions, client_modes=modes,
        client_samples=len(client),
        lethal_damage_entries=[dict(frame=c["frame"], result_flags=c["result_flags"],
            total_fixed=c["total_fixed"], physical_fixed=c["physical_fixed"])
            for c in combat if c["stage"] == "entry" and c["function_rva"] == 0x91260 and c["result_flags"] & 2],
        sha256={name: hashlib.sha256((run/name).read_bytes()).hexdigest() for name in sources})


if __name__ == "__main__":
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("runs", type=Path, nargs="+")
    p.add_argument("--output", type=Path, required=True)
    args = p.parse_args()
    result = dict(schema=1, scope="Raven physical monster hit recovery, native predicate/RNG and mode publication",
        function_rva="0x91870", cases=[export(run) for run in args.runs],
        limitations=["Natural-life target retains its spawned HP/maxHP; the comparison target is set to 10000 HP.",
            "Both targets have regen disabled and their initial AI think event deleted. Native Dim Vision can schedule new AI actions.",
            "Selected native function calls and client polling are not a complete stack or aligned frame rendering.",
            "These captures do not cover every hit class, status, player reaction or missile path."])
    args.output.write_text(json.dumps(result, indent=2)+"\n", encoding="utf8")
    print(json.dumps([dict(natural=c["natural_life"], cases=len(c["recovery_cases"]), modes=c["client_modes"])
                      for c in result["cases"]]))
