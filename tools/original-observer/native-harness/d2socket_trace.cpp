// Passive Winsock send probe for the pinned 1.10f client.
#define D2_CONTROLLER_LIBRARY
#include "d2game_controller.cpp"

static Module loadedModule(DWORD pid, const wchar_t* name) {
  HANDLE snapshot = CreateToolhelp32Snapshot(TH32CS_SNAPMODULE | TH32CS_SNAPMODULE32, pid);
  require(snapshot != INVALID_HANDLE_VALUE, "Module snapshot");
  MODULEENTRY32W m{}; m.dwSize = sizeof m;
  for (BOOL more = Module32FirstW(snapshot, &m); more; more = Module32NextW(snapshot, &m)) {
    if (_wcsicmp(m.szModule, name) == 0) {
      CloseHandle(snapshot); return {reinterpret_cast<U>(m.modBaseAddr), m.modBaseSize};
    }
  }
  CloseHandle(snapshot); throw std::runtime_error("Required system module missing");
}

static std::string hexBytes(const std::vector<BYTE>& bytes) {
  std::ostringstream out; out << std::hex << std::setfill('0');
  for (BYTE b : bytes) out << std::setw(2) << static_cast<unsigned>(b);
  return out.str();
}

int wmain(int argc, wchar_t** argv) {
  SetErrorMode(SEM_FAILCRITICALERRORS | SEM_NOGPFAULTERRORBOX | SEM_NOOPENFILEERRORBOX);
  if (argc != 5) return 2;
  Controller d; bool success = false, hit = false; unsigned hits = 0; std::string errorText;
  auto output = std::filesystem::absolute(argv[3]);
  try {
    d.pid = std::stoul(argv[1]); const DWORD seconds = std::stoul(argv[4]);
    require(seconds >= 1 && seconds <= 60, "Invalid bounded trace duration");
    require(std::filesystem::create_directory(output), "Output directory must be new");
    enableDebugPrivilege();
    d.process = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ | PROCESS_VM_WRITE |
                             PROCESS_VM_OPERATION | PROCESS_SUSPEND_RESUME | SYNCHRONIZE, FALSE, d.pid);
    require(d.process != nullptr, "OpenProcess");
    wchar_t executable[32768]{}; DWORD chars = 32768;
    require(QueryFullProcessImageNameW(d.process, 0, executable, &chars), "Process image");
    const auto root = std::filesystem::absolute(argv[2]);
    require(std::filesystem::equivalent(executable, root / L"D2v110各版本免CD全功能启动.exe"), "Unexpected target executable");
    fileHash(root / L"D2Net.dll", "07cf1675c58cec7b62f3ac1ea4a08e21a0517f2b508f9c21cad1d3f3c845c79f");
    Module targetWs = loadedModule(d.pid, L"WSOCK32.dll");
    HMODULE localWs = LoadLibraryW(L"WSOCK32.dll"); require(localWs != nullptr, "Load local WSOCK32");
    FARPROC localSend = GetProcAddress(localWs, "send"); require(localSend != nullptr, "Resolve WSOCK32 send");
    const U send = targetWs.base + (reinterpret_cast<U>(localSend) - reinterpret_cast<U>(localWs));

    require(DebugActiveProcess(d.pid), "DebugActiveProcess"); d.attached = true;
    require(DebugSetProcessKillOnExit(FALSE), "Disable kill on debugger exit");
    d.waitBreakpoint(0, 3000, true); d.arm(send); d.resume();
    const ULONGLONG deadline = GetTickCount64() + static_cast<ULONGLONG>(seconds) * 1000;
    while (GetTickCount64() < deadline && hits < 32) {
      try {
        CONTEXT c = d.waitBreakpoint(send, 250, false); hit = true; hits++;
        std::vector<U> stack(8); d.read(c.Esp, stack.data(), stack.size() * sizeof(U));
        const U socket = stack[1], buffer = stack[2], length = stack[3], flags = stack[4];
        std::vector<BYTE> packet;
        if (buffer >= 0x10000 && buffer < 0x80000000 && length <= 4096) {
          try { packet.resize(length); if (length) d.read(buffer, packet.data(), length); }
          catch (...) { packet.clear(); }
        }
        std::ofstream trace(output / "trace.jsonl", std::ios::app);
        trace << "{\"thread\":" << d.event.dwThreadId << ",\"socket\":" << socket
              << ",\"buffer\":" << buffer << ",\"length\":" << length
              << ",\"flags\":" << flags << ",\"stack\":[";
        for (size_t i = 0; i < stack.size(); i++) trace << (i ? "," : "") << stack[i];
        trace << "]"; if (!packet.empty()) trace << ",\"bytes_hex\":\"" << hexBytes(packet) << "\"";
        trace << "}\n"; trace.close();
        d.resume(); d.arm(send);
      } catch (const std::exception& e) {
        if (std::string(e.what()).find("Timed out waiting") == std::string::npos) throw;
      }
    }
    success = true;
  } catch (const std::exception& e) { errorText = e.what(); }
  bool cleaned = d.cleanup();
  std::ofstream summary(output / "summary.json");
  summary << "{\"status\":\"" << (success && cleaned ? "passed" : "failed")
          << "\",\"hit\":" << (hit ? "true" : "false") << ",\"hits\":" << hits
          << ",\"detached\":" << (d.detached ? "true" : "false")
          << ",\"cleanup_confirmed\":" << (cleaned ? "true" : "false") << ",\"error\":\"";
  for (char ch : errorText) { if (ch == '\\' || ch == '"') summary << '\\'; if (ch == '\n') summary << "\\n"; else summary << ch; }
  summary << "\"}\n"; summary.close();
  while (d.attached && !cleaned) { Sleep(1000); cleaned = d.cleanup(); }
  return success && cleaned ? 0 : 1;
}
