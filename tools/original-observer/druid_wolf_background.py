"""Background private-desktop Werewolf client capture."""
import argparse
import ctypes as C
from ctypes import wintypes as W
import json
from pathlib import Path
import subprocess
import sys
import time
import traceback
import uuid

from observer import Win32, STARTUPINFO, PROCESS_INFORMATION, api
from orchestrator import LOADER_NAME, existing_loader_pids, wait_loader_ready
from arctic_blast_background import desktop_name, input_desktop_name, write_json
from prepare_druid_batch_fixture import prepare

ROOT = Path(__file__).resolve().parent


def run_private(home, output, wolf_level=1, seconds=8):
    output.mkdir(parents=True, exist_ok=False)
    win = Win32(); private = child = None
    report = dict(status="starting", background_probe="druid-wolf", switched_desktop=False)
    try:
        if existing_loader_pids(): raise RuntimeError("original game already running")
        name = "riiablo_wolf_" + uuid.uuid4().hex[:12]
        private = win.checked(win.create_desktop(name, None, None, 0, 0x01FF, None))
        startup = STARTUPINFO(); startup.cb = C.sizeof(startup); startup.lpDesktop = name
        startup.dwFlags = 1; startup.wShowWindow = 0
        args = [sys.executable, str(Path(__file__).resolve()), "--worker",
                "--home", str(home), "--output", str(output / "worker"),
                "--wolf-level", str(wolf_level), "--seconds", str(seconds)]
        command = C.create_unicode_buffer(subprocess.list2cmdline(args)); info = PROCESS_INFORMATION()
        win.checked(win.create_process(sys.executable, command, None, None, False,
            subprocess.CREATE_NO_WINDOW, None, str(ROOT), C.byref(startup), C.byref(info)))
        child = info.hProcess; win.close(info.hThread); report["worker_pid"] = info.dwProcessId
        write_json(output / "orchestrator.json", report)
        start = time.monotonic()
        while win.wait(child, 0) != 0:
            if time.monotonic() - start > 400: raise TimeoutError("wolf worker timeout")
            if input_desktop_name(win) == name: raise RuntimeError("private desktop became input desktop")
            time.sleep(.5)
        code = W.DWORD(); win.checked(win.exit_code(child, C.byref(code)))
        report["worker_exit_code"] = code.value
        report["worker"] = json.loads((output / "worker" / "summary.json").read_text(encoding="utf8"))
        report["status"] = "passed" if code.value == 0 else "failed"
    except Exception as error:
        report.update(status="failed", error=str(error), traceback=traceback.format_exc())
    finally:
        report["input_desktop_after"] = input_desktop_name(win)
        if private and (child is None or win.wait(child, 0) == 0): win.close_desktop(private)
        if child: win.close(child)
        write_json(output / "orchestrator.json", report)
    return 0 if report["status"] == "passed" else 1


def worker(home, output, wolf_level=1, seconds=8):
    output.mkdir(parents=True, exist_ok=False)
    win = Win32(); game = helper = None
    report = dict(status="starting", cleanup_attention_required=False)
    try:
        desktop = win.get_thread_desktop(api(win.k, "GetCurrentThreadId", W.DWORD)())
        report["desktop"] = desktop_name(win, desktop)
        assert report["desktop"].startswith("riiablo_wolf_")
        assert report["desktop"] != input_desktop_name(win)
        assert not existing_loader_pids()
        report["fixture"] = prepare(home, output, wolf=True, wolf_level=wolf_level)
        game = subprocess.Popen([str(home / LOADER_NAME), "-w", "-res800"], cwd=str(home),
                                creationflags=subprocess.CREATE_NO_WINDOW)
        report["pid"] = game.pid; report["readiness"] = wait_loader_ready(game.pid, 180)
        entry = output / "entry"
        entry_result = subprocess.run([sys.executable, str(ROOT / "menu-driver.py"), "--pid", str(game.pid),
            "--home", str(home), "--output", str(entry), "--seconds", "240", "--visible-run",
            "--auto-single-player", "--character", "CXDruWolf", "--walk-to-level", "2",
            "--capture-controls"], cwd=str(ROOT), creationflags=subprocess.CREATE_NO_WINDOW,
            timeout=260)
        report["entry"] = json.loads((entry / "summary.json").read_text(encoding="utf8"))
        if entry_result.returncode or report["entry"].get("scene", {}).get("level_id") != 2:
            raise RuntimeError("wolf entry did not reach Blood Moor")
        capture = output / "capture"
        helper = subprocess.Popen([sys.executable, str(ROOT / "druid_wolf_passive.py"), "--pid", str(game.pid),
            "--home", str(home), "--output", str(capture), "--seconds", str(seconds),
            "--expected-level", str(wolf_level)], cwd=str(ROOT), creationflags=subprocess.CREATE_NO_WINDOW)
        helper.wait(timeout=seconds + 20)
        report["capture"] = json.loads((capture / "summary.json").read_text(encoding="utf8"))
        if report["capture"]["status"] != "passed": raise RuntimeError("wolf client capture failed")
        if not report["capture"].get("attack_mode_observed"):
            raise RuntimeError("wolf attack mode was not observed; movement is not counted as attack evidence")
        report["status"] = "passed"
    except Exception as error:
        report.update(status="failed", error=str(error), traceback=traceback.format_exc())
    finally:
        if helper and helper.poll() is None: helper.terminate(); helper.wait()
        if game and game.poll() is None:
            try: game.terminate(); game.wait(timeout=8)
            except subprocess.TimeoutExpired: game.kill(); game.wait()
        report["owned_game_closed"] = game is None or game.poll() is not None
        write_json(output / "summary.json", report)
    return 0 if report["status"] == "passed" else 1


if __name__ == "__main__":
    parser = argparse.ArgumentParser(); parser.add_argument("--worker", action="store_true", required=True)
    parser.add_argument("--home", type=Path, required=True); parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--wolf-level", type=int, default=1); parser.add_argument("--seconds", type=float, default=8)
    a = parser.parse_args()
    raise SystemExit(worker(a.home, a.output, a.wolf_level, a.seconds) if a.worker else run_private(a.home, a.output, a.wolf_level, a.seconds))
