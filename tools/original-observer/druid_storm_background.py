"""Finite shared-process Hurricane/Armageddon experiment, never an input desktop."""
import argparse
import ctypes as C
from ctypes import wintypes as W
import json
from pathlib import Path
import shutil
import subprocess
import sys
import traceback

from observer import Win32, api, digest
from orchestrator import LOADER_NAME, existing_loader_pids, wait_loader_ready
from arctic_blast_background import desktop_name, input_desktop_name, write_json
from prepare_druid_batch_fixture import prepare

ROOT = Path(__file__).resolve().parent


def run_private(home, output):
    from arctic_blast_background import run_private as private_lifecycle
    return private_lifecycle(home, output, storm_batch=True)


def worker(home, output):
    output.mkdir(parents=True, exist_ok=False)
    win, game, helper = Win32(), None, None
    report = dict(status="starting", cleanup_attention_required=False)
    try:
        desktop = win.get_thread_desktop(api(win.k, "GetCurrentThreadId", W.DWORD)())
        report["desktop"] = desktop_name(win, desktop)
        assert report["desktop"].startswith("riiablo_storm_")
        assert report["desktop"] != input_desktop_name(win)
        assert not existing_loader_pids(), "Another original is already running"
        provenance = output / "provenance"
        provenance.mkdir()
        for filename in (Path(__file__).name, "arctic_blast_background.py", "arctic_blast_passive.py",
                         "druid_storm_passive.py", "observer.py", "scene_reader.py",
                         "orchestrator.py", "prepare_druid_batch_fixture.py", "test-character.py", "menu-driver.py"):
            shutil.copy2(ROOT / filename, provenance / filename)
        preflight = subprocess.run([sys.executable, str(provenance / "druid_storm_passive.py"), "--help"],
            cwd=str(ROOT), creationflags=subprocess.CREATE_NO_WINDOW, capture_output=True, timeout=15)
        assert not preflight.returncode, preflight.stderr.decode("utf8", errors="replace")
        report["fixture"] = prepare(home, output, storm=True)
        game = subprocess.Popen([str(home / LOADER_NAME), "-w", "-res800"], cwd=str(home))
        report.update(status="loading", pid=game.pid)
        write_json(output / "summary.json", report)
        report["readiness"] = wait_loader_ready(game.pid, 180)
        with (output / "entry-stdout.log").open("w", encoding="utf8") as out, (output / "entry-stderr.log").open("w", encoding="utf8") as err:
            entry = subprocess.run([sys.executable, str(ROOT / "menu-driver.py"), "--pid", str(game.pid),
                "--home", str(home), "--output", str(output / "entry"), "--seconds", "240", "--visible-run",
                "--auto-single-player", "--character", "CXDruState", "--walk-to-level", "2", "--capture-controls"],
                cwd=str(ROOT), creationflags=subprocess.CREATE_NO_WINDOW, stdout=out, stderr=err, timeout=260)
        report["entry"] = json.loads((output / "entry" / "summary.json").read_text(encoding="utf8"))
        assert not entry.returncode and report["entry"].get("scene", {}).get("level_id") == 2
        report["captures"] = []
        # One process, one save; wait for each state and all its residual effects to end.
        for skill in (250, 249):
            capture = output / str(skill)
            collector = provenance / "druid_storm_passive.py"
            with (output / (str(skill) + "-stdout.log")).open("w", encoding="utf8") as out, (output / (str(skill) + "-stderr.log")).open("w", encoding="utf8") as err:
                helper = subprocess.Popen([sys.executable, str(collector), "--pid", str(game.pid),
                    "--home", str(home), "--output", str(capture), "--skill", str(skill)],
                    cwd=str(ROOT), creationflags=subprocess.CREATE_NO_WINDOW, stdout=out, stderr=err)
                report.update(status="capturing", helper_pid=helper.pid, skill=skill)
                write_json(output / "summary.json", report)
                helper.wait(timeout=45)
            native = json.loads((capture / "summary.json").read_text(encoding="utf8"))
            report["captures"].append(dict(skill=skill, native=native, path=str(skill), collector_sha256=digest(collector)))
            write_json(output / "summary.json", report)
            assert not helper.returncode and native["status"] == "passed", "Failed skill " + str(skill)
        report["status"] = "passed"
    except Exception as error:
        report.update(status="failed", error=str(error), traceback=traceback.format_exc())
    finally:
        # Collectors only read memory and send private HWND messages; no attachment to unwind.
        if helper and helper.poll() is None:
            helper.terminate()
            helper.wait(timeout=5)
        if game and game.poll() is None:
            for window in win.windows(desktop, game.pid):
                if window["class"] == "Diablo II": win.post(window["hwnd"], 0x10, 0, 0)
            try: game.wait(timeout=8)
            except subprocess.TimeoutExpired:
                game.terminate()
                game.wait(timeout=5)
            report["owned_game_closed"] = True
        report["cleanup_confirmed"] = game is None or game.poll() is not None
        write_json(output / "summary.json", report)
    return 0 if report["status"] == "passed" else 1


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--worker", action="store_true", required=True)
    parser.add_argument("--home", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    raise SystemExit(worker(args.home, args.output))
