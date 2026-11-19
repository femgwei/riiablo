// Passive, bounded trace of the natural 1.10f server call path.
//
// This helper never calls a game function and never changes game state.  It
// briefly stops the real update thread at version-pinned entry points, records
// the live registers/stack, restores the replaced byte, and continues.  Each
// point is intentionally one-shot: this avoids perturbing the timing of this
// old single-threaded game.  It is intended to run while a human or a future
// window-message driver performs one real skill action.
#define D2_CONTROLLER_LIBRARY
#include "d2game_controller.cpp"

#include <algorithm>
#include <chrono>
#include <fstream>
#include <map>

struct Point {
  const char* name;
  U rva;
  U address;
  unsigned limit;
  unsigned hits = 0;
};

static bool isBreakpoint(const DEBUG_EVENT& event, U& address) {
  if (event.dwDebugEventCode != EXCEPTION_DEBUG_EVENT) return false;
  const auto& record = event.u.Exception.ExceptionRecord;
  if (record.ExceptionCode != EXCEPTION_BREAKPOINT) return false;
  address = reinterpret_cast<U>(record.ExceptionAddress);
  return true;
}

static void writeJsonString(std::ostream& out, const char* value) {
  out << '"';
  for (const unsigned char* p = reinterpret_cast<const unsigned char*>(value); *p; ++p) {
    if (*p == '\\' || *p == '"') out << '\\' << static_cast<char>(*p);
    else if (*p >= 0x20) out << static_cast<char>(*p);
    else out << "\\u00" << std::hex << static_cast<unsigned>(*p) << std::dec;
  }
  out << '"';
}

