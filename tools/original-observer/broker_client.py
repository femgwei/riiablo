"""Non-admin client: start once with UAC, then submit local bounded tasks."""
import argparse
import ctypes
import json
from pathlib import Path
import subprocess
import sys
import time
import uuid

from resident_broker import ROOT, validate, atomic_json

DISCOVERY = ROOT / "broker" / "current.json"


def session():
    return Path(json.loads(DISCOVERY.read_text(encoding="utf8"))["session"])


def status():
    try:
        state = json.loads((session() / "status.json").read_text(encoding="utf8"))
        state["responsive"] = state["status"] != "stopped" and time.time() - state["heartbeat"] < 10
        return state
    except (OSError, ValueError, KeyError):
        return dict(status="not_started", responsive=False)


def start():
    current = status()
    if current["responsive"]:
        return dict(reused=True, **current)
    # Prepare diagnostic/queue paths before elevation. Never rely on an admin
    # console staying open to capture a startup error.
    queue = ROOT / "broker" / (time.strftime("%Y%m%d-%H%M%S-") + uuid.uuid4().hex[:8])
    queue.mkdir(parents=True)
    atomic_json(DISCOVERY, dict(session=str(queue)))
    parameters = subprocess.list2cmdline([str(ROOT / "resident_broker.py"), "--queue", str(queue)])
    shell = ctypes.WinDLL("shell32", use_last_error=True)
    shell.ShellExecuteW.argtypes = [ctypes.c_void_p, ctypes.c_wchar_p, ctypes.c_wchar_p,
                                   ctypes.c_wchar_p, ctypes.c_wchar_p, ctypes.c_int]
    shell.ShellExecuteW.restype = ctypes.c_void_p
    result = shell.ShellExecuteW(None, "runas", sys.executable, parameters, str(ROOT), 0)
    if not result or result <= 32:
        raise OSError("UAC startup failed/cancelled: " + str(result))
    return dict(status="launch_requested", session=str(queue), note="only this startup needs UAC; check status.json")


def submit(command):
    validate(command)
    current = status()
    if not current["responsive"]:
        raise RuntimeError("receiver not ready; inspect status/startup-error, do not automatically elevate again")
    key = time.strftime("%Y%m%d-%H%M%S-") + uuid.uuid4().hex[:8]
    queue = session()
    atomic_json(queue / "inbox" / (key + ".json"), command)
    return dict(id=key, result=str(queue / "results" / (key + ".json")), broker_pid=current["pid"])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["start", "status", "registry", "submit", "batch"])
    parser.add_argument("--op")
    parser.add_argument("--character")
    parser.add_argument("--job")
    parser.add_argument("--jobs", nargs="+")
    args = parser.parse_args()
    if args.action == "start":
        result = start()
    elif args.action == "status":
        result = status()
    elif args.action == "registry":
        current = status()
        if not current["responsive"]:
            raise RuntimeError("receiver not ready")
        result = submit({"op": "get_registry"})
    else:
        command = dict(op="run_jobs" if args.action == "batch" else args.op)
        for key in ("character", "job"):
            if getattr(args, key) is not None:
                command[key] = getattr(args, key)
        if args.action == "batch":
            command["jobs"] = args.jobs or []
        result = submit(command)
    print(json.dumps(result, ensure_ascii=False, indent=2))
