"""Export a Raven hit's server Dim Vision and client overlay evidence; no clock alignment."""
import argparse
from collections import defaultdict
import hashlib
import json
from pathlib import Path


def read(path):
    return json.loads(path.read_text(encoding="utf8"))


def lines(path):
    return [json.loads(line) for line in path.read_text(encoding="utf8").splitlines()]


def export(run):
    report = read(run / "orchestrator.json")
    worker = report["worker"]
    capture = worker["capture"]
    native = worker["native_target"]
    assert report["status"] == worker["status"] == native["status"] == capture["status"] == "passed"
    assert not report["switched_desktop"] and worker["owned_game_closed"]
    assert native["detached"] and native["cleanup_confirmed"] and native["fixtures_removed"]
    assert capture["selected_skill_before_cast"] == [221, 1]
    base = run / "worker"
    identity = read(base / "native-target/identity.json")
    assert identity["target_hp_regen_after"] == 0
    target = identity["target_id"]
    server = lines(base / "native-target/target-feedback.jsonl")
    stats = lines(base / "native-target/raven-stats.jsonl")
    assert len(stats) == 1
    raven = stats[0]["unit_id"]
    # Older helper JSON wrote the DWORD unsigned; this is the signed stat value.
    raw = stats[0]["item_stupidity"]
    stats[0]["item_stupidity"] = raw - 2**32 if raw >= 2**31 else raw
    assert stats[0]["item_stupidity"] == -1
    assert all(s["unit_id"] == target for s in server)
    assert not server[0]["dimvision"]
    applied = next(s for s in server if s["dimvision"])
    assert applied["dimvision_list"] and applied["dimvision_skill"] == 71
    assert applied["dimvision_owner_type"] == 1 and applied["dimvision_owner_id"] == raven
    groups = defaultdict(list)
    for call in lines(base / "native-target/combat.jsonl"):
        assert call["attacker_id"] == raven and call["victim_id"] == target
        groups[call["frame"]].append(call)
    pecks = []
    for frame, calls in sorted(groups.items()):
        assert [(c["stage"], c["function_rva"]) for c in calls] == [
            ("entry", 0x91E70), ("return", 0x91E70), ("entry", 0x91AC0),
            ("entry", 0x91260), ("return", 0x91260), ("return", 0x91AC0)]
        before, after = calls[1], calls[-1]
        loss = after["hp_before_fixed"] - after["hp_after_fixed"]
        assert loss == calls[3]["total_fixed"]
        hit = before["eax"] == 1
        assert hit == (loss > 0)
        pecks.append(dict(frame=frame, hit=hit, hp_loss_fixed=loss,
            dimvision_before=before["dimvision"], dimvision_after=after["dimvision"],
            target_mode_after=after["target_mode"], level_after=after["dimvision_level"],
            expiry_after=after["dimvision_expiry"]))
    first = next(p for p in pecks if not p["dimvision_before"] and p["dimvision_after"])
    assert first["hit"] and first["frame"] == applied["frame"]
    client = [s for s in read(base / "capture/target-samples.json") if s["unit_id"] == target]
    assert client and all("dimvision" in s for s in client)
    assert capture["rejected_target_feedback"] == 0
    client_blind = next(s for s in client if s["dimvision"])
    overlay_runs = []
    for s in client:
        ids = sorted({o["overlay_id"] for o in s["overlays"]})
        key = (s["dimvision"], tuple(ids))
        if not overlay_runs or key != overlay_runs[-1][0]:
            overlay_runs.append((key, dict(elapsed_ms=s["elapsed_ms"], dimvision=s["dimvision"], overlay_ids=ids)))
    assert [r[1]["overlay_ids"] for r in overlay_runs] == [[], [69], [129]]
    assert all(s["target_mode"] == 1 for s in server)
    assert all(s["mode"] == 1 for s in client)
    sources = ["orchestrator.json", "worker/summary.json", "worker/capture/summary.json",
        "worker/capture/target-samples.json", "worker/native-target/summary.json",
        "worker/native-target/identity.json", "worker/native-target/target-feedback.jsonl",
        "worker/native-target/combat.jsonl", "worker/native-target/raven-stats.jsonl",
        "worker/provenance/d2game_raven_target.exe", "worker/provenance/d2game_raven_target.cpp",
        "worker/provenance/druid_raven_passive.py"]
    return dict(schema=1, scope="Raven level-one successful hit creates Dim Vision and client overlays",
        run=str(run), background_verified=True, owned_game_closed=True, native_cleanup=native,
        target_intervention=identity, raven_stats=stats[0], first_server_application=applied,
        applied_duration=applied["dimvision_expiry"]-applied["frame"], pecks=pecks,
        client=dict(samples=len(client), first_blind_elapsed_ms=client_blind["elapsed_ms"],
            overlay_transitions=[r[1] for r in overlay_runs], target_modes=[1],
            state_bit_received=True, full_stat_list_received=any(s["dimvision_list"] for s in client)),
        sha256={name: hashlib.sha256((run/name).read_bytes()).hexdigest() for name in sources},
        limitations=["Selected function entry/return probes and stopped-thread server snapshots, not a complete stack.",
            "Client read-only polling is non-atomic; elapsed milliseconds are not aligned with server ticks.",
            "The client counter at +0x107794 was zero and is not used as a clock.",
            "The 10000-HP fixed target has regeneration/AI suppressed; it never entered GH in this sample.",
            "Expiry is scheduled in the server stat list but not reached by this short capture.",
            "Overlay identity and state reception do not establish GPU drawing or pixel parity."])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = export(args.run)
    args.output.write_text(json.dumps(result, indent=2)+"\n", encoding="utf8")
    print(json.dumps(dict(duration=result["applied_duration"], pecks=result["pecks"], client=result["client"])))
