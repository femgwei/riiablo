"""Bounded read-only Raven summon polling on a private desktop."""
import argparse
import ctypes as C
from ctypes import wintypes as W
import json
from pathlib import Path
import struct
import time

from observer import Win32, api, digest, player_snapshot
from scene_reader import scene_snapshot
from arctic_blast_passive import HASHES, LOADER_HASH, LOADER_NAME, overlay_snapshot, input_desktop_name
from druid_storm_passive import selected_skill


def write(path, value): path.write_text(json.dumps(value, indent=2)+"\n", encoding="utf8")


def desktop_name(win, handle):
    buf=C.create_unicode_buffer(256); needed=W.DWORD()
    win.checked(api(win.u,"GetUserObjectInformationW",W.BOOL,W.HANDLE,C.c_int,C.c_void_p,W.DWORD,C.POINTER(W.DWORD))(
        handle,2,buf,C.sizeof(buf),C.byref(needed)))
    return buf.value


def target_feedback(win, process, unit):
    """Read only the pinned extended stat-list layout; client snapshots are non-atomic."""
    ex = win.u32(process, unit + 0x5C)
    if not ex or not win.u32(process, ex + 0x10) & 0x80000000:
        raise ValueError("Target has no extended stat list")
    flags = win.u32(process, ex + 0x58)
    result = dict(dimvision=bool(flags and win.u32(process, flags) & (1 << 23)),
                  dimvision_list=False, overlays=overlay_snapshot(win, process, unit))
    for offset in (0x3C, 0x40):
        node = win.u32(process, ex + offset)
        seen = set()
        while node:
            if node in seen or len(seen) >= 1024:
                raise ValueError("Target stat list cycle")
            seen.add(node)
            if win.u32(process, node + 0x14) == 23:
                result.update(dimvision_list=True, dimvision_expiry=win.u32(process, node + 0x18),
                    dimvision_skill=win.u32(process, node + 0x1C), dimvision_level=win.u32(process, node + 0x20))
                return result
            node = win.u32(process, node + 0x2C)
    return result


def scan_units(win, process, base, room_units=()):
    result={}
    # D2Client keeps several 128-bucket unit tables.  Raven is a type-1
    # monster summon, but the exact table can vary with the loader build, so
    # scan the two tables used by the storm collector plus the adjacent table
    # used by older 1.10 client layouts and de-duplicate by (type, unit id).
    for table in (base+0x11B000, base+0x11BC00, base+0x11AA00, base+0x11AB00):
        for head in struct.unpack("<128I",win.read(process,table,512)):
            unit=head; chain=set()
            while unit:
                if unit in chain or len(chain)>4096: break
                chain.add(unit)
                try:
                    raw=struct.unpack("<59I",win.read(process,unit,0xEC)); nxt=raw[57]
                    if raw[0] in (0,1,2,3,4,5):
                        item=dict(pointer=unit,type=raw[0],class_id=raw[1],unit_id=raw[3],mode=raw[4],
                            animation_raw=raw[17],animation_frame=raw[18],animation_rate=raw[19]&0xFFFF,
                            x_fixed=0,y_fixed=0,table_rva=table-base)
                        if raw[11]:
                            path=win.read(process,raw[11],0x9C)
                            item.update(x_fixed=struct.unpack_from("<I",path,0)[0],y_fixed=struct.unpack_from("<I",path,4)[0],
                                direction=path[0x64],new_direction=path[0x65])
                        result[(raw[0],raw[3])]=item
                    unit=nxt
                except OSError: break
    # Room lists include existing target monsters that the partial hash scan
    # misses. Never filter them by whether they predate the cast.
    for known in room_units:
        if known["type"] != 1: continue
        try:
            unit=known["pointer"]; raw=struct.unpack("<59I",win.read(process,unit,0xEC))
            if (raw[0],raw[1],raw[3]) != (1,known["class_id"],known["unit_id"]): continue
            path=win.read(process,raw[11],0x9C)
            if struct.unpack_from("<I",path,0x30)[0] != unit: continue
            result[(1,raw[3])]=dict(pointer=unit,type=1,class_id=raw[1],unit_id=raw[3],mode=raw[4],
                animation_raw=raw[17],animation_frame=raw[18],animation_rate=raw[19]&0xFFFF,
                x_fixed=struct.unpack_from("<I",path,0)[0],y_fixed=struct.unpack_from("<I",path,4)[0],
                direction=path[0x64],new_direction=path[0x65],source="room_list")
        except (OSError, ValueError): continue
    return result


