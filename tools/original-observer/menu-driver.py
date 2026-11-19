"""Bounded background menu experiment for the exact local Diablo II 1.10f build.

Reads the game's control list, then posts messages only to its selected HWND.
Never brings a window to the foreground, injects code, or writes process memory.
Hidden mode clears the minimized state under an empty window region.
The optional command directory accepts only clicks on a currently verified
control, or stop. It is not a shell/remote-execution endpoint.
"""
import argparse
import ctypes as C
from ctypes import wintypes as W
import importlib.util
import json
from pathlib import Path
import shutil
import struct
import time
import winreg

from observer import Win32, api, digest, player_snapshot
from scene_reader import scene_snapshot
from scene_reader import layout_snapshot
from background_walk import TownExitWalker

spec = importlib.util.spec_from_file_location("live_observer", Path(__file__).with_name("live-observer.py"))
live = importlib.util.module_from_spec(spec)
spec.loader.exec_module(live)
WIN_HASH = "9d47815916fc7082ef86fc50705331e3f59d1ffe19e46217ac0551f7d9ee6066"
GFX_HASH = "b4247efe73169b66fe95c8cc3d085b9646e8fbf171688fee6b69799f791e56b8"


def card_has_name(control, name):
    return control.get("type") == 4 and control.get("y", 0) >= 85 and any(
        isinstance(cell, str) and cell.strip().casefold() == name.casefold()
        for line in control.get("lines", []) for cell in line)


def write_json(path, value):
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")
    temporary.replace(path)


def unicode_string(win, process, ptr, limit=256):
    if not ptr:
        return ""
    # Read pairs to avoid requiring unreadable bytes beyond a string allocation.
    result = bytearray()
    for i in range(limit):
        pair = win.read(process, ptr + 2 * i, 2)
        if pair == b"\0\0":
            break
        result.extend(pair)
    return result.decode("utf-16-le", "replace")


def controls(win, process, modules):
    base = modules["d2win.dll"]["base"]
    # Shipped CONTROL_AppendToList begins MOV ECX,[global]; TEST ECX,ECX.
    expected = b"\x8b\x0d" + struct.pack("<I", base + 0x5E24C) + b"\x85\xc9"
    if win.read(process, base + 0xD120, len(expected)) != expected:
        raise ValueError("Control list signature mismatch")
    ptr = win.u32(process, base + 0x5E24C)
    result, seen = [], set()
    while ptr:
        if ptr in seen or len(seen) >= 256:
            raise ValueError("Invalid/unbounded control chain")
        seen.add(ptr)
        raw = win.read(process, ptr, 0x40)
        kind, cell, flags = struct.unpack_from("<III", raw)
        x, y, width, height = struct.unpack_from("<iiii", raw, 0xC)
        if not 0 <= kind <= 13 or not all(-4096 <= v <= 4096 for v in (x, y, width, height)):
            raise ValueError("Invalid control header")
        item = dict(address=ptr, type=kind, flags=flags, x=x, y=y,
                    width=width, height=height, callback=struct.unpack_from("<I", raw, 0x34)[0])
        if kind == 6:
            item["text"] = unicode_string(win, process, ptr + 0x64)
            item["virtual_key"] = win.u32(process, ptr + 0x50)
            item["pressed"] = win.u32(process, ptr + 0x44)
            item["pending"] = win.u32(process, ptr + 0x48)
        if kind == 4:
            line = win.u32(process, ptr + 0x48)
            lines, line_seen = [], set()
            while line and len(lines) < 32:
                if line in line_seen:
                    raise ValueError("Text line cycle")
                line_seen.add(line)
                line_raw = win.read(process, line, 0x20)
                lines.append([unicode_string(win, process, col) for col in struct.unpack_from("<IIIII", line_raw) if col])
                line = struct.unpack_from("<I", line_raw, 0x1C)[0]
            item["lines"] = lines
        result.append(item)
        ptr = struct.unpack_from("<I", raw, 0x3C)[0]
    return result


