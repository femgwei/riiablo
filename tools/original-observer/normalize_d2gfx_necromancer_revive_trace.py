#!/usr/bin/env python3
"""Extract Revive's minimized original draw evidence without rerunning Diablo II."""
import argparse
import json
from pathlib import Path


def normalize(trace: Path) -> dict:
    draws = []
    with trace.open(encoding="utf-8") as stream:
        for line in stream:
            event = json.loads(line)
            cell = event.get("cell", {})
            if cell.get("unit_type") != 3 or cell.get("unit_index") not in (268, 269, 270):
                continue
            draws.append({key: event.get(key) for key in (
                "tick", "elapsed_ms", "window_state", "screen_x", "screen_y")}
                | {key: cell.get(key) for key in (
                    "unit_index", "frame", "direction", "max_frames")})
    if not draws:
        raise ValueError("No Revive missile draws in trace")
    summary = json.loads((trace.parent / "summary.json").read_text(encoding="utf-8"))
    process = json.loads((trace.parent.parent / "summary.json").read_text(encoding="utf-8"))
    return {
        "schema": 1, "skill": "Revive", "skill_id": 95,
        "runtime_status": "observed_minimized_d2gfx_partial",
        "source_trace": trace.as_posix(),
        "collector": summary,
        "outer_process": {key: process.get(key) for key in (
            "status", "error", "target_alive_after")},
        "observed": {
            "draws": len(draws),
            "missile_ids": sorted({d["unit_index"] for d in draws}),
            "window_states": sorted({d["window_state"] for d in draws}),
            "screen_positions": sorted({(d["screen_x"], d["screen_y"]) for d in draws}),
            "frames_in_order": [d["frame"] for d in draws],
            "first_elapsed_ms": min(d["elapsed_ms"] for d in draws),
            "last_elapsed_ms": max(d["elapsed_ms"] for d in draws),
        },
        "samples": draws,
        "static_contract": {
            "revive_state": 96, "state_colorshift": 73, "state_color_priority": 85,
            "damage_percent_per_effective_mastery_level": 10,
            "life_percent_per_effective_mastery_level": 5,
            "revive_life_percent": 200,
            "effect_resource": "data/global/missiles/Revive.dcc",
            "effect_frames": 25, "effect_directions": 1,
        },
        "limitations": [
            "outer process failed with Original exited; only the native collector passed",
            "sampled calls do not prove every animation frame or final GPU output",
            "revived monster movement, attack and palette were not compared at runtime",
            "small/large corpse variants and lifetime cleanup remain unobserved",
        ],
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("trace", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    result = normalize(args.trace)
    args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps(result["observed"]))
