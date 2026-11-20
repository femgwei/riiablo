// One client-input Poison Javelin cast, stopped states and passive sprite probes.
// Fixed local CXPoison fixture, original scheduler, no invoked graphics/foreground calls.
#define D2_CONTROLLER_LIBRARY
#include "d2game_controller.cpp"
#include <array>
#pragma comment(lib, "user32.lib")

struct PoisonWindow { DWORD pid; HWND hwnd=nullptr; };
static BOOL CALLBACK poisonWindow(HWND hwnd, LPARAM arg) {
  auto& w=*reinterpret_cast<PoisonWindow*>(arg); DWORD pid=0;
  GetWindowThreadProcessId(hwnd,&pid); wchar_t klass[64]{}; GetClassNameW(hwnd,klass,64);
  if(pid==w.pid && std::wstring(klass)==L"Diablo II") {w.hwnd=hwnd;return FALSE;}
  return TRUE;
}

struct PoisonController : Controller {};

static U headClass = 38, cloudClass = 39;
static bool poisonClass(U id) { return id == headClass || id == cloudClass || id == 141; }

// These offsets are recorded with the raw bytes, not presumed to be the GPU's
// final draw argument. Common path direction is checked against getter code.
static void sample(PoisonController& d, std::ofstream& out, const char* side,
                   U step, U frame, U unit) {
  std::array<U, 0xEC/4> raw{}; d.read(unit, raw.data(), 0xEC);
  U path = raw[0x2C/4]; require(path && d.get(path+0x30) == unit, "Missile path back-reference");
  std::array<BYTE, 0x9C> p{}; d.read(path, p.data(), p.size());
  auto word = [&](U off) { U n; std::memcpy(&n, p.data()+off, 4); return n; };
  out << "{\"side\":\"" << side << "\",\"step\":" << step << ",\"game_frame\":" << frame
      << ",\"unit\":" << unit << ",\"class_id\":" << raw[1] << ",\"unit_id\":" << raw[3]
      << ",\"x_fixed\":" << word(0) << ",\"y_fixed\":" << word(4)
      << ",\"path_direction\":" << unsigned(p[0x64]) << ",\"path_new_direction\":" << unsigned(p[0x65])
      << ",\"direction_x\":" << static_cast<int>(word(0x6A)) << ",\"direction_y\":" << static_cast<int>(word(0x6E))
      << ",\"velocity_x\":" << static_cast<int>(word(0x72)) << ",\"velocity_y\":" << static_cast<int>(word(0x76))
      << ",\"unit_44\":" << raw[0x44/4] << ",\"unit_48\":" << raw[0x48/4]
      << ",\"unit_4c\":" << raw[0x4C/4] << ",\"gfx_pointer\":" << raw[0x54/4];
  U gfx = raw[0x54/4];
  if (gfx) {
    std::array<U, 0x48/4> g{};
    SIZE_T got = 0;
    if (ReadProcessMemory(d.process, reinterpret_cast<void*>(gfx), g.data(), sizeof g, &got) && got == sizeof g) {
      out << ",\"gfx_words\":[";
      for (size_t i=0;i<g.size();i++) { if(i) out << ','; out << g[i]; }
      out << ']';
      if (g[0x44/4]) {
        char name[128]{};
        if (ReadProcessMemory(d.process, reinterpret_cast<void*>(g[0x44/4]), name, sizeof(name)-1, &got)) {
          bool safe = true;
          for (unsigned char c : name) { if (!c) break; if(c < 32 || c > 126 || c == '"' || c == '\\') safe = false; }
          if (safe) out << ",\"gfx_name_candidate\":\"" << name << '"';
        }
      }
    }
  }
  out << ",\"unit_words\":[";
  for (size_t i=0;i<raw.size();i++) { if(i) out << ','; out << raw[i]; }
  out << ']';
  U data=raw[0x14/4];
  if(data) {
    std::array<U,0x34/4> words{};d.read(data,words.data(),sizeof words);
    out<<",\"missile_data_words\":[";
    for(size_t i=0;i<words.size();i++){if(i)out<<',';out<<words[i];}
    out<<"],\"total_frames\":"<<d.get<std::int16_t>(data+0xE)
       <<",\"current_frame\":"<<d.get<std::int16_t>(data+0x10);
  }
  out << "}\n";
}