def run(args):
    args.output.mkdir(parents=True, exist_ok=False)
    commands = args.output / "commands"
    commands.mkdir()
    win, process = Win32(), None
    report = dict(pid=args.pid, status="starting", memory_writes=False,
                  remote_execution=False, global_input=False, actions=[],
                  foreground_before=win.foreground(), screen_before=[win.system_metric(0), win.system_metric(1)])
    start = time.monotonic()
    try:
        process = win.checked(api(win.k, "OpenProcess", W.HANDLE, W.DWORD, W.BOOL, W.DWORD)(0x400 | 0x10 | 0x100000, False, args.pid))
        query = api(win.k, "QueryFullProcessImageNameW", W.BOOL, W.HANDLE, W.DWORD, W.LPWSTR, C.POINTER(W.DWORD))
        buf, size = C.create_unicode_buffer(32768), W.DWORD(32768)
        win.checked(query(process, 0, buf, C.byref(size)))
        executable = Path(buf.value)
        if executable.resolve() != (args.home / "D2v110各版本免CD全功能启动.exe").resolve():
            raise ValueError("Unexpected executable")
        modules = win.modules(args.pid)
        hashes = dict(live.EXPECTED, **{"D2Win.dll": WIN_HASH, "D2Gfx.dll": GFX_HASH})
        for name, expected in hashes.items():
            module = modules.get(name.lower())
            if not module or Path(module["path"]).resolve() != (args.home / name).resolve() or digest(Path(module["path"])) != expected:
                raise ValueError("Unsupported module: " + name)
        report.update(executable=str(executable), modules=modules, hashes=hashes,
                      command_line=live.command_line(win, process))
        save_paths = [args.home / "save"]
        try:
            with winreg.OpenKey(winreg.HKEY_CURRENT_USER, r"Software\Blizzard Entertainment\Diablo II", 0, winreg.KEY_READ | winreg.KEY_WOW64_32KEY) as key:
                registered_save = Path(winreg.QueryValueEx(key, "Save Path")[0])
                if registered_save.resolve() not in [p.resolve() for p in save_paths]:
                    save_paths.append(registered_save)
                report["registered_save_path"] = str(registered_save)
        except FileNotFoundError:
            pass
        report["save_backups"] = []
        for index, save_path in enumerate(save_paths):
            if save_path.is_dir():
                backup = args.output / ("save-backup-" + str(index))
                shutil.copytree(save_path, backup)
                report["save_backups"].append(dict(source=str(save_path), backup=str(backup),
                    files={p.name: dict(sha256=digest(p), size=p.stat().st_size, mtime=p.stat().st_mtime) for p in save_path.glob("*") if p.is_file()}))
        iconic = api(win.u, "IsIconic", W.BOOL, W.HWND)
        post = api(win.u, "PostMessageW", W.BOOL, W.HWND, W.UINT, C.c_size_t, C.c_ssize_t)
        desktop = win.get_thread_desktop(api(win.k, "GetCurrentThreadId", W.DWORD)())
        initial_windows = [w for w in win.windows(desktop, args.pid) if w["class"] == "Diablo II"]
        if args.visible_run and len(initial_windows) == 1 and iconic(initial_windows[0]["hwnd"]):
            # Restore the existing DirectDraw window without activation. This
            # is required for the native update loop; it does not move focus.
            show_async = api(win.u, "ShowWindowAsync", W.BOOL, W.HWND, C.c_int)
            show_async(initial_windows[0]["hwnd"], 4)  # SW_SHOWNOACTIVATE
            deadline = time.monotonic() + 3
            while iconic(initial_windows[0]["hwnd"]) and time.monotonic() < deadline:
                time.sleep(0.05)
            if iconic(initial_windows[0]["hwnd"]):
                raise ValueError("Visible mode could not restore minimized target")
            report["visible_restore"] = "shown_no_activate"
        hidden_prepared = bool(args.hidden_run and len(initial_windows) == 1
                               and not iconic(initial_windows[0]["hwnd"])
                               and not win.is_visible(initial_windows[0]["hwnd"]))
        report["hidden_window_reused"] = hidden_prepared
        handled, automatic_stages = set(), set()
        sample_count, animation_values, seed_values, window_states = 0, set(), set(), set()
        walker = TownExitWalker(win, process, modules, args.output) if args.walk_to_level else None
        walk_finished_at = None
        with (args.output / "samples.jsonl").open("w", encoding="utf-8") as stream:
            while time.monotonic() - start < args.seconds:
                if win.wait(process, 0) == 0:
                    raise ValueError("Target exited")
                windows = [w for w in win.windows(desktop, args.pid) if w["class"] == "Diablo II"]
                if len(windows) != 1:
                    raise ValueError("Expected exactly one Diablo II window")
                hwnd = windows[0]["hwnd"]
                if args.visible_run:
                    # The loader may minimize itself after the initial check
                    # during a compatibility/video-mode transition. Keep this
                    # explicitly visible job drawable without foreground APIs.
                    if iconic(hwnd) or not win.is_visible(hwnd):
                        api(win.u, "ShowWindowAsync", W.BOOL, W.HWND, C.c_int)(hwnd, 4)
                        time.sleep(0.1)
                    if win.u32(process, modules["d2gfx.dll"]["base"] + 0x1D84C):
                        win.checked(post(hwnd, 0x1C, 1, 0))
                # Hidden mode may start immediately after launch while the
                # loader owns focus; it is hidden below before any sampling.
                if win.foreground() == hwnd and not args.visible_run and not args.hidden_run:
                    raise ValueError("Target must remain outside foreground")
                if args.hidden_run and not hidden_prepared:
                    # A freshly launched loader is visible. Hide it first,
                    # then establish the same non-iconic hidden state used by
                    # the existing background experiment.
                    style_get = api(win.u, "GetWindowLongW", C.c_long, W.HWND, C.c_int)
                    style_set = api(win.u, "SetWindowLongW", C.c_long, W.HWND, C.c_int, C.c_long)
                    hide = api(win.u, "ShowWindowAsync", W.BOOL, W.HWND, C.c_int)
                    style = style_get(hwnd, -16)
                    report["window_style_before"] = style & 0xFFFFFFFF
                    win.checked(hide(hwnd, 0))
                    deadline = time.monotonic() + 3
                    while win.is_visible(hwnd) and time.monotonic() < deadline:
                        time.sleep(0.05)
                    if win.is_visible(hwnd):
                        raise ValueError("Window did not hide")
                    # Remove only WS_MINIMIZE while already invisible. No restore,
                    # show, focus, position or foreground operation is performed.
                    C.set_last_error(0)
                    previous = style_set(hwnd, -16, style_get(hwnd, -16) & ~0x20000000)
                    if not previous and C.get_last_error():
                        raise C.WinError(C.get_last_error())
                    if iconic(hwnd):
                        # Windows keeps a separate iconic state; clearing its
                        # style alone need not clear that state. Restore without
                        # activation under an EMPTY window region, then hide
                        # again before restoring the original region. This gives
                        # the window no drawable pixels during the transition.
                        gdi = C.WinDLL("gdi32", use_last_error=True)
                        region = api(gdi, "CreateRectRgn", W.HANDLE, C.c_int, C.c_int, C.c_int, C.c_int)
                        delete = api(gdi, "DeleteObject", W.BOOL, W.HANDLE)
                        get_region = api(win.u, "GetWindowRgn", C.c_int, W.HWND, W.HANDLE)
                        set_region = api(win.u, "SetWindowRgn", C.c_int, W.HWND, W.HANDLE, W.BOOL)
                        saved = win.checked(region(0, 0, 0, 0))
                        had_region = get_region(hwnd, saved) != 0
                        empty = win.checked(region(0, 0, 0, 0))
                        try:
                            win.checked(set_region(hwnd, empty, False))
                            empty = None  # Owned by Windows after successful SetWindowRgn.
                            win.checked(hide(hwnd, 4))  # SW_SHOWNOACTIVATE, clipped to empty region.
                            deadline = time.monotonic() + 3
                            while iconic(hwnd) and time.monotonic() < deadline:
                                time.sleep(0.05)
                        finally:
                            hide(hwnd, 0)
                            deadline = time.monotonic() + 3
                            while win.is_visible(hwnd) and time.monotonic() < deadline:
                                time.sleep(0.05)
                            if win.is_visible(hwnd):
                                raise ValueError("Could not re-hide region-clipped window")
                            win.checked(set_region(hwnd, saved if had_region else None, False))
                            if not had_region:
                                delete(saved)
                            if empty:
                                delete(empty)
                        report["hidden_transition"] = "empty_region_restore_noactivate_then_hide"
                    if iconic(hwnd) or win.is_visible(hwnd) or win.foreground() == hwnd:
                        raise ValueError("Could not establish hidden non-iconic window")
                    win.checked(post(hwnd, 0x1C, 1, 0))  # WM_ACTIVATEAPP to game's handler only
                    report["actions"].append(dict(op="hidden_noniconic", activate_app_message=True,
                                                  foreground_changed=False))
                    hidden_prepared = True
                    time.sleep(0.5)
                if args.hidden_run:
                    if iconic(hwnd) or win.is_visible(hwnd):
                        raise ValueError("Target no longer hidden/non-iconic")
                    if win.u32(process, modules["d2gfx.dll"]["base"] + 0x1D84C):
                        win.checked(post(hwnd, 0x1C, 1, 0))
                        report["actions"].append(dict(op="resume_hidden_handler", message="WM_ACTIVATEAPP"))
                        time.sleep(0.1)
                elif not args.visible_run and not iconic(hwnd):
                    raise ValueError("Target must remain minimized")
                snapshot = dict(elapsed_ms=round(1000 * (time.monotonic() - start)),
                                hwnd=hwnd, minimized=bool(iconic(hwnd)), visible=bool(win.is_visible(hwnd)), foreground=win.foreground(),
                                player=player_snapshot(win, process, modules),
                                controls=controls(win, process, modules),
                                graphics_pause=win.u32(process, modules["d2gfx.dll"]["base"] + 0x1D84C),
                                graphics_state=win.u32(process, modules["d2gfx.dll"]["base"] + 0x1D850),
                                windows=win.windows(desktop, args.pid))
                if args.capture_controls:
                    # Keep a bounded, read-only copy of the current D2Win
                    # control chain.  This is used to discover the waypoint
                    # dialog without assuming localized button text.
                    write_json(args.output / "controls-latest.json", snapshot["controls"])
                    report["controls_capture"] = "controls-latest.json"
                if snapshot["player"]["status"] == "player":
                    from inventory_reader import inventory_snapshot
                    try:
                        snapshot['inventory'] = inventory_snapshot(win, process, snapshot['player'])
                        report['inventory'] = snapshot['inventory']
                    except (OSError, ValueError) as error:
                        snapshot['inventory_error'] = str(error)
                        report['inventory_error'] = str(error)
                    animation_values.add(snapshot["player"]["animation_frame_raw"])
                    seed_values.add(tuple(snapshot["player"]["seed"]))
                    try:
                        snapshot["scene"] = scene_snapshot(win, process, modules, snapshot["player"])
                    except (OSError, ValueError) as error:
                        snapshot["scene_error"] = str(error)
                if args.capture_layouts and snapshot.get("scene", {}).get("status") == "scene":
                    name = "layout-" + str(sample_count) + ".json"
                    write_json(args.output / name, layout_snapshot(win, process, modules, snapshot["scene"]))
                    report.setdefault("layout_files", []).append(name)
                sample_count += 1
                window_states.add((snapshot["minimized"], snapshot["visible"]))
                stream.write(json.dumps(snapshot, ensure_ascii=False) + "\n")
                stream.flush()
                write_json(args.output / "latest.json", snapshot)
                if snapshot["player"]["status"] == "player":
                    report.update(status="player_observed", player=snapshot["player"])
                    if snapshot.get("scene", {}).get("status") == "scene":
                        report.update(status="scene_observed", scene=snapshot["scene"])
                        # A no-walk entry is only a fixture loader. Once a
                        # stable scene is observed, return immediately instead
                        # of sampling the unchanged town for the full timeout.
                        if args.no_walk and sample_count >= 3:
                            break
                    # Preserve enough subsequent samples to see candidate state changes.
                    automatic_stages.add("player")
                queued = [(p, json.loads(p.read_text(encoding="utf-8-sig"))) for p in sorted(commands.glob("*.json")) if p.name not in handled]
                if walker:
                    client_rect = W.RECT()
                    win.checked(win.get_rect(hwnd, C.byref(client_rect)))
                    next_walk = walker.step(snapshot, client_rect.right, client_rect.bottom)
                    if next_walk:
                        queued.append((None, next_walk))
                    if walker.finished and walk_finished_at is None:
                        walk_finished_at = time.monotonic()
                        report["walk_status"] = "level_2_observed"
                        write_json(args.output / "entered-layout.json", layout_snapshot(win, process, modules, snapshot["scene"]))
                    if walk_finished_at and time.monotonic() - walk_finished_at > 2:
                        break
                if args.auto_single_player and "single_player" not in automatic_stages:
                    matches = [c for c in snapshot["controls"] if c["type"] == 6 and c.get("text", "").strip().casefold() in ("single player", "单人游戏", "單人遊戲", "單一玩者")]
                    if len(matches) == 1:
                        c = matches[0]
                        queued.append((None, dict(op="click", address=c["address"], text=c["text"])))
                        automatic_stages.add("single_player")
                if args.auto_single_player and args.character and "select_character" not in automatic_stages:
                    cs = snapshot["controls"]
                    select_screen = any(c["type"] == 6 and c.get("text") in ("刪除", "删除", "DELETE CHARACTER") for c in cs)
                    selected = any(c["type"] == 4 and c["y"] < 85 and c.get("lines") == [[args.character]] for c in cs)
                    cards = [c for c in cs if card_has_name(c, args.character)]
                    if select_screen and not selected and len(cards) == 1:
                        c = cards[0]
                        queued.append((None, dict(op="select_character", address=c["address"], name=args.character)))
                        automatic_stages.add("select_character")
                    elif selected:
                        automatic_stages.add("select_character")

                if args.auto_single_player and args.character and "confirm_character" not in automatic_stages:
                    cs = snapshot["controls"]
                    character_selected = any(c["type"] == 4 and c["y"] < 85 and c.get("lines") == [[args.character]] for c in cs)
                    select_screen = any(c["type"] == 6 and c.get("text") in ("刪除", "删除", "DELETE CHARACTER") for c in cs)
                    ok = [c for c in cs if c["type"] == 6 and c.get("text") in ("好", "确定", "確定", "OK") and c.get("virtual_key") == 13]
                    if character_selected and select_screen and len(ok) == 1:
                        c = ok[0]
                        queued.append((None, dict(op="click", address=c["address"], text=c["text"])))
                        automatic_stages.add("confirm_character")
                for path, command in queued:
                    if path:
                        handled.add(path.name)
                    if command.get("op") == "walk" and path is None and walker:
                        x,y=command["x"],command["y"]
                        lparam=x|(y<<16)
                        action=dict(command=command, elapsed_ms=snapshot["elapsed_ms"], delivery="PostMessageW")
                        report["actions"].append(action)
                        win.checked(post(hwnd,0x200,0,lparam))
                        win.checked(post(hwnd,0x201,1,lparam))
                        time.sleep(0.1)
                        win.checked(post(hwnd,0x202,0,lparam))
                        action["posted"]=True
                        write_json(args.output / "progress.json",report)
                        continue
                    if command.get("op") == "stop":
                        report["stop_requested"] = True
                        if walker and not walker.finished:
                            report["walk_status"] = "stopped_before_target_level"
                            report["status"] = "walk_incomplete"
                        return 0 if report["status"] in ("scene_observed", "player_observed") else 1
                    if command.get("op") == "select_character":
                        current = controls(win, process, modules)
                        selected = [c for c in current if c["address"] == command.get("address")
                                    and card_has_name(c, command.get("name", ""))]
                        if len(selected) != 1:
                            raise ValueError("Character card changed or is unavailable")
                        c = selected[0]
                        x, y = c["x"] + c["width"] // 2, c["y"] - c["height"] // 2
                        win.checked(post(hwnd, 0x200, 0, x | (y << 16)))
                        win.checked(post(hwnd, 0x201, 1, x | (y << 16)))
                        time.sleep(0.1)
                        win.checked(post(hwnd, 0x202, 0, x | (y << 16)))
                        continue
                    if command.get("op") != "click":
                        raise ValueError("Only verified control clicks and stop are supported")
                    if command.get("text", "").strip().casefold() not in ("single player", "单人游戏", "單人遊戲", "單一玩者", "好", "确定", "確定", "ok"):
                        raise ValueError("Only single-player entry/confirmation buttons are in scope")
                    # Re-read and match current control before every action.
                    current = controls(win, process, modules)
                    selected = [c for c in current if c["address"] == command.get("address") and c["type"] == 6 and c.get("text") == command.get("text")]
                    if len(selected) != 1 or not (selected[0]["flags"] & 1 and selected[0]["flags"] & 4):
                        raise ValueError("Selected button changed or is disabled")
                    c = selected[0]
                    x, y = c["x"] + c["width"] // 2, c["y"] - c["height"] // 2
                    if not (0 <= x < 800 and 0 <= y < 600):
                        raise ValueError("Control outside expected client coordinates")
                    lparam = x | (y << 16)
                    action = dict(command=command, control=c, x=x, y=y, elapsed_ms=snapshot["elapsed_ms"], delivery="PostMessageW")
                    report["actions"].append(action)
                    # This does not move the system cursor or change the foreground.
                    win.checked(post(hwnd, 0x200, 0, lparam))
                    win.checked(post(hwnd, 0x201, 1, lparam))
                    time.sleep(0.1)
                    win.checked(post(hwnd, 0x202, 0, lparam))
                    action["posted"] = True
                    write_json(args.output / "progress.json", report)
                    time.sleep(0.7)
                time.sleep(0.5)
        if report["status"] == "starting":
            report["status"] = "timeout_without_player"
        if walker and not walker.finished:
            report.update(status="walk_incomplete", walk_status="timeout_before_target_level")
    except Exception as error:
        report.update(status="error", error=str(error))
    finally:
        if args.hidden_run and process and "hwnd" in locals() and win.wait(process, 0) != 0:
            # The legacy client may restore its DirectDraw window after a
            # level transition. Re-hide it before returning control to the
            # broker so the next task remains background-only.
            try:
                api(win.u, "ShowWindowAsync", W.BOOL, W.HWND, C.c_int)(hwnd, 0)
                time.sleep(0.1)
            except Exception as error:
                report["final_hide_error"] = str(error)
        if args.pause_after and process and "hwnd" in locals() and win.wait(process, 0) != 0:
            try:
                win.checked(post(hwnd, 0x1C, 0, 0))
                time.sleep(0.5)
                # Pausing can make the game minimize itself. Hide it again;
                # this operation never activates or restores it.
                api(win.u, "ShowWindowAsync", W.BOOL, W.HWND, C.c_int)(hwnd, 0)
                time.sleep(0.1)
                report["pause_after"] = dict(message_posted=True,
                    graphics_pause=win.u32(process, modules["d2gfx.dll"]["base"] + 0x1D84C),
                    graphics_state=win.u32(process, modules["d2gfx.dll"]["base"] + 0x1D850),
                    visible=bool(win.is_visible(hwnd)), minimized=bool(iconic(hwnd)))
            except Exception as error:
                report["pause_after_error"] = str(error)
        if process:
            report["target_alive_after"] = win.wait(process, 0) != 0
            win.close(process)
        report.update(duration_ms=round(1000 * (time.monotonic() - start)),
                      foreground_after=win.foreground(), screen_after=[win.system_metric(0), win.system_metric(1)])
        if "sample_count" in locals():
            report.update(samples=sample_count, distinct_animation_values=len(animation_values),
                          distinct_seed_values=len(seed_values),
                          sampled_window_states=[dict(minimized=m, visible=v) for m, v in sorted(window_states)],
                          client_state_changes_observed=len(animation_values) > 1 and len(seed_values) > 1,
                          server_tick_progress="not_observed")
        write_json(args.output / "summary.json", report)
    return 0 if report["status"] in ("scene_observed", "player_observed") else 1


