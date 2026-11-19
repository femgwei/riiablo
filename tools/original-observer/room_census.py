"""Bounded, read-only room activation and unit census for the original client.

The only state-changing operation here is a normal mouse click posted to the
game window. Memory is read for the map layout, camera and unit lists; no game
function is called and no target memory is written.
"""
import json
import math
import time
import traceback
import ctypes as C
from ctypes import wintypes as W

from background_walk import TownExitWalker
from scene_reader import u32


def write_json(path, value):
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(value, indent=2, ensure_ascii=False), encoding="utf8")
    temporary.replace(path)


def level_unit_records(scene, level_id):
    """Select this level, retaining distinct runtime records with shared IDs.

    The Act list includes other levels. Unit IDs alone are not unique across
    unit types, and captured lists can contain multiple pointers for one GUID.
    Count records by pointer/type/ID rather than silently collapsing them.
    This is an observed-record union, not a count of all spawned enemies.
    """
    return {"%s:%s:%s" % (unit["type"], unit["unit_id"], unit["pointer"]): unit
            for unit in scene.get("loaded_units", [])
            if unit.get("level_id") == level_id}


def _room_world_centers(layout, level_id):
    level = next((item for item in layout["levels"] if item["level_id"] == level_id), None)
    if level is None:
        raise ValueError("level %d is not present in generated layout" % level_id)
    ox, oy = level["origin_tiles"]
    result = {}
    for room in level["rooms"]:
        x, y, width, height = room["local_subtiles"]
        result[room["pointer"]] = {
            "pointer": room["pointer"],
            "center": [ox * 5 + x + width // 2, oy * 5 + y + height // 2],
            "adjacency": [item["pointer"] for item in room["adjacency"]
                          if item["level_id"] == level_id],
            "bounds": [ox * 5 + x, oy * 5 + y, width, height],
        }
    return result


def _current_level_room_centers(win, process, scene, level_id):
    """Read only the current generated level's RoomEx list.

    ``layout_snapshot`` is intentionally broad and walks every level linked to
    the DRLG object.  A room census needs only the current level, so this
    bounded reader avoids rescanning unrelated levels on every tick.
    """
    level_ptr = scene["level_pointer"]
    raw_level = win.read(process, level_ptr, 0x230)
    if u32(raw_level, 4) != level_id:
        raise ValueError("current level header does not match scene")
    ox, oy = __import__("struct").unpack_from("<ii", raw_level, 0x20)
    room_ptr = u32(raw_level, 0x30)
    result = {}
    seen = set()
    while room_ptr:
        if room_ptr in seen or len(seen) >= 4096:
            raise ValueError("invalid/unbounded current RoomEx list")
        seen.add(room_ptr)
        room = win.read(process, room_ptr, 0xEC)
        if u32(room, 0) != level_ptr:
            raise ValueError("current RoomEx belongs to another level")
        x, y, width, height = __import__("struct").unpack_from("<iiii", room, 4)
        adjacency = []
        near_count, near_array = u32(room, 0xC4), u32(room, 0xC0)
        if near_count > 256:
            raise ValueError("invalid current RoomEx adjacency count")
        if near_count:
            neighbors = __import__("struct").unpack(
                "<" + "I" * near_count, win.read(process, near_array, 4 * near_count))
            for neighbor in neighbors:
                nr = win.read(process, neighbor, 0x14)
                nl = u32(nr, 0)
                if nl == level_ptr:
                    adjacency.append(neighbor)
        result[room_ptr] = {
            "pointer": room_ptr,
            "center": [ox * 5 + (x - ox) * 5 + width * 5 // 2,
                       oy * 5 + (y - oy) * 5 + height * 5 // 2],
            "adjacency": adjacency,
            "bounds": [x * 5, y * 5, width * 5, height * 5],
        }
        room_ptr = u32(room, 0xE8)
    return result


def _route(rooms, start, visited, blocked=None):
    """Return the shortest room-pointer path to an unvisited room."""
    blocked = blocked or set()
    if start not in rooms:
        start = next(iter(rooms), None)
    if start is None:
        return []
    queue = [start]
    parent = {start: None}
    target = None
    while queue:
        current = queue.pop(0)
        if current not in visited:
            target = current
            break
        for nxt in rooms[current]["adjacency"]:
            # A room that repeatedly rejected a native path must not be used
            # as an intermediate node either.  Passing it only as an excluded
            # target still reconstructs a route through the same dead end.
            if nxt in rooms and nxt not in blocked and nxt not in parent:
                parent[nxt] = current
                queue.append(nxt)
    if target is None:
        # A malformed/partial adjacency list should not make the census loop
        # spin forever. Select the nearest unvisited room as a bounded fallback.
        remaining = [pointer for pointer in rooms
                     if pointer not in visited and pointer not in blocked]
        if not remaining:
            return []
        target = remaining[0]
        parent[target] = start
    path = [target]
    while path[-1] != start and path[-1] in parent:
        path.append(parent[path[-1]])
    path.reverse()
    return path


def _room_entry_candidates(room, origin):
    """Return plausible entry points instead of assuming the room center.

    A RoomEx center can lie behind a river, wall or an impassable tile.  The
    native client still exposes the room's rectangle, so sample its nearest
    boundary and perimeter points first.  The caller uses these only as
    normal input targets; no game memory is modified.
    """
    x, y, width, height = room.get("bounds", [0, 0, 0, 0])
    margin = max(4, min(12, min(width, height) // 8 if width and height else 4))
    left, top = x + margin, y + margin
    right, bottom = x + max(margin, width - margin), y + max(margin, height - margin)
    cx, cy = room.get("center", [left, top])
    ox, oy = origin
    nearest = (max(left, min(right, ox)), max(top, min(bottom, oy)))
    candidates = [nearest, (cx, cy),
                  ((left + right) // 2, top),
                  (right, (top + bottom) // 2),
                  ((left + right) // 2, bottom),
                  (left, (top + bottom) // 2),
                  (left, top), (right, top), (right, bottom), (left, bottom)]
    unique = []
    seen = set()
    for point in sorted(candidates, key=lambda p: math.dist((ox, oy), p)):
        point = (int(point[0]), int(point[1]))
        if point not in seen:
            seen.add(point)
            unique.append(point)
    return unique[:6]


def _adjacent_entry_candidates(current_room, target_room, origin):
    """Find target-room points on the side facing the current room."""
    if not current_room or not target_room:
        return _room_entry_candidates(target_room or {}, origin)
    cx, cy, cw, ch = current_room.get("bounds", [0, 0, 0, 0])
    tx, ty, tw, th = target_room.get("bounds", [0, 0, 0, 0])
    cmargin = max(4, min(12, min(cw, ch) // 8 if cw and ch else 4))
    tmargin = max(4, min(12, min(tw, th) // 8 if tw and th else 4))
    cr, cb, tr, tb = cx + cw, cy + ch, tx + tw, ty + th
    points = []
    # Choose the edge of the target rectangle facing the current rectangle.
    if tr <= cx:
        edge_x = tr - tmargin
        lo, hi = max(ty + tmargin, cy), min(tb - tmargin, cb)
        base_y = (lo + hi) // 2 if lo <= hi else max(ty + tmargin, min(tb - tmargin, cy + ch // 2))
        points.extend((edge_x, y) for y in (base_y - 20, base_y, base_y + 20))
    elif tx >= cr:
        edge_x = tx + tmargin
        lo, hi = max(ty + tmargin, cy), min(tb - tmargin, cb)
        base_y = (lo + hi) // 2 if lo <= hi else max(ty + tmargin, min(tb - tmargin, cy + ch // 2))
        points.extend((edge_x, y) for y in (base_y - 20, base_y, base_y + 20))
    elif tb <= cy:
        edge_y = tb - tmargin
        lo, hi = max(tx + tmargin, cx), min(tr - tmargin, cr)
        base_x = (lo + hi) // 2 if lo <= hi else max(tx + tmargin, min(tr - tmargin, cx + cw // 2))
        points.extend((x, edge_y) for x in (base_x - 20, base_x, base_x + 20))
    elif ty >= cb:
        edge_y = ty + tmargin
        lo, hi = max(tx + tmargin, cx), min(tr - tmargin, cr)
        base_x = (lo + hi) // 2 if lo <= hi else max(tx + tmargin, min(tr - tmargin, cx + cw // 2))
        points.extend((x, edge_y) for x in (base_x - 20, base_x, base_x + 20))
    points.extend(_room_entry_candidates(target_room, origin))
    ox, oy = origin
    unique, seen = [], set()
    for point in sorted(points, key=lambda p: math.dist((ox, oy), p)):
        point = (int(point[0]), int(point[1]))
        if point not in seen:
            seen.add(point)
            unique.append(point)
    return unique[:8]


def run_room_census(win, process, args, snapshot, resume_game_window, output,
                    level_id=42, seconds=900, navigation_plan=None, resume_census=None):
    """Walk generated rooms, taking read-only snapshots and unioning unit IDs."""
    started = time.monotonic()
    deadline = started + seconds
    report = {
        "status": "starting", "level_id": level_id, "seconds_limit": seconds,
        "memory_writes": False, "remote_execution": False,
        "scope": "original generated rooms visited by bounded window clicks; read-only unit census",
        "count_scope": "selected-level observed runtime-record union; type 1 includes NPCs and critters",
        "identity": "type:unit_id:pointer; pointer reuse/lifetime identity not established",
        "full_level_population_verified": False,
        "clicks": [], "snapshots": [], "errors": [], "rooms_seen": 0,
        "rooms_visited": 0, "rooms_remaining": None, "units": {},
        "monsters": {}, "new_unit_ids": [],
    }
    visited = set()
    units = {}
    last_click = 0.0
    last_position = None
    stalled_since = time.monotonic()
    rooms = None
    route = []
    route_index = 0
    blocked_rooms = set()
    combat_actions = []
    last_combat = 0.0
    last_escape = 0.0
    stalled_room = None
    stalled_attempts = 0
    entry_candidates = []
    last_escape_click = None
    same_escape_clicks = 0
    collision_cache = {}
    navigation = None
    navigation_target = None

    def collision_state(scene, point):
        """Return True/False when a loaded Room1 collision cell is known."""
        px, py = int(point[0]), int(point[1])
        for item in scene.get("loaded_rooms", []):
            if item.get("level_id") != level_id:
                continue
            bx, by, bw, bh = item.get("bounds", [0, 0, 0, 0])
            if not (bx <= px < bx + bw and by <= py < by + bh):
                continue
            ptr = int(item.get("pointer", 0))
            try:
                if ptr not in collision_cache:
                    raw = win.read(process, ptr, 0x80)
                    grid = u32(raw, 0x34)
                    if not grid:
                        collision_cache[ptr] = None
                    else:
                        header = win.read(process, grid, 0x24)
                        gx, gy, gw, gh = __import__("struct").unpack_from("<iiii", header)
                        mask_ptr = u32(header, 0x20)
                        if not (0 < gw <= 512 and 0 < gh <= 512 and mask_ptr):
                            collision_cache[ptr] = None
                        else:
                            masks = __import__("struct").unpack(
                                "<" + "H" * (gw * gh), win.read(process, mask_ptr, 2 * gw * gh))
                            collision_cache[ptr] = (gx, gy, gw, gh, masks)
                grid_data = collision_cache.get(ptr)
                if not grid_data:
                    return None
                gx, gy, gw, gh, masks = grid_data
                ix, iy = px - gx, py - gy
                if 0 <= ix < gw and 0 <= iy < gh:
                    return not (masks[iy * gw + ix] & (0x1C09 | 0x20))
            except (OSError, ValueError, IndexError):
                return None
        return None

    def prefer_walkable(scene, points):
        scored = [(collision_state(scene, point), point) for point in points]
        free = [point for state, point in scored if state is True]
        unknown = [point for state, point in scored if state is None]
        # Known blocked cells are retained only as a last resort for unloaded
        # rooms; a target room may not have a Room1 grid until it is activated.
        blocked = [point for state, point in scored if state is False]
        return free + unknown + blocked

    def collect(state):
        nonlocal rooms, route, route_index, last_position, stalled_since
        scene = state.get("scene", {})
        player = state.get("player", {})
        if scene.get("status") != "scene":
            return
        if player.get("mode") in (0, 17):
            raise ValueError("player is dead/corpse (PLRMODE_%s); room walk requires a living character" % player.get("mode"))
        if scene.get("level_id") != level_id:
            raise ValueError("room census reached unexpected level %s" % scene.get("level_id"))
        if rooms is None:
            rooms = _current_level_room_centers(win, process, scene, level_id)
            report["rooms_seen"] = len(rooms)
        current_room = scene.get("roomex_pointer")
        if current_room in rooms:
            visited.add(current_room)
        position = player.get("position")
        if position:
            current_position = (position["x"], position["y"])
            if current_position != last_position:
                last_position, stalled_since = current_position, time.monotonic()
        for uid, unit in level_unit_records(scene, level_id).items():
            if uid not in units:
                units[uid] = dict(unit, first_seen_elapsed_ms=round(1000 * (time.monotonic() - started)),
                                  first_seen_room=current_room)
                report["new_unit_ids"].append(uid)
        # Preserve visited intermediate rooms in a route.  They are often
        # required for backtracking around a river or a dead-end room; the
        # previous implementation skipped them and clicked directly at the
        # final target from the stuck position.
        if route:
            expected = route[route_index - 1] if route_index > 0 else route[0]
            if current_room != expected:
                if current_room in route:
                    route_index = route.index(current_room) + 1
                else:
                    route = []
        if not route or route_index >= len(route):
            route = _route(rooms, current_room, visited, blocked_rooms)
            route_index = 1 if route and route[0] == current_room else 0

    def post_click(window, state, target):
        player = state["player"]["position"]
        modules = win.modules(args.pid)
        camera = TownExitWalker(win, process, modules, output).camera()
        project = lambda p: (16 * (p[0] - p[1]) - camera[0],
                             8 * (p[0] + p[1]) + 8 - camera[1])
        rect = W.RECT()
        win.checked(win.get_rect(window["hwnd"], C.byref(rect)))
        # DirectDraw can lose its primary surface after a transient desktop
        # focus/window change during a long walk. Never clamp a zero-sized
        # surface to (28,96): refresh it before projecting the next waypoint.
        if rect.right <= 0 or rect.bottom <= 0:
            show(window["hwnd"], 9)  # SW_RESTORE
            time.sleep(0.15)
            show(window["hwnd"], 4)  # SW_SHOWNOACTIVATE
            deadline_rect = time.monotonic() + 2.0
            while time.monotonic() < deadline_rect:
                win.checked(win.get_rect(window["hwnd"], C.byref(rect)))
                if rect.right > 0 and rect.bottom > 0:
                    break
                time.sleep(0.05)
            if rect.right <= 0 or rect.bottom <= 0:
                raise ValueError("DirectDraw window lost client region during navigation")
        width, height = rect.right, rect.bottom
        origin = (player["x"], player["y"])
        # Click an on-screen point along the route. The camera follows the
        # player, so repeated clicks advance toward a distant room center.
        candidates = []
        for step in range(10, 101, 10):
            fraction = step / 100.0
            point = (round(origin[0] + (target[0] - origin[0]) * fraction),
                     round(origin[1] + (target[1] - origin[1]) * fraction))
            x, y = project(point)
            if 28 <= x < width - 28 and 96 <= y < height - 96:
                candidates.append((fraction, point, x, y))
        if not candidates:
            # A distant room can project entirely outside the current camera.
            # Click the nearest safe point on the visible playfield edge in
            # the target direction; the camera then advances and the next
            # census iteration recomputes the projection. This keeps the walk
            # window-local and avoids treating a normal off-screen route as a
            # fatal census error.
            projected = project(target)
            cx, cy = width / 2.0, height / 2.0
            dx, dy = projected[0] - cx, projected[1] - cy
            if dx == 0 and dy == 0:
                x, y = int(cx), int(cy)
            else:
                limits = []
                if dx > 0:
                    limits.append((width - 28 - cx) / dx)
                elif dx < 0:
                    limits.append((28 - cx) / dx)
                if dy > 0:
                    limits.append((height - 96 - cy) / dy)
                elif dy < 0:
                    limits.append((96 - cy) / dy)
                positive = [value for value in limits if value > 0]
                scale = min(positive) if positive else 0.1
                x = int(round(cx + dx * scale))
                y = int(round(cy + dy * scale))
            x = max(28, min(width - 28, x))
            y = max(96, min(height - 96, y))
            fraction, point = 1.0, target
        else:
            fraction, point, x, y = candidates[-1]
        user32 = C.WinDLL("user32", use_last_error=True)
        send = user32.SendMessageW
        send.argtypes = [W.HWND, W.UINT, C.c_size_t, C.c_ssize_t]
        send.restype = C.c_ssize_t
        # The hidden legacy client only consumes mouse messages while its
        # internal focus state is active. These window-local messages do not
        # activate the desktop or move the system cursor.
        send(window["hwnd"], 0x0006, 1, 0)  # WM_ACTIVATE/WA_ACTIVE
        send(window["hwnd"], 0x0007, 0, 0)  # WM_SETFOCUS
        post = user32.PostMessageW
        post.argtypes = [W.HWND, W.UINT, C.c_size_t, C.c_ssize_t]
        post.restype = W.BOOL
        lparam = int(x) | (int(y) << 16)
        for message, wparam in ((0x0200, 0), (0x0201, 1), (0x0202, 0)):
            if not post(window["hwnd"], message, wparam, lparam):
                raise C.WinError(C.get_last_error())
        return dict(target=list(target), click=[int(x), int(y)], camera=list(camera),
                    client=[width, height], fraction=fraction, from_position=list(origin))

    def post_skill_key(window, virtual_key):
        """Select a saved skill hotkey using only window-local messages."""
        user32 = C.WinDLL("user32", use_last_error=True)
        send = user32.SendMessageW
        send.argtypes = [W.HWND, W.UINT, C.c_size_t, C.c_ssize_t]
        send.restype = C.c_ssize_t
        send(window["hwnd"], 0x0006, 1, 0)  # WM_ACTIVATE/WA_ACTIVE
        send(window["hwnd"], 0x0007, 0, 0)  # WM_SETFOCUS
        # The scan-code bits are the same values used by the existing
        # runtime-trace input path (F1/F2).
        scan = 0x3B if virtual_key == 0x70 else 0x3C
        lparam_down = 1 | (scan << 16)
        lparam_up = 0xC0000001 | (scan << 16)
        send(window["hwnd"], 0x0100, virtual_key, lparam_down)
        send(window["hwnd"], 0x0101, virtual_key, lparam_up)

    def post_right_click(window, state, target, allow_edge=False):
        """Cast the currently selected right skill at a visible world point."""
        if isinstance(target, dict):
            target = (target["x"], target["y"])
        player = state["player"]["position"]
        modules = win.modules(args.pid)
        camera = TownExitWalker(win, process, modules, output).camera()
        project = lambda p: (16 * (p[0] - p[1]) - camera[0],
                             8 * (p[0] + p[1]) + 8 - camera[1])
        point = project(target)
        rect = W.RECT()
        win.checked(win.get_rect(window["hwnd"], C.byref(rect)))
        width, height = rect.right, rect.bottom
        x = max(28, min(width - 28, int(round(point[0]))))
        y = max(96, min(height - 96, int(round(point[1]))))
        # Reject targets that are not on the rendered playfield.  Teleport's
        # escape path uses the clamped edge point intentionally.
        visible = 28 <= point[0] < width - 28 and 96 <= point[1] < height - 96
        if not visible and not allow_edge:
            return None
        user32 = C.WinDLL("user32", use_last_error=True)
        post = user32.PostMessageW
        post.argtypes = [W.HWND, W.UINT, C.c_size_t, C.c_ssize_t]
        post.restype = W.BOOL
        lparam = x | (y << 16)
        for index, (message, wparam) in enumerate(((0x0204, 2), (0x0205, 0))):
            if not post(window["hwnd"], message, wparam, lparam):
                raise C.WinError(C.get_last_error())
            if index == 0:
                time.sleep(0.08)
        return dict(target=list(target), projected=[round(point[0], 2), round(point[1], 2)], click=[x, y], camera=list(camera),
                    client=[width, height], visible=visible,
                    from_position=[player["x"], player["y"]])

    def short_teleport(window, state, target, distance=24, lateral=0):
        """Attempt a bounded teleport and verify it by a fresh player read.

        Room centers can be hundreds of map units away and the original
        Teleport skill rejects those clicks.  A successful input is therefore
        only recorded after the player coordinate changes; the old code
        recorded every edge click as a teleport and could loop forever at a
        river/rock boundary.
        """
        origin = state.get("player", {}).get("position") or {}
        ox, oy = origin.get("x"), origin.get("y")
        tx, ty = target
        if ox is None or oy is None:
            return None
        dx, dy = tx - ox, ty - oy
        length = math.hypot(dx, dy)
        if length < 1:
            return None
        scale = min(1.0, float(distance) / length)
        ux, uy = dx / length, dy / length
        point = (round(ox + dx * scale - uy * lateral),
                 round(oy + dy * scale + ux * lateral))
        post_skill_key(window, 0x71)  # F2 / Teleport
        # The player can stand at the edge of the camera while the next room
        # is still only a few map units away.  In that case the short target
        # projects a handful of pixels outside the client; the edge click is
        # still the correct local input and is preferable to silently
        # classifying a usable hop as "not visible".
        cast = post_right_click(window, state, point, allow_edge=True)
        if not cast:
            return {"skill": "Teleport", "accepted": False,
                    "reason": "short_target_not_visible", "from_position": [ox, oy],
                    "target": list(point), "requested_target": list(target)}
        before = (ox, oy)
        deadline_move = time.monotonic() + 1.0
        after = before
        mode = None
        transient_reads = 0
        while time.monotonic() < deadline_move:
            time.sleep(0.12)
            try:
                fresh = snapshot()
            except (OSError, ValueError):
                # D2Client rebuilds the active unit chain while a teleport is
                # resolving.  Treat an inconsistent sample as a retryable
                # read race, not as a failed census.
                transient_reads += 1
                continue
            p = fresh.get("player", {}).get("position") or {}
            if p.get("x") is not None and p.get("y") is not None:
                after = (p["x"], p["y"])
                mode = fresh.get("player", {}).get("mode")
                if math.dist(before, after) >= 2:
                    break
        moved = math.dist(before, after)
        cast.update(skill="Teleport", accepted=True, verified=moved >= 8,
                    requested_target=list(target), short_target=list(point),
                    before_position=list(before), after_position=list(after),
                    moved_distance=round(moved, 2), after_mode=mode,
                    transient_reads=transient_reads)
        return cast

    try:
        if navigation_plan:
            from port_navigation import Navigation
            from scene_reader import layout_snapshot
            navigation = Navigation(json.loads(navigation_plan.read_text(encoding="utf8")))
            if navigation.plan["level_id"] != level_id:
                raise ValueError("Navigation level_id mismatch")
            initial = snapshot()
            collect(initial)
            layout = layout_snapshot(win, process, win.modules(args.pid), initial["scene"])
            write_json(output / "navigation-original-layout.json", layout)
            report["navigation_identity"] = navigation.bind(layout)
            if resume_census:
                from port_navigation import validate_resume
                resume = json.loads(resume_census.read_text(encoding="utf8"))
                old_visited, old_units = validate_resume(resume, args.pid, layout, level_id)
                visited.update(old_visited)
                units.update(old_units)
                report["new_unit_ids"] = list(units)
                report["resumed_from"] = resume["source"]
                report["rooms_visited_before_resume"] = len(old_visited)
                route = []
            report["navigation_plan"] = str(navigation_plan)
            report["navigation_moves"] = 0
            report["navigation_stalls"] = []
            write_json(output / "navigation-identity.json", report["navigation_identity"])
        window = resume_game_window()
        # DirectDraw reports a zero-sized client rectangle while the legacy
        # window is fully hidden.  Showing it without activation keeps the
        # render surface alive for projection/click routing, while leaving
        # the user's foreground window and system cursor untouched.
        user32 = C.WinDLL("user32", use_last_error=True)
        show = user32.ShowWindow
        show.argtypes = [W.HWND, C.c_int]
        show.restype = W.BOOL
        show(window["hwnd"], 4)  # SW_SHOWNOACTIVATE
        # A preceding waypoint probe can leave the DirectDraw window hidden
        # with a zero client region even though it is not iconic. Restore it
        # once, then show without activation; otherwise every projected click
        # collapses to the clamped corner (28,96) and the player never moves.
        rect = W.RECT()
        win.checked(win.get_rect(window["hwnd"], C.byref(rect)))
        if rect.right <= 0 or rect.bottom <= 0:
            show(window["hwnd"], 9)  # SW_RESTORE
            time.sleep(0.15)
            show(window["hwnd"], 4)  # SW_SHOWNOACTIVATE
            deadline_restore = time.monotonic() + 2.0
            while time.monotonic() < deadline_restore:
                win.checked(win.get_rect(window["hwnd"], C.byref(rect)))
                if rect.right > 0 and rect.bottom > 0:
                    break
                time.sleep(0.05)
            if rect.right <= 0 or rect.bottom <= 0:
                raise ValueError("DirectDraw window has zero client region after restore")
        while time.monotonic() < deadline:
            if win.wait(process, 0) == 0:
                raise ValueError("Original exited during room census")
            state = snapshot()
            collect(state)
            scene = state.get("scene", {})
            # Use Nova as a bounded assist when a nearby monster is visible.
            # This is ordinary hotkey/right-click input; it neither injects a
            # DLL nor writes the original process.  It keeps a high-life test
            # character from dying while the room walk activates new rooms.
            now = time.monotonic()
            monsters = [u for u in level_unit_records(scene, level_id).values()
                        if u.get("type") == 1 and u.get("position")]
            if monsters and now - last_combat >= 1.5:
                origin = state.get("player", {}).get("position") or {}
                nearest = min(monsters, key=lambda u: math.dist(
                    (origin.get("x", 0), origin.get("y", 0)),
                    (u["position"]["x"], u["position"]["y"])))
                post_skill_key(window, 0x70)  # F1 / Nova
                cast = post_right_click(window, state, nearest["position"])
                if cast:
                    cast.update(skill="Nova", target_class=nearest.get("class_id"),
                                target_id=nearest.get("unit_id"))
                    combat_actions.append(cast)
                    last_combat = now
            report["snapshots"].append({
                "elapsed_ms": round(1000 * (time.monotonic() - started)),
                "roomex_pointer": scene.get("roomex_pointer"),
                "position": state.get("player", {}).get("position"),
                "rooms_visited": len(visited),
                "loaded_monsters": sum(1 for unit in level_unit_records(scene, level_id).values()
                                       if unit.get("type") == 1),
                "unique_units": len(units),
                "new_units": len(report["new_unit_ids"]),
            })
            # Keep a small live checkpoint so a long background walk can be
            # monitored without attaching another observer to the game.
            report["status"] = "running"
            report["rooms_visited"] = len(visited)
            report["unique_units"] = len(units)
            report["monster_count"] = sum(1 for unit in units.values() if unit.get("type") == 1)
            report["combat_actions"] = combat_actions
            write_json(output / "room-census-progress.json", report)
            if rooms is not None and len(visited) >= len(rooms):
                report["status"] = "completed_all_generated_rooms"
                break
            if time.monotonic() - last_click < 0.9:
                time.sleep(0.25)
                continue
            if navigation is not None:
                position = state["player"]["position"]
                origin = (position["x"], position["y"])
                if time.monotonic() - stalled_since > 12:
                    # The port route is a hypothesis. Keep a native rejection
                    # as evidence and try a different room, never label it an
                    # impassable original room just because a click failed.
                    report["navigation_stalls"].append(dict(
                        position=list(origin), target=navigation_target,
                        goal_room=navigation.goal, elapsed_ms=round(1000*(time.monotonic()-started))))
                    if navigation.goal is not None:
                        navigation.excluded.add(navigation.goal)
                    navigation.path = []
                    stalled_since = time.monotonic()
                navigation_target = navigation.next_target(origin, visited)
                report["navigation_events"] = navigation.events
                if navigation_target is None:
                    # A free edge cell can be a valid room entry but a poor
                    # stopping point for the legacy client's path solver. Use
                    # bounded Teleport hops back toward a visited room center
                    # to escape such a native dead-end, then resume the
                    # collision-grid route. Every hop is verified by position.
                    escapes = report.setdefault("navigation_teleport_escapes", [])
                    if len(visited) < len(rooms) and len(escapes) < 24:
                        origin_point = (origin[0], origin[1])
                        candidates = sorted(
                            (room for ptr, room in rooms.items() if ptr in visited),
                            key=lambda room: math.dist(origin_point, room["center"]))
                        for room in candidates[:8]:
                            escape = short_teleport(window, state, room["center"], distance=24)
                            escapes.append(escape)
                            if escape and escape.get("verified"):
                                stalled_since = time.monotonic()
                                last_click = time.monotonic()
                                report["navigation_moves"] += 1
                                break
                        else:
                            report["status"] = "navigation_exhausted_partial"
                            break
                        if escapes[-1] and escapes[-1].get("verified"):
                            continue
                    report["status"] = "navigation_exhausted_partial"
                    break
                action = post_click(window, state, navigation_target)
                action.update(source="riiablo_collision_path", room_pointer=navigation.goal,
                              rooms_visited=len(visited), elapsed_ms=round(1000*(time.monotonic()-started)))
                report["clicks"].append(action)
                report["navigation_moves"] += 1
                last_click = time.monotonic()
                time.sleep(0.35)
                continue
            if time.monotonic() - stalled_since > 12:
                report["errors"].append("movement stalled for 12 seconds")
                current_target = route[route_index] if route_index < len(route) else None
                if current_target != stalled_room:
                    stalled_room, stalled_attempts = current_target, 0
                    entry_candidates = []
                    last_escape_click, same_escape_clicks = None, 0
                # Try the nearest boundary/perimeter points instead of only
                # the room center. A failed hop is evidence; only after all
                # candidates fail is the room classified as blocked.
                if current_target is not None and now - last_escape >= 1.5:
                    origin = state.get("player", {}).get("position") or {}
                    if not entry_candidates:
                        entry_candidates = _adjacent_entry_candidates(
                            rooms.get(scene.get("roomex_pointer")), rooms[current_target],
                            (origin.get("x", 0), origin.get("y", 0)))
                        entry_candidates = prefer_walkable(scene, entry_candidates)
                    attempt_index = min(stalled_attempts, len(entry_candidates) - 1)
                    target = entry_candidates[attempt_index]
                    hop = short_teleport(
                        window, state, target,
                        distance=24, lateral=0)
                    if hop:
                        hop.update(room_pointer=current_target, attempt=stalled_attempts + 1)
                        combat_actions.append(hop)
                        click = tuple(hop.get("click", ()))
                        if click and click == last_escape_click:
                            same_escape_clicks += 1
                        else:
                            same_escape_clicks = 0
                        last_escape_click = click or last_escape_click
                        if hop.get("verified") and hop.get("moved_distance", 0) >= 8:
                            report.setdefault("teleport_successes", 0)
                            report["teleport_successes"] += 1
                            last_position = tuple(hop["after_position"])
                            stalled_since = time.monotonic()
                            stalled_attempts = 0
                            last_escape = now
                            continue
                        report.setdefault("teleport_failures", 0)
                        report["teleport_failures"] += 1
                        stalled_attempts += 1
                        last_escape = now
                        if same_escape_clicks >= 1:
                            report.setdefault("blocked_reasons", []).append({
                                "room_pointer": current_target,
                                "reason": "edge_click_collapse",
                                "click": list(click),
                                "attempt": stalled_attempts,
                            })
                            stalled_attempts = len(entry_candidates)
                if current_target is not None and entry_candidates and stalled_attempts >= len(entry_candidates):
                    blocked_rooms.add(current_target)
                    report.setdefault("blocked_rooms", []).append(current_target)
                    stalled_attempts = 0
                    # Re-read the route and try the next room; do not loop forever.
                    route = []
                stalled_since = time.monotonic()
            if not route or route_index >= len(route):
                time.sleep(0.25)
                continue
            target_room = rooms[route[route_index]]
            origin = state.get("player", {}).get("position") or {}
            current_room = rooms.get(scene.get("roomex_pointer"))
            entry_points = _adjacent_entry_candidates(
                current_room, target_room, (origin.get("x", 0), origin.get("y", 0)))
            entry_points = prefer_walkable(scene, entry_points)
            walk_target = entry_points[0] if entry_points else target_room["center"]
            action = post_click(window, state, walk_target)
            action.update(room_pointer=route[route_index], rooms_visited=len(visited))
            action["room_center"] = list(target_room["center"])
            report["clicks"].append(action)
            last_click = time.monotonic()
            time.sleep(0.75)
        else:
            report["status"] = "timeout"
    except Exception as error:
        report["status"] = "error"
        report["errors"].append(str(error))
        report["traceback"] = traceback.format_exc()
    finally:
        report["rooms_visited"] = len(visited)
        report["visited_room_pointers"] = sorted(visited)
        report["rooms_remaining"] = max(0, (len(rooms) if rooms is not None else 0) - len(visited))
        report["units"] = units
        report["monsters"] = {uid: unit for uid, unit in units.items() if unit.get("type") == 1}
        report["unique_units"] = len(units)
        report["monster_count"] = len(report["monsters"])
        report["combat_actions"] = combat_actions
        report["monster_class_counts"] = {}
        for unit in report["monsters"].values():
            key = str(unit.get("class_id"))
            report["monster_class_counts"][key] = report["monster_class_counts"].get(key, 0) + 1
        report["finished_elapsed_ms"] = round(1000 * (time.monotonic() - started))
        write_json(output / "room-census.json", report)
    return report