int wmain(int argc, wchar_t** argv) {
  SetErrorMode(SEM_FAILCRITICALERRORS | SEM_NOGPFAULTERRORBOX | SEM_NOOPENFILEERRORBOX);
  if (argc < 4 || argc > 6 || (argc == 6 && std::wstring(argv[5]) != L"plague" && std::wstring(argv[5]) != L"firestorm")) return 2;
  bool plague = argc == 6 && std::wstring(argv[5]) == L"plague";
  bool firestorm = argc == 6 && std::wstring(argv[5]) == L"firestorm";
  U skillId = firestorm ? 225 : plague ? 25 : 15;
  U playerClass = firestorm ? 5 : 0;
  if (plague) { headClass = 43; cloudClass = 221; }
  if (firestorm) { headClass = 458; cloudClass = 457; }
  PoisonController d; bool success = false, created = false, castStarted = false;
  U firstFrame=0,lastFrame=0,serverSamples=0,clientSamples=0;
  U renderProbeEvents=0,renderPrepared=0,poisonDraws=0,poisonDrawReturns=0,poisonDrawSelections=0;
  std::map<U, std::set<U>> serverIds, clientIds;
  std::string error;
  auto output = std::filesystem::absolute(argv[3]);
  try {
    created = std::filesystem::create_directory(output); require(created, "New output required");
    d.pid = std::stoul(argv[1]); auto root = std::filesystem::absolute(argv[2]);
    d.process = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ | PROCESS_VM_WRITE | PROCESS_VM_OPERATION | PROCESS_SUSPEND_RESUME | SYNCHRONIZE, FALSE, d.pid);
    require(d.process != nullptr, "Open original process");
    wchar_t image[32768]{}; DWORD chars=32768;
    require(QueryFullProcessImageNameW(d.process, 0, image, &chars), "Process identity");
    require(std::filesystem::equivalent(image, root / L"D2v110各版本免CD全功能启动.exe"), "Wrong original loader");
    fileHash(root / L"D2Game.dll", "2bd92fb25e3e4b8269930712620ba9db00cdc1aa44569d87dc4aba76553272b6");
    fileHash(root / L"D2Common.dll", "b9d1a27c6a35992932c7c47438d02e5269f878e899f6e614d507902cecd1beab");
    fileHash(root / L"D2Client.dll", "94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282");
    fileHash(root / L"D2CMP.dll", "e725036e1118d34b98d2b739525ff75a225e6964b425469e4773c93f0c9cf882");
    Module gameModule=module(d.pid,L"D2Game.dll",root), common=module(d.pid,L"D2Common.dll",root), client=module(d.pid,L"D2Client.dll",root);
    Module cmp=module(d.pid,L"D2CMP.dll",root);
    U entry=gameModule.base+0x86D0;
    signature(d,entry,{0x83,0xEC,8,0x53,0x56,0x57,0x8B,0xF9,0xBB,1,0,0,0});
    U clientGlobal=client.base+0x11C200;
    std::vector<BYTE> getter{0xA1}; for(U i=0;i<4;i++) getter.push_back(static_cast<BYTE>(clientGlobal>>(8*i))); getter.push_back(0xC3);
    signature(d,client.base+0x883D0,getter);
    U missileHash=client.base+0x11BC00;
    std::vector<BYTE> hashGetter{0x83,0xE1,0x7F,0x8B,0x34,0x8D};
    for(U i=0;i<4;i++) hashGetter.push_back(static_cast<BYTE>(missileHash>>(8*i)));
    signature(d,client.base+0x86B1B,hashGetter);
    signature(d,common.base+0x69BF0,{0x8B,0x44,0x24,0x04,0x85,0xC0,0x75,0x05,0x32,0xC0,0xC2,0x04,0x00,0x8A,0x40,0x64,0xC2,0x04,0x00});
    enableDebugPrivilege(); require(DebugActiveProcess(d.pid), "Attach"); d.attached=true;
    require(DebugSetProcessKillOnExit(FALSE), "Disable kill-on-exit");
    d.waitBreakpoint(0,3000,true); d.arm(entry); d.resume();
    CONTEXT original=d.waitBreakpoint(entry,5000); d.gameThread=d.event.dwThreadId;
    U game=original.Ecx, returnTo=d.get(original.Esp), player=0;
    require(returnTo>=gameModule.base && returnTo<gameModule.base+gameModule.size, "Update return outside game");
    require(d.get<BYTE>(returnTo-5)==0xE8 && returnTo+d.get(returnTo-4)==entry, "Update caller mismatch");
    validateGame(d,game,127897359,player);
    char name[16]{}; d.read(d.get(player+0x14),name,16);
    require(std::strcmp(name,firestorm ? "CXFirestorm" : plague ? "CXPlague" : "CXPoison")==0 && d.get(player+4)==playerClass, "Require separate fixed skill fixture");
    U skills=d.get(player+0xA8), skill=d.get(skills+0xC);
    require(skill && d.get<std::uint16_t>(d.get(skill))==skillId && d.get(skill+0x28)==1, "Require selected fixed javelin level one");
    U path=d.get(player+0x2C), room=d.get(path+0x1C);
    require(d.get(d.get(d.get(room+0x38))+4)==2, "Require Blood Moor");
    int px=d.get<std::uint16_t>(path+2),py=d.get<std::uint16_t>(path+6);
    U grid=d.get(room+0x34),cells=d.get(grid+0x20);
    int gx=d.get<int>(grid),gy=d.get<int>(grid+4),gw=d.get<int>(grid+8),gh=d.get<int>(grid+12);
    require(gw>0 && gh>0 && gw<=512 && gh<=512, "Collision bounds");
    int tx=0,ty=0,directionIndex=-1;
    const std::vector<std::pair<int,int>> directions{{1,0},{0,1},{0,-1},{-1,0},{1,1},{1,-1},{-1,1},{-1,-1}};
    int requested=argc>=5 ? std::stoi(argv[4]) : -1;
    require(requested>=-1 && requested<8,"Unsupported bounded direction");
    for(int index=0;index<8;index++) {
      if(requested>=0 && index!=requested) continue;
      auto delta=directions[index];
      bool clear=true;
      for(int n=1;n<=12;n++) {
        int x=px+delta.first*n-gx,y=py+delta.second*n-gy;
        if(x<0 || x>=gw || y<0 || y>=gh || (d.get<std::uint16_t>(cells+2*(y*gw+x))&0x0127)) {clear=false;break;}
      }
      if(clear) {tx=px+delta.first*8;ty=py+delta.second*8;directionIndex=index;break;}
    }
    require(tx && ty, "No clear 12-subtile Poison corridor");
    std::ofstream identity(output/"identity.json");
    identity << "{\"player\":" << player << ",\"game\":" << game << ",\"client_player\":" << d.get(clientGlobal)
             << ",\"skill\":" << skillId << ",\"level\":1,\"direction_index\":" << directionIndex
             << ",\"x\":" << px << ",\"y\":" << py << ",\"target_x\":" << tx << ",\"target_y\":" << ty << '}';
    // Observe the existing renderer only. Never invoke a renderer, wake a window,
    // switch desktops, or force StartDraw to succeed. No hits is a valid finding.
    const U preparing=client.base+0xBAF28, prepareReturned=client.base+0xBAF2D, selected=cmp.base+0xBFE7,
            prepared=client.base+0xBAF3E, submit=client.base+0xBB066,
            otherSubmit=client.base+0xBB0B8, drawReturned=client.base+0xBB06B,
            otherReturned=client.base+0xBB0BD;
    signature(d,prepared,{0xE8,0x4D,0xA9,0xF5,0xFF}); // camera getter after cell validation
    signature(d,submit,{0xE8,0xE5,0x07,0x01,0x00}); // D2Gfx ordinal 10072 thunk
    signature(d,otherSubmit,{0xE8,0x93,0x07,0x01,0x00});
    signature(d,drawReturned,{0xB8,1,0,0,0});
    signature(d,otherReturned,{0x8B,0x84,0x24,0x34,1,0,0});
    signature(d,preparing,{0xE8,0xA5,0xF9,0,0});
    signature(d,prepareReturned,{0x85,0xC0,0x75,0x0D});
    signature(d,selected,{0x33,0xC9,0x8A,0x4D,0x23,0x49});
    std::ofstream draws(output/"sprite-submissions.jsonl");
    require(draws.is_open(),"Open sprite submission log");
    U renderThread=0,renderUnit=0,renderData=0,pendingPoison=0;
    U selectedDir=0,selectedDirs=0,selectedFrames=0,selectedOffset=0;
    bool hasSelection=false;
    d.observeOtherThreads=true;
    d.observeOnce=[&](CONTEXT c) {
      renderProbeEvents++;
      if(!renderThread) renderThread=d.event.dwThreadId;
      require(renderThread==d.event.dwThreadId,"Renderer hook changed thread");
      U at=c.Eip;
      if(at==preparing) {
        renderUnit=c.Esi; renderData=d.get(c.Esp); hasSelection=false;
        selectedDir=selectedDirs=selectedFrames=selectedOffset=0;
        require(renderUnit && d.get(renderUnit)<=5,"Preparing renderer unit identity");
        if(d.get(renderUnit)==3 && poisonClass(d.get(renderUnit+4))) {
          require(!d.get(renderData+4),"Poison Javelin unexpectedly uses the uncompressed cell-file branch");
          d.observedAlternative=selected; d.arm(selected);
        }
        d.observedEntry=prepareReturned;
      } else if(at==selected) {
        require(c.Esi==renderData,"DCC selection buffer identity");
        selectedDir=c.Eax;
        selectedDirs=U(d.get<BYTE>(c.Ebp+0x23))-1;
        selectedFrames=d.get<BYTE>(c.Ebp+0x24);
        require(selectedDirs>0 && selectedDirs<=64 && selectedDir<selectedDirs,"DCC direction selection bounds");
        selectedOffset=d.get(c.Ebp+0x2C+selectedDir*4);
        hasSelection=true; d.observedAlternative=0;
        d.observedEntry=prepareReturned;
      } else if(at==prepareReturned) {
        if(d.observedAlternative) {d.disarm(d.observedAlternative);d.observedAlternative=0;}
        require(c.Esi==renderUnit,"Preparing renderer changed unit");
        d.observedEntry=c.Eax ? prepared : preparing;
      } else if(at==prepared) {
        renderPrepared++; renderUnit=c.Esi; renderData=c.Esp+0xDC;
        require(renderUnit && d.get(renderUnit)<=5,"Renderer unit identity");
        d.observedEntry=d.get(renderUnit)==3 ? submit : otherSubmit;
      } else if(at==submit || at==otherSubmit) {
        require(d.get(c.Esp)==renderData,"Final draw cell differs from prepared cell");
        pendingPoison=0;
        if(d.get(renderUnit)==3 && poisonClass(d.get(renderUnit+4))) {
          std::array<U,18> cell{}; d.read(renderData,cell.data(),sizeof cell);
          require(cell[8]==3 && cell[9]==d.get(renderUnit+4),"Final draw class identity");
          U mp=d.get(renderUnit+0x2C); require(mp && d.get(mp+0x30)==renderUnit,"Draw path back-reference");
          pendingPoison=++poisonDraws;
          if(hasSelection)poisonDrawSelections++;
          draws << "{\"kind\":\"sprite_submission\",\"index\":" << pendingPoison
                << ",\"game_frame\":" << d.get(game+0xA8) << ",\"thread\":" << renderThread
                << ",\"call_rva\":" << at-client.base << ",\"unit\":" << renderUnit
                << ",\"unit_id\":" << d.get(renderUnit+0xC) << ",\"class_id\":" << cell[9]
                << ",\"x_fixed\":" << d.get(mp) << ",\"y_fixed\":" << d.get(mp+4)
                << ",\"velocity_x\":" << d.get<int>(mp+0x72) << ",\"velocity_y\":" << d.get<int>(mp+0x76)
                << ",\"path_direction\":" << unsigned(d.get<BYTE>(mp+0x64))
                << ",\"animation_cursor\":" << d.get(renderUnit+0x44)
                << ",\"frame\":" << cell[2] << ",\"direction\":" << cell[3]
                << ",\"selection_observed\":" << (hasSelection?"true":"false")
                << ",\"dcc_direction\":" << selectedDir << ",\"dcc_directions\":" << selectedDirs
                << ",\"dcc_frames\":" << selectedFrames << ",\"cache_direction_entry_raw\":" << selectedOffset
                << ",\"cell_10_raw\":" << cell[4] << ",\"cell_14_raw\":" << cell[5]
                << ",\"cell_raw\":[";
          for(size_t n=0;n<cell.size();n++) draws << (n?",":"") << cell[n];
          draws << ']';
          // The prepared block's +10/+14 may be zero. Counts belong to the
          // actual cell file, and its cell pointer array identifies selection.
          if(cell[1]) {
            std::array<U,6> header{}; d.read(cell[1],header.data(),sizeof header);
            draws << ",\"cell_file_header\":[";
            for(size_t n=0;n<header.size();n++) draws << (n?",":"") << header[n];
            draws << ']';
            if(header[0]==6 && header[4]>0 && header[4]<=64 && header[5]>0 && header[5]<=256) {
              std::vector<U> pointers(header[4]*header[5]);
              d.read(cell[1]+0x18,pointers.data(),pointers.size()*sizeof(U));
              draws << ",\"matching_cell_indices\":["; bool firstIndex=true;
              for(size_t n=0;n<pointers.size();n++) if(pointers[n]==cell[0]) {
                draws << (firstIndex?"":",") << n; firstIndex=false;
              }
              draws << ']';
            }
          }
          if(cell[17]) {
            char name[128]{}; SIZE_T got=0;
            if(ReadProcessMemory(d.process,reinterpret_cast<void*>(cell[17]),name,sizeof(name)-1,&got)) {
              bool safe=true;
              for(unsigned char ch:name) {if(!ch)break;if(ch<32 || ch>126 || ch=='"' || ch=='\\')safe=false;}
              if(safe) draws << ",\"cell_name\":\"" << name << '"';
            }
          }
          draws << "}\n"; draws.flush();
        }
        d.observedEntry=at==submit ? drawReturned : otherReturned;
      } else {
        require(at==drawReturned || at==otherReturned,"Unknown renderer probe stage");
        if(pendingPoison) {
          draws << "{\"kind\":\"sprite_call_returned\",\"index\":" << pendingPoison << "}\n";
          poisonDrawReturns++; pendingPoison=0; draws.flush();
        }
        d.observedEntry=preparing;
      }
      // Bound debugger overhead even when an unexpected scene is very busy.
      if(renderProbeEvents<80000) {if(!d.patches.count(d.observedEntry)) d.arm(d.observedEntry);}
      else {if(d.observedAlternative)d.disarm(d.observedAlternative);d.observedAlternative=0;d.observedEntry=0;}
    };
    d.observedEntry=preparing; d.arm(preparing);
    // Start through the original client input handler. A server-only mode
    // call can produce valid server missiles without executing CltDo on the
    // local client, which is precisely the evidence needed for this probe.
    PoisonWindow window{d.pid}; EnumWindows(poisonWindow,reinterpret_cast<LPARAM>(&window));
    require(window.hwnd!=nullptr,"Private-desktop game window");
    int camX=d.get<int>(client.base+0x10B9BC)-d.get<int>(client.base+0x10B9C8);
    int camY=d.get<int>(client.base+0x10B9B8);
    int sx=16*(tx-ty)-camX,sy=8*(tx+ty)+8-camY;
    require(sx>=16 && sx<784 && sy>=16 && sy<480,"Poison input outside client playfield");
    LPARAM point=MAKELPARAM(sx,sy);
    require(PostMessageW(window.hwnd,WM_MOUSEMOVE,0,point),"Post private mouse move");
    require(PostMessageW(window.hwnd,WM_RBUTTONDOWN,MK_RBUTTON,point),"Post private cast down");
    firstFrame=d.get(game+0xA8);
    std::ofstream samples(output/"missiles.jsonl");
    std::ofstream frames(output/"frames.csv");
    frames << "step,game_frame,server_player_mode,client_player_mode,client_hash_missiles\n";
    for(U step=0;step<=240;step++) {
      U frame=d.get(game+0xA8); lastFrame=frame;
      castStarted=castStarted || d.get(player+0x10)==(firestorm ? 10u : 11u);
      if(step==4) require(PostMessageW(window.hwnd,WM_RBUTTONUP,0,point),"Post private cast up");
      require(frame==firstFrame+step,"Non-contiguous server frames");
      std::set<U> seen;
      for(U bin=0;bin<128;bin++) for(U unit=d.get(game+0x1120+4*128*4+bin*4);unit;unit=d.get(unit+0xE4)) {
        require(seen.insert(unit).second && seen.size()<=4096,"Invalid server missile chain");
        if(d.get(unit)==3 && poisonClass(d.get(unit+4))) {
          sample(d,samples,"server",step,frame,unit); serverSamples++; serverIds[d.get(unit+4)].insert(d.get(unit+0xC));
        }
      }
      U cp=d.get(clientGlobal); require(cp && cp!=player && d.get(cp)==0 && d.get(cp+4)==playerClass,"Separate client player");
      U act=d.get(cp+0x1C); require(act && d.get(act+0x1C),"Require client Act");
      seen.clear(); std::set<U> rooms;
      // Missiles are primarily client-only hash units and need not appear in
      // Room1 unit lists. The original missile lookup above proves this base.
      U clientHashCount=0;
      for(U table : {client.base+0x11AA00+3*128*4, missileHash}) {
        for(U bin=0;bin<128;bin++) {
          std::set<U> chain;
          for(U unit=d.get(table+bin*4);unit;unit=d.get(unit+0xE4)) {
            require(chain.insert(unit).second && chain.size()<=4096,"Invalid client missile hash chain");
            require(d.get(unit)==3 && (d.get(unit+0xC)&127)==bin,"Client missile hash identity");
            clientHashCount++;
            if(seen.insert(unit).second && poisonClass(d.get(unit+4))) {
              sample(d,samples,"client",step,frame,unit); clientSamples++; clientIds[d.get(unit+4)].insert(d.get(unit+0xC));
            }
          }
        }
      }
      frames << step << ',' << frame << ',' << d.get(player+0x10) << ',' << d.get(cp+0x10) << ',' << clientHashCount << '\n';
      frames.flush();
      for(U cr=d.get(act+4);cr;cr=d.get(cr+0x7C)) {
        require(rooms.insert(cr).second && rooms.size()<=1024 && d.get(cr+0x78)==act,"Invalid client room chain");
        std::set<U> local;
        for(U unit=d.get(cr+0x2C);unit;unit=d.get(unit+0xE8)) {
          require(local.insert(unit).second && local.size()<=8192,"Invalid client room unit chain");
          if(seen.insert(unit).second && d.get(unit)==3 && poisonClass(d.get(unit+4))) {
            sample(d,samples,"client",step,frame,unit); clientSamples++; clientIds[d.get(unit+4)].insert(d.get(unit+0xC));
          }
        }
      }
      samples.flush();
      if(step==240) break;
      d.arm(returnTo); d.set(original); d.resume(); CONTEXT returned=d.waitBreakpoint(returnTo,3000);
      require(d.get(game+0xA8)==frame+1,"Server update skipped frame");
      d.arm(entry); d.set(returned); d.resume(); original=d.waitBreakpoint(entry,3000);
      require(original.Ecx==game && d.get(original.Esp)==returnTo,"Update context changed");
    }
    require(castStarted && serverIds[headClass].size()==(firestorm ? 3u : 1u),"Unexpected server head count");
    require(clientIds[headClass].size()==(firestorm ? 3u : 1u) && clientIds[cloudClass].size()>0
        && (firestorm || clientIds[141].size()>0),"Missing client skill or trail observations");
    success=true;
  } catch(const std::exception& e) {error=e.what();std::cerr << error << '\n';}
  bool cleaned=d.cleanup();
  auto save=[&]() {
    if(!created) return;
    std::ofstream s(output/"summary.json");
    s << "{\"status\":\"" << (success && cleaned?"passed":"failed") << "\",\"cast_started\":" << (castStarted?"true":"false")
      << ",\"first_frame\":" << firstFrame << ",\"last_frame\":" << lastFrame << ",\"server_samples\":" << serverSamples << ",\"client_samples\":" << clientSamples
      << ",\"render_probe_events\":" << renderProbeEvents << ",\"render_cells_prepared\":" << renderPrepared
      << ",\"poison_sprite_submissions\":" << poisonDraws << ",\"poison_sprite_calls_returned\":" << poisonDrawReturns
      << ",\"poison_dcc_selections_observed\":" << poisonDrawSelections
      << ",\"server_unique\":{";
    bool first=true; for(auto& p:serverIds) {if(!first)s<<',';first=false;s<<'"'<<p.first<<"\":"<<p.second.size();}
    s << "},\"client_unique\":{";
    first=true;for(auto& p:clientIds) {if(!first)s<<',';first=false;s<<'"'<<p.first<<"\":"<<p.second.size();}
    s << "},\"detached\":" << (d.detached?"true":"false") << ",\"cleanup_confirmed\":" << (cleaned?"true":"false")
      << ",\"cleanup_attention_required\":" << (cleaned?"false":"true") << ",\"error\":\"" << error
      << "\",\"scope\":\"original client-input cast; separate server/client states; observed D2CMP selections and D2Gfx sprite calls/returns; no pixel or damage parity claim\"}";
  };
  save(); while(d.attached && !cleaned) {Sleep(1000);cleaned=d.cleanup();} save();
  return success && cleaned?0:1;
}
