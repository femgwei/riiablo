"""Freeze successful unsuspended Arctic Blast polling; never label it draw evidence."""
import argparse
import hashlib
import json
from pathlib import Path


def read(path):
    return json.loads(path.read_text(encoding="utf8"))


def export(run, output, batch_directory=None):
    outer = read(run / "orchestrator" / "orchestrator.json")
    worker = run / "orchestrator" / "worker"
    report = read(worker / "summary.json")
    assert outer["status"] == report["status"] == "passed"
    assert outer["switched_desktop"] is False
    assert outer["input_desktop_before"] == outer["input_desktop_after"]
    assert outer["private_desktop"] != outer["input_desktop_after"]
    assert outer["screen_before"] == outer["screen_after"]
    assert report["owned_game_closed"]
    fixture_path = worker / (report["fixture"]["name"] + "-input.d2s")
    assert hashlib.sha256(fixture_path.read_bytes()).hexdigest() == report["fixture"]["sha256"]
    result = dict(schema=1, run_id=run.name,
        scope="Client missile polling and cursor state only; no DCC submission, server damage or GPU evidence",
        background_verified=True, owned_game_closed=True,
        loadout=report["fixture"], captures=[], source_files={})
    for capture in report["captures"]:
        folder = worker / capture["path"]
        summary = read(folder / "summary.json")
        assert summary["status"] == "passed" and summary["final_client_units"] == 0
        assert not any(summary[k] for k in ("debugger_attached", "memory_writes", "remote_execution", "global_input"))
        identity = read(folder / "identity.json")
        rows = [json.loads(line) for line in (folder / "missiles.jsonl").read_text(encoding="utf8").splitlines()]
        units = {}
        duplicates = 0
        for row in rows:
            unit = units.setdefault(row["unit_id"], dict(unit_id=row["unit_id"], class_id=row["class_id"], states=[]))
            signature = {k: v for k, v in row.items() if k not in ("snapshot_index", "elapsed_ms")}
            if unit["states"] and signature == {k: v for k, v in unit["states"][-1].items() if k not in ("snapshot_index", "elapsed_ms")}:
                duplicates += 1
                continue
            unit["states"].append(row)
        result["captures"].append(dict(name=capture["path"], identity=identity,
            release_ms_actual=summary["release_ms_actual"], raw_samples=len(rows), duplicates=duplicates,
            rejected_racy_reads=summary["rejected_racy_reads"], units=list(units.values()),
            caster_states=read(folder / "caster.json")))
        for name in ("identity.json", "summary.json", "missiles.jsonl", "caster.json"):
            path = folder / name
            result["source_files"][str(path.relative_to(run))] = hashlib.sha256(path.read_bytes()).hexdigest()
    result["identity"] = result["captures"][-1]["identity"]
    result["provenance_sha256"] = {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
        for p in (worker / "provenance").iterdir() if p.is_file()}
    result["limitations"] = ["Snapshots are unsuspended and non-atomic; sampling may miss births and intermediate states",
        "Hold/release timestamps use the collector clock, not original game frames",
        "Caster SQ cursor is recorded; no stack, render callback, overlay submission or pixel observation",
        "One naked level-one Druid, two hold lengths, one corridor direction"]
    output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf8")
    if batch_directory is not None:
        batch_directory.mkdir(parents=True, exist_ok=True)
        retained = batch_directory / "druid-arctic-blast-replay-input-1.10f.json"
        retained.write_bytes(output.read_bytes())
        runtime = dict(schema=1, run_id=run.name, source=str(run.resolve()), class_code="dru",
            background_verified=True, owned_game_closed=True, launch_count=1,
            observation_mode="unsuspended_client_memory_polling", fixture=result["loadout"],
            provenance_sha256=result["provenance_sha256"], captures=[dict(skill_id=230,
                file=retained.name, sha256=hashlib.sha256(retained.read_bytes()).hexdigest(),
                runtime_status="captured_client_polling", comparison_status="pending_matched_loadout_replay",
                observation_mode="unsuspended_client_memory_polling",
                unique_client_units=sum(len(c["units"]) for c in result["captures"]),
                unique_states=sum(len(u["states"]) for c in result["captures"] for u in c["units"]),
                dcc_selections=0)])
        (batch_directory / "runtime-arctic-polling-summary.json").write_text(json.dumps(runtime, indent=2) + "\n", encoding="utf8")
    print(json.dumps(dict(run=run.name, captures=[dict(name=c["name"], units=len(c["units"]),
        states=sum(len(u["states"]) for u in c["units"])) for c in result["captures"]])))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--batch-directory", type=Path)
    args = parser.parse_args()
    export(args.run, args.output, args.batch_directory)
