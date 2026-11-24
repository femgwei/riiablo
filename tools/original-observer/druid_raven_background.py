"""Finite Raven private-desktop launch and read-only summon capture."""
import argparse,ctypes as C,json,shutil,subprocess,sys,time,traceback,uuid
from ctypes import wintypes as W
from pathlib import Path
from observer import Win32, STARTUPINFO, PROCESS_INFORMATION, api, digest
from orchestrator import LOADER_NAME,existing_loader_pids,wait_loader_ready
from arctic_blast_background import desktop_name,input_desktop_name,write_json
from prepare_druid_batch_fixture import prepare
ROOT=Path(__file__).resolve().parent

def run_private(home,output,raven_level=1,raven_casts=1,raven_cast_spacing=.18,raven_seconds=16,raven_advance=False,raven_harness=False,raven_profile="attack-natural"):
    output.mkdir(parents=True,exist_ok=False);win=Win32();private=child=None
    report=dict(status="starting",background_probe="druid-raven",raven_level=raven_level,batches=[],switched_desktop=False)
    report["input_desktop_before"]=input_desktop_name(win)
    try:
        assert not existing_loader_pids(); name="riiablo_raven_"+uuid.uuid4().hex[:12]
        private=win.checked(win.create_desktop(name,None,None,0,0x01FF,None)); report["private_desktop"]=name
        startup=STARTUPINFO(); startup.cb=C.sizeof(startup); startup.lpDesktop=name
        startup.dwFlags=1; startup.wShowWindow=0
        args=[sys.executable,str(Path(__file__).resolve()),"--worker","--home",str(home),"--output",str(output/"worker"),"--raven-level",str(raven_level),"--raven-casts",str(raven_casts),"--raven-cast-spacing",str(raven_cast_spacing),"--raven-seconds",str(raven_seconds)]
        if raven_advance: args.append("--raven-advance")
        if raven_harness: args += ["--raven-harness","--raven-profile",raven_profile]
        command=C.create_unicode_buffer(subprocess.list2cmdline(args));info=PROCESS_INFORMATION()
        win.checked(win.create_process(sys.executable,command,None,None,False,subprocess.CREATE_NO_WINDOW,None,str(ROOT),C.byref(startup),C.byref(info)))
        child=info.hProcess; win.close(info.hThread)
        report["worker_pid"]=info.dwProcessId;write_json(output/"orchestrator.json",report)
        started=time.monotonic()
        while win.wait(child,0)!=0:
            assert time.monotonic()-started<700 and input_desktop_name(win)!=name;time.sleep(.5)
        code=W.DWORD();win.checked(win.exit_code(child,C.byref(code)));report["worker_exit_code"]=code.value
        report["worker"]=json.loads((output/"worker"/"summary.json").read_text(encoding="utf8"));report["status"]="passed" if code.value==0 else "failed"
        if report["worker"].get("cleanup_attention_required"):
            report["session_left_running_for_cleanup"]=True
    except Exception as e: report.update(status="failed",error=str(e),traceback=traceback.format_exc())
    finally:
        report["input_desktop_after"]=input_desktop_name(win)
        if private and (child is None or win.wait(child,0)==0): win.close_desktop(private)
        if child: win.close(child)
        write_json(output/"orchestrator.json",report)
    return 0 if report["status"]=="passed" else 1

