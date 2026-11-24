"""Fixed offline Arctic Blast experiment on an unshown Windows desktop.

The elevated broker starts this through its repository job registry. No
SwitchDesktop, SetForegroundWindow, global mouse/keyboard input or display-mode
changes. The worker and game inherit the private desktop. Existing games are
never reused or closed. Only a separately named CXArctic test save is installed.
"""
import argparse
import ctypes as C
from ctypes import wintypes as W
import hashlib
import json
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import time
import traceback
import uuid

from observer import Win32, STARTUPINFO, PROCESS_INFORMATION, api, digest
from orchestrator import LOADER_NAME, existing_loader_pids, wait_loader_ready

ROOT = Path(__file__).resolve().parent
LOADER_HASH = "602d3c638d782df6ad3edf8efbe07bbddd771f625e9491056084b83484848958"


def write_json(path, value):
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf8")
    tmp.replace(path)


def desktop_name(win, handle):
    size = W.DWORD()
    buffer = C.create_unicode_buffer(256)
    win.checked(api(win.u, "GetUserObjectInformationW", W.BOOL, W.HANDLE, C.c_int,
                    C.c_void_p, W.DWORD, C.POINTER(W.DWORD))(
        handle, 2, buffer, C.sizeof(buffer), C.byref(size)))
    return buffer.value


def input_desktop_name(win):
    handle = win.checked(api(win.u, "OpenInputDesktop", W.HANDLE, W.DWORD, W.BOOL, W.DWORD)(0, False, 1))
    try:
        return desktop_name(win, handle)
    finally:
        win.close_desktop(handle)


def run_private(home, output, birth_release=False, turn_probe=False, storm_batch=False):
    output.mkdir(parents=True, exist_ok=False)
    win = Win32()
    private = None
    child = None
    report = dict(status="starting", background_probe="druid-storm" if storm_batch else "arctic-blast", batches=[],
                  switched_desktop=False, scope="private desktop original runtime; no pixel comparison")
    try:
        if existing_loader_pids():
            raise ValueError("An existing original game is running; refusing to reuse or interrupt it")
        if digest(home / LOADER_NAME) != LOADER_HASH:
            raise ValueError("Original loader hash mismatch")
        report["input_desktop_before"] = input_desktop_name(win)
        report["screen_before"] = [win.system_metric(0), win.system_metric(1)]
        report["foreground_before"] = win.foreground()
        name = ("riiablo_storm_" if storm_batch else "riiablo_arctic_") + uuid.uuid4().hex[:12]
        private = win.checked(win.create_desktop(name, None, None, 0, 0x01FF, None))
        report["private_desktop"] = name
        startup = STARTUPINFO()
        startup.cb = C.sizeof(startup)
        startup.lpDesktop = name
        worker_out = output / "worker"
        startup.dwFlags = 1
        startup.wShowWindow = 0
        # CPython's subprocess.STARTUPINFO does not marshal lpDesktop. Use
        # the complete Win32 structure so the child really inherits this
        # desktop before Python imports any UI library.
        arguments = [
            sys.executable, str(ROOT / "druid_storm_background.py" if storm_batch else Path(__file__).resolve()), "--worker",
            "--home", str(home), "--output", str(worker_out)]
        if birth_release: arguments.append('--birth-release')
        if turn_probe: arguments.append('--turn-probe')

        command = C.create_unicode_buffer(subprocess.list2cmdline(arguments))
        info = PROCESS_INFORMATION()
        win.checked(win.create_process(sys.executable, command, None, None, False,
                    subprocess.CREATE_NO_WINDOW, None, str(ROOT), C.byref(startup), C.byref(info)))
        child = info.hProcess
        win.close(info.hThread)
        report.update(status="running", worker_pid=info.dwProcessId)
        write_json(output / "orchestrator.json", report)
        started = time.monotonic()
        checks = 0
        while win.wait(child, 0) != 0:
            if time.monotonic() - started > 780:
                # Never kill a worker/helper that may own a live debugger.
                raise TimeoutError("Worker deadline; left alive for explicit cleanup inspection")
            if input_desktop_name(win) == name:
                raise RuntimeError("Private desktop unexpectedly became input desktop")
            checks += 1
            time.sleep(0.5)
        code = W.DWORD()
        win.checked(win.exit_code(child, C.byref(code)))
        report["worker_exit_code"] = code.value
        report["private_desktop_not_input_checks"] = checks
        worker_report = json.loads((worker_out / "summary.json").read_text(encoding="utf8"))
        report["worker"] = worker_report
        report["status"] = "passed" if code.value == 0 else "failed"
        if worker_report.get("cleanup_attention_required"):
            report["session_left_running_for_cleanup"] = True
    except Exception as error:
        report.update(status="failed", error=str(error), traceback=traceback.format_exc())
        if child and win.wait(child, 0) != 0:
            report["session_left_running_for_cleanup"] = True
    finally:
        report["input_desktop_after"] = input_desktop_name(win)
        report["screen_after"] = [win.system_metric(0), win.system_metric(1)]
        report["foreground_after"] = win.foreground()
        if private and (child is None or win.wait(child, 0) == 0):
            report["desktop_handle_closed"] = bool(win.close_desktop(private))
        if child:
            win.close(child)
        write_json(output / "orchestrator.json", report)
    return 0 if report["status"] == "passed" else 1


