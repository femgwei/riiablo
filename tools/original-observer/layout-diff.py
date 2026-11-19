"""Compare original process layout samples with a riiablo scene manifest.

Rejects mismatched seeds/difficulties and missing geometry. Missing native
preset maps and unavailable native adjacency are explicitly not tested.
"""
import argparse
import collections
import json
from pathlib import Path
import re


def fields(line):
    return dict(re.findall(r"(\w+)=([^\s]+)", line))


def read_scene(path):
    context, rooms, entities = None, [], collections.defaultdict(list)
    for line in Path(path).read_text(encoding="utf-8-sig").splitlines():
        if line.startswith("SCENE_CONTEXT "):
            if context is not None:
                raise ValueError("Duplicate scene context")
            context = fields(line)
        elif line.startswith("ROOM "):
            f = fields(line)
            adjacent = re.search(r"adjacent=\[([^]]*)\]", line)
            if not adjacent:
                raise ValueError("Missing adjacency field")
            rooms.append(dict(id=int(f["id"]),
                world=[int(f[k]) for k in ("x", "y", "width", "height")],
                preset=list(map(int, f["preset"].split(":"))),
                adjacent=[int(x.strip()) for x in adjacent[1].split(",") if x.strip()]))
        elif line.startswith(("NATIVE_OBJECT ", "NATIVE_MONSTER ", "RUNTIME_OBJECT ", "RUNTIME_MONSTER ", "GROUND_ITEM ")):
            entities[line.split(" ", 1)[0]].append(fields(line))
    if context is None:
        raise ValueError("SCENE_CONTEXT with coordinate origin and seed is required")
    ox, oy = int(context["originX"]), int(context["originY"])
    for room in rooms:
        x,y,w,h=room["world"]
        room["local"]=(x-ox,y-oy,w,h)
    if len({r["id"] for r in rooms}) != len(rooms) or len({r["local"] for r in rooms}) != len(rooms):
        raise ValueError("Duplicate room ids or geometry")
    return dict(context=context, rooms=rooms, entities=dict(entities))


def counter_diff(expected, actual):
    def rows(counter):
        return [dict(key=list(key) if isinstance(key,tuple) else key, count=count) for key,count in sorted(counter.items())]
    return dict(missing=rows(expected-actual), extra=rows(actual-expected))


