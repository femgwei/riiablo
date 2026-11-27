"""Export separate minimized draw and non-atomic client depletion evidence cases."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path

from normalize_d2gfx_bone_armor_trace import normalize


def read(path):
    return json.loads(path.read_text(encoding="utf8"))


def export(draw_run, feedback_run):
    def base(run):
        return run / "orchestrator/worker/capture-run/process-session"

    def trace(run):
        return base(run) / "001-bone-armor-hit-d2gfx-minimized/d2gfx-call-trace.jsonl"

    drawing, second = normalize(trace(draw_run)), normalize(trace(feedback_run))
    for result in (drawing, second):
        assert result["collector"]["status"] == "passed"
        assert result["collector"]["detached"] and result["collector"]["cleanup_confirmed"]
        assert all(s["window_state"] == "minimized" for s in result["samples"])
    rows = [json.loads(l) for l in (base(feedback_run) /
        "001-bone-armor-hit-d2gfx-minimized-client-animation/samples.jsonl").read_text(encoding="utf8").splitlines()]
    feedback = [r for r in rows if r.get("bone_armor")]
    assert not [r for r in rows if r.get("bone_armor_error")]
    stages, previous = [], None
    for row in feedback:
        armor = row["bone_armor"]
        ids = [o["overlay_id"] for o in armor["overlays"] if 140 <= o["overlay_id"] <= 144]
        key = (armor["state_bit"], armor["aggregate"].get("132", 0),
               armor["aggregate"].get("133", 0), armor["aggregate"].get("355", 0), tuple(ids))
        if key != previous:
            stages.append(dict(elapsed_ms=row["elapsed_ms"], state_bit=key[0], current_fixed=key[1],
                maximum_fixed=key[2], native_count=key[3], overlay_ids=ids,
                hp_fixed=armor["aggregate"].get("6"), stat_list_count=len(armor["lists"])))
            previous = key
    full = next(s for s in stages if s["state_bit"] and s["current_fixed"] == s["maximum_fixed"] > 0)
    hit = next(s for s in stages if s["state_bit"] and 0 < s["current_fixed"] < s["maximum_fixed"])
    assert full["native_count"] == 3 and hit["native_count"] == 2
    assert hit["overlay_ids"] == [144, 143] and full["hp_fixed"] == hit["hp_fixed"]
    end = next(s for s in stages if s["elapsed_ms"] > hit["elapsed_ms"] and not s["state_bit"])
    assert not end["overlay_ids"] and end["stat_list_count"] == 0
    loops = next(s for s in stages if Counter(s["overlay_ids"]) == Counter([141, 142, 141, 142]))
    assert loops["native_count"] == 2
    empty = [b for b in drawing["render_batches"] if b["closed_by_next_start_draw"]
        and b["body_draw_ordinals"] and not b["draws"]]
    assert empty, "Disappearance must include continued body drawing, not the debugger's sprite cap"
    resources = []
    for run in (draw_run, feedback_run):
        report = read(run / "orchestrator/orchestrator.json")
        prep = base(run) / "001-bone-armor-hit-d2gfx-minimized-artifacts/bone-armor-attacker"
        resources.append(dict(run_id=run.name, outer_status=report["status"],
            private_desktop_checks=report["private_desktop_not_input_checks"],
            input_desktop_before=report["input_desktop_before"], input_desktop_after=report["input_desktop_after"],
            foreground_before=report["foreground_before"], foreground_after=report["foreground_after"],
            owned_game_closed=report["worker"]["owned_game_closed"],
            attacker=read(prep / "identity.json"), attacker_stats=[json.loads(l) for l in
                (prep / "attacker-stats.jsonl").read_text().splitlines()],
            collector=read(trace(run).parent / "summary.json")))
    return dict(schema=1, runtime_status="observed_private_desktop_minimized_early_hit_and_depletion_partial",
        cases=resources, client_feedback_samples=len(feedback), state_and_overlay_stages=stages,
        full=full, early_hit=hit, stable_two_pairs=loops, depleted=end,
        disappeared_draw_batches_with_body=len(empty),
        draw_case={"run_id": draw_run.name, "observed": drawing["observed"], "render_batches": drawing["render_batches"]},
        feedback_draw_case={"run_id": feedback_run.name, "observed": second["observed"], "render_batches": second["render_batches"]},
        sha256={str(trace(run)): hashlib.sha256(trace(run).read_bytes()).hexdigest() for run in (draw_run, feedback_run)},
        limitations=["Separate runs; no cross-run or D2Gfx-to-poller clock alignment",
            "Client stat and overlay polling is read-only but non-atomic",
            "High-AR same-level 80-base-damage test attacker; natural room enemies remain active",
            "No per-hit attribution or exact original damage-formula equivalence is claimed",
            "Second run native collector passed but outer failed after original exit; retained as partial evidence",
            "Sprite collector cap is not disappearance evidence; require a closed live-body render batch",
            "No original single-pair stage, recast or GPU/pixel parity in these samples"])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("draw_run", type=Path)
    parser.add_argument("feedback_run", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    result = export(args.draw_run, args.feedback_run)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf8")
    print(json.dumps({k: result[k] for k in ("client_feedback_samples", "early_hit", "depleted", "disappeared_draw_batches_with_body")}))