def prepare_fixture(home, output):
    import winreg
    with winreg.OpenKey(winreg.HKEY_CURRENT_USER, r"Software\Blizzard Entertainment\Diablo II", 0,
                        winreg.KEY_READ | winreg.KEY_WOW64_32KEY) as key:
        save = Path(winreg.QueryValueEx(key, "Save Path")[0])
    from prepare_arctic_blast_fixture import build
    data, fixture, source = build(save)
    name, skill = "CXArctic", 230
    class_id, first_skill = (5, 221)
    target = save / (name + ".d2s")
    if target.exists():
        existing = target.read_bytes()
        _, skill_offset = fixture.read_stats(existing)
        learned = existing[skill_offset+2:skill_offset+32]
        if (fixture.checksum(existing) != struct.unpack_from("<I", existing, 12)[0]
                or existing[20:36].split(b"\0")[0] != name.encode("ascii") or existing[40] != class_id
                or existing[36] & 0x0C or learned != bytes(skill-first_skill)+b"\x01"+bytes(29-skill+first_skill)
                or struct.unpack_from("<I", existing, 171)[0] != 127897359
                or struct.unpack_from("<I", existing, 124)[0] != skill
                or existing[skill_offset+32:skill_offset+36] != b"JM\0\0"):
            raise ValueError("Existing " + name + " is not the fixed alive skill fixture; refusing overwrite")
        data = bytearray(existing)  # Reuse the game's own saved progress.
    if not target.exists():
        with target.open("xb") as stream:
            stream.write(data)
    (output / (name + "-input.d2s")).write_bytes(data)
    return dict(name=name, path=str(target), sha256=hashlib.sha256(data).hexdigest(),
                class_id=class_id, skill_id=skill, skill_level=1,
                map_seed=struct.unpack_from("<I", data, 171)[0],
                source_sha256=hashlib.sha256(source).hexdigest())


