// Passive D2Net outbound-call probe for the hash-pinned 1.10f loader.
//
// This helper does not send packets or change target memory except for
// temporary software breakpoints. It records the first call to any D2Net
// export, then restores the bytes, detaches and reports the observed stack.
// The result is ABI evidence only; it is not a navigation command.
#define D2_CONTROLLER_LIBRARY
#include "d2game_controller.cpp"

static std::string hexBytes(const std::vector<BYTE>& bytes) {
  std::ostringstream out;
  out << std::hex << std::setfill('0');
  for (BYTE b : bytes) out << std::setw(2) << static_cast<unsigned>(b);
  return out.str();
}

int wmain(int argc, wchar_t** argv) {
  SetErrorMode(SEM_FAILCRITICALERRORS | SEM_NOGPFAULTERRORBOX | SEM_NOOPENFILEERRORBOX);
  if (argc != 5) return 2; // pid, home, output, seconds
  Controller d;
  bool success = false, hit = false;
  unsigned hits = 0;
  std::string errorText;
  auto output = std::filesystem::absolute(argv[3]);
  try {
    d.pid = std::stoul(argv[1]);
    const DWORD seconds = std::stoul(argv[4]);
    require(seconds >= 1 && seconds <= 60, "Invalid bounded trace duration");
    require(std::filesystem::create_directory(output), "Output directory must be new");
    enableDebugPrivilege();
    d.process = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ |
                             PROCESS_VM_WRITE | PROCESS_VM_OPERATION |
                             PROCESS_SUSPEND_RESUME | SYNCHRONIZE,
                             FALSE, d.pid);
    require(d.process != nullptr, "OpenProcess");
    wchar_t executable[32768]{}; DWORD chars = 32768;
    require(QueryFullProcessImageNameW(d.process, 0, executable, &chars), "Process image");
    const auto root = std::filesystem::absolute(argv[2]);
    require(std::filesystem::equivalent(executable, root / L"D2v110各版本免CD全功能启动.exe"),
            "Unexpected target executable");
    fileHash(root / L"D2Net.dll", "07cf1675c58cec7b62f3ac1ea4a08e21a0517f2b508f9c21cad1d3f3c845c79f");
    Module netModule = module(d.pid, L"D2Net.dll", root);
    // All 41 ordinal exports from the hash-pinned D2Net.dll.  Tracing the
    // complete table matters here because local single-player input does not
    // necessarily use the export that Game.exe imports for online startup.
    const std::vector<U> exportRvas = {
      0x11B0,0x1240,0x2130,0x2150,0x2220,0x1760,0x22B0,0x1300,
      0x1310,0x2250,0x2270,0x2290,0x19A0,0x2410,0x2470,0x24F0,
      0x1000,0x2510,0x2530,0x2550,0x2580,0x25F0,0x2200,0x1B30,
      0x1120,0x21D0,0x21F0,0x2610,0x2620,0x1B60,0x1E60,0x2490,
      0x24B0,0x24D0,0x2190,0x21B0,0x2450,0x2430,0x1FA0,0x1FB0,
      0x1FB0};
    const U interruptedSend = netModule.base + 0x21B0; // ordinal 10036
    const std::vector<BYTE> expected = {0x8B,0x44,0x24,0x04,0x8B,0x54,0x24,0x08,
                                        0x8B,0x0D,0x68,0xB2,0xC0,0x6F,0x50};
    std::vector<BYTE> live(expected.size());
    d.read(interruptedSend, live.data(), live.size());
    // A previously interrupted probe can leave only its one-byte INT3 in
    // place after the helper process is forcibly stopped.  The old helper is
    // no longer attached at this point, so restore that byte after verifying
    // the remaining instruction bytes still match the pinned DLL.
    if (live[0] == 0xCC && std::equal(live.begin() + 1, live.end(), expected.begin() + 1)) {
      const BYTE original = expected[0];
      d.write(interruptedSend, &original, 1, true);
      live[0] = original;
    }
    require(live == expected, "Unsupported live instruction signature");

    require(DebugActiveProcess(d.pid), "DebugActiveProcess");
    d.attached = true;
    require(DebugSetProcessKillOnExit(FALSE), "Disable kill on debugger exit");
    d.waitBreakpoint(0, 3000, true);
    for (U rva : exportRvas) {
      const U address = netModule.base + rva;
      // The export table has no duplicate RVAs in the file; keep the list
      // defensive so a future table edit cannot arm one address twice.
      if (!d.patches.count(address)) d.arm(address);
    }
    d.resume();

    const ULONGLONG deadline = GetTickCount64() + static_cast<ULONGLONG>(seconds) * 1000;
    while (GetTickCount64() < deadline && hits < 32) {
      try {
        CONTEXT c = d.waitBreakpoint(0, 250, false);
        hit = true;
        hits++;
        std::vector<U> stack(8);
        d.read(c.Esp, stack.data(), stack.size() * sizeof(U));
        std::vector<BYTE> packet;
        // D2Net exports 10010/10011/10012 use stdcall(packet, length).  Keep
        // those two raw arguments even when the first hit is another wrapper;
        // this is the evidence needed to identify the client send ABI.
        const U arg1 = stack.size() > 1 ? stack[1] : 0;
        const U arg2 = stack.size() > 2 ? stack[2] : 0;
        if (arg1 >= 0x10000 && arg1 < 0x80000000 && arg2 <= 0x10000) {
          try {
            const size_t bytesToRead = std::min<size_t>(arg2, 2048);
            packet.resize(bytesToRead);
            if (bytesToRead) d.read(arg1, packet.data(), bytesToRead);
          } catch (...) { packet.clear(); }
        }
        std::ofstream trace(output / "trace.jsonl", std::ios::app);
        const U eip = reinterpret_cast<U>(d.event.u.Exception.ExceptionRecord.ExceptionAddress);
        trace << "{\"eip_rva\":\"0x" << std::hex << (eip - netModule.base) << std::dec
              << "\",\"thread\":" << d.event.dwThreadId
              << ",\"eax\":" << c.Eax << ",\"ecx\":" << c.Ecx
              << ",\"edx\":" << c.Edx << ",\"esp\":" << c.Esp
              << ",\"arg1\":" << arg1 << ",\"arg2\":" << arg2
              << ",\"stack\":[";
        for (size_t i = 0; i < stack.size(); i++) trace << (i ? "," : "") << stack[i];
        trace << "]";
        if (!packet.empty()) trace << ",\"candidate_bytes_hex\":\"" << hexBytes(packet) << "\"";
        trace << "}\n";
        trace.close();
        // Re-arm only the export that actually fired.  The bounded hit cap
        // prevents a busy client from producing an unbounded trace.
        if (eip >= netModule.base && eip < netModule.base + netModule.size) {
          d.resume();
          d.arm(eip);
        } else {
          d.resume();
        }
      } catch (const std::exception& e) {
        // A short timeout is expected while the game is idle. Any other
        // exception is retained and handled by the common cleanup path.
        if (std::string(e.what()).find("Timed out waiting") == std::string::npos) throw;
      }
    }
    success = true;
  } catch (const std::exception& error) {
    errorText = error.what();
  }
  bool cleaned = d.cleanup();
  std::ofstream summary(output / "summary.json");
  summary << "{\"status\":\"" << (success && cleaned ? "passed" : "failed")
          << "\",\"hit\":" << (hit ? "true" : "false")
          << ",\"hits\":" << hits
          << ",\"detached\":" << (d.detached ? "true" : "false")
          << ",\"cleanup_confirmed\":" << (cleaned ? "true" : "false")
          << ",\"error\":\"";
  for (char ch : errorText) {
    if (ch == '\\' || ch == '"') summary << '\\';
    if (ch == '\n') summary << "\\n"; else summary << ch;
  }
  summary << "\"}\n";
  summary.close();
  while (d.attached && !cleaned) { Sleep(1000); cleaned = d.cleanup(); }
  return success && cleaned ? 0 : 1;
}
