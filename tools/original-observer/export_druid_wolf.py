"""Export compact original Werewolf client morph evidence."""
import argparse
import hashlib
import json
from pathlib import Path


def main():
    p = argparse.ArgumentParser(); p.add_argument("run", type=Path); p.add_argument("--output", type=Path, required=True)
    a = p.parse_args(); base = a.run / "worker/capture"
    report = json.loads((a.run / "orchestrator.json").read_text(encoding="utf8"))
    capture = json.loads((base / "summary.json").read_text(encoding="utf8"))
    rows = [json.loads(x) for x in (base / "player-animation.jsonl").read_text(encoding="utf8").splitlines()]
    assert report["status"] == "passed" and capture["status"] == "passed"
    assert capture["selected_skill"] == [223, 1]
    assert any(row["mode"] == 10 for row in rows)
    morph = [row for row in rows if row["mode"] == 10]
    overlays = [overlay for row in rows for overlay in row["overlays"]]
    unique_overlay_ids = sorted({overlay["overlay_id"] for overlay in overlays})
    events = rows[-1].get("events", [])
    transitions = []
    for row in rows:
        if not transitions or row["mode"] != transitions[-1]["mode"]:
            transitions.append(dict(elapsed_ms=row["elapsed_ms"], mode=row["mode"],
                                    animation_raw=row["animation_raw"], events=row.get("events", [])))
    result = dict(schema=2, scope="Original 1.10f Werewolf client morph, movement and restore polling",
        run=str(a.run), background_verified=True, owned_game_closed=report["worker"]["owned_game_closed"],
        selected_skill=capture["selected_skill"], sample_count=len(rows), events=events,
        target=capture.get("target"), mode_transitions=transitions,
        mode_values=sorted({row["mode"] for row in rows}),
        morph_sample_count=len(morph), morph_first_ms=morph[0]["elapsed_ms"],
        morph_last_ms=morph[-1]["elapsed_ms"],
        direction_values=sorted({row["direction"] for row in morph}),
        new_direction_values=sorted({row["new_direction"] for row in morph}),
        overlay_ids=unique_overlay_ids,
        overlays=[dict(pointer=x["pointer"], overlay_id=x["overlay_id"], cursor=x["cursor"],
                       total_cursor=x["total_cursor"], frame_delta=x["frame_delta"])
                  for x in overlays],
        animation_raw_values=sorted({row["animation_raw"] for row in morph}),
        animation_rate_values=sorted({row["animation_rate"] for row in morph}),
        sha256={name: hashlib.sha256((a.run / name).read_bytes()).hexdigest()
                for name in ("orchestrator.json", "worker/capture/summary.json",
                             "worker/capture/player-animation.jsonl")})
    a.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps({k: result[k] for k in ("sample_count", "mode_values", "morph_sample_count", "overlay_ids")}))


if __name__ == "__main__": main()
