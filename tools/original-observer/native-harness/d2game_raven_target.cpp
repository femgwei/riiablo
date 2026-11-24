// Fixed Raven experiment: one durable skeleton, no synthetic Raven actions.
#define D2_FALLEN_HELPERS_ONLY
#include "d2game_fallen_death.cpp"

static void ravenMotion(CastController& d,std::ostream& out,U unit,U owner) {
  U md=d.get(unit+0x14),control=d.get(md+0x28),parameters=d.get(md+0x2C),path=d.get(unit+0x2C);
  require(control && parameters && path,"Raven motion layout");
  auto op=position(d,owner);
  out<<",\"mode\":"<<d.get(unit+0x10)<<",\"ai_pecks\":"<<d.get<int>(control+0x14)
    <<",\"attack_due\":"<<d.get(control+0x18)<<",\"circle_side\":"<<d.get(control+0x1C)
    <<",\"path_type\":"<<d.get(path+0x3C)<<",\"path_count\":"<<d.get(path+0x28)
    <<",\"path_index\":"<<d.get(path+0x24)<<",\"path_step_num\":"<<unsigned(d.get<BYTE>(path+0x93))
    <<",\"path_distance\":"<<unsigned(d.get<BYTE>(path+0x94))<<",\"path_direction_offset\":"<<d.get<int>(path+0x98)
    <<",\"x_fixed\":"<<d.get(path)<<",\"y_fixed\":"<<d.get(path+4)
    <<",\"owner_x\":"<<op.first<<",\"owner_y\":"<<op.second
    <<",\"direction\":"<<unsigned(d.get<BYTE>(path+0x64))<<",\"new_direction\":"<<unsigned(d.get<BYTE>(path+0x65))
    <<",\"path_velocity\":"<<d.get(path+0x7C)<<",\"path_target_type\":"<<d.get(path+0x5C)
    <<",\"path_target_id\":"<<d.get(path+0x60)
    <<",\"requested_path_type\":"<<d.get(parameters+0x18)<<",\"velocity_bonus\":"<<d.get<int>(parameters+0x1C)
    <<",\"requested_distance\":"<<unsigned(d.get<BYTE>(parameters+0x20))
    <<",\"seed_low\":"<<d.get(unit+0x20)<<",\"seed_high\":"<<d.get(unit+0x24);
  U count=d.get(path+0x28); require(count<=78,"Native path point count");
  out<<",\"path_points\":[";
  for(U i=0;i<count;++i){ if(i) out<<','; U point=path+0x9C+i*4;
    out<<"{\"x\":"<<d.get<std::uint16_t>(point)<<",\"y\":"<<d.get<std::uint16_t>(point+2)<<"}"; }
  out<<"]";
}

static void exportRoomGridOne(CastController& d,std::ostream& out,U room,bool& first) {
  U grid=d.get(room+0x34),cells=d.get(grid+0x20);
  int gx=d.get<int>(grid),gy=d.get<int>(grid+4),width=d.get<int>(grid+8),height=d.get<int>(grid+12);
  require(cells && width>0 && height>0 && width<=512 && height<=512,"Native room grid");
  if(!first) out<<','; first=false;
  out<<"{\"room_pointer\":"<<room<<",\"grid_x\":"<<gx<<",\"grid_y\":"<<gy
    <<",\"width\":"<<width<<",\"height\":"<<height<<",\"flags\":[";
  for(int y=0;y<height;++y){ if(y) out<<','; out<<'[';
    for(int x=0;x<width;++x){ if(x) out<<','; out<<d.get<std::uint16_t>(cells+2*(y*width+x)); }
    out<<']'; }
  out<<"]}";
}

static void exportRoomGrid(CastController& d,std::ostream& out,U room) {
  out<<"{\"source_room\":"<<room<<",\"rooms\":["; bool first=true;
  exportRoomGridOne(d,out,room,first);
  U list=d.get(room+0x24),count=d.get(room+0x28);
  require(count<128,"Native room adjacency count");
  std::set<U> seen{room};
  for(U i=0;i<count;++i){
    U adjacent=d.get(list+i*4);
    if(!adjacent || !seen.insert(adjacent).second) continue;
    if(!d.get(adjacent+0x34)) continue;
    exportRoomGridOne(d,out,adjacent,first);
  }
  out<<"]}\n";
}

