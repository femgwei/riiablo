// Reuse the version-pinned call and placement helpers; legacy entry is unused.
#define wmain unused_fallen_entry
#include "d2game_fallen_death.cpp"
#undef wmain

// Read-only snapshot of the current active room and its loaded neighbors.
// Keep room identity: overlapping grids must not silently overwrite each other.
static void exportNeighborCollision(CastController& d,U room,const std::filesystem::path& output) {
  U list=d.get(room+0x24),count=d.get(room+0x28),act=d.get(room+0x78);
  require(count<=128 && (!count || list),"Invalid adjacent room list");
  std::vector<U> rooms{room};
  for(U i=0;i<count;++i){U neighbor=d.get(list+i*4);
    require(neighbor!=0,"Null adjacent room");
    if(std::find(rooms.begin(),rooms.end(),neighbor)==rooms.end())rooms.push_back(neighbor);
  }
  std::ofstream metadata(output/"collision-rooms.csv"),cellsOut(output/"collision-neighbors.csv");
  metadata<<"room,x,y,width,height,is_player_room\n";
  cellsOut<<"room,x,y,flags\n";
  for(U candidate:rooms){
    require(d.get(candidate+0x78)==act,"Adjacent room act mismatch");
    U grid=d.get(candidate+0x34);require(grid!=0,"Adjacent collision grid missing");
    int x=d.get<int>(grid),y=d.get<int>(grid+4),w=d.get<int>(grid+8),h=d.get<int>(grid+12);
    require(w>0&&h>0&&w<=512&&h<=512,"Invalid adjacent collision bounds");
    U cells=d.get(grid+0x20);require(cells!=0,"Adjacent collision cells missing");
    std::vector<std::uint16_t> flags(w*h);d.read(cells,flags.data(),flags.size()*2);
    metadata<<candidate<<','<<x<<','<<y<<','<<w<<','<<h<<','<<(candidate==room?1:0)<<'\n';
    for(int cy=0;cy<h;++cy)for(int cx=0;cx<w;++cx)
      cellsOut<<candidate<<','<<x+cx<<','<<y+cy<<','<<flags[cy*w+cx]<<'\n';
  }
  require(metadata.good()&&cellsOut.good(),"Neighbor collision write failed");
}

