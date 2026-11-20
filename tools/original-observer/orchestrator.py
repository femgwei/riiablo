"""Elevated, allow-listed original-vs-riiablo test orchestrator.

The user approves elevation once when starting this program. It launches only
the pinned Diablo II loader, the repository's bounded menu driver, the bounded
process session, and explicitly allow-listed native collectors. It is not a
general shell or arbitrary command runner.
"""
import argparse
import ctypes
import json
import os
import shutil
from pathlib import Path
import subprocess
import sys
import time
from collector_specs import COLLECTORS, validate_collector
from compare_barbarian_formulas import compare as compare_barbarian

ROOT = Path(__file__).resolve().parent
PYTHON = Path(sys.executable).resolve()
LOADER_NAME = "D2v110各版本免CD全功能启动.exe"
ALLOWED_COLLECTORS = {name: validate_collector(name, {}) for name in COLLECTORS}


class PROCESSENTRY32W(ctypes.Structure):
    _fields_ = [("dwSize", ctypes.c_ulong), ("cntUsage", ctypes.c_ulong),
                ("th32ProcessID", ctypes.c_ulong), ("th32DefaultHeapID", ctypes.c_size_t),
                ("th32ModuleID", ctypes.c_ulong), ("cntThreads", ctypes.c_ulong),
                ("th32ParentProcessID", ctypes.c_ulong), ("pcPriClassBase", ctypes.c_long),
                ("dwFlags", ctypes.c_ulong), ("szExeFile", ctypes.c_wchar * 260)]


def existing_loader_pids():
    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    snapshot = kernel.CreateToolhelp32Snapshot
    snapshot.restype = ctypes.c_void_p
    snapshot.argtypes = [ctypes.c_ulong, ctypes.c_ulong]
    close = kernel.CloseHandle
    close.argtypes = [ctypes.c_void_p]
    first = kernel.Process32FirstW
    first.restype = ctypes.c_bool
    first.argtypes = [ctypes.c_void_p, ctypes.POINTER(PROCESSENTRY32W)]
    nxt = kernel.Process32NextW
    nxt.restype = ctypes.c_bool
    nxt.argtypes = [ctypes.c_void_p, ctypes.POINTER(PROCESSENTRY32W)]
    handle = snapshot(0x2, 0)
    if handle == ctypes.c_void_p(-1).value:
        raise ctypes.WinError(ctypes.get_last_error())
    result = []
    try:
        item = PROCESSENTRY32W()
        item.dwSize = ctypes.sizeof(item)
        ok = first(handle, ctypes.byref(item))
        while ok:
            if item.szExeFile.casefold() == LOADER_NAME.casefold():
                result.append(int(item.th32ProcessID))
            ok = nxt(handle, ctypes.byref(item))
    finally:
        close(handle)
    return result


def atomic_json(path, value):
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(json.dumps(value, indent=2, ensure_ascii=False), encoding="utf8")
    tmp.replace(path)


def is_admin():
    return bool(ctypes.windll.shell32.IsUserAnAdmin())


def read_json(path):
    return json.loads(path.read_text(encoding="utf8"))


def wait_file(path, timeout):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if path.exists():
            return
        time.sleep(0.25)
    raise TimeoutError("Timed out waiting for " + str(path))


def wait_summary(path, accepted, timeout):
    deadline = time.monotonic() + timeout
    last = None
    while time.monotonic() < deadline:
        if path.exists():
            try:
                last = read_json(path)
            except (OSError, json.JSONDecodeError):
                last = None
            if last and last.get("status") in accepted:
                return last
        time.sleep(0.25)
    diagnostic = ({"status": last.get("status"), "actions": [
        {k: action.get(k) for k in ("command_file", "status", "error")}
        for action in last.get("actions", [])]} if last else None)
    raise TimeoutError("Timed out waiting for session summary: " + str(path) +
                       (" last=" + str(diagnostic) if diagnostic else ""))


