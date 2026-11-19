// One bounded original 1.10f Whirlwind cast, on the real update thread.
// A temporary native skill node and disposable monster are removed on success.
#define D2_CONTROLLER_LIBRARY
#include "d2game_controller.cpp"
#include <array>

struct WhirlwindController : Controller {
  std::map<U, std::function<void(CONTEXT)>> observers;
  U stepping = 0;

  CONTEXT wait(U expected, DWORD timeout) {
    const auto deadline = GetTickCount64() + timeout;
    while (GetTickCount64() < deadline) {
      require(!held, "Unconsumed WW event");
      if (!WaitForDebugEvent(&event, 100)) {
        require(GetLastError() == ERROR_SEM_TIMEOUT, "Wait WW event"); continue;
      }
      held = true; handles();
      if (event.dwDebugEventCode == EXIT_PROCESS_DEBUG_EVENT) throw std::runtime_error("Target exited");
      if (event.dwDebugEventCode != EXCEPTION_DEBUG_EVENT) { resume(); continue; }
      const auto& exception = event.u.Exception.ExceptionRecord;
      U at = reinterpret_cast<U>(exception.ExceptionAddress);
      if (exception.ExceptionCode == EXCEPTION_SINGLE_STEP && stepping) {
        require(event.dwThreadId == gameThread, "WW step on wrong thread");
        CONTEXT c = context(); c.EFlags &= ~0x100u; set(c);
        arm(stepping); stepping = 0; resume(); continue;
      }
      if (exception.ExceptionCode == EXCEPTION_BREAKPOINT && patches.count(at)) {
        require(event.dwThreadId == gameThread, "WW breakpoint on wrong thread");
        CONTEXT c = context(); c.Eip = at; set(c); disarm(at);
        if (at == expected) return c;
        auto callback = observers.find(at);
        require(callback != observers.end(), "Unexpected WW breakpoint");
        callback->second(c);
        // Execute the restored original instruction exactly once, then arm
        // this observer again. This retains multiple attacks in the same tick.
        stepping = at; c.EFlags |= 0x100u; set(c); resume(); continue;
      }
      resume(DBG_EXCEPTION_NOT_HANDLED);
      throw std::runtime_error("Unexpected WW native exception " + std::to_string(exception.ExceptionCode));
    }
    throw std::runtime_error("WW breakpoint timeout");
  }

  U call(CONTEXT entry, U returnTo, U function, U ecx, U edx, std::initializer_list<U> stack) {
    require(held && !pendingCall && !stepping && stack.size() <= 5, "Invalid WW call boundary");
    scratch = entry.Esp - 0x100;
    std::vector<U> args{ returnTo }; args.insert(args.end(), stack.begin(), stack.end());
    scratchOriginal.resize(args.size() * 4); read(scratch, scratchOriginal.data(), scratchOriginal.size());
    callRestore = entry; pendingCall = true;
    write(scratch, args.data(), scratchOriginal.size()); arm(returnTo);
    CONTEXT c = entry; c.Eip = function; c.Esp = scratch; c.Ecx = ecx; c.Edx = edx; set(c); resume();
    CONTEXT result = wait(returnTo, 4000);
    require(result.Esp == scratch + args.size() * 4, "WW call stack imbalance");
    write(scratch, scratchOriginal.data(), scratchOriginal.size()); set(entry); pendingCall = false;
    return result.Eax;
  }

  bool finishDebugger() {
    if (held && stepping) {
      CONTEXT c = context(); c.EFlags &= ~0x100u; set(c); stepping = 0;
    }
    return cleanup();
  }
};

static std::pair<U,U> position(WhirlwindController& d, U unit) {
  U path = d.get(unit + 0x2C);
  require(path && d.get(path + 0x30) == unit, "Invalid WW dynamic path");
  return {d.get<std::uint16_t>(path + 2), d.get<std::uint16_t>(path + 6)};
}

