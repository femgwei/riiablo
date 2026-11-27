#!/usr/bin/env python3
"""Extract Necromancer Golem mode/frame evidence from a minimized original trace."""
import argparse
import hashlib
import itertools
import json
from pathlib import Path


def normalize(trace: Path, skill_id: int = 75) -> dict:
    skill, expected_token = {
        75: ("Clay Golem", "G1"), 85: ("Blood Golem", "G2"),
        94: ("Fire Golem", "G3"),
    }[skill_id]
    draws = []
    caster = []
    aura_draws = []
    for line in trace.read_text(encoding="utf-8").splitlines():
        event = json.loads(line)
        cell = event.get("cell", {})
        token = cell.get("token", "").strip()
        if skill_id == 94 and cell.get("unit_type") == 6 and cell.get("overlay") in (15, 16):
            aura_draws.append({key: event.get(key) for key in (
                "elapsed_ms", "window_state", "screen_x", "screen_y")}
                | {key: cell.get(key) for key in ("overlay", "frame", "direction")})
        if token == "NE" and cell.get("mode_token", "").strip() == "SC":
            caster.append(cell.get("frame"))
        if token != expected_token or cell.get("unit_type") != 1:
            continue
        draws.append({key: event.get(key) for key in (
            "tick", "elapsed_ms", "window_state", "screen_x", "screen_y")}
            | {key: cell.get(key) for key in (
                "unit_index", "mode", "frame", "direction", "component")}
            | {"mode_name": cell.get("mode_token", "").strip()})
    if not draws:
        raise ValueError(f"No {expected_token} monster draws observed")
    phases = []
    for (class_id, mode), group in itertools.groupby(draws, lambda d: (d["unit_index"], d["mode_name"])):
        group = list(group)
        phases.append({"class_id": class_id, "mode": mode, "draws": len(group),
                       "frames": sorted({d["frame"] for d in group}),
                       "first_elapsed_ms": group[0]["elapsed_ms"],
                       "last_elapsed_ms": group[-1]["elapsed_ms"],
                       "first_screen": [group[0]["screen_x"], group[0]["screen_y"]],
                       "last_screen": [group[-1]["screen_x"], group[-1]["screen_y"]]})
    native = json.loads((trace.parent / "summary.json").read_text(encoding="utf-8"))
    outer = json.loads((trace.parent.parent / "summary.json").read_text(encoding="utf-8"))
    action = (outer.get("actions") or [{}])[0]
    return {"schema": 1, "skill": skill, "skill_id": skill_id, "token": expected_token,
            "source_trace": trace.as_posix(), "source_sha256": hashlib.sha256(trace.read_bytes()).hexdigest(),
            "runtime_status": "observed_minimized_d2gfx_partial",
            "collector": native,
            "outer_process": {k: outer.get(k) for k in ("status", "error", "target_alive_after")},
            "action_error": action.get("error"),
            "caster_sampler": {
                "mode_ids": action.get("client_animation_mode_ids", []),
                "original_report_skill_mode_observed": action.get("client_animation_skill_mode_observed"),
                "expected_sc_mode_id": 10,
                "sc_mode_observed": 10 in action.get("client_animation_mode_ids", []),
            },
            "caster_sc_observed_in_draws": bool(caster),
            "observed": {"draws": len(draws), "phases": phases,
                         "caster_sc_frames": sorted(set(caster)),
                         "window_states": sorted({d["window_state"] for d in draws})},
            "samples": draws,
            "aura_samples": aura_draws,
            "aura_observed": [{
                "overlay_id": overlay,
                "draws": sum(d["overlay"] == overlay for d in aura_draws),
                "frames": sorted({d["frame"] for d in aura_draws if d["overlay"] == overlay}),
                "window_states": sorted({d["window_state"] for d in aura_draws if d["overlay"] == overlay}),
            } for overlay in sorted({d["overlay"] for d in aura_draws})],
            "limitations": ["breakpoint samples are not a complete frame stream",
                            "unit_index is the monster class, not a unique unit GUID",
                            "wall time includes debugger stalls and is not a native tick duration",
                            "only one birth/follow/idle segment sampled; attacks and removal unobserved",
                            "GPU blend, shadow and pixel equality are not established",
                            "outer-task and native-collector status are recorded separately"]}


def main(default_skill: int = 75) -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("trace", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--skill", type=int, choices=(75, 85, 94), default=default_skill)
    args = parser.parse_args()
    result = normalize(args.trace, args.skill)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result["observed"]))


if __name__ == "__main__":
    main()
