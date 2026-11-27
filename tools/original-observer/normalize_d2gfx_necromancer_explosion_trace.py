"""Keep caster, corpse and missile cells, including missiles with empty tokens.

GfxData.unit_index is a class/resource index, NOT a unique runtime unit ID.
GfxData pointers can be stack scratch memory reused between draws. Neither is
used to claim entity counts, birth/death, or complete frame coverage here.
"""
from __future__ import annotations

import argparse
from collections import defaultdict
import hashlib
import json
from pathlib import Path


def convert(source: Path, inventory: Path, output: Path) -> dict:
    data = json.loads(inventory.read_text(encoding="utf8"))
    missiles = {int(row["Id"]): row for row in data["missile_rows"].values()
                if row.get("Id", "").isdigit()}
    events, headers, malformed = [], [], []
    last_event_ms, cell_count = None, 0
    for line_number, line in enumerate(source.read_text(encoding="utf8").splitlines(), 1):
        if not line.strip():
            continue
        try:
            raw = json.loads(line)
        except json.JSONDecodeError:
            malformed.append(line_number)
            continue
        if raw.get("type") == "header":
            headers.append(raw)
        if raw.get("elapsed_ms") is not None:
            last_event_ms = max(last_event_ms or 0, raw["elapsed_ms"])
        if raw.get("kind") != "ORIGINAL_D2GFX_EXPORT" or "cell" not in raw:
            continue
        cell_count += 1
        cell = raw["cell"]
        token = str(cell.get("token", "")).strip()
        unit_type, index = cell.get("unit_type"), cell.get("unit_index")
        row = missiles.get(index, {}) if unit_type == 3 else {}
        if unit_type == 3:
            role, family = "missile", row.get("Missile", "unknown_missile")
        elif unit_type == 0 and token == "NE":
            role, family = "caster", "necromancer"
        elif unit_type == 1 and str(cell.get("mode_token", "")).strip() in ("DT", "DD"):
            role, family = "corpse_candidate", token
        else:
            continue  # scene objects (including TO/X1) are not explosion evidence
        events.append(dict(kind="LAYER_DRAW", role=role, effect_family=family,
                           class_index=index, unit_type=unit_type, token=token,
                           resource=row.get("CelFile"), frame=cell.get("frame"),
                           mode=str(cell.get("mode_token", "")).strip(),
                           direction=cell.get("direction"), component=cell.get("component"),
                           component_token=str(cell.get("component_token", "")).strip(),
                           screen_x=raw.get("screen_x"), screen_y=raw.get("screen_y"),
                           elapsed_ms=raw.get("elapsed_ms"), export=raw.get("name"),
                           raw_line=line_number, window_state=raw.get("window_state")))
    groups = defaultdict(list)
    for event in events:
        groups[(event["role"], event["effect_family"], event["class_index"])].append(event)
    summaries = []
    for (role, family, index), draws in sorted(groups.items(), key=lambda item: str(item[0])):
        summaries.append(dict(role=role, effect_family=family, class_index=index,
            draw_events=len(draws), first_observed_ms=draws[0]["elapsed_ms"],
            last_observed_ms=draws[-1]["elapsed_ms"],
            frames=sorted({d["frame"] for d in draws}),
            modes=sorted({d["mode"] for d in draws}),
            directions=sorted({d["direction"] for d in draws}),
            positions=sorted({(d["screen_x"], d["screen_y"]) for d in draws}),
            cleanup_status="unproven_last_draw_is_not_removal"))
    effect_count = sum(event["role"] == "missile" for event in events)
    cast_count = sum(event["role"] == "caster" and event["mode"] == "SC" for event in events)
    status = ("partial_cast_and_effect_observed" if effect_count and cast_count else
              "skill_trigger_unproven" if events else "incomplete_no_draw_evidence")
    result = dict(schema=1, status=status, missile_draw_events=effect_count,
        caster_sc_draw_events=cast_count,
        source=str(source), source_sha256=hashlib.sha256(source.read_bytes()).hexdigest(),
        inventory_sha256=hashlib.sha256(inventory.read_bytes()).hexdigest(),
        raw_headers=headers, raw_cell_events=cell_count, last_event_ms=last_event_ms,
        malformed_lines=malformed, summaries=summaries, events=events,
        limitations=["Export sampling is bounded; not a complete frame or GPU image.",
            "Class indices are not entity IDs; draw calls are not unique entity counts.",
            "Elapsed wall time includes debugger overhead; do not infer exact game speed.",
            "Last observed draw does not prove cleanup; the probe has a 5000 texture-call cap.",
            "Resource names join observed missile class indices to offline effective tables."])
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    return {k: result[k] for k in ("status", "raw_cell_events", "last_event_ms", "summaries")}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("--inventory", type=Path, default=Path(__file__).parent /
                        "class-skill-batch-2026-11-21/class-skill-inventory.json")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(convert(args.source, args.inventory, args.output), ensure_ascii=False))
