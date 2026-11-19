"""Temporary elevated, file-command session for one background skill fixture.

Finite operations only: snapshots, menu navigation, verified menu button clicks,
test-character selection, town exit, a version-pinned cast executable, captures,
and stop. No shell command endpoint, global input or foreground activation.
"""
import argparse
import ctypes as C
from ctypes import wintypes as W
import importlib.util
import json
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import time
import winreg

from observer import Win32, api, digest, player_snapshot

ROOT = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("menu", ROOT / "menu-driver.py")
menu = importlib.util.module_from_spec(spec)
spec.loader.exec_module(menu)


def capture(win, hwnd, path):
    """Capture only the target window via PrintWindow; output is an untrusted probe."""
    gdi = C.WinDLL("gdi32", use_last_error=True)
    rect = W.RECT()
    win.checked(win.get_rect(hwnd, C.byref(rect)))
    width, height = rect.right, rect.bottom
    if not (320 <= width <= 1024 and 200 <= height <= 768):
        raise ValueError("Unexpected client size")
    dc = api(win.u, "GetDC", W.HDC, W.HWND)(hwnd)
    mem = api(gdi, "CreateCompatibleDC", W.HDC, W.HDC)(dc)
    bitmap = api(gdi, "CreateCompatibleBitmap", W.HANDLE, W.HDC, C.c_int, C.c_int)(dc, width, height)
    select = api(gdi, "SelectObject", W.HANDLE, W.HDC, W.HANDLE)
    old = select(mem, bitmap)
    try:
        ok = api(win.u, "PrintWindow", W.BOOL, W.HWND, W.HDC, W.UINT)(hwnd, mem, 3)
        select(mem, old)
        size = width * height * 4
        info = C.create_string_buffer(struct.pack("<IiiHHIIiiII", 40, width, -height, 1, 32, 0, size, 0, 0, 0, 0))
        pixels = C.create_string_buffer(size)
        got = api(gdi, "GetDIBits", C.c_int, W.HDC, W.HANDLE, W.UINT, W.UINT, C.c_void_p, C.c_void_p, W.UINT)(
            mem, bitmap, 0, height, pixels, info, 0)
        win.checked(got == height)
        raw = pixels.raw
        with path.open("wb") as stream:
            stream.write(struct.pack("<2sIHHI", b"BM", 54+size, 0, 0, 54))
            stream.write(info.raw[:40])
            stream.write(raw)
        return dict(api="PrintWindow(client/full-content)", returned=bool(ok), width=width, height=height,
                    distinct_byte_values=len(set(raw)), nonzero_bytes=sum(v != 0 for v in raw),
                    file=str(path), status="probe_requires_visual_validation")
    finally:
        select(mem, old)
        api(gdi, "DeleteObject", W.BOOL, W.HANDLE)(bitmap)
        api(gdi, "DeleteDC", W.BOOL, W.HDC)(mem)
        api(win.u, "ReleaseDC", C.c_int, W.HWND, W.HDC)(hwnd, dc)


