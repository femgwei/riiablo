"""Private-HWND Werewolf morph and client animation polling."""
import argparse
import ctypes as C
from ctypes import wintypes as W
import json
from pathlib import Path
import struct
import time

from observer import Win32, api, digest, player_snapshot
from scene_reader import scene_snapshot
from arctic_blast_passive import HASHES, LOADER_HASH, LOADER_NAME, overlay_snapshot, input_desktop_name
from druid_storm_passive import selected_skill


def desktop_name(win, handle):
    buf = C.create_unicode_buffer(256); needed = W.DWORD()
    win.checked(api(win.u, "GetUserObjectInformationW", W.BOOL, W.HANDLE, C.c_int,
                    C.c_void_p, W.DWORD, C.POINTER(W.DWORD))(
        handle, 2, buf, C.sizeof(buf), C.byref(needed)))
    return buf.value


def write(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf8")


def screen_point(win, process, modules, x, y):
    base = modules["d2client.dll"]["base"]
    camx = struct.unpack("<i", win.read(process, base + 0x10B9BC, 4))[0] \
        - struct.unpack("<i", win.read(process, base + 0x10B9C8, 4))[0]
    camy = struct.unpack("<i", win.read(process, base + 0x10B9B8, 4))[0]
    return 16 * (x - y) - camx, 8 * (x + y) + 8 - camy


def collect(args):
    args.output.mkdir(parents=True, exist_ok=False)
    win = Win32(); process = None; hwnd = None; pressed = False
    report = dict(status="starting", skill=223, global_input=False,
                  debugger_attached=False, memory_writes=False,
                  remote_execution=False, cleanup_confirmed=False,
                  scope="private HWND morph input and unsuspended client snapshots")
    samples = []
    try:
        desktop = win.get_thread_desktop(api(win.k, "GetCurrentThreadId", W.DWORD)())
        report["desktop"] = desktop_name(win, desktop)
        assert report["desktop"].startswith("riiablo_wolf_")
        assert report["desktop"] != input_desktop_name(win)
        process = win.checked(api(win.k, "OpenProcess", W.HANDLE, W.DWORD, W.BOOL, W.DWORD)(
            0x400 | 0x10 | 0x100000, False, args.pid))
        image, size = C.create_unicode_buffer(32768), W.DWORD(32768)
        win.checked(api(win.k, "QueryFullProcessImageNameW", W.BOOL, W.HANDLE, W.DWORD,
                        W.LPWSTR, C.POINTER(W.DWORD))(process, 0, image, C.byref(size)))
        assert Path(image.value).resolve() == (args.home / LOADER_NAME).resolve()
        assert digest(Path(image.value)) == LOADER_HASH
        modules = win.modules(args.pid)
        for dll, expected in HASHES.items():
            assert Path(modules[dll.lower()]["path"]).resolve() == (args.home / dll).resolve()
            assert digest(args.home / dll) == expected
        player = player_snapshot(win, process, modules)
        assert player["status"] == "player" and player["name"] == "CXDruWolf" and player["class_id"] == 5
        cp = player["pointer"]
        windows = [w for w in win.windows(desktop, args.pid) if w["class"] == "Diablo II"]
        assert len(windows) == 1
        hwnd = windows[0]["hwnd"]
        selected = selected_skill(win, process, cp)
        assert selected == (223, args.expected_level)
        # F1 is the fixed right-hand Werewolf binding. A private HWND message
        # is delivered on the isolated desktop and never activates it.
        point = (min(300, windows[0]["height"] - 100) << 16) | min(400, windows[0]["width"] - 40)
        scene = scene_snapshot(win, process, modules, player)
        target = next((u for u in scene.get("loaded_units", [])
                       if u.get("type") == 1 and u.get("position") and u.get("class_id") != 419), None)
        # Walk toward the nearest loaded monster until its projected point is
        # clickable. This remains private-HWND input and avoids mistaking a
        # long approach WL for a wolf A1 attack.
        for _ in range(30):
            if not target: break
            sx, sy = screen_point(win, process, modules,
                                  target["position"]["x"], target["position"]["y"])
            if 30 <= sx < windows[0]["width"] - 30 and 30 <= sy < windows[0]["height"] - 30:
                break
            px, py = player_snapshot(win, process, modules)["position"]["x"], player_snapshot(win, process, modules)["position"]["y"]
            tx, ty = target["position"]["x"], target["position"]["y"]
            dx, dy = tx - px, ty - py
            # Clamp the world direction to an edge click; the next scene
            # sample supplies a fresh camera and target position.
            edge_x = windows[0]["width"] - 45 if dx >= 0 else 45
            edge_y = windows[0]["height"] - 45 if dy >= 0 else 45
            win.post(hwnd, 0x200, 0, (int(edge_y) << 16) | int(edge_x))
            win.post(hwnd, 0x201, 1, (int(edge_y) << 16) | int(edge_x))
            time.sleep(.5); win.post(hwnd, 0x202, 0, (int(edge_y) << 16) | int(edge_x))
            player = player_snapshot(win, process, modules)
            scene = scene_snapshot(win, process, modules, player)
            target = next((u for u in scene.get("loaded_units", [])
                           if u.get("type") == 1 and u.get("position") and u.get("class_id") != 419), target)
        if target:
            sx, sy = screen_point(win, process, modules, target["position"]["x"], target["position"]["y"])
        else:
            sx, sy = 400, 300
        target_point = None
        if target and 16 <= sx < windows[0]["width"] - 16 and 16 <= sy < windows[0]["height"] - 16:
            target_point = (int(sy) << 16) | int(sx)
        events = []
        win.post(hwnd, 0x100, 0x70, 0); win.post(hwnd, 0x101, 0x70, 0)
        deadline = time.perf_counter() + 3
        while selected_skill(win, process, cp) != selected:
            assert time.perf_counter() < deadline
            time.sleep(.02)
        win.post(hwnd, 0x200, 0, point); win.post(hwnd, 0x204, 2, point); pressed = True
        start = time.perf_counter()
        with (args.output / "player-animation.jsonl").open("w", encoding="utf8") as out:
            while time.perf_counter() - start < args.seconds:
                elapsed = time.perf_counter() - start
                if pressed and elapsed >= .22:
                    win.post(hwnd, 0x205, 0, point); pressed = False
                if target_point is not None and elapsed >= .6 and "attack" not in events:
                    win.post(hwnd, 0x201, 0, target_point); time.sleep(.04)
                    win.post(hwnd, 0x202, 0, target_point); events.append("attack")
                if elapsed >= 2.5 and "restore" not in events:
                    win.post(hwnd, 0x100, 0x70, 0); win.post(hwnd, 0x101, 0x70, 0)
                    events.append("restore")
                raw = struct.unpack("<59I", win.read(process, cp, 0xEC))
                path = win.read(process, raw[11], 0x9C)
                item = dict(elapsed_ms=round((time.perf_counter() - start) * 1000),
                            mode=raw[4], animation_raw=raw[17], animation_frame=raw[18],
                            animation_rate=raw[19] & 0xFFFF,
                            x_fixed=struct.unpack_from("<I", path, 0)[0],
                            y_fixed=struct.unpack_from("<I", path, 4)[0],
                            direction=path[0x64], new_direction=path[0x65],
                            overlays=overlay_snapshot(win, process, cp))
                item["events"] = list(events)
                samples.append(item); out.write(json.dumps(item) + "\n"); out.flush()
                time.sleep(.02)
        assert samples and any(item["mode"] == 10 for item in samples)
        overlays = []
        for item in samples:
            for overlay in item["overlays"]:
                value = overlay.get("overlay") if isinstance(overlay, dict) else overlay
                if value is not None and value not in overlays: overlays.append(value)
        report.update(status="passed", selected_skill=list(selected), sample_count=len(samples),
                      events=events, target=target,
                      attack_mode_observed=any(item["mode"] in (4, 5) for item in samples),
                      mode_values=sorted({item["mode"] for item in samples}),
                      animation_raw_values=sorted({item["animation_raw"] for item in samples}),
                      overlay_keys=sorted(overlays))
    except Exception as error:
        import traceback
        report.update(status="failed", error=str(error), traceback=traceback.format_exc())
    finally:
        if pressed and hwnd: win.post(hwnd, 0x205, 0, 0)
        if process: win.close(process)
        report["cleanup_confirmed"] = True
        write(args.output / "summary.json", report)
    return 0 if report["status"] == "passed" else 1


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--pid", type=int, required=True)
    parser.add_argument("--home", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--seconds", type=float, default=8)
    parser.add_argument("--expected-level", type=int, default=1)
    raise SystemExit(collect(parser.parse_args()))
