// Fixed offline Necromancer Bone Prison probe, private desktop only.
// Native target-mode cast and ordinary scheduler with passive layer observation.
#define D2_CONTROLLER_LIBRARY
#include "d2game_controller.cpp"
#include <array>

struct PrisonController : Controller {
  U call(CONTEXT entry, U returnTo, U fn, U ecx, U edx, std::initializer_list<U> stack) {
    require(held && !pendingCall && stack.size()<=5,"Invalid fixed call");
    scratch=entry.Esp-0x100;
    std::vector<U> args{returnTo}; args.insert(args.end(),stack.begin(),stack.end());
    scratchOriginal.resize(args.size()*4); read(scratch,scratchOriginal.data(),scratchOriginal.size());
    callRestore=entry; pendingCall=true; write(scratch,args.data(),args.size()*4); arm(returnTo);
    CONTEXT c=entry; c.Eip=fn; c.Esp=scratch; c.Ecx=ecx; c.Edx=edx; set(c); resume();
    CONTEXT result=waitBreakpoint(returnTo,3000);
    require(result.Esp==scratch+args.size()*4,"Fixed call stack imbalance");
    write(scratch,scratchOriginal.data(),scratchOriginal.size()); set(entry); pendingCall=false;
    return result.Eax;
  }
};

static std::vector<U> roomMonsters(PrisonController& d,U act) {
  std::vector<U> result; std::set<U> rooms,units;
  for(U room=d.get(act+4);room;room=d.get(room+0x7C)) {
    require(rooms.insert(room).second && rooms.size()<2048 && d.get(room+0x78)==act,"Room chain");
    std::set<U> chain;
    for(U unit=d.get(room+0x2C);unit;unit=d.get(unit+0xE8)) {
      require(chain.insert(unit).second && chain.size()<8192,"Unit chain");
      if(d.get(unit)==1 && units.insert(unit).second) result.push_back(unit);
    }
  }
  return result;
}

static void sample(PrisonController& d,std::ofstream& out,const char* side,U step,U frame,U unit) {
  U path=d.get(unit+0x2C); require(path && d.get(path+0x30)==unit,"Monster path backref");
  out << "{\"side\":\""<<side<<"\",\"step\":"<<step<<",\"game_frame\":"<<frame
      <<",\"unit_id\":"<<d.get(unit+0xC)<<",\"class_id\":"<<d.get(unit+4)
      <<",\"mode\":"<<d.get(unit+0x10)<<",\"x_fixed\":"<<d.get(path)<<",\"y_fixed\":"<<d.get(path+4)
      <<",\"direction\":"<<static_cast<U>(d.get<BYTE>(path+0x64))
      <<",\"animation_cursor\":"<<d.get(unit+0x44)<<",\"animation_frames_raw\":"<<d.get(unit+0x48)
      <<",\"animation_rate_raw\":"<<d.get(unit+0x4C)<<",\"flags\":"<<d.get(unit+0xC4)
      <<",\"unit_words\":[";
  std::array<U,0xEC/4> raw{}; d.read(unit,raw.data(),sizeof raw);
  for(size_t i=0;i<raw.size();i++) {if(i)out<<',';out<<raw[i];}
  out<<"]}\n";
}

