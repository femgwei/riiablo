// Two ordinary self-Enchant casts on a private desktop; passive overlay probes.
#define D2_CONTROLLER_LIBRARY
#include "d2game_controller.cpp"
#include <array>
#pragma comment(lib, "user32.lib")

struct EnchantController : Controller {
  U call(CONTEXT entry, U returnTo, U function, std::initializer_list<U> values) {
    require(held && !pendingCall && values.size()<=4,"Fixed getter boundary");
    scratch=entry.Esp-0x100;
    std::vector<U> args{returnTo}; args.insert(args.end(),values.begin(),values.end());
    scratchOriginal.resize(args.size()*4); read(scratch,scratchOriginal.data(),scratchOriginal.size());
    callRestore=entry; pendingCall=true; write(scratch,args.data(),args.size()*4); arm(returnTo);
    CONTEXT c=entry; c.Eip=function; c.Esp=scratch; set(c); resume();
    CONTEXT result=waitBreakpoint(returnTo,3000);
    require(result.Esp==scratch+args.size()*4,"Getter stack imbalance");
    write(scratch,scratchOriginal.data(),scratchOriginal.size()); set(entry); pendingCall=false;
    return result.Eax;
  }
};
struct Window {DWORD pid; HWND hwnd=nullptr;};
static BOOL CALLBACK findWindow(HWND hwnd,LPARAM arg) {
  auto& w=*reinterpret_cast<Window*>(arg); DWORD pid=0; GetWindowThreadProcessId(hwnd,&pid);
  wchar_t klass[64]{}; GetClassNameW(hwnd,klass,64);
  if(pid==w.pid && std::wstring(klass)==L"Diablo II") {w.hwnd=hwnd;return FALSE;} return TRUE;
}
static U enchantList(EnchantController& d,U unit) {
  U ex=d.get(unit+0x5C); require(ex && (d.get(ex+0x10)&0x80000000u),"Extended stat list");
  for(U listOffset:{0x3Cu,0x40u}) {
    std::set<U> seen;
    for(U list=d.get(ex+listOffset);list;list=d.get(list+0x2C)) {
      require(seen.insert(list).second && seen.size()<1024,"Stat list cycle");
      if(d.get(list+0x14)==16) return list;
    }
  }
  return 0;
}
static int stat(EnchantController& d,U unit,U id) {
  U ex=d.get(unit+0x5C), array=d.get(ex+0x48), count=d.get<std::uint16_t>(ex+0x4C);
  require(count<2048,"Stat array bound");
  for(U i=0;i<count;i++) if(d.get(array+i*8)==(id<<16)) return d.get<int>(array+i*8+4);
  return 0;
}
static void snapshot(EnchantController& d,std::ofstream& out,const char* side,U unit,U frame,U step) {
  U list=enchantList(d,unit);
  out<<"{\"side\":\""<<side<<"\",\"step\":"<<step<<",\"game_frame\":"<<frame
     <<",\"unit_id\":"<<d.get(unit+0xC)<<",\"mode\":"<<d.get(unit+0x10)
     <<",\"unit44\":"<<d.get(unit+0x44)<<",\"unit48\":"<<d.get(unit+0x48)<<",\"unit4c\":"<<d.get(unit+0x4C)
     <<",\"enchant_state\":"<<(list?"true":"false")<<",\"state_list\":"<<list
     <<",\"expiry_frame\":"<<(list?d.get(list+0x18):0)<<",\"state_skill\":"<<(list?d.get(list+0x1C):0)
     <<",\"state_level\":"<<(list?d.get(list+0x20):0)<<",\"fire_min\":"<<stat(d,unit,48)
     <<",\"fire_max\":"<<stat(d,unit,49)<<",\"attack_rating_percent\":"<<stat(d,unit,119)
     <<",\"mana_fixed\":"<<stat(d,unit,8)<<"}\n";
}

