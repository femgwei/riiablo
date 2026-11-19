"""Use an exported riiablo collision grid as hints, gated by native map identity."""
import argparse
from array import array
from collections import deque
import importlib.util
import json
from pathlib import Path


def export_plan(scene_path, output):
    spec = importlib.util.spec_from_file_location("layout_diff", Path(__file__).with_name("layout-diff.py"))
    diff = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(diff)
    scene = diff.read_scene(scene_path)
    context = scene["context"]
    width, height = int(context["width"]), int(context["height"])
    rows, metadata = {}, None
    for line in Path(scene_path).read_text(encoding="utf-8-sig").splitlines():
        if line.startswith("NAV_CONTEXT "):
            metadata = diff.fields(line)
        elif line.startswith("NAV_ROW "):
            f = diff.fields(line)
            y = int(f["y"])
            if y in rows:
                raise ValueError("Duplicate navigation row")
            rows[y] = [] if f["spans"] == "-" else [list(map(int, part.split(":"))) for part in f["spans"].split(",")]
    if metadata is None or set(rows) != set(range(height)):
        raise ValueError("Full navigation grid export required")
    plan = dict(schema=1, source=str(Path(scene_path).resolve()), level_id=int(context["level"]),
                seed=int(context["seed"]) & 0xFFFFFFFF, difficulty=int(context["difficulty"]),
                port_origin=[int(context["originX"]), int(context["originY"])],
                size=[width, height], footprint=int(metadata["footprint"]),
                terrain_mask=int(metadata["terrainMask"]),
                scope="riiablo production terrain collision; dynamic units excluded; original validates movement",
                rooms=[dict(id=r["id"], bounds=list(r["local"]), preset=r["preset"]) for r in scene["rooms"]],
                free_spans=[rows[y] for y in range(height)])
    # Parse the grid before publishing a usable plan.
    Navigation(plan)
    Path(output).write_text(json.dumps(plan, ensure_ascii=False, indent=2), encoding="utf8")
    return plan


