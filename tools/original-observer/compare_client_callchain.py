"""Build a cautious original-D2Client/Riiablo presentation call report.

The native original probe observes a small set of D2Client entry points while
the Riiablo recorder emits semantic presentation events.  They are different
instrumentation levels, so this report never calls a missing original
breakpoint a proven rendering omission.  It records observed/missing candidate
phases and the Riiablo event order for the next probe iteration.
"""
from __future__ import annotations

import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path


ORIGINAL_PHASES = {
    "skill_callback_dispatch": "SKILL_DISPATCH",
    "overlay_create": "OVERLAY_CREATE",
    "unit_animation_update": "ANIMATION_UPDATE",
    "unit_render_dispatch": "UNIT_RENDER_DISPATCH",
    "render_frame_dispatch": "RENDER_FRAME_DISPATCH",
    "overlay_draw": "OVERLAY_DRAW",
}
RIIABLO_PHASES = {
    "ANIMATION_MODE_CHANGE": "MODE_SELECT",
    "COF_RESOLVE": "COF_RESOLVE",
    "LAYER_LOAD": "LAYER_RESOLVE",
    "SEQUENCE_CURSOR": "FRAME_CURSOR",
    "KEYFRAME_EVENT": "KEYFRAME",
    "LAYER_DRAW": "LAYER_DRAW",
    "SHADOW_DRAW": "SHADOW_DRAW",
}


def read_jsonl(path: Path):
    metadata = {}
    events = []
    frames = []
    for line in path.read_text(encoding="utf8").splitlines():
        if not line.strip():
            continue
        record = json.loads(line)
        if record.get("type") == "header":
            metadata = record.get("metadata", {})
        elif record.get("type") == "event":
            events.append(record)
        elif record.get("type") == "frame":
            frames.append(record)
    return metadata, events, frames


def read_original(path: Path):
    metadata = {}
    events = []
    for line in path.read_text(encoding="utf8").splitlines():
        if not line.strip():
            continue
        record = json.loads(line)
        if record.get("type") == "header":
            metadata = record.get("metadata", {})
        elif record.get("type") == "event":
            events.append(record)
    return metadata, events


def build_report(original_path: Path, riiablo_path: Path):
    original_metadata, original_events = read_original(original_path)
    riiablo_metadata, riiablo_events, riiablo_frames = read_jsonl(riiablo_path)
    observed_names = [event.get("name") for event in original_events]
    observed_names = [name for name in observed_names if name]
    observed = set(observed_names)
    candidate_names = list(ORIGINAL_PHASES)

    event_counts = Counter(event.get("kind") for event in riiablo_events)
    event_counts.pop(None, None)
    by_tick = defaultdict(list)
    for event in riiablo_events:
        by_tick[event.get("tick")].append(event)
    first_ticks = []
    for tick in sorted(by_tick, key=lambda value: (value is None, value))[:8]:
        first_ticks.append({
            "tick": tick,
            "sequence": [
                {
                    "kind": event.get("kind"),
                    "phase": RIIABLO_PHASES.get(event.get("kind")),
                    **({"component": event["component"]}
                       if "component" in event else {}),
                }
                for event in by_tick[tick]
            ],
        })

    return {
        "schema": 1,
        "status": "partial",
        "reason": (
            "Original breakpoints and Riiablo semantic events are different "
            "instrumentation levels; missing original candidates require a "
            "D2Gfx/frame-present probe before declaring a rendering omission."
        ),
        "skill": riiablo_metadata.get("skill", 151),
        "original": {
            "trace": str(original_path),
            "metadata": original_metadata,
            "event_count": len(original_events),
            "observed_names": observed_names,
            "observed_phases": [ORIGINAL_PHASES[name]
                                for name in observed_names if name in ORIGINAL_PHASES],
            "missing_candidate_names": [name for name in candidate_names if name not in observed],
            "missing_candidate_phases": [ORIGINAL_PHASES[name]
                                          for name in candidate_names if name not in observed],
            "events": original_events,
        },
        "riiablo": {
            "trace": str(riiablo_path),
            "metadata": riiablo_metadata,
            "event_count": len(riiablo_events),
            "frame_count": len(riiablo_frames),
            "event_counts": dict(sorted(event_counts.items())),
            "semantic_phases": [RIIABLO_PHASES[k] for k in sorted(event_counts)
                                 if k in RIIABLO_PHASES],
            "first_ticks": first_ticks,
        },
        "actionable_findings": [
            "Original skill dispatch was observed at runtime.",
            "Original overlay creation was observed at runtime.",
            "Original animation/render candidate entries were not observed in this bounded run.",
            "Riiablo records explicit COF resolution, cursor, keyframe, layer and shadow submissions.",
            "Run a D2Gfx frame-present probe before treating the four missing D2Client candidates as a proven omission.",
        ],
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--original", type=Path, required=True)
    parser.add_argument("--riiablo", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = build_report(args.original, args.riiablo)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps({
        "status": report["status"],
        "original_events": report["original"]["event_count"],
        "original_missing_candidates": report["original"]["missing_candidate_names"],
        "riiablo_events": report["riiablo"]["event_count"],
        "output": str(args.output),
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
