// Batch numeric oracle, not a cast harness. Read all loaded skill rows and
// evaluate all Barbarian formula columns at levels 1..40 in ONE attachment.
// Uses the existing CXSorc with zero Barbarian hard points; never assigns skills
// or changes equipment. This context does not prove Barbarian action parity.
#define D2_CONTROLLER_LIBRARY
#include "d2game_controller.cpp"

struct FormulaColumn { const char* name; U offset; };
static const FormulaColumn columns[] = {
  {"prgcalc1",0x38},{"prgcalc2",0x3C},{"prgcalc3",0x40},
  {"auralencalc",0x60},{"aurarangecalc",0x64},
  {"aurastatcalc1",0x68},{"aurastatcalc2",0x6C},{"aurastatcalc3",0x70},
  {"aurastatcalc4",0x74},{"aurastatcalc5",0x78},{"aurastatcalc6",0x7C},
  {"passivecalc1",0xA4},{"passivecalc2",0xA8},{"passivecalc3",0xAC},
  {"passivecalc4",0xB0},{"passivecalc5",0xB4},
  {"petmax",0xC0},{"sumsk1calc",0xD0},{"sumsk2calc",0xD4},
  {"sumsk3calc",0xD8},{"sumsk4calc",0xDC},{"sumsk5calc",0xE0},
  {"cltcalc1",0x114},{"cltcalc2",0x118},{"cltcalc3",0x11C},
  {"perdelay",0x128},{"calc1",0x138},{"calc2",0x13C},
  {"calc3",0x140},{"calc4",0x144},{"skpoints",0x170},
  {"delay",0x190},{"ToHitCalc",0x1A0},{"DmgSymPerCalc",0x1D8},
  {"EDmgSymPerCalc",0x210},{"ELenSymPerCalc",0x224}
};
int wmain(int argc, wchar_t** argv) {
  SetErrorMode(SEM_FAILCRITICALERRORS | SEM_NOGPFAULTERRORBOX | SEM_NOOPENFILEERRORBOX);
  if (argc != 4) return 2;
  Controller d; bool success=false, created=false; U calls=0, rows=0, firstFrame=0;
  const auto output=std::filesystem::absolute(argv[3]); std::string error;
  try {
    d.pid=std::stoul(argv[1]); const auto root=std::filesystem::absolute(argv[2]);
    require(std::filesystem::create_directory(output), "Output must be new"); created=true;
    d.process=OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_CREATE_THREAD | PROCESS_VM_READ |
      PROCESS_VM_WRITE | PROCESS_VM_OPERATION | PROCESS_SUSPEND_RESUME | SYNCHRONIZE,FALSE,d.pid);
    require(d.process!=nullptr,"OpenProcess");
    wchar_t image[32768]{}; DWORD size=32768;
    require(QueryFullProcessImageNameW(d.process,0,image,&size),"Process image");
    require(std::filesystem::equivalent(image,root/L"D2v110各版本免CD全功能启动.exe"),"Wrong executable");
    fileHash(root/L"D2v110各版本免CD全功能启动.exe","602d3c638d782df6ad3edf8efbe07bbddd771f625e9491056084b83484848958");
    fileHash(root/L"D2Game.dll","2bd92fb25e3e4b8269930712620ba9db00cdc1aa44569d87dc4aba76553272b6");
    fileHash(root/L"D2Common.dll","b9d1a27c6a35992932c7c47438d02e5269f878e899f6e614d507902cecd1beab");
    const auto gm=module(d.pid,L"D2Game.dll",root), cm=module(d.pid,L"D2Common.dll",root);
    const U entry=gm.base+0x86D0, evaluate=cm.base+0x6F7E0;
    signature(d,entry,{0x83,0xec,0x08,0x53,0x56,0x57,0x8b,0xf9,0xbb,0x01,0,0,0});
    // Hash-pinned export #11276: stdcall(unit, bytecodeOffset, skillId, level), ret 0x10.
    signature(d,evaluate,{0xA1});
    require(d.get(evaluate+1)==cm.base+0x96A20,"Unexpected evaluator table pointer");
    signature(d,evaluate+5,{0x83,0xEC,0x0C,0x8B,0x48,0x40});
    enableDebugPrivilege();
    require(DebugActiveProcess(d.pid),"DebugActiveProcess"); d.attached=true;
    require(DebugSetProcessKillOnExit(FALSE),"Disable kill on debugger exit");
    d.waitBreakpoint(0,3000,true); d.arm(entry); d.resume();
    CONTEXT original=d.waitBreakpoint(entry,8000); d.gameThread=d.event.dwThreadId;
    const U game=original.Ecx, returnTo=d.get(original.Esp); U player=0;
    require(returnTo>=gm.base && returnTo<gm.base+gm.size,"Caller outside D2Game");
    validateGame(d,game,127897359,player); firstFrame=d.get(game+0xA8);
    require(d.get(player+4)==1,"Requires pinned zero-Barbarian CXSorc context");
    const U playerData=d.get(player+0x14); char name[17]{}; d.read(playerData,name,16);
    require(std::string(name)=="CXSorc","Wrong test character");
    const U skills=d.get(player+0xA8); require(skills!=0,"Missing skill list");
    std::set<U> seen;
    for(U sk=d.get(skills+4);sk;sk=d.get(sk+4)) {
      require(seen.insert(sk).second && seen.size()<512,"Invalid skill list");
      const U id=d.get<std::uint16_t>(d.get(sk));
      require(id<126 || id>155 || d.get(sk+0x28)==0,"Barbarian hard points must be zero");
    }
    const U tables=d.get(cm.base+0x96A20), count=d.get(tables+0xBA0);
    const U records=d.get(tables+0xB98), code=d.get(tables+0x40), codeSize=d.get(tables+0x44);
    require(count>=156 && count<=4096 && code && codeSize>0 && codeSize<=16*1024*1024,"Invalid skill tables");
    auto dump=[&](const char* filename,U at,U bytes) {
      std::vector<BYTE> data(bytes); d.read(at,data.data(),bytes);
      std::ofstream out(output/filename,std::ios::binary); out.write(reinterpret_cast<char*>(data.data()),bytes);
      require(out.good(),"Write table artifact");
    };
    // 0x23c stride confirmed in this DLL's getter at RVA 0x72832..0x7283a.
    dump("skills-loaded.bin",records,count*0x23C); dump("skillcode-loaded.bin",code,codeSize);
    std::ofstream identity(output/"identity.json");
    identity<<"{\"owner_class\":1,\"character\":\"CXSorc\",\"profile\":\"zero-barbarian-hardpoints\",\"skill_count\":"<<count
      <<",\"record_stride\":572,\"code_bytes\":"<<codeSize<<",\"frame\":"<<firstFrame<<",\"seed\":127897359}";
    std::ofstream values(output/"formula-values.csv");
    values<<"skill,level,field,code_offset,value,owner_class,profile\n";
    for(U id=126;id<=155;id++) {
      const U row=records+id*0x23C;
      require(d.get<std::uint16_t>(row)==id && d.get<BYTE>(row+0xC)==4,"Wrong Barbarian skill row");
      for(U level=1;level<=40;level++) for(const auto& column:columns) {
        const U offset=d.get(row+column.offset);
        if(offset==0xFFFFFFFF) continue;
        require(offset<codeSize,"Invalid formula bytecode offset");
        const U result=d.callStdcall4(original,returnTo,evaluate,player,offset,id,level); calls++;
        values<<id<<','<<level<<','<<column.name<<','<<offset<<','<<static_cast<std::int32_t>(result)
          <<",1,zero-barbarian-hardpoints\n"; rows++;
        require(d.get(game+0xA8)==firstFrame,"Formula advanced simulation frame");
      }
      values.flush(); require(values.good(),"Write formula artifact");
      std::cout<<"skill "<<id<<" collected; calls="<<calls<<'\n'<<std::flush;
    }
    success=true;
  } catch(const std::exception& e) {
    error=e.what(); std::cerr<<error<<'\n';
    if(created) { std::ofstream out(output/"error.txt"); out<<error; }
  }
  bool cleaned=d.cleanup();
  auto summary=[&]() {
    if(!created) return;
    std::ofstream out(output/"summary.json");
    out<<"{\"status\":\""<<(success&&cleaned?"passed":"failed")<<"\",\"skills_requested\":30,\"levels_requested\":40,\"formula_rows\":"<<rows
      <<",\"native_calls\":"<<calls<<",\"first_frame\":"<<firstFrame<<",\"detached\":"<<(d.detached?"true":"false")
      <<",\"cleanup_confirmed\":"<<(cleaned?"true":"false")
      <<",\"scope\":\"numeric formulas in zero-Barbarian-hardpoint Sorceress context; casts, equipment, synergies, damage settlement and visual parity untested\"}";
  };
  summary();
  while(d.attached&&!cleaned) { Sleep(1000); cleaned=d.cleanup(); }
  summary(); return success&&cleaned?0:1;
}
