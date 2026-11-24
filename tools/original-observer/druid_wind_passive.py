"""Bounded private-desktop client-input wind cast and read-only client missile polling.

No debugger, suspension, memory writes, injected calls, desktop switch or GPU readback.
Polling is explicitly non-atomic; this is not a DCC submission trace.
"""
import argparse
import ctypes as C
from ctypes import wintypes as W
import json
from pathlib import Path
import struct
import time

from observer import Win32, api, digest, player_snapshot
from scene_reader import scene_snapshot
LOADER_NAME = "D2v110各版本免CD全功能启动.exe"
LOADER_HASH = "602d3c638d782df6ad3edf8efbe07bbddd771f625e9491056084b83484848958"

def input_desktop_name(win):
    desktop = api(win.u, "OpenInputDesktop", W.HANDLE, W.DWORD, W.BOOL, W.DWORD)(0, False, 1)
    if not desktop:
        return ""
    try:
        buffer = C.create_unicode_buffer(256); needed = W.DWORD()
        getter = api(win.u, "GetUserObjectInformationW", W.BOOL, W.HANDLE, C.c_int, C.c_void_p, W.DWORD, C.POINTER(W.DWORD))
        return buffer.value if getter(desktop, 2, buffer, C.sizeof(buffer), C.byref(needed)) else ""
    finally:
        win.close_desktop(desktop)

HASHES = {
    "D2Client.dll": "94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282",
    "D2Common.dll": "b9d1a27c6a35992932c7c47438d02e5269f878e899f6e614d507902cecd1beab",
    "D2Game.dll": "2bd92fb25e3e4b8269930712620ba9db00cdc1aa44569d87dc4aba76553272b6"}


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf8")


