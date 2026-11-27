// D2Gfx export-entry probe for the shipped 1.10f renderer.
// One-shot software breakpoints temporarily modify code bytes and EIP.
// Window state is observed, not inferred from the caller's show policy.
#define D2_CONTROLLER_LIBRARY
#include "d2game_controller.cpp"
#include <array>
#include <map>
#include <string>
#pragma comment(lib, "user32.lib")

struct Candidate { const char* name; U rva; };
struct WindowSearch { DWORD pid; HWND window = nullptr; };
static BOOL CALLBACK findWindow(HWND hwnd, LPARAM argument) {
  auto& search = *reinterpret_cast<WindowSearch*>(argument);
  DWORD pid = 0; GetWindowThreadProcessId(hwnd, &pid);
  wchar_t name[64]{}; GetClassNameW(hwnd, name, 64);
  if (pid == search.pid && std::wstring(name) == L"Diablo II") {
    search.window = hwnd; return FALSE;
  }
  return TRUE;
}
static std::string windowState(DWORD pid, HWND hwnd) {
  DWORD foregroundPid = 0;
  HWND foreground = GetForegroundWindow();
  GetWindowThreadProcessId(foreground, &foregroundPid);
  const char* state = !IsWindow(hwnd) ? "no_window" :
      IsIconic(hwnd) ? "minimized" : !IsWindowVisible(hwnd) ? "hidden" :
      foregroundPid == pid ? "foreground" : "visible_unfocused";
  std::ostringstream out;
  out << "\"window_state\":\"" << state << "\",\"foreground_pid\":" << foregroundPid
      << ",\"foreground_hwnd\":" << reinterpret_cast<std::uintptr_t>(foreground)
      << ",\"target_hwnd\":" << reinterpret_cast<std::uintptr_t>(hwnd);
  return out.str();
}
static void cellData(Controller& d, U pointer, std::ostream& out) {
  std::array<U, 18> data{}; SIZE_T got = 0;
  const bool valid = pointer && ReadProcessMemory(d.process, reinterpret_cast<void*>(pointer),
      data.data(), sizeof data, &got) && got == sizeof data;
  out << ",\"gfx_data_pointer\":" << pointer << ",\"gfx_data_readable\":" << (valid ? "true" : "false");
  if (!valid) return;
  out << ",\"gfx_data_raw\":[";
  for (std::size_t i = 0; i < data.size(); i++) out << (i ? "," : "") << data[i];
  out << "],\"cell\":{\"frame\":" << data[2] << ",\"direction\":" << data[3]
      << ",\"max_directions\":" << data[4] << ",\"max_frames\":" << data[5]
      << ",\"component\":" << ((data[7] >> 8) & 255)
      << ",\"unit_type\":" << data[8] << ",\"unit_index\":" << data[9]
      << ",\"mode\":" << data[10] << ",\"overlay\":" << data[11];
  const char* names[] = {"token", "component_token", "armor", "mode_token", "hit_class"};
  for (int i = 0; i < 5; i++) {
    out << ",\"" << names[i] << "\":\"";
    const auto* bytes = reinterpret_cast<const unsigned char*>(&data[12 + i]);
    for (int j = 0; j < 4 && bytes[j]; j++) {
      unsigned char value = bytes[j];
      out << (value >= 32 && value < 127 && value != '"' && value != '\\' ? static_cast<char>(value) : '?');
    }
    out << '"';
  }
  out << '}';
}

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
    fileHash(root / L"D2Gfx.dll",
        "b4247efe73169b66fe95c8cc3d085b9646e8fbf171688fee6b69799f791e56b8");
    Module gfx = module(d.pid, L"D2Gfx.dll", root);
    WindowSearch window{d.pid}; EnumWindows(findWindow, reinterpret_cast<LPARAM>(&window));
    const Candidate candidates[] = {
      {"D2Gfx_ORD_10000", 0x3750u},
      {"D2Gfx_ORD_10001", 0x39a0u},
      {"D2Gfx_ORD_10002", 0x3990u},
      {"D2Gfx_ORD_10003", 0x3ac0u},
      {"D2Gfx_ORD_10004", 0x3ad0u},
      {"D2Gfx_ORD_10005", 0x3ae0u},
      {"D2Gfx_ORD_10006_10047", 0x3b20u},
      {"D2Gfx_ORD_10007", 0x3af0u},
      {"D2Gfx_ORD_10008", 0x3b00u},
      {"D2Gfx_ORD_10009", 0x3b30u},
      {"D2Gfx_ORD_10010", 0x3b50u},
      {"D2Gfx_ORD_10011", 0x3b70u},
      {"D2Gfx_ORD_10012", 0x3b80u},
      {"D2Gfx_ORD_10013", 0x3ba0u},
      {"D2Gfx_ORD_10014", 0x3bb0u},
      {"D2Gfx_ORD_10015", 0x3bc0u},
      {"D2Gfx_ORD_10016", 0x3be0u},
      {"D2Gfx_ORD_10017", 0x3bf0u},
      {"D2Gfx_ORD_10018", 0x3c00u},
      {"D2Gfx_ORD_10019", 0x3c20u},
      {"D2Gfx_ORD_10020", 0x3c30u},
      {"D2Gfx_ORD_10021", 0x3c40u},
      {"D2Gfx_ORD_10022", 0x3c50u},
      {"D2Gfx_ORD_10023", 0x4450u},
      {"D2Gfx_ORD_10024", 0x4820u},
      {"D2Gfx_ORD_10025", 0x48b0u},
      {"D2Gfx_ORD_10026", 0x48c0u},
      {"D2Gfx_ORD_10027", 0x49c0u},
      {"D2Gfx_ORD_10028", 0x4a80u},
      {"D2Gfx_ORD_10029", 0x4a10u},
      {"D2Gfx_ORD_10030", 0x4ae0u},
      {"D2Gfx_ORD_10031", 0x4c60u},
      {"D2Gfx_ORD_10032", 0x4c70u},
      {"D2Gfx_ORD_10033", 0x4ca0u},
      {"D2Gfx_ORD_10034", 0x4de0u},
      {"D2Gfx_ORD_10035", 0x4e20u},
      {"D2Gfx_ORD_10036", 0x4e50u},
      {"D2Gfx_ORD_10037", 0x4e90u},
      {"D2Gfx_ORD_10038", 0x4ed0u},
      {"D2Gfx_ORD_10039", 0x4f10u},
      {"D2Gfx_ORD_10040", 0x4fa0u},
      {"D2Gfx_ORD_10041", 0x49d0u},
      {"D2Gfx_ORD_10042", 0x3c60u},
      {"D2Gfx_ORD_10043", 0x4aa0u},
      {"D2Gfx_ORD_10044", 0x3c90u},
      {"D2Gfx_ORD_10045", 0x3cb0u},
      {"D2Gfx_ORD_10046", 0x3cd0u},
      {"D2Gfx_ORD_10048", 0x3e70u},
      {"D2Gfx_ORD_10049", 0x3eb0u},
      {"D2Gfx_ORD_10050", 0x3ed0u},
      {"D2Gfx_ORD_10051", 0x3ce0u},
      {"D2Gfx_ORD_10052", 0x3d00u},
      {"D2Gfx_ORD_10053", 0x3d20u},
      {"D2Gfx_ORD_10054", 0x3d40u},
      {"D2Gfx_ORD_10055", 0x3d60u},
      {"D2Gfx_ORD_10056", 0x3da0u},
      {"D2Gfx_ORD_10057", 0x3de0u},
      {"D2Gfx_ORD_10058", 0x3e10u},
      {"D2Gfx_ORD_10059", 0x3f30u},
      {"D2Gfx_ORD_10060", 0x3f50u},
      {"D2Gfx_ORD_10061", 0x3f60u},
      {"D2Gfx_ORD_10062", 0x3f80u},
      {"D2Gfx_ORD_10063", 0x3fb0u},
      {"D2Gfx_ORD_10064", 0x3fe0u},
      {"D2Gfx_ORD_10065", 0x4060u},
      {"D2Gfx_ORD_10066", 0x4100u},
      {"D2Gfx_ORD_10067", 0x4170u},
      {"D2Gfx_ORD_10068", 0x4200u},
      {"D2Gfx_ORD_10069", 0x3ef0u},
      {"D2Gfx_ORD_10070", 0x3f10u},
      {"D2Gfx_ORD_10071", 0x4270u},
      {"D2Gfx_ORD_10072", 0x42a0u},
      {"D2Gfx_ORD_10073", 0x42d0u},
      {"D2Gfx_ORD_10074", 0x4300u},
      {"D2Gfx_ORD_10075", 0x4360u},
      {"D2Gfx_ORD_10076", 0x4380u},
      {"D2Gfx_ORD_10077", 0x4330u},
      {"D2Gfx_ORD_10078", 0x43a0u},
      {"D2Gfx_ORD_10079", 0x3e30u},
      {"D2Gfx_ORD_10080", 0x43c0u},
      {"D2Gfx_ORD_10081", 0x43f0u},
      {"D2Gfx_ORD_10082", 0x4420u},
    };
    std::map<U, std::string> labels;
    // Re-arm StartDraw only after its original caller returns. No trap-flag
    // single stepping is used. This proves repeated frame attempts without
    // trapping thousands of sprite calls per second.
    U frameReturn = 0; DWORD frameThread = 0; U frameStarts = 0;
    U textureReturn = 0; DWORD textureThread = 0; U textureEntry = 0; U textureCalls = 0;
    for (const auto& candidate : candidates) {
      U address = gfx.base + candidate.rva;
      require(address >= gfx.base && address < gfx.base + gfx.size,
          "D2Gfx export outside image");
      require(d.get<BYTE>(address) != 0xCC, "Candidate already contains breakpoint");
      auto found = labels.find(address);
      if (found == labels.end()) labels.emplace(address, candidate.name);
      else found->second += "," + std::string(candidate.name);
    }
    require(DebugActiveProcess(d.pid), "DebugActiveProcess"); d.attached = true;
    require(DebugSetProcessKillOnExit(FALSE), "Disable kill on debugger exit");
    d.waitBreakpoint(0, 3000, true);
    for (const auto& item : labels) d.arm(item.first);
    std::ofstream trace(output / "d2gfx-call-trace.jsonl");
    require(trace.is_open(), "Open D2Gfx trace");
    trace << "{\"type\":\"header\",\"schema\":2,\"metadata\":{";
    trace << "\"source\":\"original-d2gfx-software-breakpoints\",";
    trace << "\"module\":\"D2Gfx.dll\",";
    trace << "\"sha256\":\"b4247efe73169b66fe95c8cc3d085b9646e8fbf171688fee6b69799f791e56b8\",";
    trace << "\"address_mode\":\"module-relative-rva\",\"capabilities\":[\"return_address\",\"raw_stack_words\",\"window_state\",\"cell_data_candidate\"],"
          << "\"coverage\":\"first hit per export plus repeated StartDraw; not a full frame or an unwound stack\","
          << "\"temporary_code_and_context_mutation\":true}}\n";
    std::ofstream states(output / "window-states.jsonl");
    require(states.is_open(), "Open window-state trace");
    d.resume();
    const ULONGLONG started = GetTickCount64();
    ULONGLONG lastState = 0;
    while (GetTickCount64() - started < timeout && !labels.empty()) {
      ULONGLONG elapsedState = GetTickCount64() - started;
      if (!lastState || elapsedState >= lastState + 100) {
        states << "{\"elapsed_ms\":" << elapsedState << ',' << windowState(d.pid, window.window) << "}\n" << std::flush;
        lastState = elapsedState;
      }
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
      const bool atFrameReturn = frameReturn && address == frameReturn && d.event.dwThreadId == frameThread;
      const bool atTextureReturn = textureReturn && address == textureReturn && d.event.dwThreadId == textureThread;
      if (exception.ExceptionCode != EXCEPTION_BREAKPOINT || (found == labels.end() && !atFrameReturn && !atTextureReturn)) {
        d.resume(); continue;
      }
      // Open for this event instead of retaining handles across thread exits.
      HANDLE eventThread = OpenThread(THREAD_GET_CONTEXT | THREAD_SET_CONTEXT, FALSE, d.event.dwThreadId);
      require(eventThread != nullptr, "Open event thread");
      CONTEXT c{}; c.ContextFlags = CONTEXT_CONTROL | CONTEXT_INTEGER;
      const BOOL readContext = GetThreadContext(eventThread, &c);
      if (!readContext) { DWORD error = GetLastError(); CloseHandle(eventThread); SetLastError(error); }
      require(readContext != FALSE, "Read event context");
      c.Eip = address;
      const BOOL wroteContext = SetThreadContext(eventThread, &c);
      const DWORD contextError = GetLastError(); CloseHandle(eventThread); SetLastError(contextError);
      require(wroteContext != FALSE, "Restore event EIP");
      d.disarm(address);
      if (atFrameReturn) {
        trace << "{\"type\":\"event\",\"kind\":\"ORIGINAL_D2GFX_FRAME_RETURN\",\"elapsed_ms\":"
              << (GetTickCount64() - started) << ",\"start_draw_result\":" << c.Eax
              << ',' << windowState(d.pid, window.window) << "}\n" << std::flush;
        frameReturn = 0;
        d.arm(gfx.base + 0x3c90);
        d.resume(); continue;
      }
      if (atTextureReturn) {
        trace << "{\"type\":\"event\",\"kind\":\"ORIGINAL_D2GFX_TEXTURE_RETURN\",\"elapsed_ms\":"
              << (GetTickCount64() - started) << ',' << windowState(d.pid, window.window) << "}\n" << std::flush;
        textureReturn = 0;
        if (textureEntry && textureCalls < 5000) d.arm(textureEntry);
        d.resume(); continue;
      }
      std::array<U, 16> stack{};
      SIZE_T bytes = 0;
      ReadProcessMemory(d.process, reinterpret_cast<void*>(c.Esp), stack.data(),
          stack.size() * sizeof(U), &bytes);
      const auto elapsed = GetTickCount64() - started;
      trace << "{\"type\":\"event\",\"tick\":" << calls
            << ",\"kind\":\"ORIGINAL_D2GFX_EXPORT\",\"name\":\""
            << found->second << "\",\"eip\":" << address
            << ",\"rva\":" << (address - gfx.base)
            << ",\"return_address\":" << stack[0]
            << ",\"thread_id\":" << d.event.dwThreadId
            << ",\"elapsed_ms\":" << elapsed << ',' << windowState(d.pid, window.window)
            << ",\"stack_bytes_read\":" << bytes << ",\"stack\":[";
      for (std::size_t i = 0; i < stack.size(); i++)
        trace << (i ? "," : "") << stack[i];
      trace << ']';
      if (address - gfx.base >= 0x4270 && address - gfx.base <= 0x4380 && bytes >= 16) {
        trace << ",\"screen_x\":" << static_cast<std::int32_t>(stack[2])
              << ",\"screen_y\":" << static_cast<std::int32_t>(stack[3]);
        cellData(d, stack[1], trace);
      }
      trace << "}\n" << std::flush;
      calls++;
      if (address == gfx.base + 0x3c90 && bytes >= 4 && stack[0] && !d.patches.count(stack[0])) {
        frameStarts++;
        frameReturn = stack[0]; frameThread = d.event.dwThreadId;
        d.arm(frameReturn);
      } else if (address - gfx.base >= 0x4270 && address - gfx.base <= 0x4380 &&
                 address - gfx.base != 0x43a0 && bytes >= 8 && stack[0] &&
                 !textureReturn && !d.patches.count(stack[0])) {
        textureReturn = stack[0];
        textureThread = d.event.dwThreadId;
        textureEntry = address;
        textureCalls++;
        d.arm(textureReturn);
      } else if (found != labels.end()) labels.erase(found);
      d.resume();
    }
    trace.flush();
    require(trace.good(), "D2Gfx trace write failed; event count is not persisted evidence");
    trace.close();
    success = calls > 0;
  } catch (const std::exception& error) {
    std::cerr << error.what() << '\n';
  }
  bool cleaned = d.cleanup();
  std::ofstream summary(output / "summary.json");
  summary << "{\"status\":\"" << (success && cleaned ? "passed" : "incomplete")
          << "\",\"events\":" << calls
          << ",\"candidate_count\":82"
          << ",\"detached\":" << (d.detached ? "true" : "false")
          << ",\"cleanup_confirmed\":" << (cleaned ? "true" : "false")
          << ",\"scope\":\"one-shot D2Gfx entries; temporary software breakpoint code/context writes; no synthetic game calls; not final backend output\"}";
  return success && cleaned ? 0 : 1;
}