def wait_loader_ready(pid, timeout=180):
    """Wait until the pinned loader has created the real game surface.

    The Onlyer loader spends a long time in its own initialization window before
    D2Client/D2Game are mapped.  A fixed sleep races that transition and makes
    menu-driver report a false failure.  Polling is read-only and gives the
    caller a useful diagnostic when the loader really did not finish.
    """
    from observer import Win32
    win = Win32()
    required = {"d2client.dll", "d2game.dll", "d2win.dll", "d2gfx.dll"}
    deadline = time.monotonic() + timeout
    last = {}
    while time.monotonic() < deadline:
        try:
            modules = win.modules(pid)
            desktop = win.get_thread_desktop(ctypes.windll.kernel32.GetCurrentThreadId())
            windows = win.windows(desktop, pid)
            classes = sorted({item.get("class", "") for item in windows})
            last = {"modules": sorted(modules), "windows": windows, "classes": classes}
            if required.issubset(modules) and any(item.get("class") == "Diablo II" for item in windows):
                return last
        except (OSError, ValueError):
            pass
        time.sleep(0.5)
    raise TimeoutError("Timed out waiting for loader readiness: " + json.dumps(last, ensure_ascii=False))


def write_command(directory, name, command):
    directory.mkdir(parents=True, exist_ok=True)
    tmp = directory / (name + ".tmp")
    dst = directory / (name + ".json")
    atomic_json(tmp, command)
    tmp.replace(dst)
    return dst


