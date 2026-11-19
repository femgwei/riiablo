#!/usr/bin/env python3
"""Summarize validated original Diablo II Bash target samples.

The collector writes one summary.json and a sibling equipment.json per sample.
This script intentionally excludes failed runs and unrelated natural-runtime
traces so the resulting distribution can be used as a differential baseline.
"""

from __future__ import annotations

import argparse
import csv
import json
import statistics
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional


def read_timing(sample_dir: Path, first_frame: int) -> Dict[str, Optional[int]]:
    """Measure the A1 transition and damage event, not the capture duration."""
    attack_frames = damage_frame = None
    try:
        with (sample_dir / "frames.csv").open(encoding="utf-8", newline="") as stream:
            frames = list(csv.DictReader(stream))
        started = False
        for row in frames:
            if int(row["player_mode"]) == 7:
                started = True
            elif started:
                attack_frames = int(row["frame"]) - first_frame
                break
        with (sample_dir / "damage.csv").open(encoding="utf-8", newline="") as stream:
            events = list(csv.DictReader(stream))
        if events:
            damage_frame = int(events[0]["frame"]) - first_frame
    except (OSError, ValueError, KeyError):
        pass
    return {"attack_frames": attack_frames, "damage_frame": damage_frame}


def iter_samples(runs_root: Path) -> Iterable[Dict[str, Any]]:
    for summary_path in sorted(runs_root.glob("*/**/orchestrator/process-session/*/summary.json")):
        try:
            summary = json.loads(summary_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            continue
        scope = str(summary.get("scope", ""))
        if (summary.get("status") != "passed" or not summary.get("cleanup_confirmed")
                or not summary.get("detached") or "Bash" not in scope or "entity-targeted" not in scope):
            continue
        equipment = {}
        equipment_path = summary_path.with_name("equipment.json")
        if equipment_path.is_file():
            try:
                equipment = json.loads(equipment_path.read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError):
                equipment = {}
        identity = {}
        identity_path = summary_path.with_name("identity.json")
        if identity_path.is_file():
            try:
                identity = json.loads(identity_path.read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError):
                identity = {}
        outcome = summary.get("outcome")
        if outcome not in ("hit", "miss"):
            outcome = "hit" if int(summary.get("damage_total_fixed", 0) or 0) > 0 else "miss"
        physical_packet = cached_total = None
        try:
            with summary_path.with_name("damage.csv").open(encoding="utf-8", newline="") as stream:
                damage = next(csv.DictReader(stream))
            physical_packet = int(damage["physical_fixed"])
            cached_total = int(damage["total_fixed"])
        except (OSError, ValueError, KeyError, StopIteration):
            pass
        yield {
            "run": summary_path.relative_to(runs_root).parts[0],
            "sample": summary_path.parent.name,
            "level": int(identity.get("level", 1) or 1),
            "outcome": outcome,
            "damage_total_fixed": int(summary.get("damage_total_fixed", 0) or 0),
            "physical_packet_fixed": physical_packet,
            "cached_total_fixed": cached_total,
            "physical_minus_total_fixed": physical_packet - cached_total
                if physical_packet is not None and cached_total is not None else None,
            "damage_fire_fixed": int(summary.get("damage_fire_fixed", 0) or 0),
            "target_hp_before_fixed": int(summary.get("target_hp_before_fixed", 0) or 0),
            "target_hp_after_fixed": int(summary.get("target_hp_after_fixed", 0) or 0),
            "first_frame": int(summary.get("first_frame", 0) or 0),
            "last_frame": int(summary.get("last_frame", 0) or 0),
            **read_timing(summary_path.parent, int(summary.get("first_frame", 0) or 0)),
            "mana_before_fixed": int(summary.get("mana_before_fixed", 0) or 0),
            "lowest_mana_fixed": int(summary.get("lowest_mana_fixed", 0) or 0),
            "damage_events": int(summary.get("damage_events", 0) or 0),
            "target_moved_after_hit": bool(summary.get("target_moved_after_hit", False)),
            "fixture_removed": summary.get("fixture_removed"),
            "equipment": {
                key: equipment.get(key)
                for key in ("unit_type", "class_id", "quality", "item_flags", "item_level", "body_loc")
                if key in equipment
            },
        }


def mean_or_none(values: List[int]) -> Optional[float]:
    return round(statistics.mean(values), 3) if values else None


def summarize(samples: List[Dict[str, Any]]) -> Dict[str, Any]:
    hits = [s for s in samples if s["outcome"] == "hit"]
    misses = [s for s in samples if s["outcome"] == "miss"]
    damage = [s["damage_total_fixed"] for s in hits]
    frames = [s["attack_frames"] for s in samples if s.get("attack_frames") is not None]
    observation_frames = [s["last_frame"] - s["first_frame"] for s in samples]
    mana_drop = [s["mana_before_fixed"] - s["lowest_mana_fixed"] for s in samples]
    equipment_values: Dict[str, List[Any]] = {}
    for sample in samples:
        for key, value in sample["equipment"].items():
            equipment_values.setdefault(key, []).append(value)
    equipment = {key: sorted(set(values)) for key, values in equipment_values.items()}
    by_level: Dict[str, Dict[str, Any]] = {}
    for level in sorted(set(s["level"] for s in samples)):
        level_samples = [s for s in samples if s["level"] == level]
        level_hits = [s for s in level_samples if s["outcome"] == "hit"]
        level_damage = [s["damage_total_fixed"] for s in level_hits]
        by_level[str(level)] = {
            "sample_count": len(level_samples),
            "hit_count": len(level_hits),
            "miss_count": len(level_samples) - len(level_hits),
            "hit_rate": round(len(level_hits) / len(level_samples), 6) if level_samples else None,
            "damage_min_fixed": min(level_damage) if level_damage else None,
            "damage_max_fixed": max(level_damage) if level_damage else None,
            "damage_mean_fixed": mean_or_none(level_damage),
            "physical_minus_total_fixed": sorted(set(s["physical_minus_total_fixed"]
                for s in level_samples if s.get("physical_minus_total_fixed") is not None)),
            "attack_frames": sorted(set(s["attack_frames"] for s in level_samples
                                         if s.get("attack_frames") is not None)),
            "damage_frame": sorted(set(s["damage_frame"] for s in level_samples
                                        if s.get("damage_frame") is not None)),
            "mana_drop_fixed": sorted(set(
                s["mana_before_fixed"] - s["lowest_mana_fixed"] for s in level_samples)),
            "target_moved_after_hit_count": sum(s["target_moved_after_hit"] for s in level_hits),
        }
    return {
        "source": "original-diablo-ii-1.10f",
        "fixture": "CXBarb / Bash / entity-targeted Attack1",
        "limitations": ["Damage extrema are observed samples, not theoretical bounds.",
                        "A1 duration is measured from mode transitions; observation window is separate.",
                        "This report does not establish Riiablo or client visual parity."],
        "sample_count": len(samples),
        "hit_count": len(hits),
        "miss_count": len(misses),
        "hit_rate": round(len(hits) / len(samples), 6) if samples else None,
        "damage_total_fixed": {
            "min": min(damage) if damage else None,
            "max": max(damage) if damage else None,
            "mean": mean_or_none(damage),
        },
        "attack_frames": {
            "min": min(frames) if frames else None,
            "max": max(frames) if frames else None,
            "mean": mean_or_none(frames),
        },
        "observation_frames": sorted(set(observation_frames)),
        "damage_frame": sorted(set(s["damage_frame"] for s in samples
                                    if s.get("damage_frame") is not None)),
        "mana_drop_fixed": {
            "min": min(mana_drop) if mana_drop else None,
            "max": max(mana_drop) if mana_drop else None,
            "values": sorted(set(mana_drop)),
        },
        "target_moved_after_hit_count": sum(s["target_moved_after_hit"] for s in hits),
        "fixture_removed_count": sum(s.get("fixture_removed") is True for s in samples),
        "late_physical_delta_matches_level_count": sum(
            s.get("physical_minus_total_fixed") == s["level"] * 256 for s in samples),
        "damage_event_count": sorted(set(s["damage_events"] for s in samples)),
        "equipment": equipment,
        "by_level": by_level,
        "samples": samples,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--runs-root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--run-id", action="append", help="Include only these immutable broker runs (repeatable)")
    parser.add_argument("--level", type=int, help="Include only this Bash level")
    parser.add_argument("--require-levels", type=int, help="Fail unless every level 1..N is present")
    args = parser.parse_args()
    samples = list(iter_samples(args.runs_root))
    if args.run_id:
        samples = [s for s in samples if s["run"] in args.run_id]
    if args.level is not None:
        samples = [s for s in samples if s["level"] == args.level]
    result = summarize(samples)
    result["run_id"] = args.run_id
    if args.require_levels is not None:
        result["missing_levels"] = sorted(set(range(1, args.require_levels + 1))
                                          - {s["level"] for s in samples})
        result["status"] = "incomplete" if result["missing_levels"] else "complete"
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({k: result[k] for k in ("sample_count", "hit_count", "miss_count", "hit_rate", "damage_total_fixed")}, ensure_ascii=False))
    return 1 if result.get("missing_levels") else 0


if __name__ == "__main__":
    raise SystemExit(main())
