"""Bounded private-desktop client-input Arctic Blast hold/release and read-only client missile polling.

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


def publication_caster(win, process, player, base):
    """Read-only phase markers; matching endpoints still do not make a scan atomic."""
    mode = win.u32(process, player + 0x10)
    seed = struct.unpack('<2I', win.read(process, player + 0x20, 8))
    sequence = struct.unpack('<8I', win.read(process, player + 0x30, 32))
    # Pinned D2Client+87D57 increments this counter in its client allocator.
    counter = win.u32(process, base + 0xF08FC)
    path = win.u32(process, player + 0x2C)
    target = struct.unpack('<2H', win.read(process, path + 0x10, 4))
    return dict(mode=mode, sequence_cursor=sequence[2], source_cursor=sequence[5],
        remaining=sequence[6], action_frame=(sequence[7] >> 16) & 255,
        seed_low=seed[0], seed_high=seed[1], client_allocation_counter=counter,
        client_frame=win.u32(process, base + 0x107794), target_x=target[0], target_y=target[1])


def overlay_snapshot(win, process, player):
    """Same hash-pinned +54/+2C/+A4 layout retained by the Enchant collector."""
    graphics = win.u32(process, player+0x54)
    if not graphics:
        return []
    node = win.u32(process, graphics+0x2C)
    seen, result = set(), []
    while node:
        if node in seen or len(seen) >= 64:
            raise ValueError('Invalid overlay list')
        seen.add(node)
        raw = win.read(process, node, 0xA8)
        values = struct.unpack_from('<12I', raw)
        next_node = struct.unpack_from('<I', raw, 0xA4)[0]
        # Reject definite recycling; remaining mixed-update reads are still possible.
        if win.read(process, node, 8) != raw[:8]:
            raise ValueError('Overlay recycled during snapshot')
        result.append(dict(pointer=node, overlay_type=values[0], overlay_id=values[1],
            value_08=values[2], value_0c=values[3], value_10=values[4],
            frame_delta=values[5], cursor=values[6], total_cursor=values[7],
            value_20=values[8], value_24=values[9], draw_mode=values[10], value_2c=values[11]))
        node = next_node
    return result


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
        assert name_buffer.value.startswith("riiablo_arctic_")
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
        report['client_game_type'] = win.u32(process, base + 0x107960)
        assert win.read(process, base+0x883D0, 6) == b"\xa1" + struct.pack("<I", base+0x11C200) + b"\xc3"
        player = player_snapshot(win, process, modules)
        assert player["status"] == "player" and player["name"] == "CXArctic" and player["class_id"] == 5
        assert scene_snapshot(win, process, modules, player)["level_id"] == 2
        cp = player["pointer"]
        tables = win.u32(process, win.u32(process, base+0xCD4AC))
        skill_row = win.u32(process, tables+0xB98) + 230*0x23C
        assert struct.unpack('<H', win.read(process, skill_row, 2))[0] == 230
        overlay_id = struct.unpack('<h', win.read(process, skill_row+0x10C, 2))[0]
        assert 0 <= overlay_id < win.u32(process, tables+0xBC0)
        overlay_row = win.read(process, win.u32(process, tables+0xBBC)+overlay_id*0x84, 0x84)
        filename = overlay_row[2:66].split(b'\0')[0].decode('ascii')
        assert filename.lower() == 'expansion\\coldwindoverlay'
        report['progress_overlay'] = dict(id=overlay_id, filename=filename,
            frames=struct.unpack_from('<I',overlay_row,0x44)[0],
            rate=struct.unpack_from('<I',overlay_row,0x6C)[0], trans=overlay_row[0x7C])
        report['baseline_overlays'] = overlay_snapshot(win,process,cp)
        skills = win.u32(process, cp+0xA8); selected = win.u32(process, skills+0xC)
        assert struct.unpack("<H", win.read(process, win.u32(process, selected), 2))[0] == 230
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
        identity = dict(client_player=cp, game=None, skill=230, level=1, x=px, y=py, target_x=tx, target_y=ty)
        identity["hold_ms_requested"] = args.hold_ms
        identity["release_after_births"] = args.release_after_births
        turn_target = None
        if args.turn_after_births is not None:
            assert args.release_after_births == 15
            dx, dy = (tx-px)//8, (ty-py)//8
            for nx, ny in [(-dy,dx),(dy,-dx)]:
                if all(0 <= px+nx*n-gx < gw and 0 <= py+ny*n-gy < gh and
                    not (struct.unpack('<H',win.read(process,cells+2*((py+ny*n-gy)*gw+px+nx*n-gx),2))[0]&0x127)
                    for n in range(1,13)):
                    turn_target = [px+nx*8,py+ny*8];break
            assert turn_target is not None, 'No perpendicular clear corridor for turn capture'
        identity['turn_target'] = turn_target
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
        start = time.perf_counter()
        classes = (459, 460)
        last_count = 0
        caster_states = []
        publication_states = []
        overlay_states, overlay_rejected = [], 0
        release_ms = None
        with (args.output / "missiles.jsonl").open("w", encoding="utf8") as out:
            while time.perf_counter()-start < 6.0:
                elapsed = time.perf_counter()-start
                if pressed and args.hold_ms is not None and elapsed >= args.hold_ms / 1000:
                    win.post(hwnd, 0x205, 0, point); pressed = False; release_ms = round(elapsed * 1000)
                assert win.u32(process, base+0x11C200) == cp, "Client player changed"
                mode = win.u32(process, cp+0x10)
                cast_started |= mode == 18  # Player SQ, not ordinary SC (10).
                sequence = struct.unpack('<8I', win.read(process, cp+0x30, 32))
                caster = dict(snapshot_index=snapshots, elapsed_ms=round(elapsed*1000),
                    held=pressed, mode=mode, sequence_pointer=sequence[0],
                    sequence_length=sequence[1], sequence_cursor=sequence[2],
                    sequence_speed=sequence[3], sequence_mode=sequence[4],
                    cursor=sequence[5], anim_length=sequence[6], anim_rate=sequence[7] & 0xFFFF)
                stats = win.u32(process, cp+0x5C)
                state_bits = 0
                if stats and win.u32(process, stats+0x10) & 0x80000000:
                    flags = win.u32(process, stats+0x58)
                    if flags: state_bits = win.u32(process, flags)
                caster['inferno_state'] = bool(state_bits & (1 << 12))
                caster_states.append(caster)
                try:
                    overlay_states.append(dict(snapshot_index=snapshots, elapsed_ms=round(elapsed*1000),
                        mode=mode, held=pressed, overlays=overlay_snapshot(win,process,cp)))
                except (OSError,ValueError):
                    overlay_rejected += 1
                if sequence[0] and 'sequence_table_hex' not in report:
                    common_base = modules['d2common.dll']['base']
                    assert common_base <= sequence[0] < common_base + 0x100000
                    report['sequence_table_rva'] = sequence[0] - common_base
                    report['sequence_table_hex'] = win.read(process, sequence[0], 15*6).hex()
                phase_before_ns = time.perf_counter_ns()
                phase_before = publication_caster(win, process, cp, base)
                units, visited = [], set()
                for table in [base+0x11AA00+3*128*4, base+0x11BC00]:
                    for head in struct.unpack("<128I", win.read(process, table, 512)):
                        unit = head; chain = set()
                        while unit:
                            if unit in chain or len(chain) > 4096: raise ValueError("Invalid client hash chain")
                            chain.add(unit)
                            try:
                                raw = struct.unpack("<59I", win.read(process, unit, 0xEC))
                                if raw[0] == 3 and raw[1] in classes and unit not in visited:
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
                                            observed_perf_counter_ns=time.perf_counter_ns(), hash_table_rva=table-base,
                                            unit=unit, class_id=raw[1], unit_id=raw[3], x_fixed=struct.unpack_from("<I", p, 0)[0],
                                            y_fixed=struct.unpack_from("<I", p, 4)[0], velocity_x=vx, velocity_y=vy,
                                            path_direction=p[0x64], path_new_direction=p[0x65], total_frames=total,
                                            current_frame=current, unit_44=raw[17], unit_48=raw[18], unit_4c=raw[19],
                                            unit_tick=raw[53], missile_data_08=struct.unpack_from('<I',md,8)[0],
                                            owner_type=struct.unpack_from('<i',md,0x18)[0],
                                            owner_id=struct.unpack_from('<I',md,0x1C)[0],
                                            seed_low=raw[8], seed_high=raw[9])
                                        if 0 <= current <= total and total > 0:
                                            units.append(state); seen.add((raw[1], raw[3])); out.write(json.dumps(state)+"\n")
                                unit = raw[57]
                            except OSError:
                                rejected += 1; break
                phase_after = publication_caster(win, process, cp, base)
                phase_after_ns = time.perf_counter_ns()
                publication_states.append(dict(snapshot_index=snapshots,
                    scan_before_perf_counter_ns=phase_before_ns, scan_after_perf_counter_ns=phase_after_ns,
                    before=phase_before, after=phase_after,
                    endpoint_markers_equal=phase_before == phase_after,
                    units=[dict(unit=s['unit'], unit_id=s['unit_id'], class_id=s['class_id'],
                        native_age=s['total_frames']-s['current_frame'],
                        hash_table_rva=s['hash_table_rva']) for s in units]))
                if (pressed and turn_target is not None and 'turn_boundary' not in report
                        and len(seen) >= args.turn_after_births and phase_before == phase_after
                        and phase_after['action_frame'] == 1):
                    sx, sy = 16*(turn_target[0]-turn_target[1])-camx, 8*sum(turn_target)+8-camy
                    assert 16 <= sx < 784 and 16 <= sy < 480
                    point = (sy << 16) | sx
                    win.post(hwnd, 0x200, 2, point)
                    report['turn_boundary'] = dict(snapshot_index=snapshots,observed_births=len(seen),
                        elapsed_ms=round((time.perf_counter()-start)*1000),phase=phase_after,target=turn_target)
                if pressed and args.release_after_births is not None and len(seen) >= args.release_after_births:
                    assert len(seen) == args.release_after_births, 'Missed requested missile-birth boundary'
                    win.post(hwnd, 0x205, 0, point); pressed = False
                    release_ms = round((time.perf_counter()-start)*1000)
                    report['release_boundary'] = dict(observed_births=len(seen),snapshot_index=snapshots,
                        elapsed_ms=release_ms,sequence_cursor=win.u32(process,cp+0x38),
                        source_cursor=win.u32(process,cp+0x44),remaining=win.u32(process,cp+0x48))
                last_count = len(units); snapshots += 1
                out.flush()
                # Bounded yield-only burst avoids Windows' ~15 ms sleep granularity
                # around initial creation. Never changes system timer resolution.
                if elapsed < 2.1 and snapshots < 1800:
                    next_scan = time.perf_counter() + .001
                    while time.perf_counter() < next_scan:
                        time.sleep(0)
                else:
                    time.sleep(.005)
        write(args.output / "caster.json", caster_states)
        write(args.output / "publication.json", publication_states)
        write(args.output / "overlays.json", overlay_states)
        report['overlay_snapshots'] = len(overlay_states)
        report['sampling_profile'] = '1ms minimum yield-paced first2.1s/max1800 scans, then sleep5ms; no timer-resolution change'
        report['observation_clock'] = dict(name='perf_counter',
            resolution_seconds=time.get_clock_info('perf_counter').resolution,
            implementation=time.get_clock_info('perf_counter').implementation)
        report['overlay_rejected_reads'] = overlay_rejected
        report['overlay_ids_observed'] = sorted({o['overlay_id'] for s in overlay_states for o in s['overlays']})
        report["release_ms_actual"] = release_ms
        assert release_ms is not None, 'Requested input-release boundary was not reached'
        if turn_target is not None: assert 'turn_boundary' in report, 'Turn boundary was not reached'
        assert cast_started, "No client cast observed"
        assert len(seen) >= 2, "Insufficient client ice stream births"
        assert last_count == 0, "Client ice streams remain at boundary"
        report.update(status="passed", final_client_units=last_count)
    except Exception as error:
        report.update(status="failed", error=str(error))
    finally:
        if pressed and hwnd is not None: win.post(hwnd, 0x205, 0, point)
        if process: win.close(process)
        report.update(client_unique={str(cls):sum(1 for c, _ in seen if c == cls) for cls in (459, 460)},
            server_unique={}, client_samples=None, polling_snapshots=snapshots, rejected_racy_reads=rejected,
            cast_started=cast_started, cleanup_confirmed=True)
        write(args.output / "summary.json", report)
    return 0 if report["status"] == "passed" else 1


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pid", type=int, required=True)
    parser.add_argument("--home", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    release = parser.add_mutually_exclusive_group(required=True)
    release.add_argument("--hold-ms", type=int, choices=[640,1600])
    release.add_argument("--release-after-births", type=int, choices=[3,15])
    parser.add_argument('--turn-after-births',type=int,choices=[3])
    raise SystemExit(collect(parser.parse_args()))