int wmain(int argc, wchar_t** argv) {
  SetErrorMode(SEM_FAILCRITICALERRORS | SEM_NOGPFAULTERRORBOX | SEM_NOOPENFILEERRORBOX);
  if (argc != 4) return 2; // pid, exact home, NEW output directory

  Controller d;
  bool success = false, outputCreated = false;
  unsigned totalHits = 0, traceRecords = 0;
  U observedGame = 0, firstObservedFrame = 0, lastObservedFrame = 0;
  unsigned serverSamples = 0;
  auto output = std::filesystem::absolute(argv[3]);
  try {
    outputCreated = std::filesystem::create_directory(output);
    require(outputCreated, "Output directory must be new");
    d.pid = std::stoul(argv[1]);
    auto root = std::filesystem::absolute(argv[2]);
    enableDebugPrivilege();
    // DebugBreakProcess requires PROCESS_ALL_ACCESS on this Windows build;
    // the passive collector must be able to interrupt its own running target
    // at the bounded end of the observation window for deterministic cleanup.
    d.process = OpenProcess(PROCESS_ALL_ACCESS, FALSE, d.pid);
    require(d.process != nullptr, "OpenProcess");
    wchar_t image[32768]{}; DWORD chars = 32768;
    require(QueryFullProcessImageNameW(d.process, 0, image, &chars), "Process identity");
    require(std::filesystem::equivalent(image, root / L"D2v110各版本免CD全功能启动.exe"), "Wrong loader");
    fileHash(root / "D2Game.dll", "2bd92fb25e3e4b8269930712620ba9db00cdc1aa44569d87dc4aba76553272b6");
    fileHash(root / "D2Common.dll", "b9d1a27c6a35992932c7c47438d02e5269f878e899f6e614d507902cecd1beab");

    Module gameModule = module(d.pid, L"D2Game.dll", root);
    const U update = gameModule.base + 0x86D0;
    const U targetMode = gameModule.base + 0x51A00;
    const U damage = gameModule.base + 0x91260;
    signature(d, update, {0x83,0xEC,0x08,0x53,0x56,0x57,0x8B,0xF9,0xBB,1,0,0,0});
    signature(d, targetMode, {0x53,0x55,0x56,0x57,0x8B,0xF9,0x33,0xDB,0x3B,0xFB});
    signature(d, damage, {0x83,0xEC,0x24,0x53,0x55,0x56,0x8B,0x74,0x24,0x34,0x57,0x8B,0xD9});
    // These shipped 1.10f getters establish the MonsterData/AiControl
    // offsets used below. Reference headers alone are not layout evidence.
    signature(d, gameModule.base + 0x9EEF0,
        {0x85,0xC9,0x74,0x13,0x83,0x39,0x01,0x75,0x0E,0x8B,0x41,0x14});
    signature(d, gameModule.base + 0x9EF0A, {0x8B,0x40,0x20,0xC3});
    signature(d, gameModule.base + 0x9F33F,
        {0x8B,0x48,0x28,0x85,0xC9,0x75,0x03,0x33,0xC0,0xC3,0x8B,0x50,0x2C});

    require(DebugActiveProcess(d.pid), "Attach");
    d.attached = true;
    require(DebugSetProcessKillOnExit(FALSE), "Disable kill-on-exit");
    d.waitBreakpoint(0, 3000, true); // initial system breakpoint
    d.gameThread = d.event.dwThreadId;

    std::vector<Point> points = {
      {"update", 0x86D0, update, 1},
      {"target_mode", 0x51A00, targetMode, 1},
      {"damage", 0x91260, damage, 1},
    };
    Point damageReturnPoint{"damage_return", 0, 0, 1};
    U pendingDamageStruct = 0, pendingDamageVictim = 0, pendingDamageAttacker = 0;
    std::map<U, Point*> byAddress;
    for (auto& point : points) {
      byAddress.emplace(point.address, &point);
      d.arm(point.address);
    }

    std::ofstream trace(output / "trace.jsonl");
    std::ofstream serverTrace(output / "server-ai-samples.jsonl");
    require(trace.is_open(), "Open trace output");
    auto safe = [&](U address) -> U {
      try { return d.get(address); } catch (...) { return 0; }
    };
    auto safe16 = [&](U address) -> U {
      try { return d.get<std::uint16_t>(address); } catch (...) { return 0; }
    };
    auto emit = [&](Point& point, const CONTEXT& c) {
      if (point.name == std::string("update")) observedGame = c.Ecx;
      U returnAddress = safe(c.Esp);
      trace << "{\"point\":"; writeJsonString(trace, point.name);
      trace << ",\"rva\":\"0x" << std::hex << point.rva << std::dec
            << "\",\"eip\":" << c.Eip << ",\"ecx\":" << c.Ecx
            << ",\"edx\":" << c.Edx << ",\"esp\":" << c.Esp
            << ",\"ebp\":" << c.Ebp << ",\"return\":" << returnAddress;
      trace << ",\"stack\":[";
      for (unsigned i = 0; i < 16; ++i) {
        if (i) trace << ',';
        trace << safe(c.Esp + i * 4);
      }
      trace << "]";
      if (c.Ecx >= 0x10000) {
        trace << ",\"frame\":" << safe(c.Ecx + 0xA8);
      }
      if (point.name == std::string("target_mode")) {
        trace << ",\"skill\":" << safe(c.Esp + 4)
              << ",\"mode\":" << safe(c.Esp + 8)
              << ",\"unit_type\":" << safe(c.Esp + 12)
              << ",\"target_id\":" << safe(c.Esp + 16)
              << ",\"allow_reenter\":" << safe(c.Esp + 20);
      } else if (point.name == std::string("damage")) {
        // D2Game 1.10f's damage descriptor is version-pinned by the
        // collector's module hash and entry signature.  These fields are
        // observed read-only at the natural damage entry; no game function
        // is called and no descriptor bytes are changed.
        U damage = safe(c.Esp + 8);
        pendingDamageStruct = damage;
        pendingDamageVictim = safe(c.Esp + 4);
        pendingDamageAttacker = c.Edx;
        trace << ",\"attacker\":" << c.Edx
              << ",\"victim\":" << safe(c.Esp + 4)
              << ",\"victim_unit_id\":" << safe(safe(c.Esp + 4) + 0xC)
              << ",\"damage_struct\":" << damage
              << ",\"descriptor_stage\":\"entry\"";
      } else if (point.name == std::string("damage_return")) {
        U damage = pendingDamageStruct;
        trace << ",\"attacker\":" << pendingDamageAttacker
              << ",\"victim\":" << pendingDamageVictim
              << ",\"victim_unit_id\":" << safe(pendingDamageVictim + 0xC)
              << ",\"damage_struct\":" << damage
              << ",\"hit_flags\":" << safe(damage)
              << ",\"result_flags\":" << safe16(damage + 4)
              << ",\"physical_fixed\":" << safe(damage + 8)
              << ",\"fire_fixed\":" << safe(damage + 0x10)
              << ",\"cold_fixed\":" << safe(damage + 0x18)
              << ",\"lightning_fixed\":" << safe(damage + 0x20)
              << ",\"magic_fixed\":" << safe(damage + 0x28)
              << ",\"poison_fixed\":" << safe(damage + 0x30)
              << ",\"total_fixed\":" << safe(damage + 0x4C)
              << ",\"descriptor_stage\":\"return\"";
      }
      trace << "}\n";
      trace.flush();
      ++traceRecords;
    };

    d.resume();
    const ULONGLONG deadline = GetTickCount64() + 30000; // bounded passive window
    while (GetTickCount64() < deadline && totalHits < 160) {
      if (!WaitForDebugEvent(&d.event, 100)) {
        require(GetLastError() == ERROR_SEM_TIMEOUT, "WaitForDebugEvent");
        // Asynchronous read-only samples: frame brackets expose races rather
        // than claiming an atomic tick trace. Hash-chain back references are
        // validated before interpreting a server unit.
        if (observedGame) {
          U before = safe(observedGame + 0xA8);
          if (!serverSamples) firstObservedFrame = before;
          lastObservedFrame = before;
          serverTrace << "{\"frame_before\":" << before << ",\"wall_ms\":"
                      << GetTickCount64() << ",\"units\":[";
          std::set<U> seen;
          bool firstUnit = true;
          for (U offset = 0; offset <= 0x3000; offset += 4) {
            U unit = safe(observedGame + offset);
            for (unsigned n = 0; unit >= 0x10000 && unit < 0x80000000 && n < 8192; ++n) {
              if (safe(unit) > 5 || safe(unit + 0x80) != observedGame || !seen.insert(unit).second) break;
              if (safe(unit) <= 1) {
                U path = safe(unit + 0x2C);
                bool validPath = path >= 0x10000 && safe(path + 0x30) == unit;
                if (!firstUnit) serverTrace << ',';
                firstUnit = false;
                serverTrace << "{\"type\":" << safe(unit) << ",\"class_id\":" << safe(unit + 4)
                            << ",\"unit_id\":" << safe(unit + 0xC) << ",\"mode\":" << safe(unit + 0x10)
                            << ",\"seed_low\":" << safe(unit + 0x20) << ",\"seed_high\":" << safe(unit + 0x24)
                            << ",\"path_valid\":" << (validPath ? "true" : "false");
                if (validPath) serverTrace << ",\"x\":" << safe16(path + 2) << ",\"y\":" << safe16(path + 6);
                if (safe(unit) == 1) {
                  U data = safe(unit + 0x14);
                  U ai = data ? safe(data + 0x28) : 0;
                  bool validAi = ai >= 0x10000 && safe(ai + 0x28) == observedGame;
                  serverTrace << ",\"ai_control_valid\":" << (validAi ? "true" : "false");
                  if (validAi) {
                    serverTrace << ",\"owner_guid_ex\":" << safe(ai + 0x2C)
                                << ",\"owner_type_ex\":" << safe(ai + 0x30)
                                << ",\"ai_param\":[" << safe(ai + 0x14) << ','
                                << safe(ai + 0x18) << ',' << safe(ai + 0x1C) << ']';
                    U cmd = safe(ai + 0x20);
                    serverTrace << ",\"current_command_pointer\":" << cmd;
                    if (cmd >= 0x10000 && cmd < 0x80000000) {
                      // Strict reads: unreadable non-null commands must not
                      // silently turn into a fabricated command of zeroes.
                      try {
                        U values[5];
                        for (unsigned i = 0; i < 5; ++i) values[i] = d.get(cmd + 8 + i * 4);
                        serverTrace << ",\"command_params\":[";
                        for (unsigned i = 0; i < 5; ++i) {
                          if (i) serverTrace << ',';
                          serverTrace << values[i];
                        }
                        serverTrace << ']';
                      } catch (...) { serverTrace << ",\"command_read_error\":true"; }
                    }
                  }
                }
                serverTrace << '}';
              }
              unit = safe(unit + 0xE4);
            }
          }
          serverTrace << "],\"frame_after\":" << safe(observedGame + 0xA8)
                      << ",\"atomic\":false}\n";
          serverTrace.flush();
          ++serverSamples;
        }
        continue;
      }
      d.held = true; d.handles();
      if (d.event.dwDebugEventCode == EXIT_PROCESS_DEBUG_EVENT) break;
      if (d.event.dwDebugEventCode != EXCEPTION_DEBUG_EVENT) {
        // CREATE_THREAD/LOAD_DLL/EXIT_THREAD notifications are ordinary
        // debugger bookkeeping events.  They must be continued normally;
        // DBG_EXCEPTION_NOT_HANDLED is only valid for an exception event.
        d.resume();
        continue;
      }
      U address = 0;
      if (!isBreakpoint(d.event, address) || !byAddress.count(address)) {
        d.resume(DBG_EXCEPTION_NOT_HANDLED);
        throw std::runtime_error("Unexpected native exception while tracing");
      }
      Point& point = *byAddress[address];
      CONTEXT context = d.context();
      context.Eip = address;
      d.set(context);
      d.disarm(address);
      ++point.hits; ++totalHits;
      emit(point, context);
      byAddress.erase(address);
      if (point.name == std::string("damage")) {
        // The descriptor is populated by the callee, so observe the same
        // natural invocation again at its caller return address.  This is a
        // passive breakpoint only; the return instruction and game state are
        // left untouched.
        U returnAddress = safe(context.Esp);
        if (returnAddress >= gameModule.base && returnAddress < gameModule.base + gameModule.size) {
          damageReturnPoint.address = returnAddress;
          damageReturnPoint.rva = returnAddress - gameModule.base;
          byAddress.emplace(returnAddress, &damageReturnPoint);
          d.arm(returnAddress);
        }
      }
      // The original instruction is executed after the restored byte is
      // resumed.  Do not re-arm it: one sample is enough to identify the
      // natural call path and avoids repeated stop/resume timing changes.
      d.resume();
      // Keep sampling the resulting encounter after all one-shot points fire.
      // An empty breakpoint map does not end the observation window.
    }
    success = totalHits > 0 && traceRecords > 0;
  } catch (const std::exception& error) {
    std::cerr << error.what() << '\n';
  }
  bool cleaned = d.cleanup();
  if (outputCreated) {
    std::ofstream summary(output / "summary.json");
    summary << "{\"status\":\"" << (success && cleaned ? "passed" : "failed")
            << "\",\"total_hits\":" << totalHits << ",\"trace_records\":" << traceRecords << ",\"detached\":"
            << (d.detached ? "true" : "false") << ",\"cleanup_confirmed\":"
            << (cleaned ? "true" : "false")
            << ",\"server_samples\":" << serverSamples << ",\"first_frame\":" << firstObservedFrame
            << ",\"last_frame\":" << lastObservedFrame
            << ",\"scope\":\"passive natural breakpoints plus asynchronous server unit samples; AI decision parity untested\"}";
  }
  while (d.attached && !cleaned) { Sleep(1000); cleaned = d.cleanup(); }
  return success && cleaned ? 0 : 1;
}