int wmain(int argc, wchar_t** argv) {
  SetErrorMode(SEM_FAILCRITICALERRORS | SEM_NOGPFAULTERRORBOX | SEM_NOOPENFILEERRORBOX);
  if(argc!=5 && argc!=6)return 2;
  U klass=std::stoul(argv[4]);
  std::wstring profile=argc==6?argv[5]:L"";
  bool golem=profile==L"shaman-golem"||profile==L"shaman-golem-wall";
  bool wall=profile==L"shaman-hydra-wall"||profile==L"shaman-golem-wall";
  bool hydra=golem||wall||profile==L"shaman-hydra";
  if(hydra&&klass!=58)return 2;
  U distance=argc==6&&!hydra?std::stoul(argv[5]):0;
  if(distance && (klass!=170 || distance!=18))return 2;
  if(hydra)distance=8; // isolate ranged decisions rather than point-blank melee
  // IDs are checked against the production MonStats catalog before submission.
  if(klass!=5 && klass!=170 && klass!=19 && klass!=58 && klass!=53)return 2;
  CastController d; bool created=false,success=false,removed=false; U samples=0;
  auto output=std::filesystem::absolute(argv[3]); std::string error;
  try {
    created=std::filesystem::create_directory(output);require(created,"Output must be new");
    d.pid=std::stoul(argv[1]);auto root=std::filesystem::absolute(argv[2]);
    enableDebugPrivilege();d.process=OpenProcess(PROCESS_ALL_ACCESS,FALSE,d.pid);require(d.process!=nullptr,"OpenProcess");
    wchar_t image[32768]{};DWORD chars=32768;
    require(QueryFullProcessImageNameW(d.process,0,image,&chars),"Process identity");
    require(std::filesystem::equivalent(image,root/L"D2v110各版本免CD全功能启动.exe"),"Wrong loader");
    fileHash(root/L"D2Game.dll","2bd92fb25e3e4b8269930712620ba9db00cdc1aa44569d87dc4aba76553272b6");
    fileHash(root/L"D2Common.dll","b9d1a27c6a35992932c7c47438d02e5269f878e899f6e614d507902cecd1beab");
    Module gm=module(d.pid,L"D2Game.dll",root);
    U entry=gm.base+0x86D0,spawn=gm.base+0x3A090,remove=gm.base+0x8AEE0;
    signature(d,entry,{0x83,0xEC,8,0x53,0x56,0x57,0x8B,0xF9,0xBB,1,0,0,0});
    signature(d,spawn,{0x83,0xEC,0x28,0x56,0x8B,0x74,0x24,0x38,0x33,0xC0});
    signature(d,remove,{0x53,0x56,0x57,0x8B,0xF9,0x33,0xDB,0x8B,0xF2,0x3B,0xFB});
    require(DebugActiveProcess(d.pid),"Attach");d.attached=true;
    require(DebugSetProcessKillOnExit(FALSE),"Disable kill on exit");
    d.waitBreakpoint(0,3000,true);d.arm(entry);d.resume();
    CONTEXT original=d.waitBreakpoint(entry,5000);d.gameThread=d.event.dwThreadId;
    U game=original.Ecx,ret=d.get(original.Esp),player=0,act=d.get(game+0xBC),seed=d.get(act+0xC);
    validateGame(d,game,seed,player);char name[16]{};d.read(d.get(player+0x14),name,16);
    require(std::string(name)=="CXSorc" && d.get(player+4)==(golem?2u:1u),"Require pinned CXSorc test class");
    require(d.get(player+0x10)!=0 && d.get(player+0x10)!=17,"Require living player");
    require(ret>=gm.base && ret<gm.base+gm.size,"Invalid update caller");
    U room=d.get(d.get(player+0x2C)+0x1C);
    require(d.get(d.get(d.get(room+0x38))+4)==2,"Require Blood Moor");
    auto units=[&](){std::map<U,U> result;for(U bin=0;bin<128;++bin)
      for(U p=d.get(game+0x1320+bin*4);p;p=d.get(p+0xE4))
        require(result.emplace(p,d.get(p+0xC)).second && result.size()<8192,"Invalid unit hash");return result;};
    auto before=units();auto pp=position(d,player),point=targetPosition(d,room,pp.first,pp.second,output);
    U hydraSkill=0;
    if(klass==58){
      // Inventory learned native skill allocations before attempting a summon.
      // A skill id is not a skill pointer and must never be passed as one.
      std::ofstream skills(output/"player-skills.csv");skills<<"skill_id,level\n";
      U table=d.get(player+0xA8),node=table?d.get(table+4):0,count=0;
      require(table&&node,"Missing player skill allocations");
      std::map<U,bool> visited;
      for(;node;node=d.get(node+4)){
        require(++count<=512&&visited.emplace(node,true).second,"Invalid player skill list");
        U row=d.get(node);require(row!=0,"Missing native skill row");
        skills<<d.get<std::uint16_t>(row)<<','<<d.get(node+0x28)<<'\n';
        if(d.get<std::uint16_t>(row)==(golem?75:62)&&d.get(node+0x28)==1)hydraSkill=node;
      }
    }
    if(distance){
      U grid=d.get(room+0x34),cells=d.get(grid+0x20);
      int gx=d.get<int>(grid),gy=d.get<int>(grid+4),w=d.get<int>(grid+8),h=d.get<int>(grid+12);
      require(w>0&&h>0&&w<=512&&h<=512,"Invalid far corridor grid");
      bool found=false;
      for(auto dir:std::vector<std::pair<int,int>>{{1,0},{-1,0},{0,1},{0,-1}}){
        bool clear=true;
        for(int i=1;i<=static_cast<int>(distance)&&clear;++i){
          int x=static_cast<int>(pp.first)+dir.first*i,y=static_cast<int>(pp.second)+dir.second*i;
          int radius=i==static_cast<int>(distance)?1:0;
          for(int ox=-radius;ox<=radius&&clear;++ox)for(int oy=-radius;oy<=radius;++oy){
            int cx=x+ox-gx,cy=y+oy-gy;U mask=radius?0x3FAF:0x0127;
            if(cx<0||cy<0||cx>=w||cy>=h||(d.get<std::uint16_t>(cells+2*(cy*w+cx))&mask)){clear=false;break;}
          }
        }
        if(clear){point={pp.first+dir.first*distance,pp.second+dir.second*distance};found=true;break;}
      }
      require(found,"No clear same-room axis corridor at distance 18");
    }
    exportNeighborCollision(d,room,output);
    U monster=d.call(original,ret,spawn,game,room,{point.first,point.second,klass,1,0});
    auto after=units();std::map<U,U> owned;for(auto item:after)if(!before.count(item.first))owned.insert(item);
    U barrierCell=0;std::uint16_t barrierOld=0;bool barrierChanged=false;
    // Even placement rejection may allocate followers: remove all owned units.
    auto cleanupOwned=[&](){
      if(barrierChanged){
        std::uint16_t restored=(d.get<std::uint16_t>(barrierCell)&~4u)|(barrierOld&4u);
        d.write(barrierCell,&restored,sizeof(restored));
        require((d.get<std::uint16_t>(barrierCell)&4)==(barrierOld&4),"Barrier restore failed");
        barrierChanged=false;
      }
      auto alive=units();for(auto item:owned)if(alive.count(item.first)){
      require(alive.at(item.first)==item.second,"Owned identity changed");d.call(original,ret,remove,game,item.first,{});}
      auto remaining=units();for(auto item:owned)require(!remaining.count(item.first),"Owned unit remains");removed=true;};
    try {
      require(monster && owned.count(monster) && d.get(monster+4)==klass,"Spawn identity");
      U removedMinions=0;
      if(hydra){
        for(auto it=owned.begin();it!=owned.end();){
          if(it->first==monster){++it;continue;}
          U extra=it->first;d.call(original,ret,remove,game,extra,{});
          require(!units().count(extra),"Spawned follower removal failed");
          it=owned.erase(it);++removedMinions;
        }
      }
      if(wall){
        U grid=d.get(room+0x34),cells=d.get(grid+0x20);
        int gx=d.get<int>(grid),gy=d.get<int>(grid+4),w=d.get<int>(grid+8),h=d.get<int>(grid+12);
        int x=(pp.first+point.first)/2,y=(pp.second+point.second)/2;
        require(x>=gx&&y>=gy&&x<gx+w&&y<gy+h,"Barrier outside room grid");
        require(std::make_pair(static_cast<U>(x),static_cast<U>(y))!=pp
          &&std::make_pair(static_cast<U>(x),static_cast<U>(y))!=point,"Barrier overlaps unit centre");
        barrierCell=cells+2*((y-gy)*w+x-gx);barrierOld=d.get<std::uint16_t>(barrierCell);
        require((barrierOld&4)==0,"Fixture cell already blocks missiles");
        std::uint16_t changed=barrierOld|4;barrierChanged=true;d.write(barrierCell,&changed,sizeof(changed));
        std::ofstream barrier(output/"barrier.json");
        barrier<<"{\"x\":"<<x<<",\"y\":"<<y<<",\"mask\":4,\"old_flags\":"<<barrierOld
          <<",\"scope\":\"temporary missile barrier, not a movement wall\"}";
      }
      if(hydra){
        require(hydraSkill!=0,"Require real learned Hydra skill allocation");
        Module common=module(d.pid,L"D2Common.dll",root);
        U setStat=common.base+0x77AB0,getStat=common.base+0x77C30;
        signature(d,setStat,{0x8B,0x44,0x24,0x0C,0x53,0x8B,0x5C,0x24,0x0C,0x56,0x8B,0x74,0x24,0x18});
        signature(d,getStat,{0x8B,0x44,0x24,4,0x55,0x85,0xC0,0x75,4,0x5D,0xC2,0x0C,0});
        for(U stat:{7u,6u}){
          d.call(original,ret,setStat,0,0,{monster,stat,100000u<<8,0});
          require(d.call(original,ret,getStat,0,0,{monster,stat,0})==(100000u<<8),"Observer life override failed");
        }
        U cast=gm.base+0x517D0;
        signature(d,cast,{0x53,0x55,0x56,0x57,0x8B,0xF9,0x8B,0xF2,0x85,0xFF});
        U summonX=(pp.first+point.first)/2,summonY=(pp.second+point.second)/2+2;
        d.call(original,ret,cast,game,player,{hydraSkill,10,summonX,summonY,0});
        require(d.get(player+0x10)==10,"Hydra cast mode not entered");
      }
      if(distance)require(position(d,monster)==point,"Far spawn placement moved");
      std::ofstream identity(output/"identity.json");
      identity<<"{\"class_id\":"<<klass<<",\"map_seed\":"<<seed<<",\"difficulty\":0,\"unit_id\":"<<d.get(monster+0xC)
        <<",\"owned_units\":"<<owned.size()<<",\"player_x\":"<<pp.first<<",\"player_y\":"<<pp.second
        <<",\"unit_seed_low\":"<<d.get(monster+0x20)<<",\"unit_seed_high\":"<<d.get(monster+0x24)
        <<",\"requested_distance\":"<<distance<<",\"spawn_x\":"<<point.first<<",\"spawn_y\":"<<point.second
        <<",\"axis_corridor_clear\":"<<(distance?"true":"null")
        <<",\"natural_ai\":true,\"forced_actions\":false,\"summon_ai_paused\":false,\"observer_life_override\":"
        <<(hydra?100000:0)<<",\"spawned_followers_removed\":"<<removedMinions<<"}";identity.close();
      std::ofstream frames(output/"frames.csv");
      std::ofstream pets(output/"summons.csv");pets<<"step,unit_id,class_id,x,y,owner_type,owner_id,threat,unit_flags\n";
      U summonSamples=0;
      frames<<"step,frame,mode,x,y,seed_low,seed_high,player_x,player_y,player_mode,anim_frame,anim_speed,action_frame,room,path_target_type,path_target_id,used_skill_id\n";
      for(U step=0;step<=500;++step){
        if(hydra)for(auto item:units())if(!before.count(item.first)&&item.first!=monster){
          U unit=item.first,id=d.get(unit+4);
          if(golem?id!=289:(id<351||id>353))continue;
          owned.emplace(item);
          auto at=position(d,unit);
          U ai=d.get(d.get(unit+0x14)+0x28);require(ai!=0,"Hydra AI control missing");
          pets<<step<<','<<item.second<<','<<id<<','<<at.first<<','<<at.second<<','
            <<d.get(ai+0x30)<<','<<d.get(ai+0x2C)<<','
            <<static_cast<U>(d.get<BYTE>(d.get(d.get(unit+0x14))+0x4E))<<','<<d.get(unit+0xC4)<<'\n';++summonSamples;
        }
        require(d.get(monster+0xC)==owned.at(monster),"Monster lifetime changed");
        auto xy=position(d,monster),p=position(d,player);
        U skillList=d.get(monster+0xA8),usedSkill=skillList?d.get(skillList+0x10):0;
        int skillId=usedSkill?d.get<std::uint16_t>(d.get(usedSkill)):-1;
        frames<<step<<','<<d.get(game+0xA8)<<','<<d.get(monster+0x10)<<','<<xy.first<<','<<xy.second<<','
          <<d.get(monster+0x20)<<','<<d.get(monster+0x24)<<','<<p.first<<','<<p.second<<','<<d.get(player+0x10)<<','
          <<d.get(monster+0x44)<<','<<d.get<std::int16_t>(monster+0x4C)<<','<<static_cast<U>(d.get<BYTE>(monster+0x4E))<<','
          <<d.get(d.get(monster+0x2C)+0x1C)<<','
          <<d.get(d.get(monster+0x2C)+0x5C)<<','<<d.get(d.get(monster+0x2C)+0x60)<<','<<skillId<<'\n';
        ++samples;if(step==500)break;
        U now=d.get(game+0xA8);d.arm(ret);d.set(original);d.resume();CONTEXT returned=d.waitBreakpoint(ret,3000);
        require(d.get(game+0xA8)==now+1,"Update skipped frame");
        d.arm(entry);d.set(returned);d.resume();original=d.waitBreakpoint(entry,3000);
        require(original.Ecx==game && d.get(original.Esp)==ret,"Update context changed");
      }
      cleanupOwned();require(!hydra||summonSamples>0,"No native Hydra summon observed");success=true;
    }catch(...){if(d.held && !d.pendingCall)cleanupOwned();throw;}
  }catch(const std::exception& e){error=e.what();std::cerr<<error<<'\n';}
  bool cleaned=d.cleanup();
  auto save=[&](){if(!created)return;std::ofstream s(output/"summary.json");
    s<<"{\"status\":\""<<(success&&cleaned?"passed":"failed")<<"\",\"class_id\":"<<klass
      <<",\"samples\":"<<samples<<",\"fixtures_removed\":"<<(removed?"true":"false")
      <<",\"cleanup_confirmed\":"<<(cleaned?"true":"false")<<",\"detached\":"<<(d.detached?"true":"false")
      <<",\"restart_required\":"<<(removed?"false":"true")<<",\"ai_parity\":\"not_evaluated\"}";};
  save();while(d.attached&&!cleaned){Sleep(1000);cleaned=d.cleanup();}save();return success&&cleaned?0:1;
}
