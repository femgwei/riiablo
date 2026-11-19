"""Bounded town-exit walking through the game's own window input handlers.

No memory writes or target function calls. Reads collision and camera state;
supports only the existing Act I town -> Blood Moor seam.
"""
import gzip
import heapq
import json
import math
import struct
import time

from scene_reader import layout_snapshot, u32


class TownExitWalker:
    def __init__(self, win, process, modules, output):
        self.win, self.process, self.modules, self.output = win, process, modules, output
        self.path, self.goal = None, None
        self.started = time.monotonic()
        self.last_click = 0
        self.last_position, self.last_progress = None, self.started
        self.clicks = 0
        self.finished = False

    def camera(self):
        b = self.modules["d2client.dll"]["base"]
        signatures = {
            0x15880: b"\xA1" + struct.pack("<I", b + 0x10B9B8) + b"\xC3",
            0x15890: b"\xA1" + struct.pack("<I", b + 0x10B9BC) + b"\x8B\x0D" + struct.pack("<I", b + 0x10B9C8) + b"\x2B\xC1\xC3",
        }
        for rva, expected in signatures.items():
            if self.win.read(self.process, b + rva, len(expected)) != expected:
                raise ValueError("Camera getter signature mismatch")
        read_int = lambda rva: struct.unpack("<i", self.win.read(self.process, b + rva, 4))[0]
        # The shipped screen->subtile function at RVA 0x158B0 adds
        # b9bc-b9c8 to input X and b9b8 to input Y (the legacy getter
        # names found in third-party headers use the opposite convention).
        return read_int(0x10B9BC) - read_int(0x10B9C8), read_int(0x10B9B8)

    def prepare(self, scene, position):
        layout = layout_snapshot(self.win, self.process, self.modules, scene)
        town = next(l for l in layout["levels"] if l["level_id"] == 1)
        blood = next(l for l in layout["levels"] if l["level_id"] == 2)
        if layout["act"] != 0 or layout["difficulty"] != 0:
            raise ValueError("Walker supports this Normal Act I fixture only")
        tx, ty = [v * 5 for v in town["origin_tiles"]]
        tw, th = [v * 5 for v in town["size_tiles"]]
        bx, by = [v * 5 for v in blood["origin_tiles"]]
        bw, bh = [v * 5 for v in blood["size_tiles"]]
        if bx != tx + tw:
            raise ValueError("This bounded walker supports the verified east town exit only")
        flags = {}
        for room in scene["loaded_rooms"]:
            if room["level_id"] != 1:
                continue
            grid = self.win.u32(self.process, room["pointer"] + 0x34)
            if not grid:
                continue
            header = self.win.read(self.process, grid, 0x24)
            x, y, w, h = struct.unpack_from("<iiii", header)
            if not (0 < w <= 512 and 0 < h <= 512 and tx-5 <= x <= tx+tw and ty-5 <= y <= ty+th):
                raise ValueError("Invalid collision grid bounds")
            masks = struct.unpack("<" + "H" * (w*h), self.win.read(self.process, u32(header, 0x20), 2*w*h))
            for iy in range(h):
                for ix in range(w):
                    point = (x+ix, y+iy)
                    if tx <= point[0] < tx+tw and ty <= point[1] < ty+th:
                        flags[point] = flags.get(point, 0) | masks[iy*w+ix]
        # Native PLAYER_PATH plus invalid cells. Dynamic NPCs are left to the
        # game's pathfinder, but are excluded as click endpoints below.
        mask = 0x1C09 | 0x20
        free = lambda p: p in flags and not (flags[p] & mask)
        goals = {(tx+tw-1, y) for y in range(max(ty, by)+2, min(ty+th, by+bh)-2) if free((tx+tw-1, y))}
        if not goals:
            raise ValueError("No walkable town exit found in loaded collision grids")
        with gzip.open(self.output / "town-collision.json.gz", "wt", encoding="utf-8") as f:
            json.dump(dict(bounds=[tx, ty, tw, th], mask=mask, cells=[[x,y,v] for (x,y),v in sorted(flags.items())]), f)
        start = tuple(position)
        heap = [(0, 0, start)]
        costs, parent = {start: 0}, {}
        goal = None
        while heap and len(costs) <= 100000:
            _, cost, here = heapq.heappop(heap)
            if cost != costs[here]:
                continue
            if here in goals:
                goal = here
                break
            for dx,dy in ((1,0),(-1,0),(0,1),(0,-1),(1,1),(1,-1),(-1,1),(-1,-1)):
                nxt = (here[0]+dx, here[1]+dy)
                if not free(nxt) or (dx and dy and (not free((here[0]+dx,here[1])) or not free((here[0],here[1]+dy)))):
                    continue
                nc = cost + (14 if dx and dy else 10)
                if nc < costs.get(nxt, math.inf):
                    costs[nxt], parent[nxt] = nc, here
                    heapq.heappush(heap, (nc + abs(tx+tw-1-nxt[0])*10, nc, nxt))
        if goal is None:
            raise ValueError("No collision-validated path to town exit")
        path = [goal]
        while path[-1] != start:
            path.append(parent[path[-1]])
        path.reverse()
        path.extend([(goal[0]+i, goal[1]) for i in range(1,4)])
        self.path, self.goal = path, path[-1]
        (self.output / "walk-plan.json").write_text(json.dumps(dict(
            start=start, target=self.goal, town_bounds=[tx,ty,tw,th], blood_bounds=[bx,by,bw,bh],
            path=path, collision_mask=mask, max_clicks=100, max_stall_seconds=8,
            scope="only town exit, stop at first observed level 2"), indent=2), encoding="utf-8")

    def step(self, snapshot, width, height):
        scene, player = snapshot.get("scene", {}), snapshot["player"]
        if scene.get("status") != "scene":
            return None
        if scene["level_id"] == 2:
            self.finished = True
            return None
        if scene["level_id"] != 1:
            raise ValueError("Walker left the allowed town/Blood Moor levels")
        position = (player["position"]["x"], player["position"]["y"])
        now = time.monotonic()
        if position != self.last_position:
            self.last_position, self.last_progress = position, now
        if now - self.last_progress > 8 or now - self.started > 90 or self.clicks >= 100:
            raise ValueError("Bounded walk stopped: no progress/time/action limit")
        if self.path is None:
            self.prepare(scene, position)
        if now - self.last_click < 0.8:
            return None
        cx, cy = self.camera()
        project = lambda p: (16*(p[0]-p[1])-cx, 8*(p[0]+p[1])+8-cy)
        px, py = project(position)
        if not (width*.2 <= px <= width*.8 and height*.15 <= py <= height*.8):
            raise ValueError("Player projection is inconsistent with camera/client size: "
                             + repr(dict(camera=[cx,cy], projected=[px,py], client=[width,height])))
        nearest = min(range(len(self.path)), key=lambda i: math.dist(self.path[i], position))
        choices = []
        for index in range(nearest+1, min(len(self.path), nearest+17)):
            target = self.path[index]
            x, y = project(target)
            if math.dist(target, position) <= 12 and 24 <= x < width-24 and 24 <= y < height-80:
                choices.append((target, x, y))
        if not choices:
            raise ValueError("No path waypoint lies in the bounded client click region")
        target, x, y = choices[-1]
        # Do not click a visible monster/NPC's world tile; a nearby free
        # endpoint still asks the native pathfinder to follow the same route.
        for unit in scene["loaded_units"]:
            if unit["type"] != 1:
                continue
            path = self.win.u32(self.process, unit["pointer"] + 0x2C)
            if not path:
                continue
            raw = self.win.read(self.process, path, 8)
            ux, uy = struct.unpack_from("<H",raw,2)[0],struct.unpack_from("<H",raw,6)[0]
            if abs(ux-target[0]) <= 2 and abs(uy-target[1]) <= 2:
                choices.pop()
                if not choices:
                    return None
                target,x,y=choices[-1]
        self.last_click, self.clicks = now, self.clicks+1
        return dict(op="walk", x=x, y=y, target=list(target), player=list(position),
                    camera=[cx,cy], path_index=nearest, click=self.clicks)
