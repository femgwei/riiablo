"""Bounded session reusing an existing CXSorc instance.

Sends WM_ACTIVATEAPP to resume game ticks. Native collectors temporarily attach
a debugger and change live code/registers; snapshots only read memory.
Only fixed, validated collector contracts are accepted. No arbitrary shell.
"""
import argparse
import ctypes as C
from ctypes import wintypes as W
import importlib.util
import json
import math
from pathlib import Path
import shutil
import subprocess
import struct
import time
import winreg

from observer import Win32, api, digest, player_snapshot
from scene_reader import scene_snapshot, layout_snapshot
from collector_specs import validate_collector
from background_walk import TownExitWalker
from room_census import run_room_census

ROOT = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("live", ROOT / "live-observer.py")
live = importlib.util.module_from_spec(spec)
spec.loader.exec_module(live)
HASHES = dict(live.EXPECTED, **{
    "D2Win.dll": "9d47815916fc7082ef86fc50705331e3f59d1ffe19e46217ac0551f7d9ee6066",
    "D2Gfx.dll": "b4247efe73169b66fe95c8cc3d085b9646e8fbf171688fee6b69799f791e56b8",
})


def write_json(path, value):
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(value, indent=2, ensure_ascii=False), encoding="utf8")
    # A polling reader can briefly hold the destination open on Windows.
    for attempt in range(20):
        try:
            temporary.replace(path)
            return
        except PermissionError:
            if attempt == 19:
                raise
            time.sleep(0.05)


def write_bmp(path, width, height, bgra):
    """Write a top-down 32-bit BGRA buffer for offline visual inspection."""
    pixel_bytes = len(bgra)
    file_size = 14 + 40 + pixel_bytes
    header = struct.pack("<2sIHHI", b"BM", file_size, 0, 0, 54)
    info = struct.pack("<IiiHHIIiiII", 40, int(width), -int(height), 1, 32,
                       0, pixel_bytes, 0, 0, 0, 0)
    path.write_bytes(header + info + bgra)


