"""Validate original Raven waypoint/grid captures and compare two captured stages."""
import argparse
import json
from pathlib import Path


def load(run):
    base = Path(run) / "worker/native-target"
    grid = json.loads((base / "room-grid.json").read_text(encoding="utf8"))
    rooms = grid.get("rooms", [grid])
    motion = [json.loads(line) for line in
              (base / "raven-motion.jsonl").read_text(encoding="utf8").splitlines()]
    points = [point for row in motion for point in row.get("path_points", [])]
    outside = [point for point in points if not any(
        room["grid_x"] <= point["x"] < room["grid_x"] + room["width"] and
        room["grid_y"] <= point["y"] < room["grid_y"] + room["height"]
        for room in rooms)]
    if outside:
        raise AssertionError("waypoint outside exported active-room union: " + repr(outside[:3]))
    for row in motion:
        if row["path_count"] > 78 or row["path_index"] < 0 or row["path_index"] > row["path_count"]:
            raise AssertionError("invalid native path cursor: " + repr(row))
    return dict(room_count=len(rooms), point_rows=sum(bool(row.get("path_points")) for row in motion),
                point_count=len(points), path_types=sorted({row["path_type"] for row in motion}),
                motion_rows=len(motion))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("combat")
    parser.add_argument("town")
    args = parser.parse_args()
    combat, town = load(args.combat), load(args.town)
    types = set(combat["path_types"]) | set(town["path_types"])
    if not ({5, 6} & types) or 13 not in types:
        raise AssertionError("capture does not contain orbit and chase path types")
    print(json.dumps({"combat": combat, "town": town,
                      "same_layout_ready": combat["room_count"] > 1 and town["room_count"] > 1},
                     indent=2))


if __name__ == "__main__":
    main()
