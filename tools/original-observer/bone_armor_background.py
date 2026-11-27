"""Bone Armor draw collection on an unshown, never switched-to private desktop.

The existing bounded orchestrator owns menu entry and minimized D2Gfx capture.
Its visible menu policy applies only inside the verified non-input desktop.
"""
import argparse
import ctypes as C
from ctypes import wintypes as W
import json
from pathlib import Path
from types import SimpleNamespace
import traceback

from bone_prison_background import desktop_name, input_desktop_name, write_json
from bone_prison_background import run_private as run_isolated
from observer import Win32, api
from orchestrator import ROOT, close_loader, existing_loader_pids, run

PREFIX = "riiablo_bonearmor_"


def run_private(home, output, depletion=False):
    return run_isolated(home, output, worker_script=Path(__file__).resolve(),
                        desktop_prefix=PREFIX, probe="bone-armor-hit-d2gfx" if depletion else "bone-armor-d2gfx",
                        worker_arguments=("--depletion",) if depletion else ())


def worker(home, output, depletion=False):
    output.mkdir(parents=True, exist_ok=False)
    win = Win32()
    report = dict(status="starting", cleanup_attention_required=False,
                  switched_desktop=False, global_input=False)
    try:
        current = win.get_thread_desktop(api(win.k, "GetCurrentThreadId", W.DWORD)())
        report["desktop"] = desktop_name(win, current)
        if not report["desktop"].startswith(PREFIX) or report["desktop"] == input_desktop_name(win):
            raise ValueError("Worker requires the non-input Bone Armor private desktop")
        if existing_loader_pids():
            raise ValueError("Another original game appeared; refusing to reuse or interrupt it")
        job = json.loads((ROOT / "jobs/necromancer-bone-armor-d2gfx.json").read_text(encoding="utf8"))
        # Legacy DirectDraw transitions can restore its own window. Keeping
        # those transitions on this unshown desktop prevents user disruption.
        job["entry_visibility"] = "visible"
        if depletion:
            job["batches"][0]["name"] = "bone-armor-hit-d2gfx-minimized"
            job["batches"][0]["scenario"].update(seconds=30, prepare_bone_armor_attacker=True)
        job_path = output / "private-capture-job.json"
        write_json(job_path, job)
        report["status"] = "capturing"
        write_json(output / "summary.json", report)
        args = SimpleNamespace(home=home, job=job_path, output=output / "capture-run",
            loader_wait=1, ready_timeout=180, entry_seconds=240,
            session_seconds=180, batch_timeout=120)
        report["capture_exit_code"] = run(args)
        capture = json.loads((args.output / "orchestrator.json").read_text(encoding="utf8"))
        report["capture"] = capture
        report["pid"] = capture.get("loader_pid")
        report["cleanup_attention_required"] = bool(capture.get("session_left_running_for_cleanup"))
        report["status"] = "passed" if report["capture_exit_code"] == 0 else "failed"
    except Exception as error:
        report.update(status="failed", error=str(error), traceback=traceback.format_exc())
    finally:
        capture_path = output / "capture-run/orchestrator.json"
        if capture_path.exists():
            capture = json.loads(capture_path.read_text(encoding="utf8"))
            report["pid"] = capture.get("loader_pid")
            report["cleanup_attention_required"] |= bool(capture.get("session_left_running_for_cleanup"))
        pid = report.get("pid")
        if pid and not report["cleanup_attention_required"]:
            try:
                if pid in existing_loader_pids():
                    report["owned_game_force_closed"] = close_loader(pid)
                report["owned_game_closed"] = pid not in existing_loader_pids()
                if not report["owned_game_closed"]:
                    raise RuntimeError("Owned private-desktop game remains alive")
            except Exception as error:
                report.update(status="failed", cleanup_attention_required=True, cleanup_error=str(error))
        report["input_desktop_after"] = input_desktop_name(win)
        write_json(output / "summary.json", report)
    return 0 if report["status"] == "passed" else 1


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--worker", action="store_true", required=True)
    parser.add_argument("--home", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--depletion", action="store_true")
    args = parser.parse_args()
    raise SystemExit(worker(args.home, args.output, args.depletion))