static void targetFeedback(CastController& d,std::ostream& out,U unit) {
  U ex=d.get(unit+0x5C);
  require(ex && (d.get(ex+0x10)&0x80000000u),"Target extended stat list");
  U flags=d.get(ex+0x58),list=0;
  for(U offset:{0x3Cu,0x40u}){
    std::set<U> seen;
    for(U node=d.get(ex+offset);node;node=d.get(node+0x2C)){
      require(seen.insert(node).second && seen.size()<1024,"Target stat list chain");
      if(d.get(node+0x14)==23){list=node;break;}
    }
    if(list)break;
  }
  out<<",\"target_mode\":"<<d.get(unit+0x10)
    <<",\"target_ai_state\":"<<(d.get(unit)==1?d.get(d.get(unit+0x14)+0x54):0)
    <<",\"dimvision\":"<<(flags && (d.get(flags)&(1u<<23))?"true":"false")
    <<",\"dimvision_list\":"<<(list?"true":"false")
    <<",\"dimvision_expiry\":"<<(list?d.get(list+0x18):0)
    <<",\"dimvision_skill\":"<<(list?d.get(list+0x1C):0)
    <<",\"dimvision_level\":"<<(list?d.get(list+0x20):0)
    <<",\"dimvision_owner_type\":"<<(list?d.get(list+8):0)
    <<",\"dimvision_owner_id\":"<<(list?d.get(list+0xC):0);
}

