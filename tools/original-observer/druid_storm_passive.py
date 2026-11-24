"""Read-only state/effect snapshots driven by private-HWND casts; bounded to 22s."""
import argparse
import ctypes as C
from ctypes import wintypes as W
from collections import Counter
import json
from pathlib import Path
import struct
import time

from observer import Win32, api, digest, player_snapshot
from scene_reader import scene_snapshot
from arctic_blast_passive import HASHES, LOADER_HASH, LOADER_NAME, overlay_snapshot, write, input_desktop_name


def state_active(win, process, player, state):
    stats = win.u32(process, player + 0x5C)
    if not stats or not win.u32(process, stats + 0x10) & 0x80000000: return False
    flags = win.u32(process, stats + 0x58)
    return bool(flags and win.u32(process, flags + state // 32 * 4) & (1 << (state % 32)))


def selected_skill(win, process, player):
    skills = win.u32(process, player + 0xA8)
    selected = win.u32(process, skills + 0xC)
    return (struct.unpack('<H', win.read(process, win.u32(process, selected), 2))[0],
            win.u32(process, selected + 0x28))


def collect(args):
    args.output.mkdir(parents=True, exist_ok=False)
    win, process, hwnd = Win32(), None, None
    seen, last, rejected, scans, samples = {}, {}, 0, 0, 0
    report = dict(status='starting', skill=args.skill, memory_writes=False, remote_execution=False,
        debugger_attached=False, global_input=False, cleanup_confirmed=False,
        scope='Non-atomic client state/path/graphics observations; no runtime stacks, GPU pixels or damage evidence')
    pressed = False
    try:
        desktop = win.get_thread_desktop(api(win.k, 'GetCurrentThreadId', W.DWORD)())
        name_buffer, needed = C.create_unicode_buffer(256), W.DWORD()
        win.checked(api(win.u,'GetUserObjectInformationW',W.BOOL,W.HANDLE,C.c_int,C.c_void_p,W.DWORD,C.POINTER(W.DWORD))(
            desktop,2,name_buffer,C.sizeof(name_buffer),C.byref(needed)))
        report['desktop'] = name_buffer.value
        assert report['desktop'].startswith('riiablo_storm_') and report['desktop'] != input_desktop_name(win)
        process = win.checked(api(win.k, 'OpenProcess', W.HANDLE, W.DWORD, W.BOOL, W.DWORD)(0x400 | 0x10 | 0x100000, False, args.pid))
        image, size = C.create_unicode_buffer(32768), W.DWORD(32768)
        win.checked(api(win.k, 'QueryFullProcessImageNameW', W.BOOL, W.HANDLE, W.DWORD, W.LPWSTR, C.POINTER(W.DWORD))(
            process, 0, image, C.byref(size)))
        assert Path(image.value).resolve() == (args.home / LOADER_NAME).resolve()
        assert digest(Path(image.value)) == LOADER_HASH
        modules = win.modules(args.pid)
        for dll, expected in HASHES.items():
            assert Path(modules[dll.lower()]['path']).resolve() == (args.home / dll).resolve()
            assert digest(args.home / dll) == expected
        base = modules['d2client.dll']['base']
        player = player_snapshot(win, process, modules)
        assert player['name'] == 'CXDruState' and player['class_id'] == 5
        assert scene_snapshot(win, process, modules, player)['level_id'] == 2
        cp = player['pointer']
        state = 144 if args.skill == 250 else 145
        classes = set(range(581, 587)) if args.skill == 250 else {577, 578, 579, 580, 623}
        assert not state_active(win, process, cp, state), 'Previous state has not expired'
        windows = [w for w in win.windows(desktop, args.pid) if w['class'] == 'Diablo II']
        assert len(windows) == 1
        hwnd = windows[0]['hwnd']
        hotkey = 0x70 if args.skill == 250 else 0x71
        win.post(hwnd, 0x100, hotkey, 0); win.post(hwnd, 0x101, hotkey, 0)
        deadline = time.perf_counter() + 3
        while selected_skill(win, process, cp) != (args.skill, 1):
            assert time.perf_counter() < deadline, 'Hotkey did not select requested skill'
            time.sleep(.02)
        write(args.output / 'identity.json', dict(player=player, state_id=state, classes=sorted(classes),
            selected_skill=selected_skill(win, process, cp), hashes=HASHES))
        point = (220 << 16) | 420
        start = time.perf_counter()
        win.post(hwnd, 0x200, 0, point); win.post(hwnd, 0x204, 2, point); pressed = True
        refreshed, move_attempted, state_seen, inactive_after = False, False, False, False
        caster, events = [], [dict(event='cast_down', elapsed_ms=0)]
        with (args.output / 'missiles.jsonl').open('w', encoding='utf8') as out:
            while time.perf_counter() - start < 22:
                elapsed = time.perf_counter() - start
                assert win.u32(process, base + 0x11C200) == cp, 'Client player changed'
                if pressed and elapsed >= (.12 if not refreshed else 7.32):
                    win.post(hwnd, 0x205, 0, point); pressed = False
                    events.append(dict(event='cast_up', elapsed_ms=round(elapsed*1000)))
                if not refreshed and elapsed >= 7.2:
                    win.post(hwnd, 0x200, 0, point); win.post(hwnd, 0x204, 2, point)
                    pressed = refreshed = True
                    events.append(dict(event='refresh_down', elapsed_ms=round(elapsed*1000)))
                active = state_active(win, process, cp, state)
                state_seen |= active
                inactive_after |= state_seen and not active and elapsed > 9
                path_ptr = win.u32(process, cp + 0x2C)
                xy = struct.unpack('<2I', win.read(process, path_ptr, 8))
                if not move_attempted and elapsed >= 3:
                    # Attempt a short ordinary walk only if its corridor is known clear.
                    room = win.u32(process, path_ptr + 0x1C)
                    grid = win.u32(process, room + 0x34)
                    gx, gy, gw, gh = struct.unpack('<4i', win.read(process, grid, 16))
                    assert 0 < gw <= 512 and 0 < gh <= 512
                    cells = win.u32(process, grid + 0x20)
                    x, y = xy[0] >> 16, xy[1] >> 16
                    destination = None
                    for dx, dy in ((1,0),(0,1),(-1,0),(0,-1)):
                        if all(0 <= x+dx*n-gx < gw and 0 <= y+dy*n-gy < gh and
                            not struct.unpack('<H',win.read(process,cells+2*((y+dy*n-gy)*gw+x+dx*n-gx),2))[0]&0x127
                            for n in range(1,5)):
                            tx, ty = x+dx*4, y+dy*4
                            camx = struct.unpack('<i',win.read(process,base+0x10B9BC,4))[0]-struct.unpack('<i',win.read(process,base+0x10B9C8,4))[0]
                            camy = struct.unpack('<i',win.read(process,base+0x10B9B8,4))[0]
                            sx, sy = 16*(tx-ty)-camx, 8*(tx+ty)+8-camy
                            if 16 <= sx < 784 and 16 <= sy < 480:
                                move = (sy << 16) | sx
                                win.post(hwnd,0x200,0,move); win.post(hwnd,0x201,1,move); win.post(hwnd,0x202,0,move)
                                destination = [tx,ty]; break
                    events.append(dict(event='walk_attempt',elapsed_ms=round(elapsed*1000),destination=destination))
                    move_attempted = True
                frame_before = win.u32(process, base + 0x107794)
                units, visited = [], set()
                for table in (base+0x11B000, base+0x11BC00):
                    for head in struct.unpack('<128I', win.read(process, table, 512)):
                        unit, chain = head, set()
                        while unit:
                            if unit in chain or len(chain) >= 4096: raise ValueError('Invalid unit hash chain')
                            chain.add(unit)
                            try:
                                raw = struct.unpack('<59I',win.read(process,unit,0xEC))
                                next_unit = raw[57]
                                if raw[0] == 3 and raw[1] in classes and unit not in visited:
                                    visited.add(unit)
                                    p = win.read(process,raw[11],0x9C)
                                    md = win.read(process,raw[5],0x34)
                                    total, current = struct.unpack_from('<2h',md,0xE)
                                    graphics = win.u32(process,raw[21]+0x30) if raw[21] else 0
                                    gfx = list(struct.unpack('<19i',win.read(process,graphics,0x4C))) if graphics else None
                                    if win.read(process,unit,16) != struct.pack('<4I',*raw[:4]) or win.read(process,raw[5]+0x10,2) != md[0x10:0x12]:
                                        rejected += 1
                                    elif total > 0 and 0 <= current <= total:
                                        key = (raw[1],raw[3])
                                        pose = (current,raw[17],raw[18],raw[19],p[:8],p[0x64:0x66],tuple(gfx or ()))
                                        item = dict(class_id=raw[1],unit_id=raw[3],unit=unit,
                                            x_fixed=struct.unpack_from('<I',p)[0],y_fixed=struct.unpack_from('<I',p,4)[0],
                                            velocity=struct.unpack_from('<2i',p,0x72), direction=p[0x64],new_direction=p[0x65],
                                            total_frames=total,current_frame=current,animation=list(raw[17:20]),
                                            owner_type=struct.unpack_from('<i',md,0x18)[0],owner_id=struct.unpack_from('<I',md,0x1C)[0],
                                            graphics_aux=gfx, missile_data_hex=md.hex())
                                        units.append(key)
                                        if key not in seen: seen[key] = dict(first_ms=round(elapsed*1000),first_age=total-current)
                                        seen[key]['last_ms'] = round(elapsed*1000)
                                        if last.get(key) != pose:
                                            item.update(snapshot=scans,elapsed_ms=round(elapsed*1000),client_frame=frame_before)
                                            out.write(json.dumps(item)+'\n'); samples += 1; last[key] = pose
                                unit = next_unit
                            except OSError:
                                rejected += 1; break
                frame_after = win.u32(process, base + 0x107794)
                caster.append(dict(snapshot=scans,elapsed_ms=round(elapsed*1000),active=active,
                    player_fixed=xy,mode=win.u32(process,cp+0x10),frame_before=frame_before,frame_after=frame_after,
                    endpoint_frames_equal=frame_before==frame_after,units=len(units),
                    overlays=overlay_snapshot(win,process,cp)))
                scans += 1
                time.sleep(.01)
        write(args.output / 'caster.json',caster)
        write(args.output / 'events.json',events)
        write(args.output / 'lifetimes.json',[dict(class_id=k[0],unit_id=k[1],**v) for k,v in seen.items()])
        assert state_seen and inactive_after, 'No complete state activation/expiry observed'
        assert seen, 'No effect units observed'
        assert not caster[-1]['active'] and caster[-1]['units'] == 0, 'State/effects remain after bounded expiry'
        report.update(status='passed',state_seen=state_seen,state_expired=inactive_after,final_units=caster[-1]['units'],
            unique_by_class=dict(Counter(str(k[0]) for k in seen)),
            player_moved=len({tuple(c['player_fixed']) for c in caster})>1,
            refresh_input_sent=refreshed,refresh_effective_not_asserted=True)
    except Exception as error:
        import traceback
        report.update(status='failed',error=str(error),traceback=traceback.format_exc())
    finally:
        if pressed and hwnd: win.post(hwnd,0x205,0,point)
        if process: win.close(process)
        report.update(cleanup_confirmed=True,polling_scans=scans,samples=samples,rejected_racy_reads=rejected,
            clock='perf_counter/QPC',sampling='10ms sleep between non-atomic scans; changed poses only')
        write(args.output / 'summary.json',report)
    return 0 if report['status']=='passed' else 1


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pid',type=int,required=True)
    parser.add_argument('--home',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--skill',type=int,choices=(249,250),required=True)
    raise SystemExit(collect(parser.parse_args()))