def validate_job(job, home):
    if not isinstance(job, dict):
        raise ValueError("job must be an object")
    if "background_probe" in job:
        if job not in ({"background_probe": "frozen-orb", "entry_only": True, "character": "CXSorc"},
                       {"background_probe": "blaze", "entry_only": True, "character": "CXSorc"},
                       {"background_probe": "bone-prison", "entry_only": True, "character": "CXSorc"},
                       {"background_probe": "bone-spear", "entry_only": True, "character": "CXSorc"},
                       {"background_probe": "poison-javelin", "entry_only": True, "character": "CXSorc"},
                       {"background_probe": "plague-javelin", "entry_only": True, "character": "CXSorc"},
                       {"background_probe": "firestorm", "entry_only": True, "character": "CXSorc"},
                       {"background_probe": "molten-boulder", "entry_only": True, "character": "CXMolten"},
                       {"background_probe": "molten-boulder", "entry_only": True, "character": "CXMolten", "expiry_probe": True},
                       {"background_probe": "molten-boulder", "entry_only": True, "character": "CXMolten", "direction_batch": True},
                       {"background_probe": "poison-javelin", "entry_only": True, "character": "CXSorc", "direction_batch": True}):
            raise ValueError("background_probe accepts only the fixed registered private-desktop experiments")
    character = job.get("character", "CXSorc")
    if character not in ("CXSorc", "CXMolten", "CXPortal", "CXBarb", "CXBarbWW", "aaa", "MapWalk", "Census", "CensusB", "CensusSorc"):
        raise ValueError("Only the pinned CXSorc/CXBarb/CXBarbWW/aaa/MapWalk/Census/CensusB/CensusSorc fixtures are currently supported")
    lifecycle = job.get("lifecycle")
    if lifecycle not in (None, "restart"):
        raise ValueError("unsupported lifecycle: " + str(lifecycle))
    if job.get("fixture") not in (None, "fallen-survivor", "shaman-hydra", "shaman-golem"):
        raise ValueError("unsupported fixture")
    if job.get("fixture") in ("fallen-survivor", "shaman-hydra", "shaman-golem") and (character != "CXSorc" or lifecycle != "restart"):
        raise ValueError("fallen-survivor requires CXSorc and restart")
    if job.get("entry_visibility", "hidden") not in ("hidden", "visible"):
        raise ValueError("entry_visibility must be hidden or visible")
    batches = job.get("batches", [])
    entry_only = bool(job.get("entry_only", False))
    if entry_only and batches:
        raise ValueError("entry_only jobs cannot contain collector batches")
    if not entry_only and (not batches or len(batches) > 256):
        raise ValueError("job.batches must contain 1..256 entries")
    seen = set()
    for index, batch in enumerate(batches):
        if not isinstance(batch, dict):
            raise ValueError("batch %d is not an object" % index)
        name = batch.get("name", "batch-%03d" % index)
        if name in seen or not name or any(ch not in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_" for ch in name):
            raise ValueError("invalid or duplicate batch name: " + str(name))
        seen.add(name)
        collector = batch.get("collector", "native_cast")
        if collector not in ALLOWED_COLLECTORS:
            raise ValueError("collector is not allow-listed: " + str(collector))
        native = validate_collector(collector, batch.get("scenario", {}))
        if not native.is_file():
            raise ValueError("build collector before launch: " + str(native))
    if not (home / LOADER_NAME).is_file():
        raise ValueError("loader is missing: " + str(home / LOADER_NAME))


def close_loader(pid, timeout=20):
    """Close one pinned loader; force only after WM_CLOSE is ignored."""
    user32 = ctypes.WinDLL("user32", use_last_error=True)
    kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
    user32.EnumWindows.argtypes = [ctypes.WINFUNCTYPE(ctypes.c_bool, ctypes.c_void_p, ctypes.c_void_p), ctypes.c_void_p]
    user32.EnumWindows.restype = ctypes.c_bool
    user32.GetWindowThreadProcessId.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.c_ulong)]
    user32.GetWindowThreadProcessId.restype = ctypes.c_ulong
    user32.PostMessageW.argtypes = [ctypes.c_void_p, ctypes.c_uint, ctypes.c_size_t, ctypes.c_ssize_t]
    user32.PostMessageW.restype = ctypes.c_bool
    windows = []
    callback_type = ctypes.WINFUNCTYPE(ctypes.c_bool, ctypes.c_void_p, ctypes.c_void_p)
    def collect(hwnd, _):
        owner = ctypes.c_ulong()
        user32.GetWindowThreadProcessId(hwnd, ctypes.byref(owner))
        if owner.value == pid:
            windows.append(hwnd)
        return True
    user32.EnumWindows(callback_type(collect), None)
    if not windows:
        raise RuntimeError("restart requested but loader has no top-level window")
    posted = any(user32.PostMessageW(hwnd, 0x0010, 0, 0) for hwnd in windows)
    if not posted:
        raise ctypes.WinError(ctypes.get_last_error())
    handle = kernel32.OpenProcess(0x00100000, False, pid)  # SYNCHRONIZE
    if not handle:
        raise ctypes.WinError(ctypes.get_last_error())
    try:
        wait = kernel32.WaitForSingleObject
        wait.argtypes = [ctypes.c_void_p, ctypes.c_uint]
        wait.restype = ctypes.c_uint
        if wait(handle, timeout * 1000) == 0:
            return False
    finally:
        kernel32.CloseHandle(handle)
    terminate = kernel32.OpenProcess(0x0001 | 0x00100000, False, pid)  # TERMINATE|SYNCHRONIZE
    if not terminate:
        raise ctypes.WinError(ctypes.get_last_error())
    try:
        kernel32.TerminateProcess.argtypes = [ctypes.c_void_p, ctypes.c_uint]
        kernel32.TerminateProcess.restype = ctypes.c_bool
        if not kernel32.TerminateProcess(terminate, 0):
            raise ctypes.WinError(ctypes.get_last_error())
        if kernel32.WaitForSingleObject(terminate, 5000) != 0:
            raise TimeoutError("forced loader termination did not complete")
    finally:
        kernel32.CloseHandle(terminate)
    return True


