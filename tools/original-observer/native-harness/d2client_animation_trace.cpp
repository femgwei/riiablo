// Bounded, read-only D2Client call-chain observer for the shipped 1.10f DLL.
// It uses one-shot software breakpoints at statically verified entry points,
// records the live return address/stack, then restores every byte before
// detaching. It never calls a target function or changes target registers.
#define D2_CONTROLLER_LIBRARY
#include "d2game_controller.cpp"
#include <array>

struct Candidate { const char* name; U rva; };

int wmain(int argc, wchar_t** argv) {
  SetErrorMode(SEM_FAILCRITICALERRORS | SEM_NOGPFAULTERRORBOX | SEM_NOOPENFILEERRORBOX);
  if (argc != 5) return 2; // pid, original root, output directory, timeout ms
  Controller d;
  bool success = false;
  std::size_t calls = 0;
  auto output = std::filesystem::absolute(argv[3]);
  try {
    d.pid = std::stoul(argv[1]);
    const auto root = std::filesystem::absolute(argv[2]);
    const DWORD timeout = std::stoul(argv[4]);
    require(timeout >= 250 && timeout <= 30000, "Invalid bounded timeout");
    require(std::filesystem::create_directory(output), "Output directory must be new");
    enableDebugPrivilege();
    d.process = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ |
        PROCESS_VM_WRITE | PROCESS_VM_OPERATION | PROCESS_SUSPEND_RESUME |
        SYNCHRONIZE, FALSE, d.pid);
    require(d.process != nullptr, "OpenProcess");
    wchar_t executable[32768]{}; DWORD chars = 32768;
    require(QueryFullProcessImageNameW(d.process, 0, executable, &chars), "Process image");
    require(std::filesystem::equivalent(executable,
        root / L"D2v110各版本免CD全功能启动.exe"), "Unexpected target executable");
    fileHash(root / L"D2Client.dll",
        "94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282");
    Module client = module(d.pid, L"D2Client.dll", root);
    // These RVAs are from the hash-pinned 1.10f D2Client evidence bundle.
    // The first two are the skill callback dispatcher and generic unit update;
    // the latter two are the public overlay create/draw path used as a
    // rendering-side anchor. They are observations, not injected calls.
    const Candidate candidates[] = {
      {"skill_callback_dispatch", 0x1c6b0u}, // 0x6FABC6B0
      {"unit_render_dispatch",    0x90820u}, // 0x6FB30820
      {"render_frame_dispatch",   0x94c30u}, // 0x6FB34C30
      {"unit_animation_update",  0x9d880u}, // 0x6FB3D880
      {"overlay_create",         0xbba70u}, // 0x6FB5BA70
      {"overlay_draw",            0xb7e2au}, // 0x6FB57E2A
    };
    for (const auto& candidate : candidates) {
      U address = client.base + candidate.rva;
      require(address >= client.base && address < client.base + client.size,
          "Candidate outside D2Client image");
      require(d.get<BYTE>(address) != 0xCC, "Candidate already contains breakpoint");
    }
    require(DebugActiveProcess(d.pid), "DebugActiveProcess"); d.attached = true;
    require(DebugSetProcessKillOnExit(FALSE), "Disable kill on debugger exit");
    d.waitBreakpoint(0, 3000, true);

    std::map<U, const char*> labels;
    for (const auto& candidate : candidates) {
      U address = client.base + candidate.rva;
      labels.emplace(address, candidate.name);
      d.arm(address);
    }
    std::ofstream trace(output / "animation-call-trace.jsonl");
    require(trace.is_open(), "Open animation call trace");
    trace << "{\"type\":\"header\",\"schema\":1,\"metadata\":{"
          << "\"source\":\"original-d2client-read-only-breakpoints\","
          << "\"module\":\"D2Client.dll\","
          << "\"sha256\":\"94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282\","
          << "\"address_mode\":\"module-relative-rva\","
          << "\"capabilities\":[\"native_call_stack\"]}}\n";
    d.resume();
    const ULONGLONG started = GetTickCount64();
    while (GetTickCount64() - started < timeout && !labels.empty()) {
      if (!WaitForDebugEvent(&d.event, 100)) {
        require(GetLastError() == ERROR_SEM_TIMEOUT, "WaitForDebugEvent");
        continue;
      }
      d.held = true; d.handles();
      if (d.event.dwDebugEventCode == EXIT_PROCESS_DEBUG_EVENT) break;
      if (d.event.dwDebugEventCode != EXCEPTION_DEBUG_EVENT) {
        d.resume(); continue;
      }
      const auto& exception = d.event.u.Exception.ExceptionRecord;
      U address = reinterpret_cast<U>(exception.ExceptionAddress);
      auto found = labels.find(address);
      if (exception.ExceptionCode != EXCEPTION_BREAKPOINT || found == labels.end()) {
        d.resume(); continue;
      }
      CONTEXT c = d.context();
      c.Eip = address; d.set(c);
      d.disarm(address); // one-shot: no timing perturbation from re-arming
      std::array<U, 16> stack{};
      SIZE_T bytes = 0;
      ReadProcessMemory(d.process, reinterpret_cast<void*>(c.Esp), stack.data(),
          stack.size() * sizeof(U), &bytes);
      const auto elapsed = GetTickCount64() - started;
      trace << "{\"type\":\"event\",\"tick\":" << calls
            << ",\"game_frame\":null,\"entity_id\":-1"
            << ",\"kind\":\"ORIGINAL_NATIVE_CALL\",\"name\":\""
            << found->second << "\",\"eip\":" << address
            << ",\"rva\":" << (address - client.base)
            << ",\"return_address\":" << stack[0]
            << ",\"thread_id\":" << d.event.dwThreadId
            << ",\"elapsed_ms\":" << elapsed << ",\"stack\":[";
      for (std::size_t i = 0; i < stack.size(); i++)
        trace << (i ? "," : "") << stack[i];
      trace << "]}\n" << std::flush;
      calls++;
      labels.erase(found);
      d.resume();
    }
    trace.close();
    success = calls > 0;
  } catch (const std::exception& error) {
    std::cerr << error.what() << '\n';
  }
  bool cleaned = d.cleanup();
  std::ofstream summary(output / "summary.json");
  summary << "{\"status\":\"" << (success && cleaned ? "passed" : "incomplete")
          << "\",\"events\":" << calls
          << ",\"detached\":" << (d.detached ? "true" : "false")
          << ",\"cleanup_confirmed\":" << (cleaned ? "true" : "false")
          << ",\"scope\":\"one-shot read-only D2Client entry observations; no calls or memory mutation\"}";
  return success && cleaned ? 0 : 1;
}