int wmain(int argc, wchar_t** argv) {
  SetErrorMode(SEM_FAILCRITICALERRORS | SEM_NOGPFAULTERRORBOX | SEM_NOOPENFILEERRORBOX);
  if (argc != 4 && argc != 5) return 2;
  bool naturalLife=argc==5 && std::wstring(argv[4])==L"natural-life";
  bool townOrbit=argc==5 && std::wstring(argv[4])==L"town-orbit";
  CastController d;
  bool created=false, success=false, removed=false;
  U target=0, targetId=0, first=0, samples=0;
  auto output=std::filesystem::absolute(argv[3]);
  try {
    created=std::filesystem::create_directory(output);
    require(created,"Output directory must be new");
    d.pid=std::stoul(argv[1]); auto root=std::filesystem::absolute(argv[2]);
    enableDebugPrivilege();
    d.process=OpenProcess(PROCESS_ALL_ACCESS,FALSE,d.pid);
    require(d.process!=nullptr,"OpenProcess");
    wchar_t image[32768]{}; DWORD chars=32768;
    require(QueryFullProcessImageNameW(d.process,0,image,&chars),"Process identity");
    require(std::filesystem::equivalent(image,root/L"D2v110各版本免CD全功能启动.exe"),"Wrong loader");
    fileHash(root/L"D2Game.dll","2bd92fb25e3e4b8269930712620ba9db00cdc1aa44569d87dc4aba76553272b6");
    fileHash(root/L"D2Common.dll","b9d1a27c6a35992932c7c47438d02e5269f878e899f6e614d507902cecd1beab");
    Module gm=module(d.pid,L"D2Game.dll",root),cm=module(d.pid,L"D2Common.dll",root);
    U entry=gm.base+0x86D0,spawn=gm.base+0x3A090,remove=gm.base+0x8AEE0;
    U setStat=cm.base+0x77AB0,getStat=cm.base+0x77C30,deleteEvent=gm.base+0x4840;
    // Names follow D2MOO's 1.10 symbols; ExecuteMissileDamage is not the HP-write entry.
    U hitRoll=gm.base+0x91E70,melee=gm.base+0x91AC0,damage=gm.base+0x91260;
    U skipRecovery=gm.base+0x91870;
    U ravenThink=gm.base+0xBD190;
    signature(d,entry,{0x83,0xEC,0x08,0x53,0x56,0x57,0x8B,0xF9,0xBB,1,0,0,0});
    signature(d,spawn,{0x83,0xEC,0x28,0x56,0x8B,0x74,0x24,0x38,0x33,0xC0});
    signature(d,remove,{0x53,0x56,0x57,0x8B,0xF9,0x33,0xDB,0x8B,0xF2,0x3B,0xFB});
    signature(d,setStat,{0x8B,0x44,0x24,0x0C,0x53,0x8B,0x5C,0x24,0x0C});
    signature(d,getStat,{0x8B,0x44,0x24,4,0x55,0x85,0xC0,0x75,4,0x5D,0xC2,0x0C,0});
    signature(d,deleteEvent,{0x55,0x8B,0xE9,0x8B,0xCA,0xE8,0xF6,0x85,8,0});
    signature(d,hitRoll,{0x81,0xEC,0x40,4,0,0,0x53,0x55,0x8B,0xDA,0x33,0xED});
    signature(d,melee,{0x81,0xEC,0x94,0,0,0,0x53,0x8B,0xDA,0x55,0x56,0x85,0xDB});
    signature(d,damage,{0x83,0xEC,0x24,0x53,0x55,0x56,0x8B,0x74,0x24,0x34,0x57,0x8B,0xD9});
    signature(d,skipRecovery,{0x51,0x53,0x55,0x56,0x57,0x8B,0xF1,0x8B,0xFA,0x6A,0x01,0x56});
    signature(d,ravenThink,{0x83,0xEC,0x4C,0x53,0x55,0x8B,0x6C,0x24,0x58,0x56,0x57,0x8B,0xF2});
    require(DebugActiveProcess(d.pid),"Attach"); d.attached=true;
    require(DebugSetProcessKillOnExit(FALSE),"Disable kill on debugger exit");
    d.waitBreakpoint(0,3000,true);d.arm(entry);d.resume();
    CONTEXT original=d.waitBreakpoint(entry,5000); d.gameThread=d.event.dwThreadId;
    U game=original.Ecx,returnTo=d.get(original.Esp),player=0;
    validateGame(d,game,127897359,player);
    char name[16]{}; d.read(d.get(player+0x14),name,16);
    require(std::string(name)=="CXDruRaven" && d.get(player+4)==5,"Require CXDruRaven");
    require(returnTo>=gm.base && returnTo<gm.base+gm.size,"Invalid game caller");
    U room=d.get(d.get(player+0x2C)+0x1C);
    require(d.get(d.get(d.get(room+0x38))+4)==(townOrbit?1u:2u),"Require experiment level");
    U originalHp=0,originalMaxHp=0; int regenBefore=0,regenAfter=0;
    if(!townOrbit){
    auto pp=position(d,player),tp=targetPosition(d,room,pp.first,pp.second,output);
    target=d.call(original,returnTo,spawn,game,room,{tp.first,tp.second,0,1,0});
    require(target && d.get(target+4)==0 && d.get(target+0x80)==game,"Skeleton identity");
    targetId=d.get(target+0xC);
    // Experimental intervention on this target only. Raven AI/damage stays native.
    originalHp=d.call(original,returnTo,getStat,0,0,{target,6,0});
    originalMaxHp=d.call(original,returnTo,getStat,0,0,{target,7,0});
    if(!naturalLife){
      d.call(original,returnTo,setStat,0,0,{target,7,10000*256,0});
      d.call(original,returnTo,setStat,0,0,{target,6,10000*256,0});
    }
    regenBefore=static_cast<int>(d.call(original,returnTo,getStat,0,0,{target,74,0}));
    d.call(original,returnTo,setStat,0,0,{target,74,0,0});
    regenAfter=static_cast<int>(d.call(original,returnTo,getStat,0,0,{target,74,0}));
    require(regenAfter==0,"Target regeneration intervention failed");
    d.call(original,returnTo,deleteEvent,game,target,{2,0});
    }
    first=d.get(game+0xA8);
    std::ofstream identity(output/"identity.json");
    identity<<"{\"target_id\":"<<targetId<<",\"target_class\":0,\"target_hp_fixed\":"<<(townOrbit?0:naturalLife?originalHp:2560000)
      <<",\"town_orbit\":"<<(townOrbit?"true":"false")<<",\"target_created\":"<<(target?"true":"false")
      <<",\"target_original_hp_fixed\":"<<originalHp<<",\"target_original_maxhp_fixed\":"<<originalMaxHp
      <<",\"natural_life\":"<<(naturalLife?"true":"false")<<",\"target_ai_think_deleted\":"<<(target?"true":"false")<<",\"target_pointer\":"<<target
      <<",\"target_hp_regen_before\":"<<regenBefore<<",\"target_hp_regen_after\":"<<regenAfter
      <<",\"room\":"<<room<<",\"player_id\":"<<d.get(player+0xC)<<",\"first_frame\":"<<first<<",\"max_frames\":2600,\"combat_entry_trace\":true}";
    identity.close();
    std::ofstream frames(output/"frames.csv");
    frames<<"frame,target_hp_fixed,target_mode,target_x,target_y,player_mode\n";
    std::ofstream ravens(output/"ravens.jsonl");
    std::ofstream combat(output/"combat.jsonl");
    std::ofstream recovery(output/"recovery.jsonl");
    std::ofstream decisions(output/"raven-decisions.jsonl");
    std::ofstream motion(output/"raven-motion.jsonl");
    std::ofstream roomGrid(output/"room-grid.json");
    std::ofstream stats(output/"raven-stats.jsonl");
    std::ofstream feedback(output/"target-feedback.jsonl");
    std::set<U> profiled;
    exportRoomGrid(d,roomGrid,room); roomGrid.flush();
    struct TracedCall { U function,ret,attacker,victim; bool raven; U hpBefore; };
    std::vector<TracedCall> pending;
    for(U step=0;step<=2600;++step){
      U frame=d.get(game+0xA8); require(frame==first+step,"Non-contiguous frames");
      if(target){
        feedback<<"{\"frame\":"<<frame<<",\"unit_id\":"<<targetId;
        targetFeedback(d,feedback,target);feedback<<"}\n";feedback.flush();
      }
      if(target && step%25==0){
        auto xy=position(d,target);
        frames<<frame<<','<<d.call(original,returnTo,getStat,0,0,{target,6,0})<<','<<d.get(target+0x10)<<','<<xy.first<<','<<xy.second<<','<<d.get(player+0x10)<<'\n';frames.flush();
      }
      std::set<U> visited;
      for(U bin=0;bin<128;++bin)for(U unit=d.get(game+0x1320+bin*4);unit;unit=d.get(unit+0xE4)){
        require(visited.insert(unit).second && visited.size()<8192,"Invalid server monster chain");
        if(d.get(unit+4)!=419)continue;
        motion<<"{\"frame\":"<<frame<<",\"unit_id\":"<<d.get(unit+0xC);
        ravenMotion(d,motion,unit,player);motion<<"}\n";motion.flush();
        if(profiled.insert(d.get(unit+0xC)).second){
          stats<<"{\"frame\":"<<frame<<",\"unit_id\":"<<d.get(unit+0xC);
          U txt=d.get(d.get(unit+0x14));
          stats<<",\"ai_delay\":"<<unsigned(d.get<BYTE>(txt+0x4F))<<",\"ai_parameters\":[";
          for(U i=0;i<8;++i){if(i)stats<<',';stats<<d.get<std::int16_t>(txt+0x56+6*i);}
          stats<<"],\"velocity\":"<<d.get<std::int16_t>(txt+0x32)<<",\"run\":"<<d.get<std::int16_t>(txt+0x34)
            <<",\"unit_size\":"<<d.get(d.get(unit+0x2C)+0x44);
          for(auto field:std::vector<std::pair<const char*,U>>{{"level",12},{"tohit",19},{"mindamage",21},{"maxdamage",22},{"item_stupidity",113}})
            stats<<",\""<<field.first<<"\":"<<static_cast<int>(d.call(original,returnTo,getStat,0,0,{unit,field.second,0}));
          stats<<"}\n";stats.flush();
        }
        auto xy=position(d,unit);
        U path=d.get(unit+0x2C),victim=d.get(path+0x58);
        bool validVictim=victim && d.get(victim+0x80)==game && d.get(victim)<=1
          && d.get(victim)==d.get(path+0x5C) && d.get(victim+0xC)==d.get(path+0x60);
        ravens<<"{\"frame\":"<<frame<<",\"unit_id\":"<<d.get(unit+0xC)<<",\"mode\":"<<d.get(unit+0x10)
          <<",\"x\":"<<xy.first<<",\"y\":"<<xy.second<<",\"animation_cursor\":"<<d.get(unit+0x44)
          <<",\"animation_rate\":"<<d.get<std::uint16_t>(unit+0x4C)
          <<",\"path_target_type\":"<<d.get(path+0x5C)<<",\"path_target_id\":"<<d.get(path+0x60)
          <<",\"target_link_valid\":"<<(validVictim?"true":"false")
          <<",\"target_hp_fixed\":"<<(validVictim?d.call(original,returnTo,getStat,0,0,{victim,6,0}):0)<<"}\n";
      }
      ravens.flush();++samples;
      if(step==2600 || std::filesystem::exists(output/"collector-done.json"))break;
      for(U hook:{hitRoll,melee,damage,skipRecovery,ravenThink}) d.arm(hook);
      d.arm(returnTo); d.resume(); CONTEXT returned{};
      for(U stops=0;;++stops){
        require(stops<256,"Too many combat trace stops in one update");
        CONTEXT hit=d.waitBreakpoint(0,3000);
        if(hit.Eip==returnTo){require(pending.empty(),"Combat stack did not return");returned=hit;break;}
        if(!pending.empty() && hit.Eip==pending.back().ret){
          auto call=pending.back();pending.pop_back();
          if(call.function==ravenThink){
            decisions<<"{\"stage\":\"return\",\"frame\":"<<d.get(game+0xA8)<<",\"unit_id\":"<<d.get(call.attacker+0xC);
            ravenMotion(d,decisions,call.attacker,player);decisions<<"}\n";decisions.flush();
          } else if(call.raven){
            auto& trace=call.function==skipRecovery?recovery:combat;
            trace<<"{\"stage\":\"return\",\"frame\":"<<d.get(game+0xA8)
              <<",\"function_rva\":"<<call.function-gm.base<<",\"attacker_id\":"<<d.get(call.attacker+0xC)
              <<",\"victim_type\":"<<d.get(call.victim)<<",\"victim_id\":"<<d.get(call.victim+0xC)
              <<",\"eax\":"<<hit.Eax;
            {
              U hpAfter=d.call(hit,call.ret,getStat,0,0,{call.victim,6,0});
              trace<<",\"hp_before_fixed\":"<<call.hpBefore<<",\"hp_after_fixed\":"<<hpAfter;
            }
            targetFeedback(d,trace,call.victim);
            trace<<",\"seed_low\":"<<d.get(call.victim+0x20)<<",\"seed_high\":"<<d.get(call.victim+0x24);
            trace<<"}\n";trace.flush();
          }
          if(!d.patches.count(call.function))d.arm(call.function);
          d.resume();continue;
        }
        require(hit.Eip==hitRoll || hit.Eip==melee || hit.Eip==damage || hit.Eip==skipRecovery || hit.Eip==ravenThink,"Unexpected breakpoint");
        U function=hit.Eip,ret=d.get(hit.Esp);
        if(function==ravenThink){
          U unit=hit.Edx,tick=d.get(hit.Esp+4),candidate=d.get(tick+8);
          require(hit.Ecx==game && unit && d.get(unit+4)==419,"Raven AI call identity");
          decisions<<"{\"stage\":\"entry\",\"frame\":"<<d.get(game+0xA8)<<",\"unit_id\":"<<d.get(unit+0xC)
            <<",\"decision_target_id\":"<<(candidate?d.get(candidate+0xC):0)
            <<",\"target_distance\":"<<d.get<int>(tick+0x14)<<",\"in_combat\":"<<(d.get(tick+0x18)?"true":"false");
          ravenMotion(d,decisions,unit,player);decisions<<"}\n";decisions.flush();
          require(ret>=gm.base && ret<gm.base+gm.size && !d.patches.count(ret),"Raven AI return address");
          pending.push_back({function,ret,unit,unit,false,0});d.arm(ret);d.resume();continue;
        }
        if(function==skipRecovery){
          U victim=hit.Ecx,descriptor=hit.Edx;
          require(victim && descriptor && d.get(victim+0x80)==game,"Recovery unit identity");
          U attacker=pending.empty()?0:pending.back().attacker;
          bool raven=!pending.empty() && pending.back().raven && pending.back().victim==victim;
          if(raven){
            recovery<<"{\"stage\":\"entry\",\"frame\":"<<d.get(game+0xA8)
              <<",\"function_rva\":"<<function-gm.base<<",\"attacker_id\":"<<d.get(attacker+0xC)
              <<",\"victim_id\":"<<d.get(victim+0xC)<<",\"hit_class\":"<<d.get(hit.Esp+4)
              <<",\"total_fixed\":"<<d.get(descriptor+0x4C)<<",\"poison_fixed\":"<<d.get(descriptor+0x28)
              <<",\"maxhp_fixed\":"<<d.call(hit,ret,getStat,0,0,{victim,7,0})
              <<",\"seed_low\":"<<d.get(victim+0x20)<<",\"seed_high\":"<<d.get(victim+0x24)<<"}\n";recovery.flush();
          }
          require(ret>=gm.base && ret<gm.base+gm.size && !d.patches.count(ret),"Recovery return address");
          U hpBefore=raven?d.call(hit,ret,getStat,0,0,{victim,6,0}):0;
          pending.push_back({function,ret,attacker,victim,raven,hpBefore});d.arm(ret);d.resume();continue;
        }
        U attacker=function==hitRoll?hit.Ecx:hit.Edx;
        U victim=function==hitRoll?hit.Edx:d.get(hit.Esp+4);
        require(attacker && victim && d.get(attacker+0x80)==game && d.get(victim+0x80)==game,"Combat unit/game identity");
        require(ret>=gm.base && ret<gm.base+gm.size && ret!=returnTo && !d.patches.count(ret),"Invalid or shared combat return");
        bool raven=d.get(attacker)==1 && d.get(attacker+4)==419;
        U hpBefore=raven?d.call(hit,ret,getStat,0,0,{victim,6,0}):0;
        if(raven){
          combat<<"{\"stage\":\"entry\",\"frame\":"<<d.get(game+0xA8)<<",\"function_rva\":"<<function-gm.base
            <<",\"attacker_id\":"<<d.get(attacker+0xC)<<",\"victim_type\":"<<d.get(victim)
            <<",\"victim_id\":"<<d.get(victim+0xC)<<",\"fixed_target\":"<<(victim==target?"true":"false")
            <<",\"caller_return_rva\":"<<ret-gm.base<<",\"hp_before_fixed\":"<<hpBefore;
          if(function==damage){
            U descriptor=d.get(hit.Esp+8);require(descriptor!=0,"Missing damage descriptor");
            combat<<",\"result_flags\":"<<d.get<std::uint16_t>(descriptor+4)
              <<",\"physical_fixed\":"<<d.get<int>(descriptor+8)<<",\"total_fixed\":"<<d.get<int>(descriptor+0x4C);
          }
          combat<<"}\n";combat.flush();
        }
        pending.push_back({function,ret,attacker,victim,raven,hpBefore});
        require(pending.size()<32,"Combat trace nesting limit");
        d.arm(ret);d.resume();
      }
      for(U hook:{hitRoll,melee,damage,skipRecovery,ravenThink})if(d.patches.count(hook))d.disarm(hook);
      d.arm(entry); d.set(returned); d.resume(); original=d.waitBreakpoint(entry,3000);
      require(original.Ecx==game && d.get(original.Esp)==returnTo,"Update context changed");
    }
    if(target){
      require(d.get(target+0xC)==targetId,"Target recycled");
      d.call(original,returnTo,remove,game,target,{});
    }
    removed=true;success=true;
  } catch(const std::exception& e){std::cerr<<e.what()<<'\n';}
  bool cleaned=d.cleanup();
  auto save=[&](){if(!created)return; std::ofstream s(output/"summary.json");
    s<<"{\"status\":\""<<(success&&cleaned?"passed":"failed")<<"\",\"detached\":"<<(d.detached?"true":"false")
      <<",\"cleanup_confirmed\":"<<(cleaned?"true":"false")<<",\"fixtures_removed\":"<<(removed?"true":"false")
      <<",\"memory_writes\":true,\"remote_execution\":true,\"debugger_attached\":true,\"samples\":"<<samples<<",\"first_frame\":"<<first<<"}";};
  save();while(d.attached&&!cleaned){Sleep(1000);cleaned=d.cleanup();}save();
  return success&&cleaned?0:1;
}
