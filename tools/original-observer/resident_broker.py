"""Resident elevated receiver for a finite set of local Diablo II test tasks.

One UAC on startup; tasks execute serially and do not end the receiver. No shell,
network listener, arbitrary executable, arbitrary path or automatic logon task.
The workspace queue is controlled by the same Windows user who approved UAC.
"""
import argparse
import ctypes
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import traceback
import uuid

ROOT = Path(__file__).resolve().parent
HOME = Path(r"G:\BaiduNetdiskDownload\Diablo II 1.10F")
SAVE = Path(r"G:\BaiduNetdiskDownload\Diablo II\Save")
LOADER = "D2v110各版本免CD全功能启动.exe"
LOADER_HASH = "602d3c638d782df6ad3edf8efbe07bbddd771f625e9491056084b83484848958"
NAME = re.compile(r"[a-zA-Z0-9_-]{1,80}\Z")
# Repository-owned fixtures.  ``aaa`` is the user's all-waypoints save and is
# used only for read-only map/waypoint sampling.
CHARACTERS = {"CXSorc", "CXBarb", "aaa", "CensusB", "CensusSorc"}
SCHEMAS = {
    "ping": set(), "shutdown": set(), "launch": set(),
    "snapshot": set(), "controls": set(), "enter": {"character"}, "enter_town": {"character"}, "install_cxbarb": set(),
    "client_animation_probe": set(),
    "get_registry": set(), "run_job": {"job"}, "run_jobs": {"jobs"},
}


def job_names():
    """Discover finite, repository-owned jobs without restarting the broker."""
    return sorted(path.stem for path in (ROOT / "jobs").glob("*.json")
                  if path.is_file() and NAME.fullmatch(path.stem))


def atomic_json(path, value):
    # A fixed sibling name can collide with a reader or a stale writer on
    # Windows.  Use a per-write temp file and retry the final replace briefly.
    tmp = path.with_name(path.name + "." + str(os.getpid()) + "." + uuid.uuid4().hex + ".tmp")
    tmp.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf8")
    for attempt in range(8):
        try:
            os.replace(tmp, path)
            return
        except PermissionError:
            if attempt == 7:
                raise
            time.sleep(0.05 * (attempt + 1))


def validate(command):
    if not isinstance(command, dict) or command.get("op") not in SCHEMAS:
        raise ValueError("unknown operation; no arbitrary command execution")
    op = command["op"]
    if set(command) != SCHEMAS[op] | {"op"}:
        raise ValueError("unexpected or missing command fields")
    if op == "enter" and command["character"] not in CHARACTERS:
        raise ValueError("unsupported fixture character")
    if op == "run_job" and command["job"] not in job_names():
        raise ValueError("unsupported repository job name")
    if op == "run_jobs":
        jobs = command["jobs"]
        if not isinstance(jobs, list) or not 1 <= len(jobs) <= 256:
            raise ValueError("jobs must contain 1..256 repository jobs")
        if any(not isinstance(job, str) or job not in job_names() for job in jobs):
            raise ValueError("run_jobs contains an unsupported repository job")
    return command


def read_command(path):
    if path.is_symlink() or path.stat().st_size > 65536:
        raise ValueError("invalid queue file")
    return validate(json.loads(path.read_text(encoding="utf-8-sig")))


