"""Freeze verified private-desktop Enchant state and draw-call evidence."""
import argparse
import hashlib
import json
from pathlib import Path


def read(path):
    return json.loads(path.read_text(encoding="utf8"))


def export(run):
    outer = read(run / "orchestrator.json")
    assert outer["status"] == "passed" and not outer["switched_desktop"]
    assert outer["input_desktop_before"] == outer["input_desktop_after"] == "Default"
    assert outer["screen_before"] == outer["screen_after"]
    assert outer["worker"]["owned_game_closed"]
    capture = run / "worker/capture"
    summary = read(capture / "summary.json")
    assert summary["status"] == "passed" and summary["detached"] and summary["cleanup_confirmed"]
    provenance = {}
    for name, key in (("d2game_enchant.exe", "native_sha256"),
                      ("d2game_enchant.cpp", "native_source_sha256"),
                      ("d2game_controller.cpp", "controller_source_sha256"),
                      ("enchant_background.py", "runner_source_sha256")):
        digest = hashlib.sha256((run / "worker/provenance" / name).read_bytes()).hexdigest()
        assert digest == outer["worker"][key]
        provenance[name] = digest
    files = {}
    for name in ("states.jsonl", "overlay-states.jsonl", "overlay-events.jsonl"):
        raw = (capture / name).read_bytes()
        files[name] = dict(sha256=hashlib.sha256(raw).hexdigest(), rows=[json.loads(x) for x in raw.splitlines()])
    events = files["overlay-events.jsonl"]["rows"]
    for kind, counter in (("create", "overlay_creates"), ("draw", "overlay_draws")):
        starts = [r["index"] for r in events if r["kind"] == kind]
        returns = [r["index"] for r in events if r["kind"] == kind + "_returned"]
        assert starts == returns == list(range(1, summary[counter] + 1))
    assert len(files["overlay-states.jsonl"]["rows"]) == summary["overlay_state_samples"]
    return dict(schema=1, run_id=run.parent.name, identity=read(capture / "identity.json"),
        background_verified=True, owned_game_closed=True, source_capture=str(capture.resolve()),
        provenance_sha256=provenance, raw_sha256={name: data["sha256"] for name, data in files.items()},
        states=files["states.jsonl"]["rows"], overlays=files["overlay-states.jsonl"]["rows"], events=events,
        limitations=["Two self casts, level one, no synergies/mastery, fixed plain right-hand javelin",
            "Overlay draw arguments and returns were observed; DCC internal selections and final GPU pixels were not",
            "Native overlay field +28 is retained raw; draw-mode claims use the actual graphics call argument",
            "No target attack damage, full 3600-tick expiry, allied target, shield or palette-pixel comparison",
            "Server and client updates are asynchronous; game_frame is the server sample clock"])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    result = export(args.run)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf8")
    print(json.dumps(dict(run_id=result["run_id"], states=len(result["states"]),
        overlays=len(result["overlays"]), draws=sum(e["kind"] == "draw" for e in result["events"]))))