def worker(home,output,raven_level=1,raven_casts=1,raven_cast_spacing=.18,raven_seconds=16,raven_advance=False,raven_harness=False,raven_profile="attack-natural"):
    output.mkdir(parents=True,exist_ok=False);win=Win32();game=helper=native_proc=None;report=dict(status="starting",raven_level=raven_level,raven_harness=raven_harness,cleanup_attention_required=False)
    native_dir=output/"native-target"
    native_out=native_err=None
    try:
        desk=win.get_thread_desktop(api(win.k,"GetCurrentThreadId",W.DWORD)());assert desktop_name(win,desk).startswith("riiablo_raven_")
        assert desktop_name(win,desk) != input_desktop_name(win)
        assert not existing_loader_pids(), "Another original is already running"
        report["fixture"]=prepare(home,output,raven=True,raven_level=raven_level)
        game=subprocess.Popen([str(home/LOADER_NAME),"-w","-res800"],cwd=str(home),creationflags=subprocess.CREATE_NO_WINDOW)
        report["pid"]=game.pid;report["readiness"]=wait_loader_ready(game.pid,180)
        entry=output/"entry"
        entry_args=[sys.executable,str(ROOT/"menu-driver.py"),"--pid",str(game.pid),"--home",str(home),"--output",str(entry),"--seconds","240","--visible-run","--auto-single-player","--character","CXDruRaven","--capture-controls"]
        if raven_profile != "town-orbit": entry_args += ["--walk-to-level","2"]
        else: entry_args.append("--no-walk")
        entry_result=subprocess.run(entry_args,cwd=str(ROOT),creationflags=subprocess.CREATE_NO_WINDOW,timeout=260)
        if entry_result.returncode:
            raise RuntimeError("Private-desktop automatic entry failed: %d" % entry_result.returncode)
        entry_report=json.loads((entry/"summary.json").read_text(encoding="utf8"))
        if entry_report.get("scene",{}).get("level_id") != (1 if raven_profile == "town-orbit" else 2):
            raise RuntimeError("Private-desktop automatic entry did not reach experiment level")
        report["entry"]=entry_report
        if raven_harness:
            provenance=output/"provenance";provenance.mkdir()
            for source in (ROOT/"native-harness"/"build"/"d2game_raven_target.exe",
                           ROOT/"native-harness"/"d2game_raven_target.cpp",
                           ROOT/"native-harness"/"d2game_fallen_death.cpp",
                           ROOT/"native-harness"/"d2game_controller.cpp",
                           ROOT/"druid_raven_passive.py",Path(__file__).resolve()):
                shutil.copy2(source,provenance/source.name)
            native=provenance/"d2game_raven_target.exe"
            report["native_sha256"]=digest(native)
            native_out=(output/"native-target-stdout.log").open("w",encoding="utf8")
            native_err=(output/"native-target-stderr.log").open("w",encoding="utf8")
            native_proc=subprocess.Popen([str(native),str(game.pid),str(home),str(native_dir),raven_profile],cwd=str(ROOT),creationflags=subprocess.CREATE_NO_WINDOW,stdout=native_out,stderr=native_err)
            report["cleanup_attention_required"]=True
            report["native_target_pid"]=native_proc.pid
            identity=native_dir/"identity.json"; deadline=time.monotonic()+60
            while not identity.exists():
                if native_proc.poll() is not None: raise RuntimeError("Raven target harness exited before identity")
                if time.monotonic()>deadline: raise TimeoutError("Timed out waiting for Raven target harness identity")
                time.sleep(.1)
            report["native_target_identity"]=json.loads(identity.read_text(encoding="utf8"))
        collector=(provenance if raven_harness else ROOT)/"druid_raven_passive.py";capture=output/"capture";collector_args=[sys.executable,str(collector),"--pid",str(game.pid),"--home",str(home),"--output",str(capture),"--casts",str(raven_casts),"--cast-spacing",str(raven_cast_spacing),"--seconds",str(raven_seconds)]
        if raven_harness:
            # Frozen collector dependencies resolve from the repository directory.
            for dependency in ("observer.py","arctic_blast_passive.py","arctic_blast_background.py","druid_storm_passive.py","scene_reader.py","orchestrator.py","collector_specs.py","compare_barbarian_formulas.py"):
                shutil.copy2(ROOT/dependency,provenance/dependency)
        if raven_advance: collector_args.append("--advance")
        collector_args += ["--expected-level",str(raven_level)]
        helper=subprocess.Popen(collector_args,cwd=str(ROOT),creationflags=subprocess.CREATE_NO_WINDOW);helper.wait(timeout=raven_seconds+25);report["capture"]=json.loads((capture/"summary.json").read_text(encoding="utf8"));assert report["capture"]["status"]=="passed"
        if native_proc:
            write_json(native_dir/"collector-done.json",dict(status=report["capture"]["status"]))
            native_proc.wait(timeout=90)
            report["native_target_exit_code"]=native_proc.returncode
            native_summary=native_dir/"summary.json"
            if native_summary.exists(): report["native_target"]=json.loads(native_summary.read_text(encoding="utf8"))
            native_report=report.get("native_target",{})
            report["cleanup_attention_required"]=not (native_report.get("cleanup_confirmed") and native_report.get("detached"))
            if report["cleanup_attention_required"]: raise RuntimeError("Native debugger cleanup is unresolved")
            if native_proc.returncode: raise RuntimeError("Raven target harness failed")
        report["status"]="passed"
    except Exception as e: report.update(status="failed",error=str(e),traceback=traceback.format_exc())
    finally:
        if helper and helper.poll() is None: helper.terminate();helper.wait()
        if native_proc:
            if native_proc.poll() is None:
                if native_dir.exists(): write_json(native_dir/"collector-done.json",dict(status="worker_exit"))
                try: native_proc.wait(timeout=145)
                except subprocess.TimeoutExpired: report["cleanup_attention_required"]=True
            summary_path=native_dir/"summary.json"
            if summary_path.exists():
                report["native_target"]=json.loads(summary_path.read_text(encoding="utf8"))
                report["cleanup_attention_required"]=not (native_proc.poll() is not None and report["native_target"].get("cleanup_confirmed") and report["native_target"].get("detached"))
            elif native_proc.poll() is not None:
                report["cleanup_attention_required"]=True
        for stream in (native_out,native_err):
            if stream: stream.close()
        if game and game.poll() is None and not report["cleanup_attention_required"]:
            for w in win.windows(desk,game.pid):
                if w["class"]=="Diablo II": win.post(w["hwnd"],0x10,0,0)
            try: game.wait(timeout=8)
            except subprocess.TimeoutExpired: game.terminate();game.wait()
        report["owned_game_closed"]=game is None or game.poll() is not None;write_json(output/"summary.json",report)
    return 0 if report["status"]=="passed" else 1

if __name__=="__main__":
    p=argparse.ArgumentParser();p.add_argument("--worker",action="store_true",required=True);p.add_argument("--home",type=Path,required=True);p.add_argument("--output",type=Path,required=True);p.add_argument("--raven-level",type=int,default=1);p.add_argument("--raven-casts",type=int,default=1);p.add_argument("--raven-cast-spacing",type=float,default=.18);p.add_argument("--raven-seconds",type=float,default=16);p.add_argument("--raven-advance",action="store_true");p.add_argument("--raven-harness",action="store_true");p.add_argument("--raven-profile",default="attack-natural");a=p.parse_args();raise SystemExit(worker(a.home,a.output,a.raven_level,a.raven_casts,a.raven_cast_spacing,a.raven_seconds,a.raven_advance,a.raven_harness,a.raven_profile) if a.worker else run_private(a.home,a.output,a.raven_level,a.raven_casts,a.raven_cast_spacing,a.raven_seconds,a.raven_advance,a.raven_harness,a.raven_profile))