struct Arena { U targetX, targetY, destinationX, destinationY; };
static Arena arena(WhirlwindController& d, U room, U px, U py, const std::filesystem::path& output) {
  U grid = d.get(room + 0x34), cells = d.get(grid + 0x20);
  int x = d.get<int>(grid), y = d.get<int>(grid + 4), w = d.get<int>(grid + 8), h = d.get<int>(grid + 12);
  require(cells && w > 0 && h > 0 && w <= 512 && h <= 512, "Invalid WW collision grid");
  std::vector<std::uint16_t> flags(w*h); d.read(cells, flags.data(), flags.size()*2);
  std::ofstream dump(output / "collision.csv"); dump << "x,y,flags\n";
  for (int cy=0;cy<h;cy++) for (int cx=0;cx<w;cx++) dump << x+cx << ',' << y+cy << ',' << flags[cy*w+cx] << '\n';
  auto clear = [&](int tx, int ty, int radius, U mask) {
    for (int ox=-radius;ox<=radius;ox++) for (int oy=-radius;oy<=radius;oy++) {
      int cx=tx+ox-x, cy=ty+oy-y;
      if (cx<0 || cy<0 || cx>=w || cy>=h || (flags[cy*w+cx] & mask)) return false;
    }
    return true;
  };
  for (int length=20;length>=12;length--) {
    for (auto dir : std::vector<std::pair<int,int>>{{1,0},{0,1},{-1,0},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}}) {
      int tx=static_cast<int>(px)+dir.first*3, ty=static_cast<int>(py)+dir.second*3;
      if (!clear(tx,ty,1,0x3FAF)) continue;
      bool valid=true;
      for (int i=1;i<=length;i++) if (!clear(px+dir.first*i,py+dir.second*i,1,0x401)) { valid=false; break; }
      if (valid) return {static_cast<U>(tx),static_cast<U>(ty),px+dir.first*length,py+dir.second*length};
    }
  }
  throw std::runtime_error("No same-room 12..20 subtile Whirlwind corridor");
}