int wmain(int argc,wchar_t** argv) {
  SetErrorMode(SEM_FAILCRITICALERRORS|SEM_NOGPFAULTERRORBOX|SEM_NOOPENFILEERRORBOX);
  if(argc!=4)return 2;
  PrisonController d; bool created=false,success=false,castStarted=false;
  U firstFrame=0,lastFrame=0,serverSamples=0,clientSamples=0,targetId=0,targetX=0,targetY=0;
  U drawEvents=0,drawCalls=0,drawReturns=0,drawSelections=0;
  std::set<U> serverIds,clientIds; std::string error;
  auto output=std::filesystem::absolute(argv[3]);
  try {
    created=std::filesystem::create_directory(output);require(created,"New output required");
    d.pid=std::stoul(argv[1]);auto root=std::filesystem::absolute(argv[2]);
    d.process=OpenProcess(PROCESS_QUERY_INFORMATION|PROCESS_VM_READ|PROCESS_VM_WRITE|PROCESS_VM_OPERATION|PROCESS_SUSPEND_RESUME|SYNCHRONIZE,FALSE,d.pid);
    require(d.process!=nullptr,"Open original");
    wchar_t image[32768]{};DWORD chars=32768;
    require(QueryFullProcessImageNameW(d.process,0,image,&chars),"Image identity");
    require(std::filesystem::equivalent(image,root/L"D2v110各版本免CD全功能启动.exe"),"Wrong loader");
    fileHash(root/L"D2Game.dll","2bd92fb25e3e4b8269930712620ba9db00cdc1aa44569d87dc4aba76553272b6");
    fileHash(root/L"D2Common.dll","b9d1a27c6a35992932c7c47438d02e5269f878e899f6e614d507902cecd1beab");
    fileHash(root/L"D2Client.dll","94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282");
    fileHash(root/L"D2CMP.dll","e725036e1118d34b98d2b739525ff75a225e6964b425469e4773c93f0c9cf882");
    Module gm=module(d.pid,L"D2Game.dll",root),cm=module(d.pid,L"D2Common.dll",root),cl=module(d.pid,L"D2Client.dll",root);
    Module cmp=module(d.pid,L"D2CMP.dll",root);
    U entry=gm.base+0x86D0,spawn=gm.base+0x3A090,targetMode=gm.base+0x51A00,deleteEvent=gm.base+0x4840;
    signature(d,entry,{0x83,0xEC,8,0x53,0x56,0x57,0x8B,0xF9,0xBB,1,0,0,0});
    signature(d,spawn,{0x83,0xEC,0x28,0x56,0x8B,0x74,0x24,0x38,0x33,0xC0});
    signature(d,targetMode,{0x53,0x55,0x56,0x57,0x8B,0xF9,0x33,0xDB,0x3B,0xFB});
    signature(d,deleteEvent,{0x55,0x8B,0xE9,0x8B,0xCA,0xE8,0xF6,0x85,0x08,0});
    enableDebugPrivilege();require(DebugActiveProcess(d.pid),"Attach");d.attached=true;
    require(DebugSetProcessKillOnExit(FALSE),"Disable kill-on-exit");
    d.waitBreakpoint(0,3000,true);d.arm(entry);d.resume();
    CONTEXT original=d.waitBreakpoint(entry,5000);d.gameThread=d.event.dwThreadId;
    U game=original.Ecx,returnTo=d.get(original.Esp),player=0;
    require(returnTo>=gm.base && returnTo<gm.base+gm.size,"Update caller");
    validateGame(d,game,127897359,player);
    char name[16]{};d.read(d.get(player+0x14),name,16);
    require(std::strcmp(name,"CXPrison")==0 && d.get(player+4)==2,"Require CXPrison Necromancer");
    U skills=d.get(player+0xA8),skill=d.get(skills+0xC);
    require(skill && d.get<std::uint16_t>(d.get(skill))==88 && d.get(skill+0x28)==1,"Selected Bone Prison level one");
    U path=d.get(player+0x2C),room=d.get(path+0x1C),act=d.get(player+0x1C);
    require(d.get(d.get(d.get(room+0x38))+4)==2,"Require Blood Moor");
    int px=d.get<std::uint16_t>(path+2),py=d.get<std::uint16_t>(path+6);
    U grid=d.get(room+0x34),cells=d.get(grid+0x20);
    int gx=d.get<int>(grid),gy=d.get<int>(grid+4),gw=d.get<int>(grid+8),gh=d.get<int>(grid+12);
    require(gw>0 && gh>0 && gw<512 && gh<512,"Collision bounds");
    for(int radius=8;radius<=18 && !targetX;radius++) for(auto delta:std::vector<std::pair<int,int>>{{1,0},{0,1},{0,-1},{-1,0},{1,1},{1,-1},{-1,1},{-1,-1}}) {
      int tx=px+radius*delta.first,ty=py+radius*delta.second;bool clear=true;
      for(int dy=-5;dy<=5 && clear;dy++)for(int dx=-5;dx<=5;dx++) {
        int x=tx+dx-gx,y=ty+dy-gy;
        if(x<0 || y<0 || x>=gw || y>=gh || (d.get<std::uint16_t>(cells+2*(y*gw+x))&0x0127)) {clear=false;break;}
      }
      if(clear){targetX=tx;targetY=ty;break;}
    }
    require(targetX!=0,"No clear prison ring in current room");
    std::set<U> before;for(U u:roomMonsters(d,act)) before.insert(d.get(u+0xC));
    U target=d.call(original,returnTo,spawn,game,room,{targetX,targetY,0,1,0});
    require(target && d.get(target)==1 && d.get(target+4)==0,"Spawn skeleton target");
    targetId=d.get(target+0xC);before.insert(targetId);
    d.call(original,returnTo,deleteEvent,game,target,{2,0});
    d.call(original,returnTo,deleteEvent,game,target,{3,0});
    std::ofstream identity(output/"identity.json");
    identity<<"{\"character\":\"CXPrison\",\"class_id\":2,\"skill_id\":88,\"level\":1,\"target_id\":"<<targetId
      <<",\"target_x\":"<<targetX<<",\"target_y\":"<<targetY<<",\"cast_method\":\"server target-mode; normal scheduler\"}";identity.close();
    d.call(original,returnTo,targetMode,game,player,{skill,10,1,targetId,1});
    castStarted=d.get(player+0x10)==10;require(castStarted,"Cast mode did not start");
    firstFrame=d.get(game+0xA8);
    // Observe the layered-unit path, not the single-sprite missile branch.
    // BADED calls the same D2Gfx #10072 thunk used by ordinary cell drawing.
    // No renderer calls, foreground activation or pixels are forced here.
    const U submit=cl.base+0xBADED,drawReturned=cl.base+0xBADF2,selected=cmp.base+0xBFE7;
    signature(d,submit,{0xE8,0x5E,0x0A,0x01,0});
    signature(d,drawReturned,{0x8B,0x54,0x24,0x48,0x8B,0x44,0x24,0x54});
    signature(d,selected,{0x33,0xC9,0x8A,0x4D,0x23,0x49});
    std::ofstream draws(output/"bone-layer-submissions.jsonl");
    require(draws.is_open(),"Open bone layer log");
    U pending=0,renderData=0,renderThread=0;
    d.observeOtherThreads=true;
    d.observeOnce=[&](CONTEXT c) {
      require(++drawEvents<=30000,"Layer observation budget exceeded");
      if(!renderThread)renderThread=d.event.dwThreadId;
      require(renderThread==d.event.dwThreadId,"Layer renderer changed thread");
      if(c.Eip==submit) {
        pending=0;
        if(c.Esi && d.get(c.Esi)==1 && d.get(c.Esi+4)==344 && serverIds.count(d.get(c.Esi+0xC))) {
          renderData=d.get(c.Esp);
          std::array<U,18> cell{};d.read(renderData,cell.data(),sizeof cell);
          require(cell[8]==1 && cell[9]==344,"Bone draw class identity");
          pending=++drawCalls;
          draws<<"{\"kind\":\"layer_submission\",\"index\":"<<pending
               <<",\"game_frame\":"<<d.get(game+0xA8)<<",\"thread\":"<<renderThread
               <<",\"call_rva\":"<<submit-cl.base<<",\"unit_id\":"<<d.get(c.Esi+0xC)
               <<",\"mode\":"<<cell[10]<<",\"frame\":"<<cell[2]<<",\"direction\":"<<cell[3]
               <<",\"component\":"<<U(d.get<BYTE>(renderData+0x1D))<<",\"cell_raw\":[";
          for(size_t n=0;n<cell.size();n++)draws<<(n?",":"")<<cell[n];
          draws<<"]}\n";draws.flush();
          d.observedAlternative=selected;d.arm(selected);
        }
        d.observedEntry=drawReturned;
      } else if(c.Eip==selected) {
        require(pending && c.Esi==renderData,"Bone DCC selection identity");
        U dirs=U(d.get<BYTE>(c.Ebp+0x23))-1,frames=d.get<BYTE>(c.Ebp+0x24);
        require(dirs>0 && dirs<=64 && c.Eax<dirs,"Bone DCC direction bounds");
        draws<<"{\"kind\":\"dcc_selection\",\"index\":"<<pending
             <<",\"dcc_direction\":"<<c.Eax<<",\"dcc_directions\":"<<dirs
             <<",\"dcc_frames\":"<<frames<<"}\n";draws.flush();
        drawSelections++;d.observedAlternative=0;
      } else {
        require(c.Eip==drawReturned,"Unknown bone draw stage");
        if(d.observedAlternative){d.disarm(d.observedAlternative);d.observedAlternative=0;}
        if(pending) {
          draws<<"{\"kind\":\"layer_call_returned\",\"index\":"<<pending<<"}\n";
          draws.flush();drawReturns++;pending=0;
        }
        d.observedEntry=submit;
      }
      if(!d.patches.count(d.observedEntry))d.arm(d.observedEntry);
    };
    std::ofstream states(output/"units.jsonl"),frames(output/"frames.csv");
    frames<<"step,game_frame,player_mode,server_new_monsters,client_new_monsters\n";
    for(U step=0;step<=680;step++) {
      // Bounded windows cover settled live bones and the expected death
      // transition. Disarming only removes observation; execution stays native.
      if(step==8 || step==605){pending=0;d.observedEntry=submit;d.arm(submit);}
      if(step==40 || step==650) {
        if(d.observedEntry && d.patches.count(d.observedEntry))d.disarm(d.observedEntry);
        if(d.observedAlternative && d.patches.count(d.observedAlternative))d.disarm(d.observedAlternative);
        d.observedEntry=d.observedAlternative=0;pending=0;
      }
      U frame=d.get(game+0xA8);lastFrame=frame;require(frame==firstFrame+step,"Non-contiguous frames");
      U sc=0,cc=0;
      for(U u:roomMonsters(d,act)) if(!before.count(d.get(u+0xC))) {
        sample(d,states,"server",step,frame,u);serverIds.insert(d.get(u+0xC));serverSamples++;sc++;
      }
      U cp=d.get(cl.base+0x11C200);require(cp && d.get(cp)==0,"Client player");
      for(U u:roomMonsters(d,d.get(cp+0x1C))) if(serverIds.count(d.get(u+0xC))) {
        sample(d,states,"client",step,frame,u);clientIds.insert(d.get(u+0xC));clientSamples++;cc++;
      }
      frames<<step<<','<<frame<<','<<d.get(player+0x10)<<','<<sc<<','<<cc<<'\n';frames.flush();states.flush();
      if(step==680)break;
      d.call(original,returnTo,deleteEvent,game,target,{2,0});
      d.call(original,returnTo,deleteEvent,game,target,{3,0});
      d.arm(returnTo);d.set(original);d.resume();CONTEXT returned=d.waitBreakpoint(returnTo,3000);
      require(d.get(game+0xA8)==frame+1,"Update skipped frame");
      d.arm(entry);d.set(returned);d.resume();original=d.waitBreakpoint(entry,3000);
      require(original.Ecx==game && d.get(original.Esp)==returnTo,"Update context changed");
    }
    require(serverIds.size()>=1 && clientIds.size()>=1,"No paired prison observations");success=true;
  } catch(const std::exception& e){error=e.what();std::cerr<<error<<'\n';}
  bool cleaned=d.cleanup();
  auto save=[&](){if(!created)return;std::ofstream s(output/"summary.json");
    s<<"{\"status\":\""<<(success&&cleaned?"passed":"failed")<<"\",\"cast_started\":"<<(castStarted?"true":"false")
      <<",\"first_frame\":"<<firstFrame<<",\"last_frame\":"<<lastFrame<<",\"server_samples\":"<<serverSamples<<",\"client_samples\":"<<clientSamples
      <<",\"server_unique\":"<<serverIds.size()<<",\"client_unique\":"<<clientIds.size()<<",\"detached\":"<<(d.detached?"true":"false")
      <<",\"layer_probe_events\":"<<drawEvents<<",\"bone_layer_calls\":"<<drawCalls
      <<",\"bone_layer_returns\":"<<drawReturns<<",\"bone_dcc_selections\":"<<drawSelections
      <<",\"cleanup_confirmed\":"<<(cleaned?"true":"false")<<",\"cleanup_attention_required\":"<<(cleaned?"false":"true")
      <<",\"error\":\""<<error<<"\",\"scope\":\"level-one server target-mode cast, paired unit states and bounded ordinary layered-draw observation; no local CltDo or pixel parity claim\"}";};
  save();while(d.attached&&!cleaned){Sleep(1000);cleaned=d.cleanup();}save();return success&&cleaned?0:1;
}