def run(args):
    args.output.mkdir(parents=True, exist_ok=False)
    commands = args.output / "commands"
    commands.mkdir()
    win, process = Win32(), None
    report = dict(status="starting", pid=args.pid, actions=[], window_control="WM_ACTIVATEAPP",
                  scope="original-only process observations/cast; visual and port parity untested")

    def snapshot():
        modules = win.modules(args.pid)
        for name, expected in HASHES.items():
            item = modules.get(name.lower())
            if not item or Path(item["path"]).resolve() != (args.home / name).resolve() or digest(Path(item["path"])) != expected:
                raise ValueError("Unsupported module: " + name)
        player = player_snapshot(win, process, modules)
        desktop = win.get_thread_desktop(api(win.k, "GetCurrentThreadId", W.DWORD)())
        # Unit lists are mutated by the game thread while a spell kills or
        # removes a unit. Retry only this transient reader race; persistent
        # corruption still fails closed after the bounded attempts.
        scene = None
        for attempt in range(5):
            try:
                scene = scene_snapshot(win, process, modules, player)
                break
            except ValueError as error:
                transient = ("unit list" in str(error)
                             or str(error) == "Invalid unit type")
                if not transient or attempt == 4:
                    raise
                time.sleep(0.04)
        return dict(time=time.time(), player=player, scene=scene,
                    windows=win.windows(desktop, args.pid), foreground=win.foreground(),
                    screen=[win.system_metric(0), win.system_metric(1)])

    def object_coordinate_candidates(unit, target=(0, 0)):
        """Read-only coordinate hints for a D2 object unit.

        Objects do not use the same validated PLAYER_PATH layout as players
        and monsters.  This bounded scan records plausible uint16 pairs from
        the object and its pointed-to blocks as diagnostic evidence only.
        """
        result = dict(pointer=unit.get("pointer"), class_id=unit.get("class_id"),
                      type=unit.get("type"), mode=unit.get("mode"),
                      candidates=[], pointer_fields=[])
        raw = win.read(process, int(unit["pointer"]), 0xEC)
        blocks = [("unit", int(unit["pointer"]), raw)]
        for off in range(0, len(raw) - 3, 4):
            value = struct.unpack_from("<I", raw, off)[0]
            if 0x10000 <= value <= 0x7FFFFFFF:
                result["pointer_fields"].append(dict(offset=off, value=value))
                if len(result["pointer_fields"]) >= 128:
                    break
        for item in result["pointer_fields"]:
            try:
                preview = win.read(process, item["value"], 0x80)
            except (OSError, ValueError):
                continue
            blocks.append(("ptr+0x%X" % item["offset"], item["value"], preview))
        tx, ty = int(target[0]), int(target[1])
        # Town object coordinates vary by Act; bound the diagnostic scan around
        # the requested anchor instead of assuming the Act II 4800..5600 box.
        min_x, max_x = tx - 1200, tx + 1200
        min_y, max_y = ty - 1200, ty + 1200
        for name, base, data in blocks:
            for off in range(0, len(data) - 3, 2):
                x, y = struct.unpack_from("<HH", data, off)
                if not (min_x <= x <= max_x and min_y <= y <= max_y):
                    continue
                result["candidates"].append(dict(block=name, base=base, offset=off,
                                                  x=x, y=y,
                                                  distance=abs(x - tx) + abs(y - ty)))
            # Some object/path layouts store world coordinates as signed
            # DWORDs or 16.16 fixed point rather than adjacent uint16s.
            for off in range(0, len(data) - 7, 4):
                ix, iy = struct.unpack_from("<ii", data, off)
                if min_x <= ix <= max_x and min_y <= iy <= max_y:
                    result["candidates"].append(dict(block=name, base=base,
                                                      offset=off, x=ix, y=iy,
                                                      encoding="i32",
                                                      distance=abs(ix - tx) + abs(iy - ty)))
                fx, fy = ix >> 16, iy >> 16
                if min_x <= fx <= max_x and min_y <= fy <= max_y:
                    result["candidates"].append(dict(block=name, base=base,
                                                      offset=off, x=fx, y=fy,
                                                      encoding="fp16",
                                                      distance=abs(fx - tx) + abs(fy - ty)))
        result["candidates"].sort(key=lambda item: (item["distance"], item["block"], item["offset"]))
        result["candidates"] = result["candidates"][:128]
        return result

    def object_room_geometry(unit, scene, target):
        """Locate the object's loaded Room1 and summarize its collision grid."""
        pointer = int(unit["pointer"])
        for item in scene.get("loaded_rooms", []):
            room_ptr = int(item["pointer"])
            try:
                room = win.read(process, room_ptr, 0x80)
                uptr = struct.unpack_from("<I", room, 0x2C)[0]
                seen = set()
                while uptr:
                    if uptr in seen or len(seen) >= 8192:
                        break
                    seen.add(uptr)
                    if uptr == pointer:
                        grid_ptr = struct.unpack_from("<I", room, 0x34)[0]
                        if not grid_ptr:
                            return dict(room_pointer=room_ptr, grid_status="no_grid",
                                        bounds=item.get("bounds"))
                        header = win.read(process, grid_ptr, 0x24)
                        gx, gy, gw, gh = struct.unpack_from("<iiii", header)
                        mask_ptr = struct.unpack_from("<I", header, 0x20)[0]
                        if not (0 < gw <= 512 and 0 < gh <= 512 and mask_ptr):
                            return dict(room_pointer=room_ptr, grid_status="invalid_grid",
                                        bounds=item.get("bounds"), header=[gx, gy, gw, gh])
                        masks = struct.unpack("<" + "H" * (gw * gh),
                                              win.read(process, mask_ptr, 2 * gw * gh))
                        tx, ty = int(target[0]), int(target[1])
                        nearby = []
                        for radius in range(0, 17):
                            for dx in range(-radius, radius + 1):
                                for dy in range(-radius, radius + 1):
                                    if max(abs(dx), abs(dy)) != radius:
                                        continue
                                    x, y = tx + dx, ty + dy
                                    ix, iy = x - gx, y - gy
                                    if 0 <= ix < gw and 0 <= iy < gh:
                                        mask = masks[iy * gw + ix]
                                        nearby.append(dict(x=x, y=y, mask=mask,
                                                           walkable=not (mask & (0x1C09 | 0x20)),
                                                           distance=abs(dx) + abs(dy)))
                            if any(row["walkable"] for row in nearby if row["distance"] == radius):
                                break
                        return dict(room_pointer=room_ptr, grid_pointer=grid_ptr,
                                    grid_status="ok", header=[gx, gy, gw, gh],
                                    target=list(target), nearby=nearby[:256])
                    raw = win.read(process, uptr, 0xEC)
                    uptr = struct.unpack_from("<I", raw, 0xE8)[0]
            except (OSError, ValueError, struct.error):
                continue
        return dict(grid_status="object_room_not_found")

    def resume_game_window():
        desktop = win.get_thread_desktop(api(win.k, "GetCurrentThreadId", W.DWORD)())
        windows = [item for item in win.windows(desktop, args.pid) if item["class"] == "Diablo II"]
        if len(windows) != 1:
            raise ValueError("Expected one Diablo II window")
        post = api(win.u, "PostMessageW", W.BOOL, W.HWND, W.UINT, C.c_size_t, C.c_ssize_t)
        win.checked(post(windows[0]["hwnd"], 0x1C, 1, 0))  # WM_ACTIVATEAPP
        return windows[0]

    def post_bash_target(window, state, right_button=False, target_level=None):
        """Post one background left-click at the nearest loaded monster.

        Camera/projection values come from the version-pinned client getter
        used by TownExitWalker.  This sends only window-targeted mouse
        messages; it never moves the system cursor or writes game memory.
        """
        player = state["player"]
        scene = state["scene"]
        if player.get("status") != "player" or scene.get("status") != "scene":
            raise ValueError("skill input requires a verified player scene")
        if player.get("mode") in (0, 17):
            raise ValueError("skill input requires a living player; DT/DD cannot execute an attack")
        origin = (player["position"]["x"], player["position"]["y"])
        monsters = [u for u in scene.get("loaded_units", [])
                    if u.get("type") == 1 and u.get("position")
                    and (target_level is None or u.get("level_id") == target_level)
                    and u.get("unit_id") != player.get("unit_id")]
        if not monsters:
            raise ValueError("no loaded monster has a validated path position")
        current_modules = win.modules(args.pid)
        camera = TownExitWalker(win, process, current_modules, args.output).camera()
        project = lambda p: (16 * (p[0] - p[1]) - camera[0],
                             8 * (p[0] + p[1]) + 8 - camera[1])
        client = W.RECT()
        win.checked(win.get_rect(window["hwnd"], C.byref(client)))
        px, py = project(origin)
        if not (client.right * .2 <= px <= client.right * .8 and
                client.bottom * .15 <= py <= client.bottom * .85):
            raise ValueError("player projection is outside client bounds")
        visible = []
        for candidate in monsters:
            candidate_pos = (candidate["position"]["x"], candidate["position"]["y"])
            sx, sy = project(candidate_pos)
            if 8 <= sx < client.right - 8 and 8 <= sy < client.bottom - 8:
                visible.append((math.dist(origin, candidate_pos), candidate,
                                 candidate_pos, sx, sy))
        if visible:
            _, target, target_pos, tx, ty = min(visible, key=lambda item: item[0])
            kind = "attack"
        else:
            # The loaded list can contain a whole monster pack outside the
            # current camera.  Click the furthest screen-visible point along
            # the path to the nearest monster, then let the next resample
            # choose a real target after the player has approached.
            nearest = min(monsters, key=lambda candidate: math.dist(
                origin, (candidate["position"]["x"], candidate["position"]["y"])))
            nearest_pos = (nearest["position"]["x"], nearest["position"]["y"])
            candidates = []
            for step in range(1, 11):
                fraction = step / 10.0
                point = (round(origin[0] + (nearest_pos[0] - origin[0]) * fraction),
                         round(origin[1] + (nearest_pos[1] - origin[1]) * fraction))
                sx, sy = project(point)
                if 24 <= sx < client.right - 24 and 24 <= sy < client.bottom - 80:
                    candidates.append((fraction, point, sx, sy))
            if not candidates:
                raise ValueError("no screen-visible approach point toward loaded monsters")
            _, target_pos, tx, ty = candidates[-1]
            target = nearest
            kind = "approach"
        post = api(win.u, "PostMessageW", W.BOOL, W.HWND, W.UINT, C.c_size_t, C.c_ssize_t)
        lparam = int(tx) | (int(ty) << 16)
        down = 0x204 if right_button else 0x201
        up = 0x205 if right_button else 0x202
        win.checked(post(window["hwnd"], down, 2 if right_button else 1, lparam))
        time.sleep(0.08)
        win.checked(post(window["hwnd"], up, 0, lparam))
        return dict(kind=kind, button="right" if right_button else "left",
                    delivery="PostMessageW", hwnd=window["hwnd"],
                    target_pointer=target["pointer"], target_id=target["unit_id"],
                    target_class=target["class_id"], player=list(origin),
                    target=list(target_pos), camera=list(camera),
                    client=[client.right, client.bottom], click=[int(tx), int(ty)])

    try:
        process = win.checked(api(win.k, "OpenProcess", W.HANDLE, W.DWORD, W.BOOL, W.DWORD)(
            0x400 | 0x10 | 0x100000, False, args.pid))
        query = api(win.k, "QueryFullProcessImageNameW", W.BOOL, W.HANDLE, W.DWORD, W.LPWSTR, C.POINTER(W.DWORD))
        image, size = C.create_unicode_buffer(32768), W.DWORD(32768)
        win.checked(query(process, 0, image, C.byref(size)))
        executable = args.home / "D2v110各版本免CD全功能启动.exe"
        if Path(image.value).resolve() != executable.resolve() or digest(executable) != "602d3c638d782df6ad3edf8efbe07bbddd771f625e9491056084b83484848958":
            raise ValueError("Wrong original loader identity")
        report.update(command_line=live.command_line(win, process), hashes=HASHES)
        initial = snapshot()
        write_json(args.output / "initial.json", initial)
        paths = [args.home / "save"]
        try:
            with winreg.OpenKey(winreg.HKEY_CURRENT_USER, r"Software\Blizzard Entertainment\Diablo II", 0,
                                winreg.KEY_READ | winreg.KEY_WOW64_32KEY) as key:
                registered = Path(winreg.QueryValueEx(key, "Save Path")[0])
                if registered.resolve() not in [p.resolve() for p in paths]:
                    paths.append(registered)
        except FileNotFoundError:
            pass
        report["save_backups"] = []
        for index, source in enumerate(paths):
            if source.is_dir():
                destination = args.output / ("save-backup-" + str(index))
                shutil.copytree(source, destination)
                report["save_backups"].append(dict(source=str(source), backup=str(destination),
                    files={str(p.relative_to(destination)): digest(p) for p in destination.rglob("*") if p.is_file()}))
        report["status"] = "ready"
        resume_game_window()
        write_json(args.output / "summary.json", report)
        deadline, handled = time.monotonic() + args.seconds, set()
        while time.monotonic() < deadline:
            if win.wait(process, 0) == 0:
                raise ValueError("Original exited")
            for path in sorted(commands.glob("*.json")):
                if path.name in handled:
                    continue
                handled.add(path.name)
                action = dict(command_file=path.name, status="starting", started=time.time())
                report["actions"].append(action)
                write_json(args.output / "summary.json", report)
                try:
                    command = json.loads(path.read_text(encoding="utf-8-sig"))
                    op = command["op"]
                    if op == "stop":
                        action["status"] = "completed"
                        failed = any(a["status"] == "failed" for a in report["actions"])
                        report["status"] = "stopped_with_failures" if failed else "stopped"
                        return 1 if failed else 0
                    state = snapshot()
                    write_json(args.output / (path.stem + "-before.json"), state)
                    if op == "snapshot":
                        pass
                    elif op == "portal_scroll_probe":
                        from inventory_reader import inventory_snapshot
                        if state["player"].get("name") != "CXPortal" or state["scene"].get("level_id") != 2:
                            raise ValueError("Portal probe requires CXPortal in Blood Moor")
                        inv = inventory_snapshot(win, process, state["player"])
                        if inv["count"] != 8 or any(i["class_id"] != 529 for i in inv["items"]):
                            raise ValueError("Portal probe requires untouched eight-scroll fixture")
                        window = resume_game_window()
                        if window.get("visible"):
                            raise ValueError("Portal probe requires hidden window")
                        rect = W.RECT()
                        win.checked(win.get_rect(window["hwnd"], C.byref(rect)))
                        if (rect.right, rect.bottom) not in ((640, 480), (800, 600)):
                            raise ValueError("Unsupported inventory client size")
                        post = api(win.u, "PostMessageW", W.BOOL, W.HWND, W.UINT, C.c_size_t, C.c_ssize_t)
                        send = api(win.u, "SendMessageW", C.c_ssize_t, W.HWND, W.UINT, C.c_size_t, C.c_ssize_t)
                        send(window["hwnd"], 0x6, 1, 0)
                        send(window["hwnd"], 0x7, 0, 0)
                        def key(vk, scan):
                            send(window["hwnd"], 0x100, vk, (scan << 16) | 1)
                            time.sleep(.08)
                            send(window["hwnd"], 0x101, vk, (scan << 16) | 0xC0000001)
                        action["inventory_before"] = inv
                        # Fixed fixture stores tsc at inventory row 0, column 0.
                        key(0x49, 0x17)
                        time.sleep(.7)
                        pixels = win.capture_window_pixels(window["hwnd"])
                        action["panel_capture_status"] = pixels.get("status")
                        if pixels.get("status") == "captured":
                            write_bmp(args.output / (path.stem + "-inventory.bmp"), pixels["width"], pixels["height"], pixels["pixels"])
                        x, y = 367 + (rect.right - 640)//2, 250 + (rect.bottom - 480)//2
                        lp = x | (y << 16)
                        win.checked(post(window["hwnd"], 0x200, 0, lp))
                        win.checked(post(window["hwnd"], 0x204, 2, lp))
                        time.sleep(.08)
                        win.checked(post(window["hwnd"], 0x205, 0, lp))
                        time.sleep(2)
                        action["inventory_after"] = inventory_snapshot(win, process, snapshot()["player"])
                        key(0x49, 0x17)
                        time.sleep(.4)
                        after = snapshot()
                        before_ids = {u["unit_id"] for u in state["scene"].get("loaded_units", []) if u["type"] == 2}
                        action["new_objects"] = [u for u in after["scene"].get("loaded_units", []) if u["type"] == 2 and u["unit_id"] not in before_ids]
                        action["input"] = dict(delivery="PostMessageW", inventory_click=[x,y], global_input=False)
                        action["input"]["inventory_toggle"] = "SendMessageW/WM_CHAR"
                        write_json(args.output / (path.stem + "-portal-evidence.json"), action)
                        if action["inventory_after"]["count"] != 7:
                            raise ValueError("Scroll consumption not observed; no portal-entry input attempted")
                    elif op == "walk":
                        if state["player"].get("name") != "CXSorc" or state["scene"].get("level_id") != 1:
                            raise ValueError("Walk operation requires CXSorc in Rogue Encampment")
                        walk_dir = args.output / path.stem
                        command = [
                            str(__import__("sys").executable), str(ROOT / "menu-driver.py"),
                            "--pid", str(args.pid), "--home", str(args.home),
                            "--output", str(walk_dir), "--seconds", "100",
                            "--hidden-run", "--walk-to-level", "2", "--capture-layouts",
                        ]
                        child = subprocess.run(command, capture_output=True, text=True,
                                               creationflags=subprocess.CREATE_NO_WINDOW)
                        action["walk_exit_code"] = child.returncode
                        action["walk_stdout"] = child.stdout[-2000:]
                        action["walk_stderr"] = child.stderr[-2000:]
                        walk_summary = walk_dir / "summary.json"
                        action["walk_result"] = json.loads(walk_summary.read_text(encoding="utf8")) if walk_summary.exists() else {}
                        if child.returncode or action["walk_result"].get("status") != "scene_observed":
                            raise ValueError("Background walk did not reach Blood Moor")
                    elif op == "room_census_walk":
                        scenario = command.get("scenario", {})
                        level_id = scenario.get("level_id", 42)
                        seconds = scenario.get("seconds", 900)
                        if state["player"].get("name") not in ("aaa", "MapWalk", "Census", "CensusB", "CensusSorc"):
                            raise ValueError("Room census requires the aaa/MapWalk/Census fixture")
                        if state["scene"].get("level_id") != level_id:
                            raise ValueError("Room census requires the selected level already loaded")
                        census_dir = args.output / path.stem
                        census_dir.mkdir()
                        census = run_room_census(
                            win, process, args, snapshot, resume_game_window,
                            census_dir, level_id=level_id, seconds=seconds,
                            navigation_plan=(ROOT / "plans" / (scenario["navigation_plan"] + ".json")
                                             if scenario.get("navigation_plan") else None),
                            resume_census=(ROOT / "plans" / (scenario["resume_census"] + ".json")
                                           if scenario.get("resume_census") else None))
                        # Keep the polled session status small. Full unit and
                        # click evidence already lives in room-census.json.
                        action["room_census_result"] = {
                            key: census.get(key) for key in (
                                "status", "level_id", "rooms_seen", "rooms_visited",
                                "rooms_remaining", "monster_count", "unique_units",
                                "full_level_population_verified", "errors")}
                        action["room_census_path"] = str(census_dir / "room-census.json")
                        if census.get("status") not in ("completed_all_generated_rooms", "timeout", "navigation_exhausted_partial"):
                            raise ValueError("Room census failed: " + str(census.get("errors")))
                        report["status"] = "ready"
                    elif op == "waypoint_open":
                        scenario = command.get("scenario", {})
                        if state["player"].get("name") not in ("aaa", "MapWalk", "Census", "CensusB", "CensusSorc"):
                            raise ValueError("Waypoint probe requires the aaa/MapWalk/Census fixture")
                        waypoint_level = int(scenario.get("level_id", 40))
                        if state["scene"].get("level_id") != waypoint_level:
                            raise ValueError("Waypoint probe requires level %d" % waypoint_level)
                        game_window = resume_game_window()
                        modules = win.modules(args.pid)
                        target = (int(scenario.get("x", 5069 if waypoint_level == 40 else 4419)),
                                  int(scenario.get("y", 5084 if waypoint_level == 40 else 5609)))
                        waypoint_class_id = int(scenario.get("class_id", 156 if waypoint_level == 40 else 119))
                        # D2's object path is an anchor at the north edge of
                        # the waypoint graphic.  The clickable center is 12
                        # subtiles south in this 1.10f town layout.
                        nav_target = (target[0], target[1] + int(scenario.get("nav_offset_y", 12)))
                        diagnostic_state = snapshot()
                        diagnostic_objects = [u for u in diagnostic_state.get("scene", {}).get("loaded_units", [])
                                              if u.get("type") == 2 and u.get("class_id") == waypoint_class_id]
                        action["object_diagnostics"] = [
                            dict(coordinates=object_coordinate_candidates(u, target),
                                 room=object_room_geometry(u, diagnostic_state.get("scene", {}), target))
                            for u in diagnostic_objects
                        ]
                        action["object_diagnostics_count"] = len(diagnostic_objects)
                        user32 = C.WinDLL("user32", use_last_error=True)
                        # DirectDraw in this legacy build stops consuming game
                        # input while the primary surface is fully hidden.
                        # Show the window without activating it; all later
                        # input is still delivered with window-local messages.
                        show = user32.ShowWindow
                        show.argtypes = [W.HWND, C.c_int]
                        show.restype = W.BOOL
                        # Preserve the caller's visibility choice. A hidden
                        # waypoint job must never expose the original window.
                        if game_window.get("visible"):
                            show(game_window["hwnd"], 4)  # SW_SHOWNOACTIVATE
                        action["window_visibility_preserved"] = True
                        action["foreground_activation_requested"] = False
                        send = user32.SendMessageW
                        send.argtypes = [W.HWND, W.UINT, C.c_size_t, C.c_ssize_t]
                        send.restype = C.c_ssize_t
                        send(game_window["hwnd"], 0x0006, 1, 0)
                        send(game_window["hwnd"], 0x0007, 0, 0)
                        def window_click(x, y):
                            # Client-local messages leave the system cursor
                            # and foreground activation under user control.
                            lparam = (int(x) & 0xFFFF) | ((int(y) & 0xFFFF) << 16)
                            send(game_window["hwnd"], 0x0200, 0, lparam)
                            send(game_window["hwnd"], 0x0201, 1, lparam)
                            time.sleep(0.08)
                            send(game_window["hwnd"], 0x0202, 0, lparam)
                            return [int(x), int(y)], "window-local SendMessageW"
                        clicks = []
                        diagnostic_seen = {item.get("pointer") for item in diagnostic_objects}
                        destination_scene = None
                        for _ in range(60):
                            refreshed = snapshot()
                            refreshed_scene = refreshed.get("scene", {})
                            if refreshed_scene.get("level_id") != waypoint_level:
                                destination_scene = dict(
                                    level_id=refreshed_scene.get("level_id"),
                                    player=refreshed.get("player", {}),
                                    time=refreshed.get("time"))
                                break
                            for candidate in refreshed.get("scene", {}).get("loaded_units", []):
                                if (candidate.get("type") == 2 and candidate.get("class_id") == waypoint_class_id
                                        and candidate.get("pointer") not in diagnostic_seen):
                                    action["object_diagnostics"].append(
                                        dict(coordinates=object_coordinate_candidates(candidate, target),
                                             room=object_room_geometry(candidate,
                                                                       refreshed.get("scene", {}), target)))
                                    diagnostic_seen.add(candidate.get("pointer"))
                            position = refreshed.get("player", {}).get("position")
                            if not position:
                                raise ValueError("player position disappeared during waypoint approach")
                            camera = TownExitWalker(win, process, win.modules(args.pid), args.output).camera()
                            project = lambda p: (16 * (p[0] - p[1]) - camera[0],
                                                 8 * (p[0] + p[1]) + 8 - camera[1])
                            rect = W.RECT()
                            win.checked(win.get_rect(game_window["hwnd"], C.byref(rect)))
                            origin = (position["x"], position["y"])
                            # Approach stops south of the object, but the
                            # interaction click must hit the object itself.
                            tx, ty = project(target)
                            if 64 <= tx < rect.right - 64 and 72 <= ty < rect.bottom - 72:
                                point = nav_target
                            else:
                                candidates = []
                                # The waypoint can be several screen heights
                                # away.  Use fine-grained fractions so an
                                # intermediate point remains visible instead
                                # of rejecting the whole straight approach.
                                for step in range(1, 101):
                                    fraction = step / 100.0
                                    point = (round(origin[0] + (nav_target[0] - origin[0]) * fraction),
                                             round(origin[1] + (nav_target[1] - origin[1]) * fraction))
                                    sx, sy = project(point)
                                    if 64 <= sx < rect.right - 64 and 72 <= sy < rect.bottom - 72:
                                        candidates.append((fraction, point, sx, sy))
                                if not candidates:
                                    raise ValueError("no visible approach point for waypoint: "
                                                     + repr(dict(target=nav_target, origin=origin, camera=list(camera),
                                                                 client=[rect.right, rect.bottom])))
                                _, point, tx, ty = candidates[-1]
                            tx, ty = project(point)
                            delivered_click, delivery = window_click(tx, ty)
                            clicks.append(dict(target=list(point), click=[int(tx), int(ty)],
                                               screen=None,
                                               delivery=delivery, camera=list(camera),
                                               client=[rect.right, rect.bottom]))
                            time.sleep(0.8)
                            latest = snapshot()
                            latest_position = latest.get("player", {}).get("position")
                            if latest_position and abs(latest_position["x"] - nav_target[0]) <= 8 and abs(latest_position["y"] - nav_target[1]) <= 8:
                                break
                        action["waypoint_click"] = dict(
                            target=list(nav_target), logical_target=list(target), clicks=clicks,
                            delivery="window-local SendMessageW",
                            class_id=waypoint_class_id, level_id=waypoint_level)
                        if destination_scene:
                            action["destination_scene"] = destination_scene
                            action["waypoint_click"]["stopped_on_level_change"] = True
                        # The first click may only enqueue a native path to the
                        # object's walkable interaction radius.  Give the path
                        # a moment to settle, then issue one explicit object
                        # click using the latest camera instead of immediately
                        # sampling the D2Win chain while the player is still
                        # walking.
                        time.sleep(2.0)
                        settled = snapshot()
                        settled_position = settled.get("player", {}).get("position")
                        if settled_position and not destination_scene:
                            camera = TownExitWalker(win, process, win.modules(args.pid), args.output).camera()
                            project = lambda p: (16 * (p[0] - p[1]) - camera[0],
                                                 8 * (p[0] + p[1]) + 8 - camera[1])
                            tx, ty = project(nav_target)
                            rect = W.RECT()
                            win.checked(win.get_rect(game_window["hwnd"], C.byref(rect)))
                            if 0 <= tx < rect.right and 0 <= ty < rect.bottom:
                                extra_attempts = []
                                pre_pixels = win.capture_window_pixels(game_window["hwnd"])
                                visual_point = None
                                if pre_pixels.get("status") == "captured":
                                    before_image = args.output / (path.stem + "-waypoint-before-click.bmp")
                                    write_bmp(before_image, pre_pixels["width"],
                                              pre_pixels["height"], pre_pixels["pixels"])
                                    # Detect the bright blue waypoint flame in
                                    # the game area.  The mana orb and skill
                                    # bar are below y=360 and are excluded.
                                    width, height = pre_pixels["width"], pre_pixels["height"]
                                    raw = pre_pixels["pixels"]
                                    mask = set()
                                    for yy in range(min(360, height)):
                                        row = yy * width * 4
                                        for xx in range(width):
                                            b, g, r, _ = raw[row + xx * 4:row + xx * 4 + 4]
                                            if b > 130 and b > r * 1.2 and b > g * 1.05 and g > 70:
                                                mask.add((xx, yy))
                                    components = []
                                    while mask:
                                        seed = mask.pop()
                                        queue, component = [seed], [seed]
                                        while queue:
                                            qx, qy = queue.pop()
                                            for dx in (-1, 0, 1):
                                                for dy in (-1, 0, 1):
                                                    neighbor = (qx + dx, qy + dy)
                                                    if neighbor in mask:
                                                        mask.remove(neighbor)
                                                        queue.append(neighbor)
                                                        component.append(neighbor)
                                        if len(component) >= 10:
                                            components.append(component)
                                    if components:
                                        # A waypoint has two blue flames. The
                                        # clickable stone is centered between
                                        # them and slightly below their bases;
                                        # using only the largest flame lands on
                                        # the visual effect in Act I.
                                        selected = sorted(components, key=len, reverse=True)[:2]
                                        points = [(sum(x for x, _ in c) / len(c),
                                                   sum(y for _, y in c) / len(c)) for c in selected]
                                        cx = sum(x for x, _ in points) / len(points)
                                        cy = sum(y for _, y in points) / len(points)
                                        visual_point = (round(cx), round(cy + 25))
                                        visual_point = (max(8, min(width - 8, visual_point[0])),
                                                        max(8, min(height - 8, visual_point[1])))
                                        action["waypoint_visual_target"] = dict(
                                            blue_components=[[len(c), min(x for x, _ in c),
                                                              max(x for x, _ in c),
                                                              min(y for _, y in c),
                                                              max(y for _, y in c)] for c in selected],
                                            click=list(visual_point))
                                click_point = visual_point or (int(tx), int(ty))
                                extra_click, extra_delivery = window_click(*click_point)
                                extra_attempts.append(dict(delivered=extra_click,
                                                           delivery=extra_delivery))
                                time.sleep(0.8)
                                action["waypoint_extra_click"] = dict(
                                    target=list(nav_target), logical_target=list(target), click=list(click_point),
                                    attempts=extra_attempts, camera=list(camera),
                                    position=settled_position)
                                pixels = win.capture_window_pixels(game_window["hwnd"])
                                if pixels.get("status") == "captured":
                                    menu_image = args.output / (path.stem + "-waypoint-menu.bmp")
                                    write_bmp(menu_image, pixels["width"], pixels["height"], pixels["pixels"])
                                    action["waypoint_menu_image"] = str(menu_image)
                                time.sleep(1.0)
                        controls_dir = args.output / (path.stem + "-controls")
                        child = subprocess.run([
                            str(__import__("sys").executable), str(ROOT / "menu-driver.py"),
                            "--pid", str(args.pid), "--home", str(args.home),
                            "--output", str(controls_dir), "--seconds", "4",
                            ("--visible-run" if game_window.get("visible") else "--hidden-run"), "--capture-controls"],
                            capture_output=True, text=True,
                            creationflags=subprocess.CREATE_NO_WINDOW)
                        action["controls_exit_code"] = child.returncode
                        action["controls_path"] = str(controls_dir / "controls-latest.json")
                        action["controls_stdout"] = child.stdout[-2000:]
                        action["controls_stderr"] = child.stderr[-2000:]
                        if child.returncode:
                            raise ValueError("waypoint control capture failed")
                        action["controls"] = json.loads((controls_dir / "controls-latest.json").read_text(encoding="utf8"))
                        report["status"] = "ready"
                    elif op == "waypoint_select":
                        # waypoint_open deliberately leaves the native menu
                        # visible.  Keep selection separate so one missed
                        # click can never turn into a burst of unintended
                        # waypoint activations.
                        scenario = command.get("scenario", {})
                        if state["player"].get("name") not in ("aaa", "MapWalk", "Census", "CensusB", "CensusSorc"):
                            raise ValueError("Waypoint selection requires the aaa/MapWalk/Census fixture")
                        expected_level = int(scenario.get("level_id", 40))
                        destination_level = int(scenario.get("destination_level", 3))
                        if state["scene"].get("level_id") != expected_level:
                            raise ValueError("Waypoint selection requires level %d already loaded" % expected_level)
                        game_window = resume_game_window()
                        user32 = C.WinDLL("user32", use_last_error=True)
                        send = user32.SendMessageW
                        send.argtypes = [W.HWND, W.UINT, C.c_size_t, C.c_ssize_t]
                        send.restype = C.c_ssize_t
                        def menu_click(x, y):
                            lparam = (int(x) & 0xFFFF) | ((int(y) & 0xFFFF) << 16)
                            send(game_window["hwnd"], 0x0200, 0, lparam)
                            send(game_window["hwnd"], 0x0201, 1, lparam)
                            time.sleep(0.08)
                            send(game_window["hwnd"], 0x0202, 0, lparam)
                            return [int(x), int(y)]
                        tab = [int(scenario.get("tab_x", 35)), int(scenario.get("tab_y", 16))]
                        row = [int(scenario.get("row_x", 150)), int(scenario.get("row_y", 112))]
                        action["waypoint_selection"] = {
                            "source_level": expected_level,
                            "destination_level": destination_level,
                            "act": int(scenario.get("act", 1)),
                            "tab_click": menu_click(*tab),
                        }
                        time.sleep(0.8)
                        tab_pixels = win.capture_window_pixels(game_window["hwnd"])
                        if tab_pixels.get("status") == "captured":
                            tab_image = args.output / (path.stem + "-waypoint-act-tab.bmp")
                            write_bmp(tab_image, tab_pixels["width"], tab_pixels["height"], tab_pixels["pixels"])
                            action["waypoint_selection"]["act_tab_image"] = str(tab_image)
                        action["waypoint_selection"]["destination_click"] = menu_click(*row)
                        time.sleep(0.4)
                        post_pixels = win.capture_window_pixels(game_window["hwnd"])
                        if post_pixels.get("status") == "captured":
                            post_image = args.output / (path.stem + "-waypoint-after-select.bmp")
                            write_bmp(post_image, post_pixels["width"], post_pixels["height"], post_pixels["pixels"])
                            action["waypoint_selection"]["after_select_image"] = str(post_image)
                        waypoint_deadline = time.monotonic() + 20.0
                        destination_scene = None
                        last_scene = None
                        while time.monotonic() < waypoint_deadline:
                            latest = snapshot()
                            latest_scene = latest.get("scene", {})
                            last_scene = latest_scene
                            if latest_scene.get("level_id") != expected_level:
                                destination_scene = dict(
                                    level_id=latest_scene.get("level_id"),
                                    player=latest.get("player", {}),
                                    time=latest.get("time"))
                                if latest_scene.get("level_id") == destination_level:
                                    break
                            time.sleep(0.5)
                        action["waypoint_selection"]["destination_scene"] = destination_scene
                        action["waypoint_selection"]["last_scene"] = last_scene
                        if not destination_scene or destination_scene.get("level_id") != destination_level:
                            raise ValueError("waypoint selection did not reach destination level %d: %s" %
                                             (destination_level, destination_scene or last_scene))
                        report["status"] = "ready"
                    elif op == "layout_snapshot":
                        scenario = command.get("scenario", {})
                        expected_level = int(scenario.get("level_id", 3))
                        if state["player"].get("name") not in ("aaa", "MapWalk", "Census", "CensusB", "CensusSorc"):
                            raise ValueError("Layout snapshot requires the aaa/MapWalk/Census fixture")
                        if state["scene"].get("level_id") != expected_level:
                            raise ValueError("Layout snapshot requires level %d already loaded" % expected_level)
                        modules = win.modules(args.pid)
                        layout = layout_snapshot(win, process, modules, state["scene"])
                        layout_dir = args.output / path.stem
                        layout_dir.mkdir()
                        layout_path = layout_dir / "layout.json"
                        write_json(layout_path, layout)
                        game_window = resume_game_window()
                        pixels = win.capture_window_pixels(game_window["hwnd"])
                        if pixels.get("status") == "captured":
                            image_path = layout_dir / "scene.bmp"
                            write_bmp(image_path, pixels["width"], pixels["height"], pixels["pixels"])
                        else:
                            image_path = None
                        levels = [item for item in layout.get("levels", [])
                                  if item.get("level_id") == expected_level]
                        level = levels[0] if levels else {}
                        preset_units = [unit for room in level.get("rooms", [])
                                        for unit in room.get("preset_units", [])]
                        preset_counts = {}
                        preset_monster_classes = {}
                        preset_object_classes = {}
                        for unit in preset_units:
                            kind = str(unit.get("type"))
                            preset_counts[kind] = preset_counts.get(kind, 0) + 1
                            target = preset_monster_classes if unit.get("type") == 1 else preset_object_classes
                            if unit.get("type") in (1, 2):
                                key = str(unit.get("class_id"))
                                target[key] = target.get(key, 0) + 1
                        action["layout_snapshot"] = {
                            "path": str(layout_path),
                            "image": str(image_path) if image_path else None,
                            "level_id": expected_level,
                            "rooms": len(level.get("rooms", [])),
                            "preset_units": len(preset_units),
                            "preset_type_counts": preset_counts,
                            "preset_monster_class_counts": preset_monster_classes,
                            "preset_object_class_counts": preset_object_classes,
                            "full_level_census": state["scene"].get("full_level_census", {}),
                        }
                        waypoint_objects = []
                        for unit in state["scene"].get("loaded_units", []):
                            if unit.get("type") == 2 and unit.get("class_id") == 156:
                                waypoint_objects.append(object_coordinate_candidates(unit, (0, 0)))
                        action["layout_snapshot"]["waypoint_objects"] = waypoint_objects
                        # Diagnostic only: identify RoomEx fields that point
                        # at another generated room or an active Room1. This
                        # can reveal a retained server/materialization link
                        # without walking into combat or calling game code.
                        generated_room_ptrs = {int(room.get("pointer")) for room in level.get("rooms", [])}
                        active_room_ptrs = {int(room.get("pointer")) for room in state["scene"].get("loaded_rooms", [])}
                        room_pointer_probe = []
                        for room in level.get("rooms", []):
                            room_ptr = int(room["pointer"])
                            try:
                                raw_room = win.read(process, room_ptr, 0xEC)
                            except (OSError, ValueError):
                                continue
                            pointer_fields = []
                            for offset in range(0, len(raw_room) - 3, 4):
                                value = struct.unpack_from("<I", raw_room, offset)[0]
                                if value in generated_room_ptrs or value in active_room_ptrs:
                                    pointer_fields.append({
                                        "offset": offset,
                                        "value": value,
                                        "kind": "generated_room" if value in generated_room_ptrs else "active_room1",
                                    })
                            room_pointer_probe.append({
                                "roomex": room_ptr,
                                "active_flag": bool(room.get("active")),
                                "pointer_fields": pointer_fields,
                            })
                        action["layout_snapshot"]["room_pointer_probe"] = room_pointer_probe
                        report["status"] = "ready"
                    elif op in ("native_ai_batch", "fallen_death", "native_cast", "barbarian_formulas", "barbarian_bash", "barbarian_bash_target", "native_runtime_trace", "native_net_trace", "native_socket_trace", "native_server_population", "barbarian_whirlwind", "barbarian_whirlwind_animation"):
                        collector_op = "barbarian_whirlwind" if op == "barbarian_whirlwind_animation" else op
                        native = validate_collector(collector_op, command.get("scenario", {}))
                        if (collector_op == "native_runtime_trace"
                                and command.get("scenario", {}).get("input") is not None
                                and state["player"].get("mode") in (0, 17)):
                            raise ValueError("runtime input requires a living player before debugger attach")
                        game_window = resume_game_window()
                        time.sleep(0.5)
                        # DirectDraw/D2Gfx does not advance the legacy client
                        # presentation path while the primary window is fully
                        # hidden.  The client-callchain and animation probes
                        # need a real paint target, but must not steal the
                        # user's foreground focus.  Show the existing window
                        # without activation immediately before arming the
                        # observer/helper.  This is intentionally limited to
                        # visual/callchain collectors; all other sessions keep
                        # their requested hidden/visible policy.
                        if (collector_op == "native_runtime_trace"
                                and command.get("scenario", {}).get("input") in ("client_callchain", "whirlwind", "bash")) \
                                or op == "barbarian_whirlwind_animation":
                            user32 = C.WinDLL("user32", use_last_error=True)
                            show = user32.ShowWindow
                            show.argtypes = [W.HWND, C.c_int]
                            show.restype = W.BOOL
                            show(game_window["hwnd"], 4)  # SW_SHOWNOACTIVATE
                            game_window = resume_game_window()
                            action["render_window_forced_visible"] = True
                            action["render_window_visible"] = bool(game_window.get("visible"))
                        if collector_op == "native_net_trace":
                            if state["player"].get("status") != "player":
                                raise ValueError("D2Net trace requires an existing player scene")
                        elif collector_op == "native_server_population":
                            if state["player"].get("name") not in ("aaa", "MapWalk", "Census", "CensusB", "CensusSorc") or state["scene"].get("status") != "scene":
                                raise ValueError("native_server_population requires the MapWalk/aaa/Census scene")
                        else:
                            expected_names = {"CXBarb", "CXBarbWW"} if state["player"].get("class_id") == 4 else {"CXSorc"}
                            if state["player"].get("name") not in expected_names or state["scene"].get("level_id") != 2:
                                raise ValueError("Requires existing test character in Blood Moor")
                        artifacts = args.output / (path.stem + "-artifacts")
                        artifacts.mkdir()
                        if op in ("native_cast", "barbarian_bash", "barbarian_bash_target") and state["player"].get("class_id") == 4:
                            native_name = "d2game_barbarian_target.exe" if op == "barbarian_bash_target" else "d2game_barbarian_cast.exe"
                            native = ROOT / "native-harness" / "build" / native_name
                        # Keep the native executable at its already-built,
                        # hash-pinned path.  Some endpoint scanners quarantine
                        # a copied debugger helper before CreateProcess sees it,
                        # which surfaces as WinError 5.  We still record its
                        # digest and archive all source/controller inputs.
                        native_launch = native
                        native_source = ROOT / "native-harness" / (native.stem + ".cpp")
                        if not native_source.exists() and collector_op == "native_server_population":
                            native_source = ROOT / "native-harness" / "d2game_server_population.cpp"
                        sources = (native_source,
                                   ROOT / "native-harness/d2game_fallen_death.cpp",
                                   ROOT / "native-harness/d2game_controller.cpp", Path(__file__),
                                   ROOT / "collector_specs.py")
                        action["artifacts"] = {}
                        action["artifacts"][native.name] = digest(native)
                        for source in sources:
                            saved = artifacts / source.name
                            shutil.copy2(source, saved)
                            action["artifacts"][source.name] = digest(saved)
                        # Execute the archived bytes, so subsequent builds cannot
                        # change the executable between hashing and launch.
                        client_observer = None
                        if (op == "barbarian_whirlwind_animation" or
                                (collector_op == "native_runtime_trace" and
                                 command.get("scenario", {}).get("input") == "client_callchain") or
                                (collector_op == "native_runtime_trace" and
                                 command.get("scenario", {}).get("input") in ("bash", "whirlwind"))):
                            # Start the read-only D2Client sampler before the
                            # native helper triggers the skill. Both processes
                            # already run elevated inside this session.
                            client_dir = args.output / (path.stem + "-client-animation")
                            client_cmd = [str(__import__("sys").executable), str(ROOT / "live-observer.py"),
                                          "--pid", str(args.pid), "--home", str(args.home),
                                          "--output", str(client_dir), "--seconds", "12", "--interval", "0.04",
                                          "--animation-candidates", "--visual"]
                            client_observer = subprocess.Popen(client_cmd, stdout=subprocess.PIPE,
                                                               stderr=subprocess.PIPE,
                                                               creationflags=subprocess.CREATE_NO_WINDOW)
                            action["client_observer_pid"] = client_observer.pid
                            action["client_observer_output"] = str(client_dir)
                            time.sleep(0.25)
                        with (args.output / (path.stem + ".stdout")).open("w") as out, (args.output / (path.stem + ".stderr")).open("w") as err:
                            native_args = [str(native_launch), str(args.pid), str(args.home),
                                           str(args.output / path.stem)]
                            if collector_op == "native_ai_batch":
                                native_args.append(str(command.get("scenario", {}).get("class_id", 5)))
                                if "distance" in command.get("scenario", {}):
                                    native_args.append(str(command["scenario"]["distance"]))
                                elif command.get("scenario", {}).get("profile") in ("shaman-hydra", "shaman-hydra-wall", "shaman-golem", "shaman-golem-wall"):
                                    native_args.append(command["scenario"]["profile"])
                            if collector_op in ("native_net_trace", "native_socket_trace"):
                                native_args.append(str(command.get("scenario", {}).get("seconds", 20)))
                            if collector_op == "native_runtime_trace" and command.get("scenario", {}).get("input") == "client_callchain":
                                # Keep the debugger armed for the whole bounded
                                # visual sample.  Five seconds was enough for
                                # the initial callback but often expired before
                                # the subsequent animation/render dispatches.
                                trace_seconds = int(command.get("scenario", {}).get("seconds", 15))
                                native_args.append(str(max(5000, min(30000, trace_seconds * 1000))))
                            if op == "barbarian_bash_target":
                                native_args.append(str(command.get("scenario", {}).get("level", 1)))
                            if op == "fallen_death":
                                native_args.append(command.get("scenario", {}).get("profile", "same-room-native-death"))
                            child = subprocess.Popen(native_args,
                                stdout=out, stderr=err, creationflags=subprocess.CREATE_NO_WINDOW)
                            action["native_pid"] = child.pid
                            write_json(args.output / "summary.json", report)
                        if collector_op in ("native_runtime_trace", "native_net_trace", "native_socket_trace"):
                                # Let the helper attach and arm its one-shot
                                # breakpoint before delivering one bounded,
                                # window-targeted input in the background.
                                time.sleep(1.0)
                                input_mode = command.get("scenario", {}).get("input")
                                scenario = command.get("scenario", {})
                                whirlwind_input = (input_mode == "whirlwind"
                                                   or (input_mode == "client_callchain"
                                                       and scenario.get("skill") == 151
                                                       and scenario.get("class", 4) == 4))
                                if whirlwind_input:
                                    # F1 is pinned to Whirlwind in CXBarbWW.
                                    post_key = api(win.u, "PostMessageW", W.BOOL, W.HWND, W.UINT, C.c_size_t, C.c_ssize_t)
                                    send_key = api(win.u, "SendMessageW", C.c_ssize_t, W.HWND, W.UINT, C.c_size_t, C.c_ssize_t)
                                    hwnd = game_window["hwnd"]
                                    send_key(hwnd, 0x0006, 1, 0)  # WM_ACTIVATE/WA_ACTIVE
                                    send_key(hwnd, 0x0007, 0, 0)  # WM_SETFOCUS
                                    send_key(hwnd, 0x0100, 0x70, 0x003B0001)
                                    send_key(hwnd, 0x0101, 0x70, 0xC03B0001)
                                    time.sleep(0.2)
                                if collector_op == "native_runtime_trace" and input_mode is not None:
                                    input_error = None
                                    for attempt in range(8):
                                        refreshed = snapshot()
                                        try:
                                            action["skill_input"] = post_bash_target(
                                                game_window, refreshed, right_button=whirlwind_input,
                                                target_level=2 if input_mode == "encounter" else None)
                                        except (OSError, ValueError) as error:
                                            input_error = str(error)
                                            break
                                        # A right-button approach is still movement;
                                        # for Whirlwind it must not be reported as a
                                        # successful skill trigger. Keep posting
                                        # bounded approach clicks until the target
                                        # is actually screen-visible and the input
                                        # is classified as an attack.
                                        if action["skill_input"].get("kind") == "attack":
                                            break
                                        time.sleep(0.9)
                                    else:
                                        input_error = "approach did not bring a monster into view"
                                    # Do not leave the bounded debugger child
                                    # unobserved when input delivery fails.
                                    if input_error:
                                        action["input_error"] = input_error
                                elif input_mode in ("move", "move-burst"):
                                    count = 5 if input_mode == "move-burst" else 1
                                    inputs = []
                                    for burst_index in range(count):
                                        refreshed = snapshot()
                                        inputs.append(post_bash_target(
                                            game_window, refreshed, right_button=False))
                                        if burst_index + 1 < count:
                                            time.sleep(0.45)
                                    action["skill_input"] = inputs[0]
                                    if count > 1:
                                        action["skill_inputs"] = inputs
                                write_json(args.output / "summary.json", report)
                        try:
                            child.wait(timeout=600 if collector_op == "barbarian_formulas" else 120)
                        except subprocess.TimeoutExpired:
                            report["status"] = "native_cleanup_attention_required"
                            write_json(args.output / "summary.json", report)
                            # Never kill a debugger with unresolved cleanup.
                            while child.poll() is None:
                                time.sleep(0.5)
                        action["exit_code"] = child.returncode
                        if client_observer is not None:
                            try:
                                client_stdout, client_stderr = client_observer.communicate(timeout=30)
                            except subprocess.TimeoutExpired:
                                client_observer.kill()
                                client_stdout, client_stderr = client_observer.communicate(timeout=5)
                            action["client_observer_exit_code"] = client_observer.returncode
                            if client_stdout:
                                action["client_observer_stdout"] = client_stdout.decode("utf8", "replace")[-2000:]
                            if client_stderr:
                                action["client_observer_stderr"] = client_stderr.decode("utf8", "replace")[-2000:]
                            client_summary = Path(action["client_observer_output"]) / "summary.json"
                            # live-observer deliberately stores raw samples as
                            # samples.jsonl; native collectors use trace.jsonl
                            # for a different server-side format.
                            client_trace = Path(action["client_observer_output"]) / "samples.jsonl"
                            if client_summary.exists():
                                action["client_animation_result"] = json.loads(client_summary.read_text(encoding="utf8"))
                            if client_trace.exists():
                                canonical = Path(action["client_observer_output"]) / "animation-trace.json"
                                from animation_compare import client_trace as read_client_trace, write_trace
                                metadata, frames = read_client_trace(client_trace)
                                scenario = command.get("scenario", {})
                                metadata.update({
                                    "skill": scenario.get("skill"),
                                    "level": scenario.get("level"),
                                    "class": scenario.get("class", state["player"].get("class_id")),
                                    "character": state["player"].get("name"),
                                })
                                write_trace(canonical, metadata, frames)
                                action["client_animation_trace"] = str(canonical)
                                action["client_animation_mode_ids"] = sorted({
                                    frame.get("mode_id") for frame in frames
                                    if frame.get("mode_id") is not None
                                })
                                action["client_animation_skill_mode_observed"] = any(
                                    frame.get("mode_id") == 18 for frame in frames
                                )
                        result_path = args.output / path.stem / "summary.json"
                        result = json.loads(result_path.read_text(encoding="utf8")) if result_path.exists() else {}
                        action["native_result"] = result
                        if not result.get("cleanup_confirmed") or not result.get("detached"):
                            report["status"] = "native_cleanup_attention_required"
                            raise RuntimeError("No confirmed debugger cleanup; session stops")
                        report["status"] = "ready"
                        if child.returncode or result.get("status") != "passed":
                            raise ValueError("Original collector failed; inspect native artifacts")
                        if collector_op == "fallen_death":
                            from fallen_death_observations import analyze_controlled
                            death_report = analyze_controlled(result_path.with_name("frames.csv"))
                            death_path = result_path.with_name("death-observations.json")
                            write_json(death_path, death_report)
                            action["death_observation_path"] = str(death_path)
                            action["death_observation_status"] = death_report["status"]
                            action["ai_parity"] = "untested"
                        if (collector_op == "native_runtime_trace"
                                and command.get("scenario", {}).get("input") == "encounter"):
                            from fallen_death_observations import analyze
                            death_report = analyze(result_path.with_name("server-ai-samples.jsonl"))
                            death_path = result_path.with_name("death-observations.json")
                            write_json(death_path, death_report)
                            action["death_observation_path"] = str(death_path)
                            action["death_observation_status"] = death_report["status"]
                            action["ai_parity"] = "untested"
                        if action.get("input_error"):
                            raise ValueError(action["input_error"])
                    else:
                        raise ValueError("Supported operations: snapshot, walk, room_census_walk, waypoint_open, waypoint_select, layout_snapshot, native_cast, barbarian_formulas, barbarian_bash, barbarian_bash_target, native_runtime_trace, native_net_trace, native_socket_trace, native_server_population, barbarian_whirlwind, barbarian_whirlwind_animation, stop")
                    write_json(args.output / (path.stem + "-after.json"), snapshot())
                    action["status"] = "completed"
                except Exception as error:
                    action.update(status="failed", error=str(error))
                write_json(args.output / "summary.json", report)
                if report["status"] == "native_cleanup_attention_required":
                    return 1
            time.sleep(0.25)
        failed = any(a["status"] == "failed" for a in report["actions"])
        report["status"] = "expired_with_failures" if failed else "expired"
        return 1 if failed else 0
    except Exception as error:
        report.update(status="failed", error=str(error))
        return 1
    finally:
        if process:
            report["target_alive_after"] = win.wait(process, 0) != 0
            win.close(process)
        report["finished"] = time.time()
        write_json(args.output / "summary.json", report)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pid", required=True, type=int)
    parser.add_argument("--home", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--seconds", type=int, default=600)
    args = parser.parse_args()
    if not 1 <= args.seconds <= 1800:
        parser.error("Session limit: 1..1800 seconds")
    raise SystemExit(run(args))