def worker(home, output, birth_release=False, turn_probe=False):
    output.mkdir(parents=True, exist_ok=False)
    win = Win32()
    game = None
    process = None
    report = dict(status="starting", cleanup_attention_required=False)
    helper = None
    try:
        current = win.get_thread_desktop(api(win.k, "GetCurrentThreadId", W.DWORD)())
        report["desktop"] = desktop_name(win, current)
        if not report["desktop"].startswith("riiablo_arctic_") or report["desktop"] == input_desktop_name(win):
            raise ValueError("Worker must run on a non-input private desktop")
        if existing_loader_pids():
            raise ValueError("Another original game appeared before launch")
        provenance = output / "provenance"
        provenance.mkdir()
        for filename in ("arctic_blast_passive.py", "observer.py", "scene_reader.py", "prepare_arctic_blast_fixture.py", "test-character.py", Path(__file__).name):
            shutil.copy2(ROOT / filename, provenance / filename)
        preflight = subprocess.run([sys.executable, str(provenance / "arctic_blast_passive.py"), "--help"],
            cwd=str(ROOT), creationflags=subprocess.CREATE_NO_WINDOW, capture_output=True)
        if preflight.returncode:
            raise RuntimeError("Collector import preflight failed: " + preflight.stderr.decode("utf8", errors="replace"))
        report["fixture"] = prepare_fixture(home, output)
        game = subprocess.Popen([str(home / LOADER_NAME), "-w", "-res800"], cwd=str(home))
        report.update(status="loading", pid=game.pid)
        write_json(output / "summary.json", report)
        report["readiness"] = wait_loader_ready(game.pid, 180)
        entry = output / "entry"
        with (output / "entry-stdout.log").open("w", encoding="utf8") as out, (output / "entry-stderr.log").open("w", encoding="utf8") as err:
            entry_result = subprocess.run([sys.executable, str(ROOT / "menu-driver.py"), "--pid", str(game.pid),
                "--home", str(home), "--output", str(entry), "--seconds", "240", "--visible-run",
                "--auto-single-player", "--character", report["fixture"]["name"], "--walk-to-level", "2", "--capture-controls"],
                cwd=str(ROOT), creationflags=subprocess.CREATE_NO_WINDOW, stdout=out, stderr=err)
        report["entry_exit_code"] = entry_result.returncode
        report["entry"] = json.loads((entry / "summary.json").read_text(encoding="utf8"))
        if entry_result.returncode or report["entry"].get("scene", {}).get("level_id") != 2:
            raise RuntimeError("Private-desktop automatic entry did not reach Blood Moor")
        report["status"] = "ready_for_capture"
        write_json(output / "summary.json", report)
        report["captures"] = []
        for hold_ms in ((15,) if turn_probe else (3,15) if birth_release else (640,1600)):
            capture_name = ("birth-" if birth_release else "hold-") + str(hold_ms)
            collector = provenance / "arctic_blast_passive.py"
            arguments = [sys.executable, str(collector), "--pid", str(game.pid), "--home", str(home),
                "--output", str(output / capture_name), "--release-after-births" if birth_release else "--hold-ms", str(hold_ms)]
            if turn_probe: arguments += ['--turn-after-births','3']
            with (output / (capture_name + "-stdout.log")).open("w", encoding="utf8") as out, (output / (capture_name + "-stderr.log")).open("w", encoding="utf8") as err:
                helper = subprocess.Popen(arguments, cwd=str(ROOT), creationflags=subprocess.CREATE_NO_WINDOW, stdout=out, stderr=err)
                report.update(status="capturing", helper_pid=helper.pid,
                    hold_ms=None if birth_release else hold_ms,
                    release_after_births=hold_ms if birth_release else None, cleanup_attention_required=True)
                write_json(output / "summary.json", report)
                helper.wait(timeout=30)
            native_report = json.loads((output / capture_name / "summary.json").read_text(encoding="utf8"))
            report["cleanup_attention_required"] = not native_report.get("cleanup_confirmed", False)
            report["captures"].append(dict(path=capture_name, hold_ms=None if birth_release else hold_ms,
                release_after_births=hold_ms if birth_release else None,
                collector_sha256=digest(collector), status=native_report["status"], native=native_report))
            write_json(output / "summary.json", report)
            if helper.returncode or not native_report.get("cleanup_confirmed"):
                raise RuntimeError("Original Arctic Blast capture failed; inspect native report")
        if not any(c["status"] == "passed" for c in report["captures"]):
            raise RuntimeError("No requested direction could be captured in the current room")
        report["status"] = "passed"
    except Exception as error:
        report.update(status="failed", error=str(error), traceback=traceback.format_exc())
    finally:
        # The worker owns only this new private-desktop process. Prefer a
        # normal close; never close it with an unresolved debug attachment.
        if game and game.poll() is None and not report["cleanup_attention_required"] and (helper is None or helper.poll() is not None):
            for window in win.windows(current, game.pid):
                if window["class"] == "Diablo II":
                    win.post(window["hwnd"], 0x10, 0, 0)
            try:
                game.wait(timeout=8)
            except subprocess.TimeoutExpired:
                game.terminate()
                game.wait(timeout=5)
            report["owned_game_closed"] = True
        if process:
            win.close(process)
        write_json(output / "summary.json", report)
    return 0 if report["status"] == "passed" else 1


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--worker", action="store_true", required=True)
    parser.add_argument("--home", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument('--birth-release',action='store_true')
    parser.add_argument('--turn-probe',action='store_true')
    args = parser.parse_args()
    raise SystemExit(worker(args.home, args.output,args.birth_release,args.turn_probe))
