"""Bounded read-only sampling of an existing, explicitly selected Diablo II PID.

No debugger attach, input, remote execution, memory writes, or process termination.
Run elevated when the selected game is elevated. Output stays in the workspace.
Version-specific reads are candidate observations, not proof of scene parity.
"""
import argparse
import ctypes as C
from ctypes import wintypes as W
import json
from pathlib import Path
import struct
import time

from observer import Win32, api, digest, player_snapshot, animation_candidate_snapshot
from scene_reader import scene_snapshot, layout_snapshot

EXPECTED = {
    "D2Client.dll": "94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282",
    "D2Common.dll": "b9d1a27c6a35992932c7c47438d02e5269f878e899f6e614d507902cecd1beab",
    "D2Game.dll": "2bd92fb25e3e4b8269930712620ba9db00cdc1aa44569d87dc4aba76553272b6",
}


def command_line(win, process):
    """Read the WOW64 PEB's command line without calling code in the target."""
    nt = C.WinDLL("ntdll")
    query = api(nt, "NtQueryInformationProcess", C.c_long, W.HANDLE, C.c_int,
                C.c_void_p, W.ULONG, C.POINTER(W.ULONG))
    peb, needed = C.c_size_t(), W.ULONG()
    status = query(process, 26, C.byref(peb), C.sizeof(peb), C.byref(needed))
    if status < 0 or not peb.value:
        return {"status": "unavailable", "ntstatus": hex(status & 0xFFFFFFFF)}
    params = win.u32(process, peb.value + 0x10)
    length, maximum, address = struct.unpack("<HHI", win.read(process, params + 0x40, 8))
    if length > maximum or length > 32768 or length % 2:
        raise ValueError("Invalid WOW64 command-line descriptor")
    return {"status": "read", "value": win.read(process, address, length).decode("utf-16-le")}