def collect(args):
    args.output.mkdir(parents=True,exist_ok=False)
    win=Win32(); process=None; hwnd=None; pressed=False
    report=dict(status="starting",skill=221,memory_writes=False,remote_execution=False,debugger_attached=False,
        global_input=False,cleanup_confirmed=False,scope="Private HWND cast and read-only unit snapshots; no runtime stacks or pixels")
    try:
        desktop=win.get_thread_desktop(api(win.k,"GetCurrentThreadId",W.DWORD)())
        report["desktop"]=desktop_name(win,desktop); assert report["desktop"].startswith("riiablo_raven_")
        assert report["desktop"] != input_desktop_name(win)
        process=win.checked(api(win.k,"OpenProcess",W.HANDLE,W.DWORD,W.BOOL,W.DWORD)(0x400|0x10|0x100000,False,args.pid))
        image,size=C.create_unicode_buffer(32768),W.DWORD(32768)
        win.checked(api(win.k,"QueryFullProcessImageNameW",W.BOOL,W.HANDLE,W.DWORD,W.LPWSTR,C.POINTER(W.DWORD))(process,0,image,C.byref(size)))
        assert Path(image.value).resolve()==(args.home/LOADER_NAME).resolve() and digest(Path(image.value))==LOADER_HASH
        modules=win.modules(args.pid)
        for dll,expected in HASHES.items():
            assert Path(modules[dll.lower()]["path"]).resolve() == (args.home/dll).resolve()
            assert digest(args.home/dll)==expected
        base=modules["d2client.dll"]["base"]; player=player_snapshot(win,process,modules)
        assert player["status"]=="player" and player["name"]=="CXDruRaven" and player["class_id"]==5
        cp=player["pointer"]
        initial_scene=scene_snapshot(win,process,modules,player)
        room_units=initial_scene.get("loaded_units",[])
        before=scan_units(win,process,base,room_units)
        windows=[w for w in win.windows(desktop,args.pid) if w["class"]=="Diablo II"]; assert len(windows)==1; hwnd=windows[0]["hwnd"]
        client_width,client_height=windows[0]["width"],windows[0]["height"]
        if args.advance:
            # Move farther into Blood Moor before casting so the Raven has a
            # chance to acquire a naturally spawned monster.  This remains a
            # private-HWND message sequence and never activates the desktop.
            advance_point=(min(client_height-80,300)<<16)|min(client_width-40,600)
            win.post(hwnd,0x200,0,advance_point); win.post(hwnd,0x201,1,advance_point)
            time.sleep(2.5); win.post(hwnd,0x202,0,advance_point); time.sleep(.5)
        scene=scene_snapshot(win,process,modules,player)
        scene_targets=[u for u in scene.get("loaded_units",[]) if u.get("type")==1
                       and u.get("class_id") != 419 and u.get("position")]
        preferred=[u for u in scene_targets if u.get("class_id") in (19,63)]
        if preferred:
            scene_targets=preferred+ [u for u in scene_targets if u not in preferred]
        if args.advance and scene_targets:
            target=scene_targets[0]; tx,ty=target["position"]["x"],target["position"]["y"]
            camx=struct.unpack("<i",win.read(process,base+0x10B9BC,4))[0]-struct.unpack("<i",win.read(process,base+0x10B9C8,4))[0]
            camy=struct.unpack("<i",win.read(process,base+0x10B9B8,4))[0]
            sx,sy=16*(tx-ty)-camx,8*(tx+ty)+8-camy
            if 16 <= sx < client_width-16 and 16 <= sy < client_height-16:
                point=(int(sy)<<16)|int(sx)
                win.post(hwnd,0x200,0,point); win.post(hwnd,0x201,1,point)
                time.sleep(1.5); win.post(hwnd,0x202,0,point); time.sleep(.5)
        # F1 is the Raven right-hand hotkey in the fixed fixture.  Raven is a
        # targeted cast, so select the hotkey and send one private-HWND right
        # click; no global input or foreground activation is involved.
        point=(min(300,client_height-100)<<16)|min(400,client_width-40)
        deadline=time.perf_counter()+3
        win.post(hwnd,0x100,0x70,0);win.post(hwnd,0x101,0x70,0)
        while selected_skill(win,process,cp)[0] != 221:
            assert time.perf_counter() < deadline, "F1 did not select Raven"
            time.sleep(.02)
        selected=selected_skill(win,process,cp)
        report["selected_skill_before_cast"]=list(selected)
        if args.expected_level is not None:
            assert selected == (221,args.expected_level), "Loaded Raven level does not match installed fixture"
        for _ in range(args.casts):
            win.post(hwnd,0x100,0x70,0);win.post(hwnd,0x101,0x70,0)
            deadline=time.perf_counter()+3
            while selected_skill(win,process,cp) != selected:
                assert time.perf_counter() < deadline, "F1 did not select Raven"
                time.sleep(.02)
            win.post(hwnd,0x200,0,point);win.post(hwnd,0x204,2,point);pressed=True
            time.sleep(.12)
            win.post(hwnd,0x205,0,point);pressed=False
            time.sleep(args.cast_spacing)
        start=time.perf_counter(); samples=[]; seen={}; raven_samples=[]; target_samples=[]; refreshed=0
        identity_path = args.output.parent / "native-target/identity.json"
        fixed_target = json.loads(identity_path.read_text(encoding="utf8"))["target_id"] if identity_path.exists() else None
        rejected_feedback = 0
        with (args.output/"units.jsonl").open("w",encoding="utf8") as out:
            while time.perf_counter()-start<args.seconds:
                if time.perf_counter()-refreshed >= .25:
                    room_units=scene_snapshot(win,process,modules,player).get("loaded_units",[])
                    refreshed=time.perf_counter()
                current=scan_units(win,process,base,room_units); elapsed=round((time.perf_counter()-start)*1000)
                for key,item in current.items():
                    if item["type"] == 1 and item["class_id"] != 419:
                        target_sample = dict(elapsed_ms=elapsed,unit_id=item["unit_id"],
                            class_id=item["class_id"],mode=item["mode"],
                            x_fixed=item["x_fixed"],y_fixed=item["y_fixed"])
                        if item["unit_id"] == fixed_target:
                            try:
                                target_sample.update(target_feedback(win, process, item["pointer"]),
                                    unvalidated_client_counter=win.u32(process, base+0x107794))
                            except (OSError, ValueError):
                                rejected_feedback += 1
                        target_samples.append(target_sample)
                    if key in before and key not in seen: continue
                    item.update(elapsed_ms=elapsed,local_new=key not in before)
                    seen[key]=item;out.write(json.dumps(item)+"\n")
                    if item["type"] == 1 and item["class_id"] == 419:
                        raven_samples.append(dict(elapsed_ms=elapsed, unit_id=item["unit_id"],
                            mode=item["mode"], animation_raw=item["animation_raw"],
                            animation_frame=item["animation_frame"], animation_rate=item["animation_rate"],
                            x_fixed=item["x_fixed"], y_fixed=item["y_fixed"],
                            direction=item.get("direction"), new_direction=item.get("new_direction"),
                            owner_type=item.get("owner_type"), owner_id=item.get("owner_id")))
                samples.append(dict(elapsed_ms=elapsed,units=len(current),new_units=len([k for k in current if k not in before]),
                    player_mode=win.u32(process,cp+0x10),overlays=overlay_snapshot(win,process,cp)))
                time.sleep(.02)
        write(args.output/"samples.json",samples); write(args.output/"units.json",list(seen.values()))
        write(args.output/"raven-samples.json",raven_samples)
        write(args.output/"target-samples.json",target_samples)
        new=[x for k,x in seen.items() if k not in before]
        summoned=[x for x in new if x["type"] == 1 and x["class_id"] == 419]
        assert summoned,"No newly created druid hawk candidate observed after Raven cast"
        attacks={}
        previous={}
        for sample in raven_samples:
            key=sample["unit_id"]; mode=sample["mode"]
            if key not in previous: previous[key]=mode; attacks[key]=0
            elif mode == 4 and previous[key] != 4: attacks[key]+=1
            previous[key]=mode
        report.update(status="passed",baseline_units=len(before),new_units_count=len(new),
            new_class_ids=sorted({x["class_id"] for x in new}), new_units=new,
            raven_candidates=summoned, owner_unit_id=player["unit_id"],
            seconds=args.seconds, casts=args.casts, scene_target_candidates=[dict(unit_id=u["unit_id"],class_id=u["class_id"],position=u.get("position")) for u in scene_targets], raven_sample_count=len(raven_samples),
            raven_attack_mode=4,
            fixed_target_id=fixed_target, rejected_target_feedback=rejected_feedback,
            raven_attack_entries=attacks, nearby_target_unit_ids=sorted({x["unit_id"] for x in target_samples}),
            raven_owner_ids=sorted({x.get("owner_id") for x in raven_samples if x.get("owner_id") is not None}),
            raven_mode_values=sorted({x["mode"] for x in raven_samples}),
            raven_animation_raw_values=sorted({x["animation_raw"] for x in raven_samples}),
            final_units=len(scan_units(win,process,base)),polling_samples=len(samples),clock="perf_counter/QPC")
    except Exception as error:
        import traceback;report.update(status="failed",error=str(error),traceback=traceback.format_exc())
    finally:
        if pressed and hwnd: win.post(hwnd,0x205,0,0)
        if process: win.close(process)
        report["cleanup_confirmed"]=True;write(args.output/"summary.json",report)
    return 0 if report["status"]=="passed" else 1


if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__);p.add_argument("--pid",type=int,required=True);p.add_argument("--home",type=Path,required=True);p.add_argument("--output",type=Path,required=True);p.add_argument("--casts",type=int,default=1);p.add_argument("--cast-spacing",type=float,default=.18);p.add_argument("--seconds",type=float,default=16);p.add_argument("--advance",action="store_true")
    p.add_argument("--expected-level",type=int)
    raise SystemExit(collect(p.parse_args()))