def collect(args):
    args.output.mkdir(parents=True, exist_ok=False)
    win, process, hwnd, point = Win32(), None, None, None
    report = dict(status="starting", pid=args.pid, debugger_attached=False, detached=False,
        memory_writes=False, remote_execution=False, global_input=False,
        cleanup_confirmed=False, cleanup_attention_required=False,
        scope="Private HWND cast and unsuspended client memory snapshots; no server, DCC call, GPU or damage evidence")
    pressed = False
    seen, rejected, cast_started, snapshots = set(), 0, False, 0
    try:
        desktop = win.get_thread_desktop(api(win.k, "GetCurrentThreadId", W.DWORD)())
        name_buffer = C.create_unicode_buffer(256); needed = W.DWORD()
        get_name = api(win.u, "GetUserObjectInformationW", W.BOOL, W.HANDLE, C.c_int, C.c_void_p, W.DWORD, C.POINTER(W.DWORD))
        win.checked(get_name(desktop, 2, name_buffer, C.sizeof(name_buffer), C.byref(needed)))
        assert name_buffer.value.startswith("riiablo_molten_")
        assert name_buffer.value != input_desktop_name(win)
        report["desktop"] = name_buffer.value
        process = win.checked(api(win.k, "OpenProcess", W.HANDLE, W.DWORD, W.BOOL, W.DWORD)(0x400 | 0x10 | 0x100000, False, args.pid))
        image, size = C.create_unicode_buffer(32768), W.DWORD(32768)
        win.checked(api(win.k, "QueryFullProcessImageNameW", W.BOOL, W.HANDLE, W.DWORD, W.LPWSTR, C.POINTER(W.DWORD))(
            process, 0, image, C.byref(size)))
        assert Path(image.value).resolve() == (args.home / LOADER_NAME).resolve()
        assert digest(Path(image.value)) == LOADER_HASH
        modules = win.modules(args.pid)
        for dll, expected in HASHES.items():
            assert Path(modules[dll.lower()]["path"]).resolve() == (args.home / dll).resolve()
            assert digest(args.home / dll) == expected
        base = modules["d2client.dll"]["base"]
        assert win.read(process, base+0x883D0, 6) == b"\xa1" + struct.pack("<I", base+0x11C200) + b"\xc3"
        player = player_snapshot(win, process, modules)
        assert player["status"] == "player" and player["name"] == "CXDruWind" and player["class_id"] == 5
        assert scene_snapshot(win, process, modules, player)["level_id"] == 2
        cp = player["pointer"]
        skills = win.u32(process, cp+0xA8); selected = win.u32(process, skills+0xC)
        assert struct.unpack("<H", win.read(process, win.u32(process, selected), 2))[0] == args.skill
        assert win.u32(process, selected+0x28) == 1
        px, py = player["position"]["x"], player["position"]["y"]
        path = win.u32(process, cp+0x2C); room = win.u32(process, path+0x1C)
        grid = win.u32(process, room+0x34)
        gx, gy, gw, gh = struct.unpack("<4i", win.read(process, grid, 16))
        cells = win.u32(process, grid+0x20)
        assert 0 < gw <= 512 and 0 < gh <= 512
        tx = ty = None
        for dx, dy in [(1,0), (0,1), (0,-1), (-1,0), (1,1), (1,-1), (-1,1), (-1,-1)]:
            clear = True
            for n in range(1, 13):
                x, y = px+dx*n-gx, py+dy*n-gy
                if not (0 <= x < gw and 0 <= y < gh) or struct.unpack("<H", win.read(process, cells+2*(y*gw+x), 2))[0] & 0x127:
                    clear = False; break
            if clear: tx, ty = px+dx*8, py+dy*8; break
        assert tx is not None, "No clear initial corridor"
        identity = dict(client_player=cp, game=None, skill=args.skill, level=1, x=px, y=py, target_x=tx, target_y=ty)
        write(args.output / "identity.json", identity)
        windows = [w for w in win.windows(desktop, args.pid) if w["class"] == "Diablo II"]
        assert len(windows) == 1
        hwnd = windows[0]["hwnd"]
        camx = struct.unpack("<i", win.read(process, base+0x10B9BC, 4))[0] - struct.unpack("<i", win.read(process, base+0x10B9C8, 4))[0]
        camy = struct.unpack("<i", win.read(process, base+0x10B9B8, 4))[0]
        sx, sy = 16*(tx-ty)-camx, 8*(tx+ty)+8-camy
        assert 16 <= sx < 784 and 16 <= sy < 480
        point = (sy << 16) | sx
        win.post(hwnd, 0x200, 0, point); win.post(hwnd, 0x204, 2, point); pressed = True
        start = time.monotonic()
        cls = 477 if args.skill == 240 else 478
        last_count = 0
        with (args.output / "missiles.jsonl").open("w", encoding="utf8") as out:
            while time.monotonic()-start < 10.0:
                elapsed = time.monotonic()-start
                if pressed and elapsed >= .16:
                    win.post(hwnd, 0x205, 0, point); pressed = False
                assert win.u32(process, base+0x11C200) == cp, "Client player changed"
                cast_started |= win.u32(process, cp+0x10) == 10
                units, visited = [], set()
                for table in [base+0x11AA00+3*128*4, base+0x11BC00]:
                    for head in struct.unpack("<128I", win.read(process, table, 512)):
                        unit = head; chain = set()
                        while unit:
                            if unit in chain or len(chain) > 4096: raise ValueError("Invalid client hash chain")
                            chain.add(unit)
                            try:
                                raw = struct.unpack("<59I", win.read(process, unit, 0xEC))
                                if raw[0] == 3 and raw[1] == cls and unit not in visited:
                                    visited.add(unit)
                                    p = win.read(process, raw[11], 0x9C)
                                    md = win.read(process, raw[5], 0x34)
                                    total, current = struct.unpack_from("<2h", md, 0xE)
                                    # Reject reads which definitely straddled an update or recycled unit.
                                    if win.read(process, unit, 16) != struct.pack("<4I", *raw[:4]) or win.read(process, raw[5]+0x10, 2) != md[0x10:0x12]:
                                        rejected += 1
                                    else:
                                        vx, vy = struct.unpack_from("<2i", p, 0x72)
                                        state = dict(side="client", snapshot_index=snapshots, elapsed_ms=round(elapsed*1000),
                                            unit=unit, class_id=cls, unit_id=raw[3], x_fixed=struct.unpack_from("<I", p, 0)[0],
                                            y_fixed=struct.unpack_from("<I", p, 4)[0], velocity_x=vx, velocity_y=vy,
                                            path_direction=p[0x64], path_new_direction=p[0x65], total_frames=total,
                                            current_frame=current, unit_44=raw[17], unit_48=raw[18], unit_4c=raw[19],
                                            seed_low=raw[8], seed_high=raw[9])
                                        if 0 <= current <= total and total > 0:
                                            units.append(state); seen.add(raw[3]); out.write(json.dumps(state)+"\n")
                                unit = raw[57]
                            except OSError:
                                rejected += 1; break
                last_count = len(units); snapshots += 1
                out.flush(); time.sleep(.005)
        assert cast_started, "No client cast observed"
        assert len(seen) == (3 if args.skill == 240 else 1), "Unexpected client missile count"
        assert last_count == 0, "Client wind missiles remain at boundary"
        report.update(status="passed", final_client_units=last_count)
    except Exception as error:
        report.update(status="failed", error=str(error))
    finally:
        if pressed and hwnd is not None: win.post(hwnd, 0x205, 0, point)
        if process: win.close(process)
        report.update(client_unique={str(477 if args.skill == 240 else 478):len(seen)},
            server_unique={}, client_samples=None, polling_snapshots=snapshots, rejected_racy_reads=rejected,
            cast_started=cast_started, cleanup_confirmed=True)
        write(args.output / "summary.json", report)
    return 0 if report["status"] == "passed" else 1


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pid", type=int, required=True)
    parser.add_argument("--home", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--skill", type=int, choices=[240,245], required=True)
    raise SystemExit(collect(parser.parse_args()))
