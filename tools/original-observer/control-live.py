"""Elevated bounded debugger experiment, separate from the read-only observer.

Reuses one explicitly selected game. Backs up saves, prepares the hidden window,
runs native cadence and a small frame burst, then requests pause/hide. Never
kills the game or a debugger with unresolved cleanup. UAC remains interactive.
"""
import argparse
import ctypes
import json
from pathlib import Path
import shutil
import subprocess
import sys
import time

from observer import Win32, digest
from importlib.util import spec_from_file_location, module_from_spec

ROOT = Path(__file__).resolve().parent
spec = spec_from_file_location("live", ROOT / "live-observer.py")
live = module_from_spec(spec)
spec.loader.exec_module(live)


def run(args):
    args.output.mkdir(parents=True, exist_ok=False)
    report = dict(status="starting", pid=args.pid, debugger_changes_live_memory=True,
                  scope="bounded frame-control and Fire Bolt getter proof; no casting/settlement/visual parity",
                  source_hashes={str(p.relative_to(ROOT)): digest(p) for p in
                    (ROOT / "native-harness/d2game_controller.cpp", Path(__file__))})
    win = Win32()
    report.update(foreground_before=win.foreground(), screen_before=[win.system_metric(0), win.system_metric(1)])
    cleanup_allowed = True

    def save():
        (args.output / "summary.json").write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf8")

    def execute(label, command):
        with (args.output / (label + ".stdout")).open("w", encoding="utf8") as stdout, (args.output / (label + ".stderr")).open("w", encoding="utf8") as stderr:
            child = subprocess.Popen(list(map(str, command)), stdout=stdout, stderr=stderr,
                                     creationflags=subprocess.CREATE_NO_WINDOW, cwd=ROOT.parent.parent)
            report[label + "_pid"] = child.pid
            save()
            try:
                code = child.wait(timeout=180)
            except subprocess.TimeoutExpired:
                report[label + "_still_running"] = True
                save()
                # Killing an attached debugger could leave an INT3 in the game.
                raise RuntimeError("Native helper exceeded its bound; left alive for cleanup, pid=" + str(child.pid))
        report[label + "_exit_code"] = code
        return code

    def menu(label, pause):
        command = [sys.executable, ROOT / "menu-driver.py", "--pid", args.pid,
                   "--home", args.home, "--output", args.output / label, "--seconds", "1", "--hidden-run"]
        if pause:
            command.append("--pause-after")
        if execute(label, command):
            raise RuntimeError(label + " failed; inspect its stderr and summary")
        return json.loads((args.output / label / "summary.json").read_text(encoding="utf8"))

    try:
        if not ctypes.windll.shell32.IsUserAnAdmin():
            raise RuntimeError("This bounded helper requires administrator access to the elevated original game")
        modules = win.modules(args.pid)
        for name, expected in live.EXPECTED.items():
            found = modules.get(name.lower())
            if not found or Path(found["path"]).resolve() != (args.home / name).resolve() or digest(args.home / name) != expected:
                raise ValueError("Unsupported original module: " + name)
        report["module_hashes"] = live.EXPECTED
        native = ROOT / "native-harness/build/d2game_controller.exe"
        report["controller_exe_sha256"] = digest(native)
        artifacts = args.output / "controller-artifacts"
        artifacts.mkdir()
        for path in (native, ROOT / "native-harness/d2game_controller.cpp", Path(__file__)):
            shutil.copy2(path, artifacts / path.name)
        prepared = menu("prepare-hidden", False)
        report["prepare"] = {key: prepared[key] for key in
            ("status", "save_backups", "actions", "foreground_before", "foreground_after", "screen_before", "screen_after")
            if key in prepared}
        for label, burst in (("native-cadence", 0), ("burst", 1)):
            for name, expected in live.EXPECTED.items():
                if digest(args.home / name) != expected:
                    raise ValueError("Original DLL changed before debugger attach")
            cleanup_allowed = False
            code = execute(label, [native, args.pid, args.home, args.output / label, 12, burst, args.seed])
            path = args.output / label / "summary.json"
            result = json.loads(path.read_text(encoding="utf8")) if path.exists() else {}
            report[label] = result
            cleanup_allowed = bool(result.get("cleanup_confirmed") and result.get("detached"))
            if code or result.get("status") != "passed" or not cleanup_allowed:
                raise RuntimeError(label + " failed; inspect native stderr and cleanup status")
        report["status"] = "passed"
    except Exception as error:
        report.update(status="failed", error=str(error))
    finally:
        if cleanup_allowed:
            try:
                report["pause_after"] = menu("pause-hidden", True).get("pause_after")
            except Exception as error:
                report.update(status="failed", pause_error=str(error))
        else:
            report["cleanup_attention_required"] = True
        report.update(foreground_after=win.foreground(), screen_after=[win.system_metric(0), win.system_metric(1)])
        if report["foreground_after"] != report["foreground_before"] or report["screen_after"] != report["screen_before"]:
            report["background_invariants"] = "changed; inspect individual samples"
        save()
    return 0 if report["status"] == "passed" else 1


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pid", required=True, type=int)
    parser.add_argument("--home", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--seed", required=True, type=int)
    args = parser.parse_args()
    raise SystemExit(run(args))