int wmain(int argc, wchar_t** argv) {
  SetErrorMode(SEM_FAILCRITICALERRORS | SEM_NOGPFAULTERRORBOX | SEM_NOOPENFILEERRORBOX);
  if (argc != 4) return 2;
  WhirlwindController d; bool created=false, success=false, fixturesClean=false, started=false, finished=false;
  U startFrame=0,lastFrame=0,target=0,targetId=0,damageEvents=0,totalDamage=0,initialHP=0,lastHP=0;
  U initialMana=0,lowestMana=0,startCalls=0,doCalls=0,firstDamageFrame=0,endFrame=0;
  auto output=std::filesystem::absolute(argv[3]);
  try {
    created=std::filesystem::create_directory(output); require(created,"Output must be new");
    const auto root=std::filesystem::absolute(argv[2]); d.pid=std::stoul(argv[1]);
    d.process=OpenProcess(PROCESS_QUERY_INFORMATION|PROCESS_CREATE_THREAD|PROCESS_VM_READ|PROCESS_VM_WRITE|PROCESS_VM_OPERATION|PROCESS_SUSPEND_RESUME|SYNCHRONIZE,FALSE,d.pid);
    require(d.process!=nullptr,"Open WW process");
    wchar_t image[32768]{}; DWORD count=32768;
    require(QueryFullProcessImageNameW(d.process,0,image,&count),"WW process identity");
    require(std::filesystem::equivalent(image,root/L"D2v110各版本免CD全功能启动.exe"),"Wrong WW loader");
    fileHash(root/L"D2Game.dll","2bd92fb25e3e4b8269930712620ba9db00cdc1aa44569d87dc4aba76553272b6");
    fileHash(root/L"D2Common.dll","b9d1a27c6a35992932c7c47438d02e5269f878e899f6e614d507902cecd1beab");
    auto gm=module(d.pid,L"D2Game.dll",root), cm=module(d.pid,L"D2Common.dll",root);
    U entry=gm.base+0x86D0,cast=gm.base+0x517D0,spawn=gm.base+0x3A090,remove=gm.base+0x8AEE0;
    U deleteEvent=gm.base+0x4840,damageEntry=gm.base+0x91260,wwStart=gm.base+0xCBFE0,wwDo=gm.base+0xCC4C0;
    U getStat=cm.base+0x77C30,setStat=cm.base+0x77AB0,addSkill=cm.base+0x70320,removeSkill=cm.base+0x6FFD0,getBody=cm.base+0x4FAE0;
    signature(d,entry,{0x83,0xEC,0x08,0x53,0x56,0x57,0x8B,0xF9,0xBB,1,0,0,0});
    signature(d,cast,{0x53,0x55,0x56,0x57,0x8B,0xF9,0x8B,0xF2});
    signature(d,spawn,{0x83,0xEC,0x28,0x56,0x8B,0x74,0x24,0x38});
    signature(d,remove,{0x53,0x56,0x57,0x8B,0xF9,0x33,0xDB,0x8B,0xF2});
    signature(d,wwStart,{0x83,0xEC,0x3C,0x53,0x8B,0x5C,0x24,0x44});
    signature(d,wwDo,{0x81,0xEC,0x84,0,0,0,0x53,0x55});
    signature(d,damageEntry,{0x83,0xEC,0x24,0x53,0x55,0x56,0x8B,0x74,0x24,0x34});
    signature(d,addSkill,{0x53,0x55,0x56,0x8B,0x74,0x24,0x10});
    signature(d,removeSkill,{0x8B,0x44,0x24,0x10,0x8B,0x4C,0x24,0x0C});
    signature(d,getBody,{0x8B,0x4C,0x24,4,0x56,0x85,0xC9});
    enableDebugPrivilege();
    require(DebugActiveProcess(d.pid),"Attach WW debugger"); d.attached=true;
    require(DebugSetProcessKillOnExit(FALSE),"Disable WW kill-on-exit");
    d.waitBreakpoint(0,3000,true); d.arm(entry); d.resume();
    // The update breakpoint is acquired once with the base dispatcher before
    // the persistent observers are armed.
    CONTEXT original=d.waitBreakpoint(entry,5000); d.gameThread=d.event.dwThreadId;
    U game=original.Ecx,returnTo=d.get(original.Esp),player=0;
    require(returnTo>=gm.base && returnTo<gm.base+gm.size,"WW update caller outside module");
    require(d.get<BYTE>(returnTo-5)==0xE8 && returnTo+d.get(returnTo-4)==entry,"WW update caller mismatch");
    validateGame(d,game,127897359,player);
    char name[16]{}; d.read(d.get(player+0x14),name,16);
    require(d.get(player+4)==4 && std::memcmp(name,"CXBarb",7)==0,"Require CXBarb");
    U skills=d.get(player+0xA8);
    auto findSkill=[&](U id) {
      std::set<U> seen;
      for (U sk=d.get(skills+4);sk;sk=d.get(sk+4)) {
        require(seen.insert(sk).second && seen.size()<512,"Invalid WW skill list");
        if (d.get<std::uint16_t>(d.get(sk))==id) return sk;
      }
      return U(0);
    };
    require(!findSkill(151),"Fixture must not already own Whirlwind");
    std::array<U,3> activeSkills{}; d.read(skills+8,activeSkills.data(),sizeof activeSkills);
    U path=d.get(player+0x2C),room=d.get(path+0x1C),roomEx=d.get(room+0x38);
    require(d.get(d.get(roomEx)+4)==2,"Require Blood Moor");
    auto start=position(d,player); auto test=arena(d,room,start.first,start.second,output);
    auto stat=[&](U unit,U id) { return d.call(original,returnTo,getStat,0,0,{unit,id,0}); };
    auto assign=[&](U unit,U id,U value) { d.call(original,returnTo,setStat,0,0,{unit,id,value,0}); require(stat(unit,id)==value,"WW fixture stat rejected"); };
    U inventory=d.get(player+0x60);
    U right=d.call(original,returnTo,getBody,0,0,{inventory,4}),left=d.call(original,returnTo,getBody,0,0,{inventory,5});
    require(right && d.get(right)==4 && d.get(right+4)==0,"Require fixture hand axe in RARM");
    require(!left || d.get(left+4)>=306,"Single-weapon WW fixture only");
    target=d.call(original,returnTo,spawn,game,room,{test.targetX,test.targetY,0,1,0});
    require(target && d.get(target)==1 && d.get(target+0x80)==game,"WW target identity");
    targetId=d.get(target+0xC);
    require(position(d,target)==std::make_pair(test.targetX,test.targetY),"WW target spawn moved");
    assign(target,7,1000<<8); assign(target,6,1000<<8);
    for (U id : {35u,36u,37u,39u,74u}) assign(target,id,0);
    d.call(original,returnTo,deleteEvent,game,target,{2,0}); d.call(original,returnTo,deleteEvent,game,target,{3,0});
    U skill=d.call(original,returnTo,addSkill,0,0,{player,151});
    require(skill && skill==findSkill(151) && d.get(skill+0x28)==1 && d.get(skill+8)==18,"Native WW skill allocation/mode");
    initialHP=lastHP=stat(target,6); initialMana=lowestMana=stat(player,8); startFrame=d.get(game+0xA8);
    std::ofstream identity(output/"identity.json");
    identity << "{\"character\":\"CXBarb\",\"class\":4,\"skill\":151,\"level\":1,\"map_seed\":127897359,\"game\":"<<game<<",\"player\":"<<player<<",\"target\":"<<target<<",\"target_id\":"<<targetId<<",\"right_item_class\":"<<d.get(right+4)<<",\"left_item_class\":"<<(left?static_cast<int>(d.get(left+4)):-1)<<",\"start_x\":"<<start.first<<",\"start_y\":"<<start.second<<",\"destination_x\":"<<test.destinationX<<",\"destination_y\":"<<test.destinationY<<",\"target_x\":"<<test.targetX<<",\"target_y\":"<<test.targetY<<",\"thread\":"<<d.gameThread<<"}"; identity.close();
    std::ofstream trace(output/"trace.jsonl"),damage(output/"damage.csv");
    std::ofstream resources(output/"resources.json");
    resources<<"{\"mana_before_fixed\":"<<initialMana<<",\"max_mana_fixed\":"<<stat(player,9)
             <<",\"mana_recovery\":"<<stat(player,26)<<",\"mana_recovery_bonus\":"<<stat(player,27)<<"}";
    resources.close();
    damage << "frame,attacker,victim,result_flags,physical_fixed,total_fixed\n";
    auto record=[&](const char* kind,CONTEXT c) {
      trace << "{\"kind\":\""<<kind<<"\",\"frame\":"<<d.get(game+0xA8)<<",\"eip\":"<<c.Eip<<",\"ecx\":"<<c.Ecx<<",\"edx\":"<<c.Edx<<",\"esp\":"<<c.Esp<<",\"ebp\":"<<c.Ebp<<",\"stack\":[";
      for (U i=0;i<16;i++) { if(i)trace<<','; trace<<d.get(c.Esp+i*4); }
      trace << "]}\n"; trace.flush();
    };
    d.observers[wwStart]=[&](CONTEXT c) { require(c.Ecx==game && c.Edx==player && d.get(c.Esp+4)==151 && d.get(c.Esp+8)==1,"Unexpected WW start context"); startCalls++; record("start",c); };
    d.observers[wwDo]=[&](CONTEXT c) { require(c.Ecx==game && c.Edx==player && d.get(c.Esp+4)==151 && d.get(c.Esp+8)==1,"Unexpected WW do context"); doCalls++; record("do",c); };
    d.observers[damageEntry]=[&](CONTEXT c) {
      U victim=d.get(c.Esp+4); if(c.Edx!=player || victim!=target) return;
      require(c.Ecx==game,"Wrong WW damage game"); U packet=d.get(c.Esp+8),total=d.get(packet+0x4C),now=d.get(game+0xA8);
      if(!damageEvents)firstDamageFrame=now; damageEvents++; totalDamage+=total;
      damage<<now<<','<<c.Edx<<','<<victim<<','<<d.get<std::uint16_t>(packet+4)<<','<<d.get(packet+8)<<','<<total<<'\n'; damage.flush(); record("damage",c);
    };
    for(auto& observer:d.observers)d.arm(observer.first);
    d.call(original,returnTo,cast,game,player,{skill,18,test.destinationX,test.destinationY,1});
    started=d.get(player+0x10)==18; require(started && startCalls==1,"Whirlwind SQ was not entered");
    std::ofstream frames(output/"frames.csv");
    frames<<"step,frame,player_mode,x,y,mana_fixed,target_hp_fixed,skill_flags,param4,x_fp16,y_fp16,path_velocity\n";
    for(U step=0;step<=160;step++) {
      U now=d.get(game+0xA8),mode=d.get(player+0x10),mana=stat(player,8),hp=stat(target,6); auto pos=position(d,player);
      lastFrame=now;lastHP=hp;lowestMana=(std::min)(lowestMana,mana);
      require(now==startFrame+step,"Non-contiguous WW authoritative frames");
      require(position(d,target)==std::make_pair(test.targetX,test.targetY),"WW target moved");
      U currentPath=d.get(player+0x2C);
      frames<<step<<','<<now<<','<<mode<<','<<pos.first<<','<<pos.second<<','<<mana<<','<<hp<<','<<d.get(skill+0xC)<<','<<d.get(skill+0x24)
            <<','<<d.get(currentPath)<<','<<d.get(currentPath+4)<<','<<d.get(currentPath+0x7C)<<'\n';frames.flush();
      if(mode==1 && step>0) { finished=true;endFrame=now;break; }
      require(step<160,"WW did not return to neutral within bounded capture");
      d.call(original,returnTo,deleteEvent,game,target,{2,0}); d.call(original,returnTo,deleteEvent,game,target,{3,0});
      d.arm(returnTo);d.set(original);d.resume();CONTEXT returned=d.wait(returnTo,4000);
      require(d.get(game+0xA8)==now+1,"WW update skipped a frame");
      d.arm(entry);d.set(returned);d.resume();original=d.wait(entry,4000);
      require(original.Ecx==game && d.get(original.Esp)==returnTo,"WW update context changed");
    }
    require(finished && doCalls>1 && damageEvents>0 && initialHP-lastHP==totalDamage,"WW cast/HP evidence incomplete");
    auto end=position(d,player); require(end!=start,"WW player did not move");
    require((d.get(skill+0xC)&1)==0,"WW skill flags still active at neutral");
    d.call(original,returnTo,remove,game,target,{});
    d.call(original,returnTo,removeSkill,0,0,{player,151,0,0}); require(!findSkill(151),"Temporary WW node remained");
    d.write(skills+8,activeSkills.data(),sizeof activeSkills);
    fixturesClean=true; success=true;
  } catch(const std::exception& error) { std::cerr<<error.what()<<'\n'; }
  bool cleaned=d.finishDebugger();
  auto save=[&]() {
    if(!created)return;
    std::ofstream out(output/"summary.json");
    out<<"{\"status\":\""<<(success&&cleaned?"passed":"failed")<<"\",\"scope\":\"one original level-1 Whirlwind with temporary native skill node, one hand axe and stationary target; no port/RNG/visual parity claim\",\"first_frame\":"<<startFrame<<",\"last_frame\":"<<lastFrame<<",\"end_frame\":"<<endFrame<<",\"cast_started\":"<<(started?"true":"false")<<",\"cast_finished\":"<<(finished?"true":"false")<<",\"start_calls\":"<<startCalls<<",\"do_calls\":"<<doCalls<<",\"damage_events\":"<<damageEvents<<",\"first_damage_frame\":"<<firstDamageFrame<<",\"damage_total_fixed\":"<<totalDamage<<",\"target_hp_before_fixed\":"<<initialHP<<",\"target_hp_after_fixed\":"<<lastHP<<",\"mana_before_fixed\":"<<initialMana<<",\"lowest_mana_fixed\":"<<lowestMana<<",\"fixtures_restored\":"<<(fixturesClean?"true":"false")<<",\"detached\":"<<(d.detached?"true":"false")<<",\"cleanup_confirmed\":"<<(cleaned?"true":"false")<<",\"cleanup_attention_required\":"<<(cleaned?"false":"true")<<"}";
  };
  save();while(d.attached&&!cleaned){Sleep(1000);cleaned=d.finishDebugger();}save();
  return success&&cleaned?0:1;
}