int wmain(int argc,wchar_t** argv) {
  SetErrorMode(SEM_FAILCRITICALERRORS|SEM_NOGPFAULTERRORBOX|SEM_NOOPENFILEERRORBOX);
  if(argc!=5 || std::wstring(argv[4])!=L"-1") return 2;
  EnchantController d; bool success=false,created=false;
  U creates=0,createReturns=0,draws=0,drawReturns=0,overlaySamples=0,firstFrame=0,lastFrame=0;
  std::string error; auto output=std::filesystem::absolute(argv[3]);
  try {
    created=std::filesystem::create_directory(output); require(created,"New output required");
    auto root=std::filesystem::absolute(argv[2]); d.pid=std::stoul(argv[1]);
    d.process=OpenProcess(PROCESS_QUERY_INFORMATION|PROCESS_VM_READ|PROCESS_VM_WRITE|PROCESS_VM_OPERATION|SYNCHRONIZE,FALSE,d.pid);
    require(d.process!=nullptr,"Open original");
    wchar_t image[32768]{}; DWORD length=32768;
    require(QueryFullProcessImageNameW(d.process,0,image,&length),"Process identity");
    require(std::filesystem::equivalent(image,root/L"D2v110各版本免CD全功能启动.exe"),"Wrong loader");
    fileHash(root/L"D2Game.dll","2bd92fb25e3e4b8269930712620ba9db00cdc1aa44569d87dc4aba76553272b6");
    fileHash(root/L"D2Common.dll","b9d1a27c6a35992932c7c47438d02e5269f878e899f6e614d507902cecd1beab");
    fileHash(root/L"D2Client.dll","94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282");
    Module gm=module(d.pid,L"D2Game.dll",root),cl=module(d.pid,L"D2Client.dll",root),co=module(d.pid,L"D2Common.dll",root);
    U entry=gm.base+0x86D0,cpGlobal=cl.base+0x11C200,getStat=co.base+0x77C30;
    signature(d,entry,{0x83,0xEC,8,0x53,0x56,0x57,0x8B,0xF9,0xBB,1,0,0,0});
    signature(d,getStat,{0x8B,0x44,0x24,4,0x55,0x85,0xC0,0x75,4,0x5D,0xC2,0x0C,0});
    signature(d,cl.base+0x88400,{0x85,0xC9,0x75,0x03,0x33,0xC0,0xC3,0x8B,0x41,0x54,0xC3});
    enableDebugPrivilege(); require(DebugActiveProcess(d.pid),"Attach"); d.attached=true;
    require(DebugSetProcessKillOnExit(FALSE),"Disable kill on exit");
    d.waitBreakpoint(0,3000,true); d.arm(entry); d.resume();
    CONTEXT original=d.waitBreakpoint(entry,5000); d.gameThread=d.event.dwThreadId;
    U game=original.Ecx,returnTo=d.get(original.Esp),player=0;
    require(returnTo>=gm.base && returnTo<gm.base+gm.size,"Update caller");
    require(d.get<BYTE>(returnTo-5)==0xE8 && returnTo+d.get(returnTo-4)==entry,"Update caller signature");
    validateGame(d,game,127897359,player);
    char name[16]{}; d.read(d.get(player+0x14),name,16);
    require(std::strcmp(name,"CXEnchant")==0 && d.get(player+4)==1,"Separate Sorceress fixture");
    U skill=d.get(d.get(player+0xA8)+0xC);
    require(skill && d.get<std::uint16_t>(d.get(skill))==52 && d.get(skill+0x28)==1,"Selected level-one Enchant");
    require(!enchantList(d,player),"Fresh Enchant state required");
    require(d.get(d.get(player+0x60)+0xC)!=0,"Equipped weapon fixture required");
    U path=d.get(player+0x2C),room=d.get(path+0x1C);
    require(d.get(d.get(d.get(room+0x38))+4)==2,"Blood Moor required");
    firstFrame=d.get(game+0xA8);
    std::ofstream identity(output/"identity.json");
    identity<<"{\"skill\":52,\"level\":1,\"map_seed\":127897359,\"cast_steps\":[0,45],\"sample_ticks\":100,\"x\":"
            <<d.get<std::uint16_t>(path+2)<<",\"y\":"<<d.get<std::uint16_t>(path+6)<<",\"player\":"<<player<<"}";
    identity.close();
    U createEntry=cl.base+0xBBA70,drawEntry=cl.base+0xCB850,drawReturn=0;
    signature(d,createEntry,{0x83,0xEC,0x10,0x53,0x55,0x56,0x57,0x8B,0xF2,0x8B,0xF9});
    signature(d,drawEntry,{0xFF,0x25}); // D2Gfx cell-draw import thunk, all callers
    std::ofstream events(output/"overlay-events.jsonl"); U createReturn=0,pendingCreate=0,pendingDraw=0;
    d.observeOtherThreads=true; d.observedEntry=createEntry; d.observedAlternative=drawEntry;
    d.observeOnce=[&](CONTEXT c) {
      if(c.Eip==createEntry) {
        require(!createReturn,"Unexpected nested overlay creation"); createReturn=d.get(c.Esp);
        pendingCreate=0;
        if(c.Ecx==d.get(cpGlobal)) {
          pendingCreate=++creates;
          events<<"{\"kind\":\"create\",\"index\":"<<creates<<",\"game_frame\":"<<d.get(game+0xA8)
                <<",\"overlay_id\":"<<c.Edx<<",\"unit\":"<<c.Ecx<<",\"caller_rva\":"<<createReturn-cl.base<<"}\n";
        }
        d.observedEntry=createReturn; d.arm(createReturn);
      } else if(c.Eip==createReturn) {
        if(pendingCreate) {events<<"{\"kind\":\"create_returned\",\"index\":"<<pendingCreate<<"}\n";createReturns++;}
        createReturn=0; pendingCreate=0; d.observedEntry=createEntry; d.arm(createEntry);
      } else if(c.Eip==drawEntry) {
        require(!drawReturn,"Unexpected nested cell draw");
        drawReturn=d.get(c.Esp); pendingDraw=0;
        U cell=d.get(c.Esp+4); std::array<U,18> raw{}; SIZE_T read=0;
        if(ReadProcessMemory(d.process,reinterpret_cast<void*>(cell),raw.data(),sizeof raw,&read) && read==sizeof raw && raw[17]) {
          char cellName[128]{};
          if(ReadProcessMemory(d.process,reinterpret_cast<void*>(raw[17]),cellName,sizeof(cellName)-1,&read)
              && std::strcmp(cellName,"FireEnchant")==0) {
            pendingDraw=++draws;
            events<<"{\"kind\":\"draw\",\"index\":"<<draws<<",\"game_frame\":"<<d.get(game+0xA8)
              <<",\"frame\":"<<raw[2]<<",\"cell_name\":\"FireEnchant\",\"caller_rva\":"<<drawReturn-cl.base
              <<",\"draw_mode\":"<<d.get(c.Esp+20)<<",\"x\":"<<d.get<int>(c.Esp+8)<<",\"y\":"<<d.get<int>(c.Esp+12)
              <<",\"cell_raw\":[";
            for(size_t i=0;i<raw.size();i++)events<<(i?",":"")<<raw[i];
            events<<"]}\n";
          }
        }
        d.observedAlternative=drawReturn; d.arm(drawReturn);
      } else {
        require(c.Eip==drawReturn,"Unexpected overlay hook");
        if(pendingDraw) {events<<"{\"kind\":\"draw_returned\",\"index\":"<<pendingDraw<<"}\n"; drawReturns++;}
        pendingDraw=0; drawReturn=0; d.observedAlternative=drawEntry; d.arm(drawEntry);
      }
      events.flush();
    };
    d.arm(createEntry); d.arm(drawEntry);
    Window window{d.pid}; EnumWindows(findWindow,reinterpret_cast<LPARAM>(&window));
    require(window.hwnd!=nullptr,"Private game window");
    // Empty ground targets fall back to the caster in native SrvDo025.
    int px=d.get<std::uint16_t>(path+2),py=d.get<std::uint16_t>(path+6);
    int cameraX=d.get<int>(cl.base+0x10B9BC)-d.get<int>(cl.base+0x10B9C8),cameraY=d.get<int>(cl.base+0x10B9B8);
    int sx=16*(px-py)-cameraX+48,sy=8*(px+py)+8-cameraY;
    require(sx>=16 && sx<784 && sy>=16 && sy<480,"Input playfield bounds");
    LPARAM point=MAKELPARAM(sx,sy); std::ofstream states(output/"states.jsonl"),overlayStates(output/"overlay-states.jsonl");
    for(U step=0;step<=100;step++) {
      U frame=d.get(game+0xA8); lastFrame=frame; require(frame==firstFrame+step,"Contiguous server frames");
      if(step==0 || step==45) {
        require(PostMessageW(window.hwnd,WM_MOUSEMOVE,0,point),"Private mouse point");
        require(PostMessageW(window.hwnd,WM_RBUTTONDOWN,MK_RBUTTON,point),"Private cast down");
      }
      if(step==2 || step==47) require(PostMessageW(window.hwnd,WM_RBUTTONUP,0,point),"Private cast release");
      U cp=d.get(cpGlobal); require(cp && cp!=player && d.get(cp)==0 && d.get(cp+4)==1,"Separate client player");
      snapshot(d,states,"server",player,frame,step); snapshot(d,states,"client",cp,frame,step); states.flush();
      // The verified 88400 getter returns unit+54; allocation links overlays
      // at gfx+2C and uses +A4 as next. Read the native clock independently
      // of whether this unshown desktop submits overlay pixels.
      U gfx=d.get(cp+0x54); require(gfx!=0,"Client graphics data");
      std::set<U> seen;
      for(U overlay=d.get(gfx+0x2C);overlay;overlay=d.get(overlay+0xA4)) {
        require(seen.insert(overlay).second && seen.size()<256,"Overlay list cycle");
        overlaySamples++;
        overlayStates<<"{\"step\":"<<step<<",\"game_frame\":"<<frame<<",\"overlay\":"<<overlay
          <<",\"overlay_type\":"<<d.get(overlay)<<",\"overlay_id\":"<<d.get(overlay+4)
          <<",\"frame_delta\":"<<d.get(overlay+0x14)<<",\"cursor\":"<<d.get(overlay+0x18)
          <<",\"total_cursor\":"<<d.get(overlay+0x1C)<<",\"draw_mode\":"<<d.get(overlay+0x28)<<"}\n";
      }
      overlayStates.flush();
      if(step==0 || step==30 || step==80) {
        for(U id:{8u,48u,49u,119u}) require(d.call(original,returnTo,getStat,{player,id,0})==static_cast<U>(stat(d,player,id)),"Raw stat read differs from native getter");
      }
      if(step==100) break;
      d.arm(returnTo); d.set(original); d.resume(); CONTEXT returned=d.waitBreakpoint(returnTo,3000);
      require(d.get(game+0xA8)==frame+1,"Update skipped frame");
      d.arm(entry); d.set(returned); d.resume(); original=d.waitBreakpoint(entry,3000);
      require(original.Ecx==game && d.get(original.Esp)==returnTo,"Update context changed");
    }
    require(enchantList(d,player)!=0 && stat(d,player,48)>0 && stat(d,player,49)>0,"Enchant state and damage observed");
    require(creates>=2 && creates==createReturns && overlaySamples>0,"Two completed activations and native overlay clocks required");
    require(draws==drawReturns,"Incomplete overlay draw call");
    success=true;
  } catch(const std::exception& e) {error=e.what(); std::cerr<<error<<'\n';}
  bool cleaned=d.cleanup();
  auto save=[&]() {
    if(!created) return;
    std::ofstream out(output/"summary.json");
    out<<"{\"status\":\""<<(success&&cleaned?"passed":"failed")<<"\",\"first_frame\":"<<firstFrame<<",\"last_frame\":"<<lastFrame
       <<",\"overlay_creates\":"<<creates<<",\"overlay_create_returns\":"<<createReturns<<",\"overlay_draws\":"<<draws<<",\"overlay_draw_returns\":"<<drawReturns
       <<",\"overlay_state_samples\":"<<overlaySamples
       <<",\"detached\":"<<(d.detached?"true":"false")<<",\"cleanup_confirmed\":"<<(cleaned?"true":"false")
       <<",\"cleanup_attention_required\":"<<(cleaned?"false":"true")<<",\"error\":\""<<error
       <<"\",\"scope\":\"original client-input casts, stat getters cross-checked with raw state lists, passive activation overlay calls; no GPU pixel or target hit parity\"}";
  };
  save(); while(d.attached && !cleaned){Sleep(1000);cleaned=d.cleanup();}save();
  return success&&cleaned?0:1;
}
