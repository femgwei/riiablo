#!/usr/bin/env python3
"""Normalize the minimized 1.10f Poison Nova D2Gfx trace.

This keeps the observed renderer cells separate from the static D2MOO ring
table.  The D2Gfx hook samples draw calls, so a missing path direction means
only that no draw for that direction was sampled; it is not treated as a
runtime miss.
"""
from __future__ import annotations

import argparse
import json
import math
from collections import Counter, defaultdict
from pathlib import Path


NOVA_X = [
    30, 29, 29, 28, 27, 26, 24, 23, 21, 19, 16, 14, 11, 8, 5, 2,
    0, -2, -5, -8, -11, -14, -16, -19, -21, -23, -24, -26, -27, -28, -29, -29,
    -30, -29, -29, -28, -27, -26, -24, -23, -21, -19, -16, -14, -11, -8, -5, -2,
    0, 2, 5, 8, 11, 14, 16, 19, 21, 23, 24, 26, 27, 28, 29, 29,
]
NOVA_Y = [
    0, 2, 5, 8, 11, 14, 16, 19, 21, 23, 24, 26, 27, 28, 29, 29,
    30, 29, 29, 28, 27, 26, 24, 23, 21, 19, 16, 14, 11, 8, 5, 2,
    0, -2, -5, -8, -11, -14, -16, -19, -21, -23, -24, -26, -27, -28, -29, -29,
    -30, -29, -29, -28, -27, -26, -24, -23, -21, -19, -16, -14, -11, -8, -5, -2,
]


def native_direction(x: int, y: int) -> int:
    """D2Common PATH_GetDirectionVector's 64-way angle for an offset."""
    sx, sy = x >= 0, y >= 0
    ax, ay = abs(x), abs(y)
    swapped = ax > ay
    if swapped:
        ax, ay = ay, ax
    tangent = 0 if ay == 0 else min(127, (127 * ax) // ay)
    angle = min(7, int(math.atan(tangent / 127.0) * 32.0 / math.pi))
    if swapped:
        angle = (-1 - angle) & 0xF
    if not sy:
        angle = (-1 - angle) & 0x1F
    if sx:
        angle = (-1 - angle) & 0x3F
    return (angle + 8) & 0x3F


def normalize(trace: Path) -> dict:
    draws = []
    with trace.open(encoding="utf-8") as stream:
        for line in stream:
            event = json.loads(line)
            cell = event.get("cell", {})
            if cell.get("unit_type") != 3 or cell.get("unit_index") != 118:
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
        raise SystemExit("No unit_type=3 class=118 Poison Nova draws found")

    by_direction = defaultdict(list)
    for draw in draws:
        by_direction[draw["direction"]].append(draw)
    direction_summary = []
    for direction, samples in sorted(by_direction.items()):
        farthest = max(
            samples,
            key=lambda d: (d["screen_x"] - 320) ** 2 + (d["screen_y"] - 232) ** 2,
        )
        dx, dy = farthest["screen_x"] - 320, farthest["screen_y"] - 232
        direction_summary.append({
            "direction": direction,
            "draws": len(samples),
            "frames": sorted({d["frame"] for d in samples}),
            "first_elapsed_ms": samples[0]["elapsed_ms"],
            "last_elapsed_ms": samples[-1]["elapsed_ms"],
            "farthest_screen_delta": [dx, dy],
            "farthest_screen_radius": round(math.hypot(dx, dy), 3),
        })

    expected_directions = [native_direction(x, y) for x, y in zip(NOVA_X, NOVA_Y)]
    frame_counts = Counter(d["frame"] for d in draws)
    return {
        "schema": 1,
        "skill": "Poison Nova",
        "skill_id": 92,
        "missile": "poisonnova",
        "missile_id": 118,
        "source_trace": str(trace),
        "runtime_status": "observed_minimized_d2gfx_partial",
        "observed": {
            "draws": len(draws),
            "window_states": sorted({d["window_state"] for d in draws}),
            "first_elapsed_ms": min(d["elapsed_ms"] for d in draws),
            "last_elapsed_ms": max(d["elapsed_ms"] for d in draws),
            "directions_sampled": sorted(by_direction),
            "direction_count_sampled": len(by_direction),
            "frame_values": sorted(frame_counts),
            "frame_draw_counts": dict(sorted(frame_counts.items())),
            "max_frames_observed": sorted({d["max_frames"] for d in draws}),
            "directions": direction_summary,
        },
        "static_native_contract": {
            "offset_count": 64,
            "offsets": [[x, y] for x, y in zip(NOVA_X, NOVA_Y)],
            "path_directions": expected_directions,
            "velocity_table": 12,
            "native_velocity_scale_percent": 75,
            "native_fp16_velocity_world_units_per_second": 14.0625,
            "native_lifetime_frames": 30,
            "dcc_directions": 32,
            "dcc_frames": 30,
        },
        "comparison": {
            "observed_directions_subset_of_static_ring": set(by_direction).issubset(expected_directions),
            "static_directions_not_sampled": sorted(set(expected_directions) - set(by_direction)),
            "observed_frame_sequence_is_odd_step_then_terminal": sorted(frame_counts) == [0, 3, 5, 7, 9, 11, 13, 15, 17, 19, 21, 22, 24, 26, 28, 29],
            "cleanup_observed": False,
            "cast_body_animation_observed": False,
        },
        "limitations": [
            "D2Gfx software breakpoints sample draw calls and do not prove every frame",
            "the minimized run did not include a controlled target for hit timing",
            "the final native removal was outside the sampled draw window",
            "GPU blend output and cast-body COF are not established by this trace",
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