def compare(original, scene):
    context=scene["context"]
    if (original["map_seed"] & 0xFFFFFFFF) != (int(context["seed"]) & 0xFFFFFFFF):
        raise ValueError("Different map seeds")
    if original["difficulty"] != int(context["difficulty"]):
        raise ValueError("Different difficulties")
    level_id=int(context["level"])
    levels=[l for l in original["levels"] if l["level_id"]==level_id]
    if len(levels)!=1:
        raise ValueError("Native level is absent or duplicated")
    native=levels[0]
    checks={}
    checks["level_dimensions"]=dict(status="passed" if [v*5 for v in native["size_tiles"]]==[int(context["width"]),int(context["height"])] else "failed",
        original_subtiles=[v*5 for v in native["size_tiles"]],riiablo_subtiles=[int(context["width"]),int(context["height"])])
    if native["reported_room_count"] != len(native["rooms"]):
        raise ValueError("Native room list does not match its reported count")
    native_boxes = [tuple(r["local_subtiles"]) for r in native["rooms"]]
    if not native_boxes or len(set(native_boxes)) != len(native_boxes):
        raise ValueError("Native room geometry is empty or duplicated")
    if any(len(box) != 4 or box[2] <= 0 or box[3] <= 0 for box in native_boxes):
        raise ValueError("Invalid native room dimensions")
    if not native["rooms"] or not scene["rooms"]:
        checks["room_geometry"]=dict(status="not_tested",reason="One side has no exported room geometry",original=len(native["rooms"]),riiablo=len(scene["rooms"]))
    else:
        expected=collections.Counter(tuple(r["local_subtiles"]) for r in native["rooms"])
        actual=collections.Counter(r["local"] for r in scene["rooms"])
        diff=counter_diff(expected,actual)
        checks["room_geometry"]=dict(status="passed" if expected==actual else "failed",matched=sum((expected&actual).values()),original=sum(expected.values()),riiablo=sum(actual.values()),**diff)
        java_by_box={r["local"]:r for r in scene["rooms"]}
        java_by_id={r["id"]:r for r in scene["rooms"]}
        definitions, variants, adjacency, unknown_variants, unknown_adjacency = [], [], [], 0, 0
        for room in native["rooms"]:
            box=tuple(room["local_subtiles"])
            if box not in java_by_box:
                continue
            java=java_by_box[box]
            preset=room["preset"] or [-1,-1]
            definitions.append(dict(room=list(box),original=preset[0],riiablo=java["preset"][0],equal=preset[0]==java["preset"][0]))
            if room["preset"]:
                if original.get("schema", 1) >= 2 and room.get("preset_map_available"):
                    variants.append(dict(room=list(box),original=preset[1],riiablo=java["preset"][1],equal=preset[1]==java["preset"][1]))
                else:
                    unknown_variants+=1
            if room["adjacency"]:
                ne={tuple(n["local_subtiles"]) for n in room["adjacency"] if n["level_id"]==level_id and tuple(n["local_subtiles"])!=box}
                je={java_by_id[n]["local"] for n in java["adjacent"] if n in java_by_id}
                adjacency.append(dict(room=list(box),equal=ne==je,original=sorted(ne),riiablo=sorted(je)))
            else:
                unknown_adjacency+=1
        for name,values,pending in (("preset_definitions",definitions,0),("selected_file_variants",variants,unknown_variants),("observed_adjacency",adjacency,unknown_adjacency)):
            mismatches=[x for x in values if not x["equal"]]
            checks[name]=dict(status="failed" if mismatches else "passed" if values else "not_tested",compared=len(values),not_observed=pending,mismatches=mismatches)
        # Compare the same stage: native DRLG preset classes, before D2Game
        # converts special selectors (e.g. 580) into concrete chest classes.
        initialized = [r for r in native["rooms"] if r.get("tile_grid_initialized")
                       and tuple(r["local_subtiles"]) in java_by_box]
        room_ids = {java_by_box[tuple(r["local_subtiles"])]["id"]: tuple(r["local_subtiles"])
                    for r in initialized}
        objects = [o for o in scene["entities"].get("NATIVE_OBJECT", [])
                   if int(o["room"]) in room_ids and not o.get("source", "").startswith("ObjGroup/")]
        if not initialized or any(int(o.get("presetClass", -1)) < 0 for o in objects):
            checks["initialized_preset_objects"] = dict(status="not_tested",
                reason="Initialized rooms or pre-resolution presetClass metadata unavailable")
        else:
            expected = collections.Counter((tuple(r["local_subtiles"]), u["class_id"],
                u["mode"], *u["local_subtiles"]) for r in initialized
                for u in r["preset_units"] if u["type"] == 2)
            actual = collections.Counter((room_ids[int(o["room"])], int(o["presetClass"]),
                int(o["mode"]), *map(int, o["pos"].split(","))) for o in objects)
            checks["initialized_preset_objects"] = dict(
                status="passed" if expected == actual else "failed", rooms=len(initialized),
                matched=sum((expected & actual).values()), original=sum(expected.values()),
                riiablo=sum(actual.values()), **counter_diff(expected, actual),
                scope="DRLG preset class, mode and local position; excludes ObjGroup runtime population",
                special_selectors=[dict(preset_class=int(o["presetClass"]),
                    resolved_class=int(o["class"]), position=o["pos"], resolver=o.get("resolver"))
                    for o in objects if 574 <= int(o["presetClass"]) <= 582])
    checks["runtime_monsters_and_items"]=dict(status="not_tested",reason="Runtime room activation, elapsed ticks and actions are not aligned")
    checks["runtime_object_classes"] = dict(status="not_tested",
        reason="Preset selector parity does not establish concrete chest/shrine RNG parity")
    return dict(schema=1,level_id=level_id,seed=original["map_seed"],difficulty=original["difficulty"],
                status="failed" if any(c["status"]=="failed" for c in checks.values()) else "partial",
                checks=checks,scope="layout and initialized preset objects; partial means explicit coverage gaps remain")


if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("original",type=Path)
    p.add_argument("riiablo",type=Path)
    p.add_argument("--output",type=Path,required=True)
    args=p.parse_args()
    try:
        result=compare(json.loads(args.original.read_text(encoding="utf-8-sig")),read_scene(args.riiablo))
        result.update(original_file=str(args.original.resolve()),riiablo_file=str(args.riiablo.resolve()))
    except (ValueError,KeyError) as error:
        result=dict(status="invalid_comparison",error=str(error))
    args.output.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding="utf-8")
    print(json.dumps(result,ensure_ascii=False,indent=2))
    raise SystemExit(1 if result["status"] in ("failed","invalid_comparison") else 0)