class Navigation:
    def __init__(self, plan):
        self.plan = plan
        self.width, self.height = plan["size"]
        w, h = self.width, self.height
        if plan.get("schema") != 1 or w <= 0 or h <= 0 or w * h > 4_000_000:
            raise ValueError("Invalid navigation dimensions/schema")
        if len(plan["free_spans"]) != h:
            raise ValueError("Incomplete navigation grid")
        self.free = bytearray(w * h)
        for y, spans in enumerate(plan["free_spans"]):
            previous = 0
            for left, right in spans:
                if not 0 <= previous <= left < right <= w:
                    raise ValueError("Invalid navigation span")
                self.free[y*w+left:y*w+right] = bytes([1]) * (right-left)
                previous = right
        self.origin = None
        self.cell_rooms = array('i', [-1]) * (w * h)
        self.room_boxes = {tuple(r["bounds"]): r for r in plan["rooms"]}
        if len(self.room_boxes) != len(plan["rooms"]):
            raise ValueError("Duplicate navigation room")
        self.path = []
        self.goal = None
        self.excluded = set()
        self.events = []

    def bind(self, layout):
        p = self.plan
        if (layout["map_seed"] & 0xFFFFFFFF) != p["seed"]:
            raise ValueError("Navigation seed mismatch: original=%s port=%s" % (layout["map_seed"], p["seed"]))
        if layout["difficulty"] != p["difficulty"]:
            raise ValueError("Navigation difficulty mismatch")
        level = next((l for l in layout["levels"] if l["level_id"] == p["level_id"]), None)
        if not level or [v * 5 for v in level["size_tiles"]] != p["size"]:
            raise ValueError("Navigation level/dimensions mismatch")
        native_boxes = {tuple(r["local_subtiles"]): r for r in level["rooms"]}
        if len(native_boxes) != len(level["rooms"]) or set(native_boxes) != set(self.room_boxes):
            raise ValueError("Navigation full room geometry mismatch")
        observed_variants = 0
        for box, room in native_boxes.items():
            preset = room.get("preset") or [-1, -1]
            expected = self.room_boxes[box]["preset"]
            if preset[0] != expected[0]:
                raise ValueError("Navigation room preset mismatch at %s" % (box,))
            if room.get("preset_map_available"):
                observed_variants += 1
                if preset[1] != expected[1]:
                    raise ValueError("Navigation selected file mismatch at %s" % (box,))
            x, y, rw, rh = box
            ptr = room["pointer"]
            def usable_cell(xx, yy):
                if not self.is_free((xx, yy)):
                    return False
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1),
                               (1, 1), (1, -1), (-1, 1), (-1, -1)):
                    nx, ny = xx + dx, yy + dy
                    if self.is_free((nx, ny)) and (not dx or not dy
                            or (self.is_free((xx + dx, yy)) and self.is_free((xx, yy + dy)))):
                        return True
                return False
            # Prefer interior target cells, but retain free edge cells as a
            # fallback. Narrow native RoomEx corridors can expose only a
            # one-cell opening at a room boundary.
            for yy in range(max(0, y+2), min(self.height, y+rh-2)):
                for xx in range(max(0, x+2), min(self.width, x+rw-2)):
                    if usable_cell(xx, yy):
                        self.cell_rooms[yy*self.width+xx] = ptr
            for yy in range(max(0, y), min(self.height, y+rh)):
                for xx in range(max(0, x), min(self.width, x+rw)):
                    i = yy*self.width+xx
                    if usable_cell(xx, yy) and self.cell_rooms[i] < 0:
                        self.cell_rooms[i] = ptr
        self.origin = tuple(v*5 for v in level["origin_tiles"])
        return dict(status="passed", seed=p["seed"], difficulty=p["difficulty"],
                    level_id=p["level_id"], matched_rooms=len(native_boxes),
                    observed_file_variants=observed_variants, original_origin=list(self.origin),
                    port_origin=p["port_origin"], free_cells=sum(self.free),
                    collision_parity="not_asserted; navigation hints only")

    def local(self, world):
        return (round(world[0]-self.origin[0]), round(world[1]-self.origin[1]))

    def index(self, point):
        x, y = point
        return y*self.width+x if 0 <= x < self.width and 0 <= y < self.height else -1

    def is_free(self, point):
        i = self.index(point)
        return i >= 0 and bool(self.free[i])

    def next_target(self, world, visited):
        start = self.local(world)
        if self.path and self.goal not in visited:
            nearest = min(range(len(self.path)), key=lambda i: (self.path[i][0]-start[0])**2+(self.path[i][1]-start[1])**2)
            if max(abs(self.path[nearest][0]-start[0]), abs(self.path[nearest][1]-start[1])) <= 4:
                self.path = self.path[nearest:]
            else:
                self.path = []
        else:
            self.path = []
        if not self.path:
            self._find(start, visited)
        if not self.path:
            return None
        # At most six subtiles per click: avoid projecting far-away goals onto
        # a single screen corner or cutting across a bend in the cell path.
        end = min(5, len(self.path)-1)
        if len(self.path) > 1:
            dx = self.path[1][0]-self.path[0][0]
            dy = self.path[1][1]-self.path[0][1]
            for i in range(2, end+1):
                if (self.path[i][0]-self.path[i-1][0], self.path[i][1]-self.path[i-1][1]) != (dx,dy):
                    end = i-1
                    break
        point = self.path[end]
        return [point[0]+self.origin[0], point[1]+self.origin[1]]

    def _find(self, start, visited):
        if not self.is_free(start):
            self.events.append(dict(reason="original_position_not_free_in_port", local=list(start)))
            # Never invent an unvalidated straight segment to a distant grid.
            self.path = []
            return
        w, h = self.width, self.height
        start_id = self.index(start)
        parents = array('i', [-1]) * (w*h)
        parents[start_id] = start_id
        queue = deque([start_id])
        goal_id = None
        while queue:
            here = queue.popleft()
            room = self.cell_rooms[here]
            if room >= 0 and room not in visited and room not in self.excluded:
                goal_id, self.goal = here, room
                break
            x, y = here % w, here // w
            for dx, dy in ((1,0),(-1,0),(0,1),(0,-1),(1,1),(1,-1),(-1,1),(-1,-1)):
                nx, ny = x+dx, y+dy
                if not (0 <= nx < w and 0 <= ny < h):
                    continue
                nxt = ny*w+nx
                if parents[nxt] >= 0 or not self.free[nxt]:
                    continue
                if dx and dy and (not self.is_free((x+dx,y)) or not self.is_free((x,y+dy))):
                    continue
                parents[nxt] = here
                queue.append(nxt)
        if goal_id is None:
            self.path = []
            return
        path = [goal_id]
        while path[-1] != start_id:
            path.append(parents[path[-1]])
        self.path = [(i % w, i // w) for i in reversed(path)]


def validate_resume(resume, pid, layout, level_id):
    """Pointers are reusable only inside the exact same live map instance."""
    if resume["pid"] != pid or resume["map_seed"] != layout["map_seed"] or resume["difficulty"] != layout["difficulty"]:
        raise ValueError("Resume process/seed/difficulty mismatch")
    level = next(l for l in layout["levels"] if l["level_id"] == level_id)
    expected = sorted((r["pointer"], tuple(r["local_subtiles"])) for r in level["rooms"])
    saved = sorted((r["pointer"], tuple(r["local_subtiles"])) for r in resume["room_pointers"])
    if expected != saved or not set(resume["visited_rooms"]).issubset({p for p, _ in expected}):
        raise ValueError("Resume native room-instance mismatch")
    if any(u.get("level_id") != level_id for u in resume["units"].values()):
        raise ValueError("Resume units cross levels")
    return set(resume["visited_rooms"]), dict(resume["units"])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("scene", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    result = export_plan(args.scene, args.output)
    print(json.dumps({k: result[k] for k in ("seed", "difficulty", "level_id", "size")}, indent=2))