def run(args):
    args.output.mkdir(parents=True, exist_ok=False)
    (args.output / "commands").mkdir()
    win = Win32()
    report = dict(status="starting", pid=args.pid, actions=[], foreground_before=win.foreground(),
                  screen_before=[win.system_metric(0), win.system_metric(1)])
    process, hwnd = None, None
    post = api(win.u, "PostMessageW", W.BOOL, W.HWND, W.UINT, C.c_size_t, C.c_ssize_t)
    hide = api(win.u, "ShowWindowAsync", W.BOOL, W.HWND, C.c_int)
    desktop = win.get_thread_desktop(api(win.k, "GetCurrentThreadId", W.DWORD)())

    def save():
        menu.write_json(args.output / "summary.json", report)

    def snapshot():
        nonlocal hwnd
        windows = [w for w in win.windows(desktop, args.pid) if w["class"] == "Diablo II"]
        if len(windows) != 1:
            raise ValueError("Expected one target window")
        hwnd = windows[0]["hwnd"]
        if win.foreground() == hwnd or win.is_visible(hwnd):
            raise ValueError("Target must remain hidden and outside foreground")
        modules = win.modules(args.pid)
        state = dict(time=time.time(), windows=windows, foreground=win.foreground(),
                     controls=menu.controls(win, process, modules))
        try:
            state["player"] = player_snapshot(win, process, modules)
        except (OSError, ValueError, KeyError) as error:
            state["player"] = dict(status="unavailable", error=str(error))
        menu.write_json(args.output / "latest.json", state)
        return state, modules

    def click(x, y):
        rect = W.RECT()
        win.checked(win.get_rect(hwnd, C.byref(rect)))
        if not (0 <= x < rect.right and 0 <= y < rect.bottom):
            raise ValueError("Click outside target client")
        pos = x | (y << 16)
        for message, wp in ((0x200, 0), (0x201, 1), (0x202, 0)):
            win.checked(post(hwnd, message, wp, pos))
            time.sleep(0.08)

    try:
        if not C.windll.shell32.IsUserAnAdmin():
            raise ValueError("This session requires elevation")
        process = win.checked(api(win.k, "OpenProcess", W.HANDLE, W.DWORD, W.BOOL, W.DWORD)(0x400|0x10|0x100000, False, args.pid))
        # Existing driver checks exact process/module identity and backs up saves.
        prepared = subprocess.run([sys.executable, str(ROOT / "menu-driver.py"), "--pid", str(args.pid),
            "--home", str(args.home), "--output", str(args.output / "prepare"), "--hidden-run", "--seconds", "1"],
            capture_output=True, creationflags=subprocess.CREATE_NO_WINDOW)
        if prepared.returncode:
            raise ValueError("Hidden preparation failed: " + prepared.stderr.decode("utf8", "replace"))
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, r"Software\Blizzard Entertainment\Diablo II", 0,
                            winreg.KEY_READ | winreg.KEY_WOW64_32KEY) as key:
            save_dir = Path(winreg.QueryValueEx(key, "Save Path")[0])
        source = ROOT / "runs/CXSorc.d2s"
        destination = save_dir / "CXSorc.d2s"
        with destination.open("xb") as stream:
            stream.write(source.read_bytes())
        report["fixture"] = dict(path=str(destination), sha256=digest(destination), source=str(source))
        handled = set()
        deadline = time.monotonic() + args.seconds
        report["status"] = "ready"
        save()
        while time.monotonic() < deadline:
            if win.wait(process, 0) == 0:
                raise ValueError("Target exited")
            state, modules = snapshot()
            for path in sorted((args.output / "commands").glob("*.json")):
                if path.name in handled:
                    continue
                handled.add(path.name)
                command = json.loads(path.read_text(encoding="utf-8-sig"))
                action = dict(command=command, command_file=path.name, started=time.time(), status="starting")
                report["actions"].append(action)
                save()
                try:
                    op = command["op"]
                    if op == "stop":
                        report["status"] = "stopped"
                        return 0
                    if op == "snapshot":
                        pass
                    elif op == "keys":
                        keys = command["keys"]
                        allowed = {"ESC":27, "ENTER":13, "UP":38, "DOWN":40, "HOME":36, "END":35}
                        if not 1 <= len(keys) <= 6 or any(k not in allowed for k in keys):
                            raise ValueError("Unsupported navigation keys")
                        if "ENTER" in keys and state["player"].get("status") != "player":
                            raise ValueError("Confirm character using the exact-name operation")
                        for key in keys:
                            win.checked(post(hwnd, 0x100, allowed[key], 1))
                            time.sleep(0.08)
                            win.checked(post(hwnd, 0x101, allowed[key], 0xC0000001))
                            time.sleep(0.3)
                    elif op == "button":
                        text = command["text"]
                        if text.casefold() not in ("單一玩者", "单人游戏", "單人遊戲", "single player", "好", "确定", "確定", "ok"):
                            raise ValueError("Only local entry/confirmation buttons are supported")
                        if text.casefold() in ("好", "确定", "確定", "ok"):
                            if not any(c["type"] == 4 and c["y"] < 85 and c.get("lines") == [["CXSorc"]] for c in state["controls"]):
                                raise ValueError("Selected character is not the exact CXSorc fixture")
                        buttons = [c for c in state["controls"] if c["type"] == 6 and c.get("text") == text and c["flags"] & 1 and c["flags"] & 4]
                        if len(buttons) != 1:
                            raise ValueError("Expected one verified enabled button")
                        c = buttons[0]
                        click(c["x"] + c["width"]//2, c["y"] - c["height"]//2)
                    elif op == "select_test":
                        labels = [c for c in state["controls"] if c["type"] == 4 and any("CXSorc" in line for line in c.get("lines", []))]
                        if len(labels) != 1 or labels[0]["y"] < 85:
                            raise ValueError("No unique non-header CXSorc card label")
                        c = labels[0]
                        click(c["x"] + min(40, c["width"]//2), c["y"] - min(20, c["height"]//2))
                    elif op == "walk":
                        if state["player"].get("name") != "CXSorc":
                            raise ValueError("Walking is limited to the test character")
                        result = subprocess.run([sys.executable, str(ROOT / "menu-driver.py"), "--pid", str(args.pid),
                            "--home", str(args.home), "--output", str(args.output / path.stem), "--hidden-run",
                            "--seconds", "100", "--walk-to-level", "2", "--capture-layouts"],
                            capture_output=True, creationflags=subprocess.CREATE_NO_WINDOW)
                        action["exit_code"] = result.returncode
                        if result.returncode:
                            raise ValueError("Background walking failed")
                    elif op == "capture":
                        action["capture"] = capture(win, hwnd, args.output / (path.stem + ".bmp"))
                    elif op == "native_cast":
                        if state["player"].get("name") != "CXSorc":
                            raise ValueError("Native casting is limited to CXSorc")
                        executable = ROOT / "native-harness/build/d2game_cast.exe"
                        action["exe_sha256"] = digest(executable)
                        target = args.output / path.stem
                        child = subprocess.Popen([str(executable), str(args.pid), str(args.home), str(target)],
                            stdout=(args.output / (path.stem + ".stdout")).open("w"),
                            stderr=(args.output / (path.stem + ".stderr")).open("w"), creationflags=subprocess.CREATE_NO_WINDOW)
                        action["native_pid"] = child.pid
                        try:
                            action["exit_code"] = child.wait(timeout=120)
                        except subprocess.TimeoutExpired:
                            # Never send more commands or terminate a debugger
                            # whose restored bytes/context are still unknown.
                            report["status"] = "native_cleanup_attention_required"
                            action.update(status="attention", error="Native helper exceeded deadline; left alive for cleanup")
                            save()
                            while child.poll() is None:
                                time.sleep(0.5)
                            action["exit_code"] = child.returncode
                        result = json.loads((target / "summary.json").read_text(encoding="utf8"))
                        action["native_result"] = result
                        if not result.get("cleanup_confirmed"):
                            report["status"] = "native_cleanup_attention_required"
                            action.update(status="attention", error="Native cleanup is unconfirmed; no further commands")
                            save()
                            return 1
                    else:
                        raise ValueError("Unsupported finite operation")
                    time.sleep(0.4)
                    current, _ = snapshot()
                    menu.write_json(args.output / (path.stem + "-after.json"), current)
                    action["status"] = "completed"
                except Exception as error:
                    action.update(status="failed", error=str(error))
                save()
            time.sleep(0.5)
        report["status"] = "expired"
    except Exception as error:
        report.update(status="failed", error=str(error))
    finally:
        if hwnd:
            post(hwnd, 0x1C, 0, 0)
            time.sleep(0.3)
            hide(hwnd, 0)
            for _ in range(20):
                if not win.is_visible(hwnd):
                    break
                time.sleep(0.05)  # ShowWindowAsync completes on the target thread
            report["window_hidden_after"] = not bool(win.is_visible(hwnd))
        if process:
            report["target_alive_after"] = win.wait(process, 0) != 0
            win.close(process)
        report.update(foreground_after=win.foreground(), screen_after=[win.system_metric(0), win.system_metric(1)])
        save()
    return 0 if report["status"] in ("stopped", "expired") else 1


if __name__ == "__main__":
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--pid", type=int, required=True)
    p.add_argument("--home", type=Path, required=True)
    p.add_argument("--output", type=Path, required=True)
    p.add_argument("--seconds", type=int, default=1800)
    a = p.parse_args()
    if not 1 <= a.seconds <= 1800:
        p.error("Session limit: 1..1800 seconds")
    raise SystemExit(run(a))