def observe(args):
    args.output.mkdir(parents=True, exist_ok=False)
    win, process = Win32(), None
    open_process = api(win.k, "OpenProcess", W.HANDLE, W.DWORD, W.BOOL, W.DWORD)
    current_tid = api(win.k, "GetCurrentThreadId", W.DWORD)
    query_path = api(win.k, "QueryFullProcessImageNameW", W.BOOL, W.HANDLE,
                     W.DWORD, W.LPWSTR, C.POINTER(W.DWORD))
    is_iconic = api(win.u, "IsIconic", W.BOOL, W.HWND)
    report = {"schema": 1, "pid": args.pid, "read_only": True,
              "debugger_attached": False, "target_terminated": False,
              "scope": "runtime identity and candidate player samples; no scene parity claim",
              "foreground_before": win.foreground(),
              "screen_before": [win.system_metric(0), win.system_metric(1)]}
    start = time.monotonic()
    states, frames, window_states = [], set(), []
    visual_samples = 0
    candidate_values = {}
    candidate_pointer_values = {}
    known_animation_values = {}
    pointer_previews = {}
    observed_tokens = {}
    observed_anim_records = {}
    try:
        # PROCESS_QUERY_INFORMATION | PROCESS_VM_READ | SYNCHRONIZE only.
        process = win.checked(open_process(0x400 | 0x10 | 0x100000, False, args.pid))
        buf, length = C.create_unicode_buffer(32768), W.DWORD(32768)
        win.checked(query_path(process, 0, buf, C.byref(length)))
        executable = Path(buf.value)
        if executable.parent.resolve() != args.home.resolve():
            raise ValueError("Selected PID is outside the explicitly selected installation")
        if executable.name not in ("D2v110各版本免CD全功能启动.exe", "D2Loader.exe", "Game.exe"):
            raise ValueError("Unexpected target executable")
        report.update(executable=str(executable), executable_sha256=digest(executable),
                      command_line=command_line(win, process))
        modules = win.modules(args.pid)
        report["modules"] = modules
        report["version_hashes"] = {}
        for name, expected in EXPECTED.items():
            module = modules.get(name.lower())
            if not module or Path(module["path"]).resolve() != (args.home / name).resolve():
                raise ValueError("Missing or unexpected module: " + name)
            actual = digest(Path(module["path"]))
            report["version_hashes"][name] = actual
            if actual != expected:
                raise ValueError("Unsupported version hash: " + name)
        report["layout_status"] = "player_getter_signature_checked; unit_layout_candidate"
        report["player_global_rva"] = "0x11c200"
        report["player_getter_rva"] = "0x883d0"
        desktop = win.get_thread_desktop(current_tid())
        with (args.output / "samples.jsonl").open("w", encoding="utf-8") as stream:
            for index in range(max(1, int(args.seconds / args.interval))):
                if win.wait(process, 0) == 0:
                    report["target_exited_during_observation"] = True
                    break
                sample = {"type": "sample", "elapsed_ms": round((time.monotonic() - start) * 1000)}
                try:
                    state = player_snapshot(win, process, modules)
                    sample["candidate_player"] = state
                    if state["status"] == "player":
                        if args.animation_candidates:
                            candidates = animation_candidate_snapshot(win, process, modules, state)
                            sample["animation_candidates"] = candidates
                            for name, value in candidates.get("known_fields", {}).items():
                                known_animation_values.setdefault(name, set()).add(value)
                            for item in candidates.get("pointer_records", []):
                                pointer_previews.setdefault(str(item["offset"]), set()).add(
                                    (item["value"], item.get("preview_hex"), item.get("readable"),
                                     item.get("ascii_token"), item.get("weapon_class_candidate")))
                                if item.get("ascii_token"):
                                    observed_tokens.setdefault(item["ascii_token"], set()).add(
                                        item.get("weapon_class_candidate"))
                                record = item.get("anim_data_record")
                                if item.get("ascii_token") and record:
                                    observed_anim_records.setdefault(item["ascii_token"], set()).add(
                                        (record.get("frames"), record.get("anim_speed"),
                                         tuple(record.get("nonzero_frame_flags", []))))
                            for item in candidates.get("small_values", []):
                                key = str(item["offset"])
                                bucket = candidate_values.setdefault(key, set())
                                bucket.add(item["value"])
                            for item in candidates.get("module_pointers", []):
                                key = str(item["offset"])
                                bucket = candidate_pointer_values.setdefault(key, set())
                                bucket.add(item["rva"])
                        sample["scene"] = scene_snapshot(win, process, modules, state)
                        if args.layouts and sample["scene"].get("status") == "scene":
                            layout = layout_snapshot(win, process, modules, sample["scene"])
                            layout_file = "layout-" + str(index) + ".json"
                            (args.output / layout_file).write_text(json.dumps(layout, ensure_ascii=False, indent=2), encoding="utf-8")
                            sample["layout_file"] = layout_file
                            report.setdefault("layout_files", []).append(layout_file)
                    states.append(state["status"])
                    if state["status"] == "player":
                        frames.add(state["animation_frame_raw"])
                except (OSError, ValueError) as error:
                    sample["candidate_error"] = str(error)
                windows = win.windows(desktop, args.pid)
                for item in windows:
                    item["minimized"] = bool(is_iconic(item["hwnd"]))
                if args.visual and state.get("status") == "player":
                    game_window = next((item for item in windows
                                        if item.get("class") == "Diablo II"
                                        and item.get("width", 0) > 0
                                        and item.get("height", 0) > 0), None)
                    if game_window:
                        sample["visual"] = win.capture_window_hash(game_window["hwnd"])
                        if sample["visual"].get("status") == "captured":
                            sample["visual_sha256"] = sample["visual"]["sha256"]
                            visual_samples += 1
                sample["windows"] = windows
                window_states = windows
                stream.write(json.dumps(sample, ensure_ascii=False) + "\n")
                stream.flush()
                time.sleep(args.interval)
        report.update(status="observed" if states else "no_successful_samples", samples=len(states),
                      candidate_player_states=sorted(set(states)),
                      candidate_animation_values=len(frames), windows=window_states,
                      visual_samples=visual_samples,
                      simulation_progress="unproven")
        if args.animation_candidates:
            report["animation_candidate_scan"] = {
                "status": "candidate_only",
                "stable_small_value_offsets": [
                    {"offset": int(offset), "distinct_values": len(values),
                     "values": sorted(values)[:128]}
                    for offset, values in sorted(candidate_values.items(), key=lambda pair: int(pair[0]))
                    if len(values) > 1
                ],
                "stable_module_pointer_offsets": [
                    {"offset": int(offset), "distinct_rvas": len(values),
                     "rvas": sorted(values)[:128]}
                    for offset, values in sorted(candidate_pointer_values.items(), key=lambda pair: int(pair[0]))
                    if len(values) > 1
                ],
                "known_fields": {
                    name: {"distinct_values": len(values),
                           "values": sorted(values)[:256]}
                    for name, values in sorted(known_animation_values.items())
                },
                "pointer_previews": {
                    offset: [{"value": value, "preview_hex": preview, "readable": readable,
                              "ascii_token": token, "weapon_class_candidate": weapon_class}
                             for value, preview, readable, token, weapon_class in
                             sorted(values, key=lambda item: item[0])[:32]]
                    for offset, values in sorted(pointer_previews.items(), key=lambda pair: int(pair[0]))
                },
                "observed_tokens": [
                    {"token": token, "weapon_class_candidate": sorted(classes)}
                    for token, classes in sorted(observed_tokens.items())
                ],
                "observed_anim_data_records": [
                    {"token": token, "records": [
                        {"frames": frames, "anim_speed": speed,
                         "nonzero_frame_flags": list(flags)}
                        for frames, speed, flags in sorted(records)
                    ]}
                    for token, records in sorted(observed_anim_records.items())
                ],
                "limitations": [
                    "unit-relative candidates are not a COF/token layout proof",
                    "samples are non-atomic and may cross client update boundaries",
                ],
            }
    except Exception as error:
        report.update(status="error", error=str(error))
    finally:
        if process:
            report["target_alive_after"] = win.wait(process, 0) != 0
            win.close(process)
        report.update(foreground_after=win.foreground(),
                      screen_after=[win.system_metric(0), win.system_metric(1)],
                      duration_ms=round((time.monotonic() - start) * 1000))
        (args.output / "summary.json").write_text(json.dumps(report, ensure_ascii=False,
                                                            indent=2), encoding="utf-8")
    return 0 if report["status"] == "observed" else 1


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pid", type=int, required=True)
    parser.add_argument("--home", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--seconds", type=float, default=12)
    parser.add_argument("--interval", type=float, default=0.5)
    parser.add_argument("--layouts", action="store_true", help="Read already-generated level topology without initializing rooms")
    parser.add_argument("--animation-candidates", action="store_true",
                        help="Record bounded read-only unit-relative animation field candidates")
    parser.add_argument("--visual", action="store_true",
                        help="Hash a non-activated Diablo II client window bitmap per sample")
    args = parser.parse_args()
    if not 0 < args.seconds <= 60 or not 0.02 <= args.interval <= 5:
        parser.error("seconds must be (0,60], interval [0.02,5]")
    raise SystemExit(observe(args))