def run(args):
    if os.name != "nt":
        raise RuntimeError("The orchestrator requires Windows")
    if not is_admin():
        raise PermissionError("Start this orchestrator once with RunAs administrator")
    home = args.home.resolve()
    job = read_json(args.job)
    validate_job(job, home)
    if job.get("background_probe") == "frozen-orb":
        from frozen_orb_background import run_private
        return run_private(home, args.output)
    if job.get("background_probe") == "blaze":
        from blaze_background import run_private
        return run_private(home, args.output)
    if job.get("background_probe") == "bone-prison":
        from bone_prison_background import run_private
        return run_private(home, args.output)
    if job.get("background_probe") == "poison-javelin":
        from poison_javelin_background import run_private
        return run_private(home, args.output, job.get("direction_batch", False))
    if job.get("background_probe") == "plague-javelin":
        from poison_javelin_background import run_private
        return run_private(home, args.output, plague=True)
    if job.get("background_probe") == "firestorm":
        from poison_javelin_background import run_private
        return run_private(home, args.output, firestorm=True)
    if job.get("background_probe") == "molten-boulder":
        from molten_boulder_background import run_private
        return run_private(home, args.output, job.get("expiry_probe", False), job.get("direction_batch", False))
    if job.get("background_probe") == "bone-spear":
        from bone_spear_background import run_private
        return run_private(home, args.output)
    if args.output.exists():
        raise FileExistsError("output directory already exists; choose a new run directory: " + str(args.output))
    args.output.mkdir(parents=True, exist_ok=False)
    report = {
        "status": "starting",
        "job": str(args.job.resolve()),
        "home": str(home),
        "character": job.get("character", "CXSorc"),
        "batches": [],
        "elevation": "administrator",
        "scope": "allow-listed original loader/menu/process session; no arbitrary shell",
    }
    atomic_json(args.output / "orchestrator.json", report)
    loader = None
    loader_pid = None
    session = None
    try:
        if any(batch.get("collector") == "barbarian_formulas" for batch in job["batches"]):
            port_source = ROOT.parent.parent / "core" / "build" / "barbarian-batch"
            if not (port_source / "formula-values.csv").is_file():
                raise ValueError("run :core:barbarianRegression before native collection")
            # Freeze comparison input before starting the debugger.
            shutil.copytree(port_source, args.output / "riiablo-input")
        loader_path = home / LOADER_NAME
        existing = existing_loader_pids()
        if len(existing) > 1:
            raise RuntimeError("multiple matching loader processes; refusing to choose one")
        if existing and job.get("lifecycle") == "restart":
            report["loader_restarted_from"] = existing[0]
            report["loader_forced_termination"] = close_loader(existing[0])
            existing = []
        if job.get("fixture") in ("fallen-survivor", "shaman-hydra", "shaman-golem"):
            # Only the repository-owned CXSorc fixture is replaced, after the
            # old process exits so it cannot overwrite the new save on exit.
            import importlib.util
            import struct
            import winreg
            with winreg.OpenKey(winreg.HKEY_CURRENT_USER,
                    r"Software\Blizzard Entertainment\Diablo II", 0,
                    winreg.KEY_READ | winreg.KEY_WOW64_32KEY) as key:
                save = Path(winreg.QueryValueEx(key, "Save Path")[0])
            target = save / "CXSorc.d2s"
            original = target.read_bytes()
            (args.output / "CXSorc-before.d2s").write_bytes(original)
            spec = importlib.util.spec_from_file_location("fallen_fixture", ROOT / "test-character.py")
            fixture = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(fixture)
            golem = job.get("fixture") == "shaman-golem"
            data = bytearray(fixture.create(original, "CXSorc", class_id=2 if golem else 1, skill_level=20))
            stats, end = fixture.read_stats(data)
            for stat in range(6, 12): stats[stat] = 8000 << 8
            data[765:end] = fixture.write_stats(stats)
            if job.get("fixture") in ("shaman-hydra", "shaman-golem"):
                _, skill_offset = fixture.read_stats(data)
                if data[skill_offset:skill_offset+2] != b"if":
                    raise ValueError("Missing fixture skill section")
                data[skill_offset+2+(75-66 if golem else 62-36)] = 1
            # Fire Bolt on both active buttons and swapped slots: one
            # ordinary targeted click can kill a Blood Moor Fallen.
            struct.pack_into("<4I", data, 120, *((66,66,66,66) if golem else (36,36,36,36)))
            struct.pack_into("<I", data, 12, fixture.checksum(data))
            target.write_bytes(data)
            report["prepared_fixture"] = {"name": "CXSorc", "life": 8000,
                "mana": 8000, "firebolt_level": 20, "backup": str(args.output / "CXSorc-before.d2s")}
            if job.get("fixture") == "shaman-hydra":
                report["prepared_fixture"]["hydra_level"] = 1
            if golem:
                report["prepared_fixture"]["class_id"] = 2
                report["prepared_fixture"]["clay_golem_level"] = 1
                report["prepared_fixture"].pop("firebolt_level", None)
        if existing:
            loader_pid = existing[0]
            report["loader_reused"] = True
        else:
            startup = subprocess.STARTUPINFO()
            if job.get("entry_visibility", "hidden") == "hidden":
                startup.dwFlags |= subprocess.STARTF_USESHOWWINDOW
                startup.wShowWindow = 0
            loader = subprocess.Popen(
                [str(loader_path), "-w", "-res800"], cwd=str(home),
                creationflags=0,
                startupinfo=startup,
            )
            loader_pid = loader.pid
            report["loader_reused"] = False
        report["loader_pid"] = loader_pid
        atomic_json(args.output / "orchestrator.json", report)
        # The loader is itself the game process for this Onlyer build.  It can
        # take well over a minute to map the game DLLs, so wait for the actual
        # window/module readiness rather than guessing with a short sleep.
        time.sleep(args.loader_wait)
        if loader is not None and loader.poll() is not None:
            raise RuntimeError("loader exited with code %s" % loader.returncode)
        report["loader_ready"] = wait_loader_ready(loader_pid, args.ready_timeout)
        atomic_json(args.output / "orchestrator.json", report)

        entry_dir = args.output / "entry"
        entry_character = job.get("character", "CXBarb") if (
            any(batch.get("scenario", {}).get("class") == 4 for batch in job["batches"])
            or "barbarian-bash" in args.job.stem.casefold()
        ) else job.get("character", "CXSorc")
        if entry_character == "aaa" and report.get("loader_reused"):
            report["entry_exit_code"] = 0
            report["entry"] = {
                "status": "reused_existing_scene",
                "character": "aaa",
                "meaning": "menu entry skipped; room census attaches read-only to the existing scene",
            }
        else:
            entry_cmd = [str(PYTHON), str(ROOT / "menu-driver.py"),
                         "--pid", str(loader_pid), "--home", str(home),
                         "--output", str(entry_dir), "--seconds", str(args.entry_seconds),
                         "--auto-single-player", "--character", entry_character,
                         ("--visible-run" if job.get("entry_visibility", "hidden") == "visible" else "--hidden-run"),
                         "--capture-layouts"]
            if entry_character in ("MapWalk", "aaa", "Census", "CensusB", "CensusSorc"):
                entry_cmd += ["--no-walk"]
            else:
                entry_cmd += ["--walk-to-level", "2"]
            entry = subprocess.run(entry_cmd, cwd=str(ROOT.parent.parent),
                                   creationflags=subprocess.CREATE_NO_WINDOW)
            report["entry_exit_code"] = entry.returncode
            entry_summary = entry_dir / "summary.json"
            if not entry_summary.exists():
                raise RuntimeError("menu entry produced no summary")
            report["entry"] = read_json(entry_summary)
            # MapWalk fixtures may intentionally start in any already-saved
            # town/act.  Only enforce a level when the job states one;
            # defaulting to level 42 made otherwise valid entry runs fail
            # before the collector could observe them.
            first_batch = (job.get("batches") or [{}])[0]
            expected_level = (first_batch.get("scenario", {}).get("level_id")
                              if entry_character == "MapWalk" else None)
            if (entry.returncode or report["entry"].get("status") not in ("scene_observed", "player_observed")
                    or report["entry"].get("player", {}).get("name") != entry_character
                    or (expected_level is not None and report["entry"].get("scene", {}).get("level_id") != expected_level)):
                raise RuntimeError("automatic entry did not reach the test world")
        atomic_json(args.output / "orchestrator.json", report)

        if job.get("entry_only", False):
            report["status"] = "passed"
            report["meaning"] = "original fixture loaded and reached the requested scene; no native collector run"
            return 0

        session_dir = args.output / "process-session"
        session_cmd = [str(PYTHON), str(ROOT / "process-session.py"),
                       "--pid", str(loader_pid), "--home", str(home),
                       "--output", str(session_dir), "--seconds", str(args.session_seconds)]
        session = subprocess.Popen(session_cmd, cwd=str(ROOT.parent.parent),
                                   creationflags=subprocess.CREATE_NO_WINDOW)
        report["session_pid"] = session.pid
        session_summary = session_dir / "summary.json"
        ready = wait_summary(session_summary, {"ready"}, 30)
        report["session_ready"] = ready["status"]
        atomic_json(args.output / "orchestrator.json", report)
        commands_dir = session_dir / "commands"
        for index, batch in enumerate(job["batches"]):
            name = batch.get("name", "batch-%03d" % index)
            command_file = write_command(commands_dir, "%03d-%s" % (index + 1, name),
                                          {"op": batch.get("collector", "native_cast"),
                                           "scenario": batch.get("scenario", {})})
            # A bounded room walk can legitimately exceed the default
            # collector timeout. Do not send stop while it is still running.
            batch_timeout = args.batch_timeout
            if batch.get("collector") == "room_census_walk":
                batch_timeout = max(batch_timeout, batch.get("scenario", {}).get("seconds", 900) + 30)
            deadline = time.monotonic() + batch_timeout
            action = None
            while time.monotonic() < deadline:
                current = read_json(session_summary)
                action = next((item for item in current.get("actions", [])
                               if item.get("command_file") == command_file.name), None)
                if action and action.get("status") != "starting":
                    break
                time.sleep(0.5)
            if not action or action.get("status") != "completed":
                raise RuntimeError("batch failed: " + name + " result=" + str(action))
            report["batches"].append({"name": name, "command_file": command_file.name,
                                      "status": action["status"],
                                      "native_result": action.get("native_result")})
            if batch.get("collector") == "barbarian_formulas":
                comparison = compare_barbarian(session_dir / command_file.stem, args.output / "riiablo-input")
                atomic_json(args.output / "barbarian-comparison.json", comparison)
                report["batches"][-1]["formula_comparison"] = {
                    key: comparison[key] for key in ("status", "compared", "native_rows", "port_rows")
                }
            atomic_json(args.output / "orchestrator.json", report)

        stop = write_command(commands_dir, "999-stop", {"op": "stop"})
        wait_summary(session_summary, {"stopped", "stopped_with_failures"}, 30)
        report["status"] = "passed"
        report["meaning"] = "requested original collectors completed; port and visual parity require separate comparisons"
        if any(batch.get("formula_comparison", {}).get("status") == "differences" for batch in report["batches"]):
            report["status"] = "differences"
            return 1
        report["stop_command"] = stop.name
        return 0
    except Exception as error:
        report["status"] = "failed"
        report["error"] = str(error)
        return 1
    finally:
        atomic_json(args.output / "orchestrator.json", report)
        # A failed batch normally leaves the session loop alive, but a native
        # helper that reports confirmed detach/cleanup no longer needs it. Stop
        # that idle loop so the resident broker can accept the next batch. Keep
        # it alive only when the helper explicitly requests cleanup attention.
        if session and session.poll() is None and report["status"] == "failed":
            attention = False
            try:
                session_summary = args.output / "process-session" / "summary.json"
                current = read_json(session_summary)
                attention = current.get("status") == "native_cleanup_attention_required"
            except Exception:
                attention = True
            if not attention:
                try:
                    write_command(args.output / "process-session" / "commands", "999-stop-after-failure", {"op": "stop"})
                    session.wait(timeout=10)
                except Exception:
                    attention = True
            if attention and session.poll() is None:
                report["session_left_running_for_cleanup"] = True
                atomic_json(args.output / "orchestrator.json", report)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--job", type=Path, required=True)
    parser.add_argument("--home", type=Path, default=Path(r"G:\BaiduNetdiskDownload\Diablo II 1.10F"))
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--loader-wait", type=float, default=1)
    parser.add_argument("--ready-timeout", type=int, default=180)
    parser.add_argument("--entry-seconds", type=int, default=240)
    parser.add_argument("--session-seconds", type=int, default=1800)
    parser.add_argument("--batch-timeout", type=int, default=180)
    args = parser.parse_args()
    if not 1 <= args.ready_timeout <= 600 or not 1 <= args.entry_seconds <= 300 or not 1 <= args.session_seconds <= 1800:
        parser.error("invalid time bounds")
    raise SystemExit(run(args))


if __name__ == "__main__":
    main()