class Broker:
    def __init__(self, queue):
        self.queue = queue.resolve()
        self.inbox = self.queue / "inbox"
        self.accepted = self.queue / "accepted"
        self.results = self.queue / "results"
        self.runs = self.queue / "runs"
        for path in (self.inbox, self.accepted, self.results, self.runs):
            path.mkdir(parents=True, exist_ok=True)
        self.active = None
        self.cleanup_blocked = False
        self.state = dict(pid=os.getpid(), elevated=True, status="ready", started=time.time(),
                          session=str(self.queue), operations=sorted(SCHEMAS), jobs=job_names(), active=None,
                          persistence="until shutdown, process exit or Windows restart; no scheduled task")

    def heartbeat(self, **changes):
        self.state.update(changes, jobs=job_names(), heartbeat=time.time())
        atomic_json(self.queue / "status.json", self.state)

    def target(self, required=True):
        from orchestrator import existing_loader_pids
        from observer import Win32, api, digest
        from ctypes import wintypes as W
        pids = existing_loader_pids()
        if not pids and not required:
            return None
        if len(pids) != 1:
            raise ValueError("expected exactly one original loader, got " + str(pids))
        win = Win32()
        process = win.checked(api(win.k, "OpenProcess", W.HANDLE, W.DWORD, W.BOOL, W.DWORD)(
            0x1000, False, pids[0]))
        try:
            query = api(win.k, "QueryFullProcessImageNameW", W.BOOL, W.HANDLE, W.DWORD, W.LPWSTR, ctypes.POINTER(W.DWORD))
            image, size = ctypes.create_unicode_buffer(32768), W.DWORD(32768)
            win.checked(query(process, 0, image, ctypes.byref(size)))
            expected = HOME / LOADER
            if Path(image.value).resolve() != expected.resolve() or digest(expected) != LOADER_HASH:
                raise ValueError("original loader path/hash mismatch")
        finally:
            win.close(process)
        return pids[0]

    def child(self, arguments, directory, native=False):
        """Poll without killing a debugger; keep the broker heartbeat visible."""
        with (directory / "stdout.log").open("w", encoding="utf8") as out, (directory / "stderr.log").open("w", encoding="utf8") as err:
            child = subprocess.Popen(arguments, cwd=str(ROOT), stdout=out, stderr=err,
                                     creationflags=subprocess.CREATE_NO_WINDOW)
            self.heartbeat(status="running", child_pid=child.pid)
            while child.poll() is None:
                self.heartbeat()
                time.sleep(0.5)
        self.heartbeat(child_pid=None)
        return child.returncode

    def install(self, directory):
        # Never overwrite an existing fixture or another character. Installing
        # an identical fixture is an idempotent success, even with a game open.
        source = ROOT / "runs" / "CXBarb.d2s"
        data = source.read_bytes()
        spec = importlib.util.spec_from_file_location("fixture", ROOT / "test-character.py")
        fixture = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(fixture)
        import struct
        if (data[:4] != bytes.fromhex("55aa55aa") or data[40] != 4 or
                data[20:36].split(b"\0")[0] != b"CXBarb" or
                struct.unpack_from("<I", data, 4)[0] != 96 or
                struct.unpack_from("<I", data, 8)[0] != len(data) or
                fixture.checksum(data) != struct.unpack_from("<I", data, 12)[0]):
            raise ValueError("invalid CXBarb fixture")
        target = SAVE / "CXBarb.d2s"
        if target.exists():
            if target.read_bytes() == data:
                copied = []
                for suffix in (".key", ".map", ".ma0"):
                    source_sidecar = SAVE / ("CXSorc" + suffix)
                    target_sidecar = SAVE / ("CXBarb" + suffix)
                    if source_sidecar.exists() and not target_sidecar.exists():
                        shutil.copy2(source_sidecar, target_sidecar)
                        copied.append(str(target_sidecar))
                return dict(status="already_installed", path=str(target), sidecars=copied,
                            sha256=hashlib.sha256(data).hexdigest())
            raise ValueError("CXBarb already exists with different contents; refusing overwrite")
        if self.target(required=False):
            raise ValueError("install a new fixture only while the game is closed")
        backup = directory / "save-backup"
        backup.mkdir()
        for original in SAVE.iterdir():
            if original.is_file():
                shutil.copy2(original, backup / original.name)
        with target.open("xb") as stream:
            stream.write(data)
        copied = []
        for suffix in (".key", ".map", ".ma0"):
            source_sidecar = SAVE / ("CXSorc" + suffix)
            target_sidecar = SAVE / ("CXBarb" + suffix)
            if source_sidecar.exists() and not target_sidecar.exists():
                shutil.copy2(source_sidecar, target_sidecar)
                copied.append(str(target_sidecar))
        return dict(status="installed", path=str(target), backup=str(backup), sidecars=copied,
                    sha256=hashlib.sha256(data).hexdigest())

    def execute(self, command, directory):
        directory.mkdir(parents=True, exist_ok=True)
        op = command["op"]
        if op == "ping":
            return dict(pong=True, broker_pid=os.getpid(), elevated=True, target_pid=self.target(False))
        if op == "shutdown":
            return dict(shutdown=True)
        if op == "get_registry":
            return dict(operations=sorted(SCHEMAS), jobs=job_names(), pid=os.getpid())
        if self.cleanup_blocked:
            raise RuntimeError("previous native helper cleanup is unresolved; refusing another game task")
        if op == "install_cxbarb":
            return self.install(directory)
        if op == "launch":
            pid = self.target(False)
            if pid:
                return dict(target_pid=pid, reused=True)
            from observer import digest
            if digest(HOME / LOADER) != LOADER_HASH:
                raise ValueError("unsupported loader hash")
            child = subprocess.Popen([str(HOME / LOADER), "-w", "-res800"], cwd=str(HOME))
            return dict(target_pid=child.pid, reused=False)
        if op in ("snapshot", "controls", "enter", "enter_town"):
            pid = self.target()
            # Do not start menu automation while the legacy loader is still
            # mapping D2Client/D2Game.  This is the common source of the
            # intermittent "no Diablo II window"/walk_incomplete result.
            from orchestrator import wait_loader_ready
            readiness = wait_loader_ready(pid, 180)
            output = directory / "entry"
            run_mode = "--visible-run" if op in ("enter", "enter_town", "controls") else "--hidden-run"
            arguments = [sys.executable, str(ROOT / "menu-driver.py"), "--pid", str(pid),
                         "--home", str(HOME), "--output", str(output), run_mode,
                         "--seconds", "240" if op == "enter" else ("4" if op == "controls" else "2"),
                         "--capture-controls"]
            if op in ("enter", "enter_town"):
                arguments += ["--auto-single-player", "--character", command["character"],
                              "--capture-layouts"]
                if op == "enter":
                    arguments += ["--walk-to-level", "2"]
                else:
                    arguments += ["--no-walk"]
            code = self.child(arguments, directory)
            summary = json.loads((output / "summary.json").read_text(encoding="utf8"))
            player = summary.get("player", {})
            expected = command.get("character")
            result = dict(exit_code=code, summary_path=str(output / "summary.json"),
                          status=summary.get("status"), error=summary.get("error"),
                          player=player, level_id=summary.get("scene", {}).get("level_id"),
                          loader_ready=readiness)
            expected_class = 4 if expected in ("CXBarb", "CXBarbWW") else (0 if expected == "aaa" else 1)
            if op in ("enter", "enter_town") and (code or player.get("name") != expected or
                                  player.get("class_id") != expected_class or result["level_id"] != 2):
                raise RuntimeError("entry not verified: " + json.dumps(result, ensure_ascii=False))
            if op == "controls":
                result["controls_path"] = str(output / "controls-latest.json")
            return result
        if op == "client_animation_probe":
            # Read-only, version-pinned D2Client sampling.  The PID is always
            # resolved by target(), never accepted from the queue, so a queued
            # command cannot read an unrelated elevated process.
            pid = self.target()
            output = directory / "client-animation"
            arguments = [sys.executable, str(ROOT / "live-observer.py"),
                         "--pid", str(pid), "--home", str(HOME),
                         "--output", str(output), "--seconds", "10", "--interval", "0.1",
                         "--animation-candidates", "--visual"]
            code = self.child(arguments, directory)
            summary_path = output / "summary.json"
            summary = json.loads(summary_path.read_text(encoding="utf8")) if summary_path.exists() else {}
            result = dict(exit_code=code, status=summary.get("status"),
                          summary_path=str(summary_path), trace_path=str(output / "samples.jsonl"),
                          samples=summary.get("samples", 0),
                          candidate_animation_values=summary.get("candidate_animation_values", 0),
                          visual_samples=summary.get("visual_samples", 0))
            if code and not summary:
                raise RuntimeError("client animation probe failed without summary")
            samples_path = output / "samples.jsonl"
            if samples_path.exists():
                from animation_compare import client_trace, write_trace
                metadata, frames = client_trace(samples_path)
                canonical = output / "animation-trace.json"
                write_trace(canonical, metadata, frames)
                result["canonical_trace"] = str(canonical)
            return result
        if op == "run_job":
            # Reload the repository-owned registry for each job.  This keeps
            # the long-lived one-UAC receiver extensible when a new reviewed
            # collector/job is added, without requiring a second elevation.
            import importlib
            import collector_specs as _collector_specs
            import orchestrator as _orchestrator
            importlib.reload(_collector_specs)
            _orchestrator = importlib.reload(_orchestrator)
            read_json, validate_job = _orchestrator.read_json, _orchestrator.validate_job
            job = ROOT / "jobs" / (command["job"] + ".json")
            validate_job(read_json(job), HOME)
            output = directory / "orchestrator"
            code = self.child([sys.executable, str(ROOT / "orchestrator.py"), "--job", str(job),
                               "--home", str(HOME), "--output", str(output), "--batch-timeout", "900"], directory)
            report = json.loads((output / "orchestrator.json").read_text(encoding="utf8"))
            # The orchestrator may leave its polling session alive after a
            # failed batch. That is harmless once the native helper confirmed
            # both detach and cleanup; only preserve the broker lock when the
            # report explicitly says manual cleanup attention is required.
            if report.get("session_left_running_for_cleanup"):
                native = {}
                for batch in report.get("batches", []):
                    native.update(batch.get("native_result") or {})
                self.cleanup_blocked = bool(native.get("cleanup_attention_required", True))
            if code:
                raise RuntimeError("batch returned " + str(code) + "; inspect " + str(output))
            return dict(status=report["status"], report=str(output / "orchestrator.json"))
        if op == "run_jobs":
            results = []
            for index, job_name in enumerate(command["jobs"], 1):
                # Each job gets its own immutable output directory. A failed
                # job stops the sequence and leaves later jobs queued in the
                # result, so the caller can resume without replaying earlier work.
                result = self.execute({"op": "run_job", "job": job_name},
                                      directory / ("batch-%03d-%s" % (index, job_name)))
                results.append(dict(job=job_name, **result))
            return dict(status="completed", jobs=results)
        raise AssertionError("unhandled operation")

    def serve_once(self):
        for path in sorted(self.inbox.glob("*.json")):
            key = path.stem
            if not NAME.fullmatch(key):
                continue
            accepted = self.accepted / path.name
            result_path = self.results / path.name
            if accepted.exists() or result_path.exists():
                continue  # already claimed, never replay a task after a crash
            path.rename(accepted)
            result = dict(id=key, status="starting", started=time.time(), broker_pid=os.getpid())
            atomic_json(result_path, result)
            self.active = key
            self.heartbeat(status="running", active=key)
            try:
                command = read_command(accepted)
                result["command"] = command
                directory = self.runs / key
                directory.mkdir()
                result["result"] = self.execute(command, directory)
                result["status"] = "completed"
            except Exception as error:
                result.update(status="failed", error=str(error), traceback=traceback.format_exc())
            result["finished"] = time.time()
            atomic_json(result_path, result)
            self.active = None
            shutdown = result.get("result", {}).get("shutdown", False)
            self.heartbeat(status="stopped" if shutdown else "ready", active=None, last_task=key,
                           last_task_status=result["status"], cleanup_blocked=self.cleanup_blocked)
            return not shutdown
        self.heartbeat()
        return True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--queue", type=Path, required=True)
    args = parser.parse_args()
    args.queue.mkdir(parents=True, exist_ok=True)
    try:
        if os.name != "nt" or not ctypes.windll.shell32.IsUserAnAdmin():
            raise PermissionError("receiver must be elevated once at startup")
        # A named kernel mutex remains held for the lifetime of the process,
        # preventing two admin receivers from executing this queue concurrently.
        kernel = ctypes.WinDLL("kernel32", use_last_error=True)
        kernel.CreateMutexW.argtypes = [ctypes.c_void_p, ctypes.c_bool, ctypes.c_wchar_p]
        kernel.CreateMutexW.restype = ctypes.c_void_p
        mutex = kernel.CreateMutexW(None, True, "Local\\RiiabloObserverBroker-" + hashlib.sha256(str(ROOT).lower().encode()).hexdigest())
        if not mutex or ctypes.get_last_error() == 183:
            raise RuntimeError("another resident receiver is already running")
        broker = Broker(args.queue)
        broker.heartbeat()
        while broker.serve_once():
            time.sleep(0.25)
    except Exception:
        (args.queue / "startup-error.log").write_text(traceback.format_exc(), encoding="utf8")
        raise


if __name__ == "__main__":
    main()
