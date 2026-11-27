#!/usr/bin/env python3
"""Normalize the minimized 1.10f Bone Spirit D2Gfx draw sample."""
from __future__ import annotations

import argparse
import json
import math
from collections import Counter
from pathlib import Path


def normalize(trace: Path) -> dict:
    draws = []
    with trace.open(encoding="utf-8") as stream:
        for line in stream:
            event = json.loads(line)
            cell = event.get("cell", {})
            if cell.get("unit_type") != 3 or cell.get("unit_index") != 193:
                continue
            draws.append({
                "tick": event.get("tick"),
                "elapsed_ms": event.get("elapsed_ms"),
                "window_state": event.get("window_state"),
                "screen_x": event.get("screen_x"),
                "screen_y": event.get("screen_y"),
                "frame": cell.get("frame"),
                "direction": cell.get("direction"),
                "max_frames": cell.get("max_frames"),
            })
    if not draws:
        raise SystemExit("No unit_type=3 class=193 Bone Spirit draws found")

    first, last = draws[0], draws[-1]
    dx = last["screen_x"] - first["screen_x"]
    dy = last["screen_y"] - first["screen_y"]
    frame_counts = Counter(d["frame"] for d in draws)
    return {
        "schema": 1,
        "skill": "Bone Spirit",
        "skill_id": 93,
        "missile": "bonespirit",
        "missile_id": 193,
        "source_trace": str(trace),
        "runtime_status": "observed_minimized_d2gfx_partial",
        "observed": {
            "draws": len(draws),
            "window_states": sorted({d["window_state"] for d in draws}),
            "first_elapsed_ms": min(d["elapsed_ms"] for d in draws),
            "last_elapsed_ms": max(d["elapsed_ms"] for d in draws),
            "directions": sorted({d["direction"] for d in draws}),
            "frames": sorted(frame_counts),
            "frame_draw_counts": dict(sorted(frame_counts.items())),
            "max_frames": sorted({d["max_frames"] for d in draws}),
            "first_sample": first,
            "last_sample": last,
            "screen_delta_first_to_last": [dx, dy],
            "screen_distance_first_to_last": round(math.hypot(dx, dy), 3),
        },
        "static_native_contract": {
            "p_srv_do_func": 7,
            "p_clt_do_func": 7,
            "p_srv_hit_func": 10,
            "p_clt_hit_func": 13,
            "velocity_table": 12,
            "native_velocity_scale_percent": 75,
            "native_fp16_velocity_world_units_per_second": 14.0625,
            "range_frames": 128,
            "dcc_directions": 8,
            "dcc_frames": 8,
            "always_explode": True,
        },
        "comparison": {
            "single_direction_sampled": len({d["direction"] for d in draws}) == 1,
            "eight_frame_cycle_observed": sorted(frame_counts) == list(range(8)),
            "hit_explosion_observed": False,
            "cleanup_observed": False,
            "cast_body_animation_observed": False,
        },
        "limitations": [
            "D2Gfx software breakpoints sample draw calls and do not prove every frame",
            "the controlled run did not produce a class-327 BoneSpiritExplode draw",
            "homing retargeting was not observable from the single sampled direction",
            "GPU blend output and caster COF are not established by this trace",
        ],
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("trace", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    result = normalize(args.trace)
    args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({"output": str(args.output), "draws": result["observed"]["draws"]}))


if __name__ == "__main__":
    main()
