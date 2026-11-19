// Read-only, version-pinned D2Game server-unit census.
// The helper pauses at the native update entry, discovers UNIT hash heads by
// their validated game back-reference, walks each chain, then detaches.
#define D2_CONTROLLER_LIBRARY
#include "d2game_controller.cpp"
#include <tuple>

static bool plausible(U value) { return value >= 0x10000 && value < 0x80000000; }

int wmain(int argc, wchar_t** argv) {
  SetErrorMode(SEM_FAILCRITICALERRORS | SEM_NOGPFAULTERRORBOX | SEM_NOOPENFILEERRORBOX);
  if (argc != 4) return 2; // pid, home, output
  Controller d; bool success = false; U game = 0, frame = 0, seed = 0;
  auto output = std::filesystem::absolute(argv[3]);
  try {
    d.pid = std::stoul(argv[1]);
    require(std::filesystem::create_directory(output), "Output directory must be new");
    enableDebugPrivilege();
    d.process = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ | PROCESS_VM_WRITE |
                            PROCESS_VM_OPERATION | PROCESS_SUSPEND_RESUME | SYNCHRONIZE,
                            FALSE, d.pid);
    require(d.process != nullptr, "OpenProcess");
    wchar_t executable[32768]{}; DWORD chars = 32768;
    require(QueryFullProcessImageNameW(d.process, 0, executable, &chars), "Process image");
    const auto root = std::filesystem::absolute(argv[2]);
    require(std::filesystem::equivalent(executable, root / L"D2v110各版本免CD全功能启动.exe"), "Unexpected target executable");
    fileHash(root / L"D2Game.dll", "2bd92fb25e3e4b8269930712620ba9db00cdc1aa44569d87dc4aba76553272b6");
    Module gm = module(d.pid, L"D2Game.dll", root);
    const U entry = gm.base + 0x86D0;
    signature(d, entry, {0x83,0xec,0x08,0x53,0x56,0x57,0x8b,0xf9,0xbb,0x01,0,0,0});
    require(DebugActiveProcess(d.pid), "DebugActiveProcess"); d.attached = true;
    require(DebugSetProcessKillOnExit(FALSE), "Disable kill on debugger exit");
    d.waitBreakpoint(0, 3000, true);
    d.arm(entry); d.resume();
    CONTEXT original = d.waitBreakpoint(entry, 5000);
    d.gameThread = d.event.dwThreadId;
    game = original.Ecx;
    require(plausible(game), "Invalid server game pointer");
    U act = d.get(game + 0xBC), drlg = d.get(act + 8);
    seed = d.get(act + 0xC);
    require(d.get(drlg + 0x3E8) == game, "Server DRLG/game linkage failed");
    frame = d.get(game + 0xA8);

    std::set<U> heads;
    // Scan the bounded game header for pointers whose unit back-reference is
    // this D2Game instance. This discovers hash tables without guessing their
    // table offset beyond the already validated player hash at +0x1120.
    for (U offset = 0; offset <= 0x3000; offset += 4) {
      U candidate = 0;
      try { candidate = d.get(game + offset); } catch (...) { continue; }
      if (!plausible(candidate)) continue;
      try {
        U type = d.get(candidate), back = d.get(candidate + 0x80);
        if (type <= 5 && back == game) heads.insert(candidate);
      } catch (...) {}
    }

    std::ofstream out(output / "server-population.json");
    out << "{\"status\":\"starting\",\"game\":" << game
        << ",\"seed\":" << seed << ",\"frame\":" << frame << ",\"hash_heads\":[";
    bool firstHead = true;
    std::set<U> units;
    std::map<unsigned, unsigned> typeCounts, classCounts;
    std::vector<std::tuple<U,U,U,U,U>> records;
    for (U head : heads) {
      if (!firstHead) out << ','; firstHead = false; out << head;
      U unit = head; std::set<U> chain;
      for (unsigned count = 0; unit && count < 8192; ++count) {
        if (!plausible(unit) || !chain.insert(unit).second) break;
        try {
          U type = d.get(unit), cls = d.get(unit + 4), id = d.get(unit + 0xC);
          if (type > 5 || d.get(unit + 0x80) != game) break;
          U next = d.get(unit + 0xE4);
          if (units.insert(unit).second) {
            typeCounts[type]++;
            if (type == 1) classCounts[cls]++;
            records.emplace_back(unit, type, cls, id, next);
          }
          unit = next;
        } catch (...) { break; }
      }
    }
    out << "],\"units\":[";
    for (size_t i = 0; i < records.size(); ++i) {
      if (i) out << ',';
      auto [ptr,type,cls,id,next] = records[i];
      out << "{\"pointer\":" << ptr << ",\"type\":" << type
          << ",\"class_id\":" << cls << ",\"unit_id\":" << id
          << ",\"next\":" << next << "}";
    }
    out << "],\"type_counts\":{";
    bool first = true; for (auto [key,value] : typeCounts) { if (!first) out << ','; first=false; out << '"' << key << "\":" << value; }
    out << "},\"monster_class_counts\":{";
    first = true; for (auto [key,value] : classCounts) { if (!first) out << ','; first=false; out << '"' << key << "\":" << value; }
    out << "},\"scope\":\"D2Game server unit hash candidates at one paused update frame; read-only\"}";
    out.close();
    success = true;
  } catch (const std::exception& error) {
    std::ofstream err(output / "error.txt"); err << error.what();
  }
  bool cleaned = d.cleanup();
  std::ofstream summary(output / "summary.json");
  summary << "{\"status\":\"" << (success && cleaned ? "passed" : "failed")
          << "\",\"detached\":" << (d.detached ? "true" : "false")
          << ",\"cleanup_confirmed\":" << (cleaned ? "true" : "false")
          << ",\"game\":" << game << ",\"seed\":" << seed
          << ",\"frame\":" << frame << "}";
  while (d.attached && !cleaned) { Sleep(1000); cleaned = d.cleanup(); }
  return success && cleaned ? 0 : 1;
}
