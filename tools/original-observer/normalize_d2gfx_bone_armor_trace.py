"""Retain Bone Armor draws and multiplicity between observed StartDraw calls.

StartDraw intervals are observed render batches, not native simulation ticks.
No GUID, exact cast tick, or native wall-time velocity is inferred.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path

OVERLAYS = {140: "bonearmor_cast", 141: "bonearmor_front", 142: "bonearmor_back",
            143: "bonearmor_null1", 144: "bonearmor_null2"}


def normalize(trace):
    samples, batches, caster = [], [], []
    current = None
    for ordinal, line in enumerate(trace.read_text(encoding="utf8").splitlines()):
        event = json.loads(line)
        if event.get("kind") == "ORIGINAL_D2GFX_EXPORT" and event.get("rva") == 0x3C90:
            if current is not None:
                current["closed_by_next_start_draw"] = True
                batches.append(current)
            current = {"index": len(batches), "start_ordinal": ordinal,
                       "elapsed_ms": event.get("elapsed_ms"), "draws": [], "body_draw_ordinals": [],
                       "closed_by_next_start_draw": False}
        cell = event.get("cell") or {}
        # ORD10075 (RVA4360) includes the earlier character shadow pass.
        # Compare ordering against the NE body sprite primitive ORD10072 only.
        if (current is not None and event.get("rva") == 0x42A0
                and cell.get("unit_type") == 0 and cell.get("token", "").strip() == "NE"):
            current["body_draw_ordinals"].append(ordinal)
        if cell.get("token", "").strip() == "NE" and cell.get("mode_token", "").strip() == "SC":
            caster.append({"ordinal": ordinal, "frame": cell.get("frame"),
                           "component": cell.get("component"), "direction": cell.get("direction"),
                           "window_state": event.get("window_state")})
        if cell.get("unit_type") != 6 or cell.get("overlay") not in OVERLAYS:
            continue
        sample = {"ordinal": ordinal, "render_batch": None if current is None else current["index"],
                  **{key: event.get(key) for key in ("elapsed_ms", "window_state", "screen_x", "screen_y", "name")},
                  **{key: cell.get(key) for key in ("overlay", "frame", "direction", "max_frames", "max_directions")}}
        samples.append(sample)
        if current is not None:
            current["draws"].append({key: sample[key] for key in (
                "overlay", "frame", "direction", "screen_x", "screen_y", "ordinal")})
    if current is not None:
        batches.append(current)
    if not samples:
        raise ValueError("No Bone Armor overlay draws; cannot create a runtime fixture")
    observed = []
    for overlay in sorted({s["overlay"] for s in samples}):
        rows = [s for s in samples if s["overlay"] == overlay]
        observed.append({"overlay": overlay, "name": OVERLAYS[overlay], "draws": len(rows),
                         "frames": sorted({s["frame"] for s in rows}),
                         "first_elapsed_ms": rows[0]["elapsed_ms"], "last_elapsed_ms": rows[-1]["elapsed_ms"],
                         "window_states": sorted({s["window_state"] for s in rows})})
    # A later closed batch with a live NE body and no armor is evidence of
    # disappearance. Empty intervals after the collector's sprite cap are not.
    first_armor = next((i for i, b in enumerate(batches) if b["draws"]), len(batches))
    retained = [b for b in batches[first_armor:] if b["draws"] or b["body_draw_ordinals"]]
    for batch in retained:
        counts = Counter(d["overlay"] for d in batch["draws"])
        batch["overlay_counts"] = {str(k): counts[k] for k in sorted(counts)}
        back = [d["ordinal"] for d in batch["draws"] if d["overlay"] == 142]
        front = [d["ordinal"] for d in batch["draws"] if d["overlay"] == 141]
        body = batch["body_draw_ordinals"]
        batch["back_body_front_order_observed"] = (
            max(back) < min(body) and max(body) < min(front)) if back and body and front else None
    return {"schema": 1, "skill": "Bone Armor", "skill_id": 68,
            "runtime_status": "observed_private_desktop_minimized_d2gfx_partial",
            "source_trace": trace.as_posix(), "source_sha256": hashlib.sha256(trace.read_bytes()).hexdigest(),
            "collector": json.loads((trace.parent / "summary.json").read_text(encoding="utf8")),
            "observed": observed, "samples": samples, "render_batches": retained,
            "caster_sc": caster,
            "max_draws_per_closed_render_batch": {str(overlay): max((
                b["overlay_counts"].get(str(overlay), 0) for b in retained if b["closed_by_next_start_draw"]), default=0)
                for overlay in OVERLAYS},
            "limitations": ["StartDraw intervals are render batches, not native simulation ticks",
                            "Same-id draw multiplicity does not independently establish unique effect GUIDs",
                            "Breakpoint stalls invalidate wall-time animation rate measurements",
                            "No original damage/depletion/recast/removal evidence unless separately captured",
                            "No final GPU, shader or pixel comparison"]}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("trace", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    result = normalize(args.trace)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf8")
    print(json.dumps({"observed": result["observed"],
                      "max_draws": result["max_draws_per_closed_render_batch"]}))
