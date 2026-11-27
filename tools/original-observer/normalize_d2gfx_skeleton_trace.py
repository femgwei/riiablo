"""Normalize D2Gfx export traces for Necromancer skeleton comparison.

The native D2Gfx probe records raw export calls.  This converter keeps only
monster cells whose token is ``SK`` and emits canonical frame/event records
that can be compared with Riiablo's AnimationTraceProbe output.  It does not
claim a complete frame capture: the probe is still bounded by its export and
breakpoint coverage.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path


def convert(source: Path, output: Path) -> dict:
    metadata = {
        "schema": 1,
        "source": "original-d2gfx-skeleton-filter",
        "capabilities": ["draw_calls", "animation_frame", "position", "mode"],
        "missing_capabilities": ["complete_frame", "visual_hash"],
        "filter": {"token": "SK", "unit_type": 1},
    }
    frames = []
    events = []
    for line in source.read_text(encoding="utf8").splitlines():
        if not line.strip():
            continue
        record = json.loads(line)
        if record.get("type") != "event" or record.get("kind") != "ORIGINAL_D2GFX_EXPORT":
            continue
        cell = record.get("cell") or {}
        if str(cell.get("token", "")).strip() != "SK" or cell.get("unit_type") != 1:
            continue
        entity = cell.get("unit_index", -1)
        event = {
            "type": "event",
            "tick": record.get("tick"),
            "game_frame": None,
            "entity_id": entity,
            "kind": "LAYER_DRAW",
            "component": cell.get("component"),
            "frame": cell.get("frame"),
            "direction": cell.get("direction"),
            "mode_id": cell.get("mode"),
            "token": str(cell.get("token", "")).strip(),
            "screen_x": record.get("screen_x"),
            "screen_y": record.get("screen_y"),
            "elapsed_ms": record.get("elapsed_ms"),
            "window_state": record.get("window_state"),
        }
        events.append(event)
        frames.append({
            "type": "frame",
            "tick": len(frames),
            "game_frame": None,
            "entity_id": entity,
            "mode_id": cell.get("mode"),
            "mode": None,
            "token": str(cell.get("token", "")).strip(),
            "wclass": None,
            "animation_frame_raw": (cell.get("frame") or 0) << 8,
            "animation_frame_count": cell.get("max_frames"),
            "animation_speed": None,
            "keyframes": None,
            "x_fp16": None,
            "y_fp16": None,
            "combat_events": [],
            "visual_sha256": None,
            "draw_component": cell.get("component"),
            "screen_x": record.get("screen_x"),
            "screen_y": record.get("screen_y"),
            "elapsed_ms": record.get("elapsed_ms"),
        })
    payload = {"schema": 1, "metadata": metadata, "frames": frames, "events": events}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    return {"status": "observed" if events else "incomplete", "skeleton_draw_events": len(events),
            "entities": sorted({event["entity_id"] for event in events})}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    print(json.dumps(convert(args.source, args.output), ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
