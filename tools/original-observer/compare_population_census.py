"""Compare a native room-walk census with a fully materialized port population.

Client pointers can change when rooms unload and return. Report pointer records
and distinct monster GUIDs separately; neither is labelled initial population.
"""
import argparse
from collections import Counter, defaultdict
import json
from pathlib import Path
import re


def fields(line):
    return dict(re.findall(r"(\w+)=([^ ]+)", line))


def read_port(path):
    result = dict(context=None, classes={}, population=[], runtime=[], rooms=[])
    for line in Path(path).read_text(encoding="utf-8-sig").splitlines():
        f = fields(line)
        if line.startswith("SCENE_CONTEXT "):
            result["context"] = f
        elif line.startswith("MONSTER_CLASS "):
            result["classes"][int(f["class"])] = f
        elif line.startswith("POPULATION_MONSTER "):
            result["population"].append(f)
        elif line.startswith("RUNTIME_MONSTER "):
            result["runtime"].append(f)
        elif line.startswith("ROOM "):
            result["rooms"].append(f)
    return result


def category(meta):
    if meta is None:
        return "unknown"
    if meta.get("npc") == "true":
        return "npc"
    if meta.get("align") == "0" and meta.get("killable") == "true":
        return "hostile"
    return "neutral_or_nonkillable"


def compare(census, port):
    identity = census.get("navigation_identity", {})
    context = port["context"]
    if not context or identity.get("status") != "passed":
        raise ValueError("Live original navigation identity and port context required")
    for native, scene in (("seed", "seed"), ("difficulty", "difficulty"), ("level_id", "level")):
        if int(identity[native]) != int(context[scene]):
            raise ValueError("Population comparison identity mismatch: " + native)
    if identity["matched_rooms"] != len(port["rooms"]):
        raise ValueError("Population comparison room count mismatch")
    if not port["classes"] or not port["population"]:
        raise ValueError("Detailed population export required")
    if not all(r["monstersSpawned"] == "true" for r in port["rooms"]):
        raise ValueError("Port population has unmaterialized rooms")
    metadata = port["classes"]
    records = [u for u in census["units"].values() if u["type"] == 1]
    if any(u["level_id"] != identity["level_id"] for u in records):
        raise ValueError("Census records cross levels")
    by_class = defaultdict(list)
    by_guid = defaultdict(set)
    for unit in records:
        by_class[unit["class_id"]].append(unit)
        by_guid[unit["unit_id"]].add(unit["class_id"])
    native_counts = {cid: len({u["unit_id"] for u in units}) for cid, units in by_class.items()}
    port_counts = Counter(int(u["class"]) for u in port["population"])
    runtime_counts = Counter(int(u["hcIdx"]) for u in port["runtime"])
    if port_counts != runtime_counts:
        raise ValueError("Port planned and materialized class counts differ")
    rows = []
    for cid in sorted(set(native_counts) | set(port_counts)):
        meta = metadata.get(cid)
        rows.append(dict(class_id=cid, id=meta.get("id") if meta else None,
                         category=category(meta), original_distinct_guids=native_counts.get(cid, 0),
                         original_pointer_records=len(by_class[cid]),
                         riiablo_materialized=port_counts[cid]))
    conflicts = {str(guid): sorted(classes) for guid, classes in by_guid.items() if len(classes) > 1}
    hostile = [r for r in rows if r["category"] == "hostile"]
    native_hostile = sum(r["original_distinct_guids"] for r in hostile)
    port_hostile = sum(r["riiablo_materialized"] for r in hostile)
    complete = (census["status"] == "completed_all_generated_rooms"
                and census["rooms_visited"] == len(port["rooms"]))
    return dict(
        status=("partial_census" if not complete else
                "observed_difference" if any(r["original_distinct_guids"] != r["riiablo_materialized"] for r in hostile)
                else "observed_counts_match"),
        seed=identity["seed"], difficulty=identity["difficulty"], level_id=identity["level_id"],
        original_rooms_visited=census["rooms_visited"], generated_rooms=len(port["rooms"]),
        all_original_rooms_visited=complete, original_type1_pointer_records=len(records),
        original_type1_distinct_guids=len(by_guid), original_hostile_distinct_guids=native_hostile,
        riiablo_materialized_hostiles=port_hostile,
        hostile_guid_difference=native_hostile-port_hostile,
        guid_class_conflicts=conflicts, per_class=rows,
        map_identity_verified=True, monster_rng_aligned=False,
        exact_initial_population_verified=False,
        interpretation="Original values are observed GUID unions in one live game. Pointer reload duplicates are excluded. Neutral critters/NPCs are separate. Runtime spawning, GUID lifetime and RNG/activation-order parity are not established; count differences alone do not prove a density bug.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("census", type=Path)
    parser.add_argument("scene", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    result = compare(json.loads(args.census.read_text(encoding="utf8")), read_port(args.scene))
    result.update(original_source=str(args.census.resolve()), port_source=str(args.scene.resolve()))
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf8")
    print(json.dumps({k: v for k, v in result.items() if k not in ("per_class", "interpretation")}, ensure_ascii=False, indent=2))
