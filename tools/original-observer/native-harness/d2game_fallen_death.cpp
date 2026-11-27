// Fixed same-room native monster death experiment in the CXSorc test world.
// All calls run on the stopped original game update thread. No arbitrary RPC.
#define D2_CONTROLLER_LIBRARY
#include "d2game_controller.cpp"
#include <array>

struct CastController : Controller {
  U call(CONTEXT entry, U returnTo, U function, U ecx, U edx, std::initializer_list<U> stack) {
    require(held && !pendingCall && stack.size() <= 5, "Invalid fixed call boundary");
    scratch = entry.Esp - 0x100;
    std::vector<U> args{ returnTo }; args.insert(args.end(), stack.begin(), stack.end());
    scratchOriginal.resize(args.size() * 4); read(scratch, scratchOriginal.data(), scratchOriginal.size());
    callRestore = entry; pendingCall = true;
    write(scratch, args.data(), args.size() * 4); arm(returnTo);
    CONTEXT c = entry; c.Eip = function; c.Esp = scratch; c.Ecx = ecx; c.Edx = edx; set(c); resume();
    CONTEXT result;
    try {
      result = waitBreakpoint(returnTo, 3000);
    } catch (...) {
      // A target-mode ABI mismatch can leave the update thread inside the
      // callback instead of returning to our scratch frame. Interrupt once
      // and record the exact instruction for diagnosis before rethrowing.
      if (!held && process && DebugBreakProcess(process)) {
        try {
          CONTEXT stuck = waitBreakpoint(0, 3000, true);
          std::cerr << "Fixed call timeout eip=" << std::hex << stuck.Eip
                    << " esp=" << stuck.Esp << std::dec << '\n';
          resume();
        } catch (...) {
          // cleanup() below will perform the final debugger interruption.
        }
      }
      throw;
    }
    require(result.Esp == scratch + args.size() * 4, "Fixed call stack imbalance");
    write(scratch, scratchOriginal.data(), scratchOriginal.size()); set(entry); pendingCall = false;
    return result.Eax;
  }
};

static std::pair<U, U> position(CastController& d, U unit) {
  U path = d.get(unit + 0x2C); require(path && d.get(path + 0x30) == unit, "Invalid dynamic path");
  return {d.get<std::uint16_t>(path + 2), d.get<std::uint16_t>(path + 6)};
}

static std::pair<U, U> targetPosition(CastController& d, U room, U px, U py, const std::filesystem::path& output) {
  U grid = d.get(room + 0x34); require(grid != 0, "Missing room collision grid");
  int x = d.get<int>(grid), y = d.get<int>(grid + 4), w = d.get<int>(grid + 8), h = d.get<int>(grid + 12);
  require(w > 0 && h > 0 && w <= 512 && h <= 512, "Invalid collision bounds");
  U cells = d.get(grid + 0x20); require(cells != 0, "Missing collision cells");
  std::vector<std::uint16_t> flags(w * h); d.read(cells, flags.data(), flags.size() * 2);
  std::ofstream collision(output / "collision.csv"); collision << "x,y,flags\n";
  for (int cy = 0; cy < h; cy++) for (int cx = 0; cx < w; cx++) collision << x+cx << ',' << y+cy << ',' << flags[cy*w+cx] << '\n';
  // Keep the complete shot corridor inside this same populated room.
  // Bash's native melee range is shorter than a projectile corridor. Prefer
  // the nearest clear point so the server can resolve a target during A1.
  for (int length = 2; length <= 7; length++) for (auto direction : std::vector<std::pair<int, int>>{{1,0},{0,1},{0,-1},{-1,0},{1,1},{1,-1},{-1,1},{-1,-1}}) {
    bool clear = true;
    for (int distance = 1; distance <= length && clear; distance++) {
      int tx = static_cast<int>(px) + direction.first * distance, ty = static_cast<int>(py) + direction.second * distance;
      int radius = distance == length ? 1 : 0;
      for (int ox = -radius; ox <= radius && clear; ox++) for (int oy = -radius; oy <= radius; oy++) {
        int cx = tx + ox - x, cy = ty + oy - y;
        U mask = distance == length ? 0x3FAF : 0x0127; // placement footprint vs missile corridor
        if (cx < 0 || cy < 0 || cx >= w || cy >= h || (flags[cy * w + cx] & mask)) { clear = false; break; }
      }
    }
    if (clear) return {px + direction.first * length, py + direction.second * length};
  }
  throw std::runtime_error("No clear bounded 3..7 subtile shot corridor in current room");
}


static std::pair<U,U> distancePoint(CastController& d,U room,U ox,U oy,int requested){
  U grid=d.get(room+0x34),cells=d.get(grid+0x20);
  int x=d.get<int>(grid),y=d.get<int>(grid+4),w=d.get<int>(grid+8),h=d.get<int>(grid+12);
  require(w>0&&h>0&&w<=512&&h<=512,"Invalid distance fixture grid");
  for(int cy=y+1;cy<y+h-1;++cy)for(int cx=x+1;cx<x+w-1;++cx){
    int dx=std::abs(cx-static_cast<int>(ox)),dy=std::abs(cy-static_cast<int>(oy));
    if((std::max)(dx,dy)+(std::min)(dx,dy)/2!=requested)continue;
    bool clear=true;
    for(int a=-1;a<=1;++a)for(int b=-1;b<=1;++b)
      if(d.get<std::uint16_t>(cells+2*((cy+b-y)*w+cx+a-x))&0x3FAF)clear=false;
    if(clear)return{static_cast<U>(cx),static_cast<U>(cy)};
  }
  throw std::runtime_error("No exact-distance clear footprint in native room");
}