if __name__ == "__main__":
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--pid", type=int, required=True)
    p.add_argument("--home", type=Path, required=True)
    p.add_argument("--output", type=Path, required=True)
    p.add_argument("--seconds", type=float, default=60)
    p.add_argument("--auto-single-player", action="store_true")
    p.add_argument("--character", help="Only confirm the already-selected character with this exact name")
    p.add_argument("--hidden-run", action="store_true", help="Experiment: hide then remove WS_MINIMIZE, without showing/activating")
    p.add_argument("--visible-run", action="store_true", help="Allow a normal visible window; input is still window-targeted PostMessage")
    p.add_argument("--walk-to-level", type=int, choices=[2], help="Walk only the verified Normal Act I town east exit; stop on level 2")
    p.add_argument("--no-walk", action="store_true", help="Stay in the selected character's starting town")
    p.add_argument("--capture-layouts", action="store_true")
    p.add_argument("--capture-controls", action="store_true",
                   help="Persist the current read-only D2Win control chain")
    p.add_argument("--pause-after", action="store_true", help="Ask the game to pause after this bounded experiment, then keep it hidden")
    args = p.parse_args()
    if not 0 < args.seconds <= 300:
        p.error("seconds must be (0, 300]")
    if args.hidden_run and args.visible_run:
        p.error("--hidden-run and --visible-run are mutually exclusive")
    if args.walk_to_level and args.no_walk:
        p.error("--walk-to-level and --no-walk are mutually exclusive")
    if args.walk_to_level and not (args.hidden_run or args.visible_run):
        p.error("--walk-to-level requires --hidden-run or --visible-run")
    raise SystemExit(run(args))
