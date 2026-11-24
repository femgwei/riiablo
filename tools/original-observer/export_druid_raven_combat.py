"""Freeze native Raven combat calls and HP deltas, with explicit target interventions."""
import argparse
from collections import defaultdict
import csv
import hashlib
import json
from pathlib import Path


HIT = 0x91E70
DRAIN = 0x91AC0
POST = 0x91260


def read(path):
    return json.loads(path.read_text(encoding="utf8"))


def lines(path):
    return [json.loads(line) for line in path.read_text(encoding="utf8").splitlines()]


def export(run):
    report = read(run / "orchestrator.json")
    worker = report["worker"]
    capture = worker["capture"]
    summary = worker["native_target"]
    assert report["status"] == worker["status"] == summary["status"] == capture["status"] == "passed"
    assert not report["switched_desktop"] and worker["owned_game_closed"]
    assert summary["detached"] and summary["cleanup_confirmed"] and summary["fixtures_removed"]
    root = run / "worker"
    native = root / "native-target"
    identity = read(native / "identity.json")
    assert identity["target_hp_regen_after"] == 0
    level = worker["fixture"]["hard_points"]["221"]
    assert capture["selected_skill_before_cast"] == [221, level]
    calls = lines(native / "combat.jsonl")
    poses = lines(native / "ravens.jsonl")
    groups = defaultdict(list)
    for call in calls:
        assert call["victim_type"] == 1 and call["victim_id"] == identity["target_id"]
        if call["stage"] == "entry":
            assert call["fixed_target"]
        groups[(call["frame"], call["attacker_id"])].append(call)
    attacks = []
    for (frame, raven), rows in sorted(groups.items()):
        # These captures contain successful pecks only; reject other paths
        # instead of silently treating misses/partial traces as settled damage.
        assert [(r["stage"], r["function_rva"]) for r in rows] == [
            ("entry", HIT), ("return", HIT), ("entry", DRAIN),
            ("entry", POST), ("return", POST), ("return", DRAIN)]
        assert rows[1]["eax"] == 1
        before, after = rows[2]["hp_before_fixed"], rows[5]["hp_after_fixed"]
        packet = rows[3]
        assert before - after == packet["total_fixed"] > 0
        assert packet["physical_fixed"] == packet["total_fixed"]
        assert packet["hp_before_fixed"] == after == rows[4]["hp_after_fixed"]
        pose = max((p for p in poses if p["unit_id"] == raven and p["frame"] <= frame),
                   key=lambda p: p["frame"])
        assert pose["mode"] == 4 and pose["target_link_valid"]
        assert pose["path_target_type"] == 1 and pose["path_target_id"] == identity["target_id"]
        attacks.append(dict(frame=frame, raven_id=raven, target_id=identity["target_id"],
            server_mode=pose["mode"], hit=True, result_flags=packet["result_flags"],
            physical_fixed=packet["physical_fixed"], hp_before_fixed=before,
            hp_after_fixed=after, hp_loss_fixed=before-after,
            call_sequence=[dict(stage=r["stage"], function_rva=hex(r["function_rva"]),
                **({"caller_return_rva": hex(r["caller_return_rva"])} if r["stage"] == "entry" else {}))
                for r in rows]))
    assert attacks
    with (native / "frames.csv").open() as stream:
        samples = [{k: int(v) for k, v in row.items()} for row in csv.DictReader(stream)]
    total = sum(a["hp_loss_fixed"] for a in attacks)
    assert samples[0]["frame"] < attacks[0]["frame"]
    assert samples[-1]["frame"] > attacks[-1]["frame"]
    assert samples[0]["target_hp_fixed"] - samples[-1]["target_hp_fixed"] == total
    assert all(a["target_hp_fixed"] >= b["target_hp_fixed"] for a, b in zip(samples, samples[1:]))
    sources = ["orchestrator.json", "worker/summary.json", "worker/capture/summary.json",
        "worker/native-target/identity.json", "worker/native-target/summary.json",
        "worker/native-target/combat.jsonl", "worker/native-target/ravens.jsonl",
        "worker/native-target/frames.csv", "worker/CXDruRaven-input.d2s"]
    stats = native / "raven-stats.jsonl"
    if stats.exists():
        sources.append("worker/native-target/raven-stats.jsonl")
    return dict(run=str(run), skill_id=221, level=level, background_verified=True,
        owned_game_closed=True, native_cleanup=summary, target_intervention=identity,
        raven_creation_stats=lines(stats) if stats.exists() else [], attacks=attacks,
        total_hp_loss_fixed=total, hp_first_fixed=samples[0]["target_hp_fixed"],
        hp_last_fixed=samples[-1]["target_hp_fixed"],
        sha256={name: hashlib.sha256((run / name).read_bytes()).hexdigest() for name in sources})


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("runs", type=Path, nargs="+")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = dict(schema=1, scope="Native Raven successful A1 pecks, target identity and physical HP settlement",
        binary_sha256={"D2Game.dll": "2bd92fb25e3e4b8269930712620ba9db00cdc1aa44569d87dc4aba76553272b6",
            "D2Common.dll": "b9d1a27c6a35992932c7c47438d02e5269f878e899f6e614d507902cecd1beab"},
        symbols={hex(HIT): "SUNITDMG_IsHitSuccessful (hit roll)",
            hex(DRAIN): "SUNITDMG_DrainItemDurability (contains melee settlement)",
            hex(POST): "SUNITDMG_ExecuteMissileDamage (observed after HP subtraction)"},
        limitations=["Symbol names are D2MOO references; execution evidence is from the hash-pinned 1.10F DLLs.",
            "Only the fixed target HP/maxHP, regeneration and AI event are modified; Raven actions are native.",
            "These short level-one traces do not cover misses, blindness, all levels or all random outcomes.",
            "Native creation mindamage/maxdamage stats can be zero: SkillDamage supplies effective attack damage separately.",
            "This does not establish client pixel parity or Riiablo per-packet RNG parity."],
        cases=[export(run) for run in args.runs])
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf8")
    print(json.dumps([dict(run=c["run"], hits=len(c["attacks"]), loss=c["total_hp_loss_fixed"])
                      for c in result["cases"]]))