#ifndef D2_FALLEN_HELPERS_ONLY
int wmain(int argc, wchar_t** argv) {
  SetErrorMode(SEM_FAILCRITICALERRORS | SEM_NOGPFAULTERRORBOX | SEM_NOOPENFILEERRORBOX);
  if (argc != 4 && argc != 5) return 2;
  std::wstring profile=argc==5?argv[4]:L"same-room-native-death";
  if(profile!=L"same-room-native-death"&&profile!=L"same-room-native-death-preserve-corpse"&&profile!=L"distance-14"&&profile!=L"distance-15"
      &&profile!=L"ring-4"&&profile!=L"ring-5"&&profile!=L"adjacent-room"&&profile!=L"recovery"&&profile!=L"recovery-reengage"&&profile!=L"attack-direct"&&profile!=L"attack-repeat"&&profile!=L"attack-natural"&&profile!=L"attack-natural-long")return 2;
  U observationFrames=profile==L"same-room-native-death-preserve-corpse"?2:profile==L"attack-natural-long"?1600:(profile==L"attack-repeat"||profile==L"attack-natural")?1040:(profile==L"recovery"||profile==L"recovery-reengage")?600:120;
  int requestedDistance=profile==L"distance-14"?14:profile==L"distance-15"?15:0;
  U deathCount=profile==L"ring-4"?4:profile==L"ring-5"?5:1;
  const bool preserveCorpse = profile==L"same-room-native-death-preserve-corpse";
  CastController d; bool success=false, created=false, removed=false, corpsePreserved=false, death=false, ring=false;
  U observer=0, corpse=0, first=0, samples=0;
  auto output=std::filesystem::absolute(argv[3]);
  try {
    created=std::filesystem::create_directory(output); require(created,"Output directory must be new");
    d.pid=std::stoul(argv[1]); auto root=std::filesystem::absolute(argv[2]);
    enableDebugPrivilege(); d.process=OpenProcess(PROCESS_ALL_ACCESS,FALSE,d.pid); require(d.process!=nullptr,"OpenProcess");
    wchar_t image[32768]{}; DWORD chars=32768;
    require(QueryFullProcessImageNameW(d.process,0,image,&chars),"Process identity");
    require(std::filesystem::equivalent(image,root / L"D2v110各版本免CD全功能启动.exe"),"Wrong loader");
    fileHash(root / L"D2Game.dll","2bd92fb25e3e4b8269930712620ba9db00cdc1aa44569d87dc4aba76553272b6");
    fileHash(root / L"D2Common.dll","b9d1a27c6a35992932c7c47438d02e5269f878e899f6e614d507902cecd1beab");
    Module gm=module(d.pid,L"D2Game.dll",root), cm=module(d.pid,L"D2Common.dll",root);
    U entry=gm.base+0x86D0,spawn=gm.base+0x3A090,remove=gm.base+0x8AEE0;
    U getMode=gm.base+0x35780,changeMode=gm.base+0x35220,setStat=cm.base+0x77AB0;
    U getStat=cm.base+0x77C30;
    U melee=gm.base+0x91AC0,damage=gm.base+0x91260;
    U randomRaw=gm.base+0x1EBF0,randomLimited=gm.base+0x1E4D0;
    signature(d,randomRaw,{0x8B,0x01,0xBA,0xC5,0x90,0xC6,0x6A,0xF7,0xE2});
    signature(d,randomLimited,{0x53,0x56,0x8B,0xF2,0x57,0x85,0xF6,0x7F,6});
    signature(d,gm.base+0x4840,{0x55,0x8B,0xE9,0x8B,0xCA,0xE8,0xF6,0x85,8,0});
    signature(d,gm.base+0x91E70,{0x81,0xEC,0x40,4,0,0,0x53,0x55,0x8B,0xDA,0x33,0xED});
    signature(d,gm.base+0x9220E,{0x8B,0x4C,0x24,0x1C,0x8B,0x54,0x24,0x28,0x0F,0xAF,0xC1});
    signature(d,gm.base+0x9223C,{0x8B,0x46,0x20,0xB9,0xC5,0x90,0xC6,0x6A,0xF7,0xE1});
    signature(d,gm.base+0x9225E,{0x8B,0x6C,0x24,0x20,0x8B,0xCE,0x8B,0xFA});
    signature(d,gm.base+0x32EC9,{0x8B,0x41,0x20,0xBA,0xC5,0x90,0xC6,0x6A});
    signature(d,gm.base+0x32EEE,{0x8A,0x86,0xA6,0,0,0});
    signature(d,gm.base+0x8E715,{0x03,0xF7,0x3B,0xC7});
    signature(d,gm.base+0x8E72B,{0x03,0xF0,0x8B,0x44,0x24,0x30});
    signature(d,melee,{0x81,0xEC,0x94,0,0,0,0x53,0x8B,0xDA,0x55,0x56,0x85,0xDB});
    signature(d,damage,{0x83,0xEC,0x24,0x53,0x55,0x56,0x8B,0x74,0x24,0x34,0x57,0x8B,0xD9});
    signature(d,getStat,{0x8B,0x44,0x24,4,0x55,0x85,0xC0,0x75,4,0x5D,0xC2,0x0C,0});
    for(U rva:{0x35080u,0x35150u})signature(d,gm.base+rva,
        {0x83,0xEC,0x20,0x53,0x56,0x8B,0xF2,0x57,0x85,0xF6,0x8B,0xD9});
    signature(d,entry,{0x83,0xEC,0x08,0x53,0x56,0x57,0x8B,0xF9,0xBB,1,0,0,0});
    signature(d,spawn,{0x83,0xEC,0x28,0x56,0x8B,0x74,0x24,0x38,0x33,0xC0});
    signature(d,remove,{0x53,0x56,0x57,0x8B,0xF9,0x33,0xDB,0x8B,0xF2,0x3B,0xFB});
    signature(d,getMode,{0x56,0x8B,0x74,0x24,0x0C,0x83,0xFE,0x10});
    signature(d,changeMode,{0x83,0xEC,0x34,0x53,0x55,0x8B,0x6C,0x24,0x44});
    signature(d,setStat,{0x8B,0x44,0x24,0x0C,0x53,0x8B,0x5C,0x24,0x0C});
    signature(d,cm.base+0x69DE0,{0x8B,0x4C,0x24,4,0x33,0xC0,0x66,0x8B,0x41,0x10,0xC2,4,0});
    signature(d,cm.base+0x69DF0,{0x8B,0x4C,0x24,4,0x33,0xC0,0x66,0x8B,0x41,0x12,0xC2,4,0});
    signature(d,cm.base+0x69BD0,{0x8B,0x44,0x24,4,0x8B,0x54,0x24,8,0x8D,0x88,0x9C,0,0,0});
    signature(d,cm.base+0x69AB0,{0x8B,0x44,0x24,4,0x8B,0x40,0x7C,0xC2,4,0});
    signature(d,cm.base+0x69B80,{0x8B,0x44,0x24,4,0x8B,0x40,0x28,0xC2,4,0});
    // Shipped #10380 frame metrics, #10373 animation advance and #10381
    // action setter establish these offsets independently of reference headers.
    signature(d,cm.base+0x7FA25,{0x8B,0x54,0x24,8,0x8B,0x48,0x44,0x89,0x0A,0x8B,0x4C,0x24,0x0C,0x8B,0x40,0x48});
    signature(d,cm.base+0x7EF02,{0x8B,0x46,0x44,0x66,0x8B,0x4E,0x4C});
    signature(d,cm.base+0x7FA49,{0x8B,0x42,0x50,0x85,0xC0});
    signature(d,cm.base+0x7FA64,{0x88,0x5A,0x4E});
    signature(d,gm.base+0x8CE40,{0x85,0xC9,0x75,3,0x33,0xC0,0xC3,0x8B,0x81,0xDC,0,0,0,0xC3});
    signature(d,gm.base+0x5185,{0x8B,0x51,0x24,0x33,0xC0,0x8A,0x01,0x3B,0xC7,0x75,0x11,0x8B,0x49,4});
    signature(d,cm.base+0x4BC36,{0x8B,0x54,0x24,0x0C,0x8B,0x48,0x28,0x89,0x0A,0x8B,0x4C,0x24,8,0x8B,0x40,0x24});
    signature(d,cm.base+0x4D600,{0x8B,0x44,0x24,4,0x85,0xC0,0x74,0x18,0x8B,0x54,0x24,8,0x33,0xC9,0x8A,0x48,0x64,0x89,0x54,0x88,0x68});
    require(DebugActiveProcess(d.pid),"Attach"); d.attached=true;
    require(DebugSetProcessKillOnExit(FALSE),"Disable kill on exit");
    d.waitBreakpoint(0,3000,true); d.arm(entry); d.resume();
    CONTEXT original=d.waitBreakpoint(entry,5000); d.gameThread=d.event.dwThreadId;
    U game=original.Ecx, returnTo=d.get(original.Esp),player=0;
    U act=d.get(game+0xBC),seed=d.get(act+0xC); validateGame(d,game,seed,player);
    char name[16]{};d.read(d.get(player+0x14),name,16);
    bool sorcFixture=(std::memcmp(name,"CXSorc",7)==0 && d.get(player+4)==1)
        || (std::memcmp(name,"CXNecro",7)==0 && d.get(player+4)==2);
    bool ravenFixture=std::memcmp(name,"CXDruRaven",10)==0 && d.get(player+4)==5;
    require(sorcFixture || ravenFixture,"Require CXSorc, CXNecro or CXDruRaven fixture");
    require(d.get(player+0x10)!=0 && d.get(player+0x10)!=17,"Require living player");
    require(returnTo>=gm.base && returnTo<gm.base+gm.size,"Update caller outside D2Game");
    U room=d.get(d.get(player+0x2C)+0x1C);
    require(d.get(d.get(d.get(room+0x38))+4)==2,"Require Blood Moor");
    auto monsterUnits=[&](){std::map<U,U> result;
      for(U bin=0;bin<128;++bin)for(U unit=d.get(game+0x1320+bin*4);unit;unit=d.get(unit+0xE4)){
        require(result.emplace(unit,d.get(unit+0xC)).second && result.size()<8192,"Invalid monster hash");
      }return result;};
    auto beforeSpawn=monsterUnits();
    auto pp=position(d,player),op=targetPosition(d,room,pp.first,pp.second,output);
    observer=d.call(original,returnTo,spawn,game,room,{op.first,op.second,19,1,0});
    require(observer && d.get(observer+4)==19 && d.get(observer+0x80)==game,"Observer identity");
    op=position(d,observer);
    auto cp=requestedDistance?distancePoint(d,room,op.first,op.second,requestedDistance)
        :targetPosition(d,room,op.first,op.second,output);
    U deathRoom=room;
    if(profile==L"adjacent-room"){
      U list=d.get(room+0x24),count=d.get(room+0x28);require(count>0&&count<128,"Invalid native adjacency list");
      bool found=false;
      for(U i=0;i<count&&!found;++i){U candidate=d.get(list+i*4);
        if(!candidate||candidate==room||!d.get(candidate+0x34))continue;
        if(d.get(d.get(d.get(candidate+0x38))+4)!=2)continue;
        for(int distance=2;distance<15&&!found;++distance)try{
          cp=distancePoint(d,candidate,op.first,op.second,distance);deathRoom=candidate;found=true;
        }catch(const std::runtime_error&){}
      }
      require(found,"No adjacent-room clear corpse point within native distance 15");
    }
    corpse=d.call(original,returnTo,spawn,game,deathRoom,{cp.first,cp.second,0,1,0});
    require(corpse && d.get(corpse+4)==0 && d.get(corpse+0x80)==game,"Corpse identity");
    require(position(d,corpse)==cp,"Corpse placement moved");
    require(d.get(d.get(observer+0x2C)+0x1C)==room && d.get(d.get(corpse+0x2C)+0x1C)==deathRoom,"Validated fixture rooms");
    U oid=d.get(observer+0xC),cid=d.get(corpse+0xC);
    std::map<U,U> owned;
    for(auto item:monsterUnits())if(!beforeSpawn.count(item.first))owned.insert(item);
    require(owned.count(observer) && owned.count(corpse),"Missing owned fixture identities");
    std::ofstream combatStats(output / "combat-stats.json");
    combatStats<<"{\"observer_id\":"<<oid<<",\"target_id\":"<<d.get(player+0xC)<<",\"attacker\":{";
    bool firstStat=true;
    for(U stat:{0u,2u,12u,19u,21u,22u,25u,17u,18u,111u}) {
      if(!firstStat)combatStats<<',';firstStat=false;
      combatStats<<'"'<<stat<<"\":"<<static_cast<int>(d.call(original,returnTo,getStat,0,0,{observer,stat,0}));
    }
    combatStats<<"}}";combatStats.close();
    std::ofstream identity(output / "identity.json");
    identity<<"{\"natural_ai\":"<<((profile==L"attack-natural"||profile==L"attack-natural-long")?"true":"false")<<",\"map_seed\":"<<seed<<",\"observer_id\":"<<oid<<",\"corpse_id\":"<<cid
      <<",\"reengage_step\":"<<(profile==L"recovery-reengage"?100:0)<<",\"observation_frames\":"<<observationFrames<<",\"death_count\":"<<deathCount<<",\"requested_distance\":"<<requestedDistance<<",\"observer_class\":19,\"corpse_class\":0,\"room\":"<<room<<",\"death_room\":"<<deathRoom<<",\"adjacent_room_fixture\":"<<(deathRoom!=room?"true":"false")<<",\"player_id\":"<<d.get(player+0xC)
      <<",\"player_x\":"<<pp.first<<",\"player_y\":"<<pp.second<<",\"observer_x\":"<<op.first
      <<",\"observer_y\":"<<op.second<<",\"corpse_x\":"<<cp.first<<",\"corpse_y\":"<<cp.second<<",\"adjacent_rooms\":[";
    U adjacentList=d.get(room+0x24),adjacentCount=d.get(room+0x28);
    require(adjacentCount<128,"Invalid room evidence count");
    for(U i=0;i<adjacentCount;++i){if(i)identity<<',';identity<<d.get(adjacentList+i*4);}
    identity<<"]}";
    identity.close();
    std::ofstream frames(output / "frames.csv");
    std::ofstream callbacks(output / "attack-callbacks.jsonl");
    std::ofstream damageTrace(output / "attack-damage.jsonl");
    std::ofstream hitTrace(output / "attack-hit-roll.jsonl");
    std::ofstream physicalTrace(output / "attack-physical-roll.jsonl");
    std::ofstream intermediateTrace(output / "attack-intermediate-rng.jsonl");
    frames<<"step,frame,observer_mode,observer_x,observer_y,seed_low,seed_high,ai_param0,command,corpse_mode,player_mode,ring_cursor,ring0,ring1,ring2,ring3,x_fp16,y_fp16,path_velocity,path_points,player_x,player_y,path_target_x,path_target_y,path_last_x,path_last_y,anim_frame_fp8,anim_length_fp8,anim_speed,anim_action,event_modechange_frame,event_endanim_frame,event_modechange_id\n";
    first=d.get(game+0xA8);
    for(U step=0;step<=observationFrames;++step){
      U now=d.get(game+0xA8);require(now==first+step,"Non-contiguous frames");
      if((step==0 && profile==L"attack-direct") || (profile==L"attack-repeat" && step<1024 && step%16==0)){
        // Deterministic action entry only; leave native hit RNG/stats intact.
        U info=original.Esp-0x60;BYTE saved[32];d.read(info,saved,32);
        d.call(original,returnTo,getMode,0,0,{observer,4,info});
        d.write(info+8,&player,4);
        U accepted=d.call(original,returnTo,changeMode,0,0,{game,info,1});
        d.write(info,saved,32);require(accepted!=0,"Native direct A1 rejected");
        // The spawn's pending AI think can replace this explicit action.
        // Remove only that think event; native attack/end timers stay intact.
        d.call(original,returnTo,gm.base+0x4840,game,observer,{2,0});
        std::ofstream intervention(output / "intervention.json");
        intervention<<"{\"step\":"<<step<<",\"frame\":"<<now<<",\"action\":\"native direct A1\",\"observer_id\":"<<oid
          <<",\"target_id\":"<<d.get(player+0xC)<<",\"pending_ai_think_deleted\":true,\"death_step\":"<<(profile==L"attack-repeat"?1030:20)<<",\"repeated_actions\":"<<(profile==L"attack-repeat"?64:1)<<",\"hit_rng_or_stats_changed\":false}";
      }
      if(step==((profile==L"attack-repeat"||profile==L"attack-natural")?1030u:profile==L"attack-direct"?20u:1u)){
        std::ofstream events(output / "deaths.jsonl");
        auto kill=[&](U unit){
          d.call(original,returnTo,setStat,0,0,{unit,6,0,0});
          U info=original.Esp-0x60;BYTE saved[32];d.read(info,saved,32);
          d.call(original,returnTo,getMode,0,0,{unit,0,info});
          require(d.get(info)==0 && d.get(info+4)==unit,"Native mode info layout");
          d.call(original,returnTo,changeMode,0,0,{game,info,1});d.write(info,saved,32);
          require(d.get(unit+0x10)==0,"Native death mode missing");
          U id=d.get(unit+0xC);bool found=false;
          events<<"{\"frame\":"<<now<<",\"guid\":"<<id<<",\"cursor\":"
            <<static_cast<U>(d.get<BYTE>(deathRoom+0x64))<<",\"ring\":[";
          for(U i=0;i<4;++i){U value=d.get(deathRoom+0x68+i*4);if(value==id)found=true;
            if(i)events<<',';events<<value;}
          events<<"]}\n";events.flush();require(found,"Native death ring did not record GUID");
        };
        kill(corpse);death=true;ring=true;
        for(U n=1;n<deathCount;++n){
          // Other new GUIDs occupy ring slots but are removed immediately:
          // they cannot themselves trigger the nearby-DT reaction.
          auto point=targetPosition(d,room,op.first,op.second,output);
          U extra=d.call(original,returnTo,spawn,game,room,{point.first,point.second,0,1,0});
          require(extra && d.get(extra+4)==0 && d.get(extra+0x80)==game,"Additional death identity");
          require(d.get(d.get(extra+0x2C)+0x1C)==room,"Additional death outside fixture room");
          kill(extra);d.call(original,returnTo,remove,game,extra,{});
        }
        bool firstStillPresent=false;
        for(U i=0;i<4;++i)if(d.get(deathRoom+0x68+i*4)==cid)firstStillPresent=true;
        require(firstStillPresent==(deathCount<=4),"Unexpected native ring eviction");
      }
      if(step==100 && profile==L"recovery-reengage"){
        // Separate, explicit intervention: after the initial flee has ended,
        // issue an ordinary native walk-to-player action. Subsequent attacks
        // are natural; this does not prove spontaneous return-to-player AI.
        U info=original.Esp-0x60;BYTE saved[32];d.read(info,saved,32);
        d.call(original,returnTo,getMode,0,0,{observer,2,info});
        d.write(info+8,&player,4);
        U accepted=d.call(original,returnTo,changeMode,0,0,{game,info,1});
        d.write(info,saved,32);require(accepted!=0,"Native reengagement walk rejected");
        std::ofstream intervention(output / "intervention.json");
        intervention<<"{\"step\":100,\"frame\":"<<now<<",\"action\":\"native walk-to-player\",\"observer_id\":"<<oid<<",\"target_id\":"<<d.get(player+0xC)<<"}";
      }
      auto xy=position(d,observer);U ai=d.get(d.get(observer+0x14)+0x28);
      require(ai && d.get(ai+0x28)==game,"Observer AI linkage");
      U cmd=d.get(ai+0x20);
      frames<<step<<','<<now<<','<<d.get(observer+0x10)<<','<<xy.first<<','<<xy.second<<','
        <<d.get(observer+0x20)<<','<<d.get(observer+0x24)<<','<<d.get(ai+0x14)<<','
        <<(cmd?d.get(cmd+8):0xFFFFFFFF)<<','<<d.get(corpse+0x10)<<','<<d.get(player+0x10)<<','
        <<static_cast<U>(d.get<BYTE>(deathRoom+0x64));
      for(U i=0;i<4;++i)frames<<','<<d.get(deathRoom+0x68+i*4);
      U currentPath=d.get(observer+0x2C);auto playerPosition=position(d,player);
      frames<<','<<d.get(currentPath)<<','<<d.get(currentPath+4)<<','<<d.get(currentPath+0x7C)
        <<','<<d.get(currentPath+0x28)<<','<<playerPosition.first<<','<<playerPosition.second;
      U pointCount=d.get(currentPath+0x28);require(pointCount<=78,"Invalid path point count");
      U lastPoint=pointCount?currentPath+0x9C+(pointCount-1)*4:0;
      frames<<','<<d.get<std::uint16_t>(currentPath+0x10)<<','<<d.get<std::uint16_t>(currentPath+0x12)
        <<','<<(lastPoint?d.get<std::uint16_t>(lastPoint):0)<<','<<(lastPoint?d.get<std::uint16_t>(lastPoint+2):0);
      frames<<','<<d.get(observer+0x44)<<','<<d.get(observer+0x48)<<','<<d.get<std::int16_t>(observer+0x4C)<<','<<static_cast<U>(d.get<BYTE>(observer+0x4E));
      U modeFrame=0,endFrame=0,modeId=0; std::set<U> timers;
      for(U timer=d.get(observer+0xDC);timer;timer=d.get(timer+0x24)){
        require(timers.insert(timer).second && timers.size()<=128,"Invalid unit timer chain");
        U type=d.get<BYTE>(timer),expires=d.get(timer+4);
        if(type==0 && expires && (!modeFrame || expires<modeFrame)){
          modeFrame=expires;modeId=d.get(timer+0x14);
        }
        if(type==1 && expires && (!endFrame || expires<endFrame))endFrame=expires;
      }
      frames<<','<<modeFrame<<','<<endFrame<<','<<modeId;
      frames<<'\n';frames.flush();++samples;
      if(step==observationFrames)break;
      // Arm only when this observer has an attack/end event due next update.
      // Unrelated monsters may reach the same dispatcher first. Run each such
      // invocation through its own return before rearming; never step over an
      // instruction or mistake a different unit for the fixture.
      U attackMode=d.get(observer+0x10);
      bool dueAttack=(attackMode==4||attackMode==5) && modeFrame==now+1;
      bool dueEnd=(attackMode==4||attackMode==5) && endFrame==now+1;
      U hook=dueAttack?gm.base+0x35080:dueEnd?gm.base+0x35150:0;
      U expectedFrame=dueAttack?modeFrame:endFrame;
      U hpBefore=hook?d.call(original,returnTo,getStat,0,0,{player,6,0}):0;
      bool observed=false;U callbackFrame=0,callbackReturnFrame=0,callbackModeAfter=0,calleeRva=0,others=0;
      U hpCallbackBefore=0,hpCallbackAfter=0,defenseFlat=0,defenseResist=0;
      d.arm(returnTo);if(hook)d.arm(hook);d.set(original);d.resume();
      CONTEXT returned{};
      for(U attempts=0;;++attempts){
        require(attempts<128,"Too many event dispatcher invocations");
        CONTEXT hit=d.waitBreakpoint(0,3000);
        if(hit.Eip==returnTo){returned=hit;break;}
        require(hook && hit.Eip==hook && hit.Ecx==game,"Unexpected event dispatcher");
        U eventReturn=d.get(hit.Esp);
        require(eventReturn>=gm.base && eventReturn<gm.base+gm.size && eventReturn!=returnTo,
            "Invalid event return site");
        bool fixture=hit.Edx==observer;
        if(fixture){
          require(!observed,"Duplicate fixture callback");
          callbackFrame=d.get(game+0xA8);
          // After GetCallbackTableRecord, inspect and stop at the actual
          // per-mode callback as well as the outer event dispatcher.
          U afterLookup=hook+0x1E;
          d.arm(afterLookup);d.resume();CONTEXT lookup=d.waitBreakpoint(afterLookup,3000);
          require(lookup.Eax!=0,"Missing monster callback table");
          U callee=d.get(lookup.Eax+(dueAttack?4:8));
          require(callee>=gm.base && callee<gm.base+gm.size,"Invalid mode callback");
          calleeRva=callee-gm.base;
          d.arm(callee);d.resume();CONTEXT called=d.waitBreakpoint(callee,3000);
          require(called.Ecx==game && called.Edx==observer,"Wrong per-mode callback identity");
          hpCallbackBefore=d.call(called,eventReturn,getStat,0,0,{player,6,0});
          defenseFlat=d.call(called,eventReturn,getStat,0,0,{player,34,0});
          defenseResist=d.call(called,eventReturn,getStat,0,0,{player,36,0});
          observed=true;
        }
        U hitReturn=0,hitStages=0,rating=0,defense=0,attackerLevel=0,defenderLevel=0,factor=0;
        U chance=0,roll=0,seedLow=0,seedHigh=0,seedAfterLow=0,seedAfterHigh=0,hitResult=0;
        U criticalStages=0,criticalChance=0,criticalRoll=0,criticalLow=0,criticalHigh=0,criticalAfterLow=0,criticalAfterHigh=0;
        U physicalStages=0,physicalMin=0,physicalMax=0,physicalBase=0,physicalRoll=0,physicalSrc=0;
        U physicalSeedLow=0,physicalSeedHigh=0,physicalAfterLow=0,physicalAfterHigh=0;
        struct ClearHitHook { Controller& d; ~ClearHitHook(){ d.observeOnce=nullptr;d.observedEntry=0; } } clearHitHook{d};
        bool traceDamage=fixture && dueAttack;
        if(traceDamage){
        d.observedEntry=gm.base+0x91E70;d.arm(d.observedEntry);
        d.observeOnce=[&](CONTEXT c){
          U next=0;
          if(c.Eip==gm.base+0x91E70){
            require(c.Ecx==observer && c.Edx==player,"Wrong native hit-roll identity");
            hitReturn=d.get(c.Esp);require(hitReturn!=returnTo && hitReturn>=gm.base && hitReturn<gm.base+gm.size,"Invalid hit return");
            next=gm.base+0x9220E;
          }else if(c.Eip==gm.base+0x9220E){
            require(c.Esi==observer,"Hit factor attacker changed");
            rating=c.Edi;defense=c.Ecx-c.Edi;factor=c.Eax;
            attackerLevel=d.get(c.Esp+0x1C);defenderLevel=d.get(c.Esp+0x28);
            next=gm.base+0x9223C;
          }else if(c.Eip==gm.base+0x9223C){
            chance=c.Ebx;seedLow=d.get(observer+0x20);seedHigh=d.get(observer+0x24);
            next=gm.base+0x9225E;
          }else if(c.Eip==gm.base+0x9225E){
            roll=c.Edx;seedAfterLow=d.get(observer+0x20);seedAfterHigh=d.get(observer+0x24);next=hitReturn;
          }else if(c.Eip==hitReturn){
            hitResult=c.Eax;if(hitResult)next=gm.base+0x8E715;
          }else if(c.Eip==gm.base+0x8E715){
            require(d.get(c.Esp+0x10)==observer,"Physical roll attacker changed");
            physicalMin=c.Edi;physicalMax=c.Eax;physicalBase=c.Esi;physicalSrc=d.get(c.Esp+0x30)&255;
            physicalSeedLow=d.get(observer+0x20);physicalSeedHigh=d.get(observer+0x24);
            ++physicalStages;next=gm.base+0x8E72B;
          }else if(c.Eip==gm.base+0x32EC9){
            require(c.Ecx==observer,"Critical roll attacker changed");
            for(U rngHook:{randomRaw,randomLimited})if(d.patches.count(rngHook))d.disarm(rngHook);
            criticalChance=d.get<BYTE>(c.Esi+0xA6);criticalLow=d.get(observer+0x20);criticalHigh=d.get(observer+0x24);
            ++criticalStages;next=gm.base+0x32EEE;
          }else if(c.Eip==gm.base+0x32EEE){
            criticalRoll=c.Edx;criticalAfterLow=d.get(observer+0x20);criticalAfterHigh=d.get(observer+0x24);++criticalStages;
          }else{
            require(c.Eip==gm.base+0x8E72B,"Unexpected physical roll trace stage");
            physicalRoll=c.Eax;physicalAfterLow=d.get(observer+0x20);physicalAfterHigh=d.get(observer+0x24);
            ++physicalStages;next=gm.base+0x32EC9;
            d.arm(randomRaw);d.arm(randomLimited);
          }
          if(physicalStages==0)++hitStages;
          d.observedEntry=next;if(next)d.arm(next);
        };
        }
        if(traceDamage){d.arm(melee);d.arm(damage);}
        d.arm(eventReturn);d.resume();CONTEXT eventReturned{};
        for(U damageStops=0;;++damageStops){
          require(damageStops<16,"Too many damage trace stops");
          CONTEXT settlement=d.waitBreakpoint(0,3000);
          if(settlement.Eip==eventReturn){eventReturned=settlement;break;}
          if(traceDamage && (settlement.Eip==randomRaw || settlement.Eip==randomLimited)) {
            U function=settlement.Eip,seedAddress=settlement.Ecx,caller=d.get(settlement.Esp),bound=settlement.Edx;
            require(caller>=gm.base && caller<gm.base+gm.size && caller!=eventReturn,"Invalid intermediate RNG return");
            U low=d.get(seedAddress),high=d.get(seedAddress+4);
            std::array<U,16> stack{};d.read(settlement.Esp,stack.data(),sizeof stack);
            d.arm(caller);d.resume();CONTEXT rngReturned=d.waitBreakpoint(caller,3000);
            intermediateTrace<<"{\"frame\":"<<callbackFrame<<",\"observer_id\":"<<oid
              <<",\"fixture_seed\":"<<(seedAddress==observer+0x20?"true":"false")
              <<",\"function_rva\":"<<function-gm.base<<",\"caller_rva\":"<<caller-gm.base
              <<",\"bound\":"<<bound<<",\"value\":"<<rngReturned.Eax
              <<",\"seed_low\":"<<low<<",\"seed_high\":"<<high
              <<",\"seed_after_low\":"<<d.get(seedAddress)<<",\"seed_after_high\":"<<d.get(seedAddress+4)<<",\"stack\":[";
            for(U n=0;n<stack.size();++n){if(n)intermediateTrace<<',';intermediateTrace<<stack[n];}
            intermediateTrace<<"]}\n";intermediateTrace.flush();
            d.arm(function);d.resume();continue;
          }
          require(traceDamage && (settlement.Eip==melee || settlement.Eip==damage),"Unexpected damage stop");
          U attacker=settlement.Edx,victim=d.get(settlement.Esp+4);
          require(settlement.Ecx==game && attacker && victim,"Invalid damage identity");
          auto recordDamage=[&](const char* stage,U descriptor){
            damageTrace<<"{\"frame\":"<<d.get(game+0xA8)<<",\"observer_id\":"<<oid
              <<",\"attacker_id\":"<<d.get(attacker+0xC)<<",\"victim_id\":"<<d.get(victim+0xC)
              <<",\"attacker_type\":"<<d.get(attacker)<<",\"victim_type\":"<<d.get(victim)
              <<",\"fixture_attacker\":"<<(attacker==observer?"true":"false")
              <<",\"player_victim\":"<<(victim==player?"true":"false")<<",\"stage\":\""<<stage<<"\"";
            if(descriptor)damageTrace<<",\"hit_flags\":"<<d.get(descriptor)
              <<",\"result_flags\":"<<d.get<std::uint16_t>(descriptor+4)
              <<",\"physical_fixed\":"<<d.get<int>(descriptor+8)
              <<",\"total_fixed\":"<<d.get<int>(descriptor+0x4C);
            damageTrace<<"}\n";damageTrace.flush();
          };
          if(settlement.Eip==melee){recordDamage("melee_entry",0);d.resume();continue;}
          U descriptor=d.get(settlement.Esp+8),damageReturn=d.get(settlement.Esp);
          require(descriptor && damageReturn>=gm.base && damageReturn<gm.base+gm.size
              && damageReturn!=eventReturn,"Invalid damage return/descriptor");
          recordDamage("settlement_entry",descriptor);
          d.arm(damageReturn);d.resume();d.waitBreakpoint(damageReturn,3000);
          recordDamage("settlement_return",descriptor);d.resume();
        }
        if(traceDamage){
        if(physicalStages) {
          physicalTrace<<"{\"frame\":"<<callbackFrame<<",\"observer_id\":"<<oid<<",\"target_id\":"<<d.get(player+0xC)
            <<",\"stages\":"<<physicalStages<<",\"min_fixed\":"<<physicalMin<<",\"max_fixed\":"<<physicalMax
            <<",\"base_fixed\":"<<physicalBase<<",\"source_scale\":"<<physicalSrc<<",\"roll\":"<<physicalRoll
            <<",\"seed_low\":"<<physicalSeedLow<<",\"seed_high\":"<<physicalSeedHigh
            <<",\"seed_after_low\":"<<physicalAfterLow<<",\"seed_after_high\":"<<physicalAfterHigh
            <<",\"critical_stages\":"<<criticalStages<<",\"critical_chance\":"<<criticalChance<<",\"critical_roll\":"<<criticalRoll
            <<",\"critical_seed_low\":"<<criticalLow<<",\"critical_seed_high\":"<<criticalHigh
            <<",\"critical_seed_after_low\":"<<criticalAfterLow<<",\"critical_seed_after_high\":"<<criticalAfterHigh<<"}\n";
          physicalTrace.flush();
        }
        for(U rngHook:{randomRaw,randomLimited})if(d.patches.count(rngHook))d.disarm(rngHook);
        if(d.observedEntry && d.patches.count(d.observedEntry))d.disarm(d.observedEntry);
        d.observedEntry=0;d.observeOnce=nullptr;

        hitTrace<<"{\"frame\":"<<callbackFrame<<",\"observer_id\":"<<oid<<",\"target_id\":"<<d.get(player+0xC)<<",\"stages\":"<<hitStages
          <<",\"effective_rating\":"<<rating<<",\"effective_defense\":"<<defense<<",\"attacker_level\":"<<attackerLevel
          <<",\"defender_level\":"<<defenderLevel<<",\"ar_factor\":"<<factor<<",\"chance\":"<<chance<<",\"roll\":"<<roll
          <<",\"seed_low\":"<<seedLow<<",\"seed_high\":"<<seedHigh<<",\"seed_after_low\":"<<seedAfterLow
          <<",\"seed_after_high\":"<<seedAfterHigh<<",\"hit_result\":"<<hitResult<<"}\n";hitTrace.flush();
        }
        if(traceDamage){if(d.patches.count(melee))d.disarm(melee);if(d.patches.count(damage))d.disarm(damage);}
        if(fixture){
          callbackReturnFrame=d.get(game+0xA8);callbackModeAfter=d.get(observer+0x10);
          hpCallbackAfter=d.call(eventReturned,eventReturn,getStat,0,0,{player,6,0});
        }
        else{++others;d.arm(hook);}
        d.resume();
      }
      if(hook){
        if(d.patches.count(hook))d.disarm(hook);
        U hpAfter=d.call(returned,returnTo,getStat,0,0,{player,6,0});
        callbacks<<"{\"step_before_update\":"<<step<<",\"type\":"<<(dueAttack?0:1)
          <<",\"expected_frame\":"<<expectedFrame<<",\"observer_id\":"<<oid
          <<",\"mode_before\":"<<attackMode<<",\"observed\":"<<(observed?"true":"false")
          <<",\"callback_frame\":"<<callbackFrame<<",\"return_frame\":"<<callbackReturnFrame
          <<",\"mode_after_callback\":"<<callbackModeAfter<<",\"callee_rva\":"<<calleeRva
          <<",\"defender_flat_physical_reduction\":"<<static_cast<int>(defenseFlat)<<",\"defender_physical_resistance\":"<<static_cast<int>(defenseResist)
          <<",\"player_hp_before_callback\":"<<hpCallbackBefore<<",\"player_hp_after_callback\":"<<hpCallbackAfter
          <<",\"other_dispatchers\":"<<others<<",\"player_hp_before_update\":"<<hpBefore
          <<",\"player_hp_after_update\":"<<hpAfter<<"}\n";callbacks.flush();
      }
      require(d.get(game+0xA8)==now+1,"Update skipped frame");
      d.arm(entry);d.set(returned);d.resume();original=d.waitBreakpoint(entry,3000);
      require(original.Ecx==game && d.get(original.Esp)==returnTo,"Update context changed");
    }
    // Only units allocated by this invocation are removed. Other room deaths
    // and AI state are deliberately not rolled back; this is a test world.
    // Native Fallen spawn may allocate followers too. Only the synchronous
    // before/after spawn delta belongs to this invocation, not later spawns.
    auto alive=monsterUnits();
    for(auto item:owned)if(alive.count(item.first)){
      require(alive.at(item.first)==item.second,"Owned unit lifetime changed");
      if(!(preserveCorpse && item.first==corpse))
        d.call(original,returnTo,remove,game,item.first,{});
    }
    corpsePreserved = preserveCorpse && alive.count(corpse) && d.get(corpse+0x10)==0;
    if(preserveCorpse) require(corpsePreserved,"Preserved corpse was removed or left death mode");
    std::set<U> remaining;
    for(U bin=0;bin<128;++bin)for(U unit=d.get(game+0x1320+bin*4);unit;unit=d.get(unit+0xE4)){
      require(remaining.insert(unit).second && remaining.size()<8192,"Invalid monster hash after cleanup");
       require((preserveCorpse && unit==corpse) || !owned.count(unit),
          "Owned fixture remains in monster hash");
    }
    removed=true;success=true;
  }catch(const std::exception& e){std::cerr<<e.what()<<'\n';}
  bool cleaned=d.cleanup();
  auto save=[&](){if(!created)return;std::ofstream s(output / "summary.json");
      s<<"{\"status\":\""<<(success&&cleaned?"passed":"failed")<<"\",\"detached\":"<<(d.detached?"true":"false")
       <<",\"cleanup_confirmed\":"<<(cleaned?"true":"false")<<",\"fixtures_removed\":"<<(removed&&!preserveCorpse?"true":"false")
       <<",\"corpse_preserved\":"<<(corpsePreserved?"true":"false")
     <<",\"native_death_observed\":"<<(death?"true":"false")<<",\"room_ring_verified\":"<<(ring?"true":"false")
     <<",\"samples\":"<<samples<<",\"first_frame\":"<<first<<",\"ai_parity\":\"untested\",\"restart_required\":"<<(removed?"false":"true")<<"}";};
  save();while(d.attached&&!cleaned){Sleep(1000);cleaned=d.cleanup();}save();
  return success&&cleaned?0:1;
}
#endif
