// Bounded x86 debugger controller for the user's hash-pinned 1.10f process.
// Unlike the observer, this changes live code bytes/registers temporarily.
// Game calls use the original game thread; no DLL injection/global input.
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <tlhelp32.h>
#include <wincrypt.h>
#include <cstdint>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <iomanip>
#include <map>
#include <set>
#include <stdexcept>
#include <sstream>
#include <string>
#include <vector>
#include <functional>
#pragma comment(lib, "advapi32.lib")

static_assert(sizeof(void*) == 4, "Build x86 for x86 CONTEXT and target ABI");
using U = std::uint32_t;
static void require(bool value, const char* message) {
  if (!value) throw std::runtime_error(std::string(message) + " (Win32 " + std::to_string(GetLastError()) + ")");
}
// An elevated administrator token may still carry SeDebugPrivilege disabled.
// The legacy loader runs with a compatibility-layer token, and
// DebugActiveProcess otherwise returns ERROR_ACCESS_DENIED even though
// OpenProcess succeeds.  Enable the privilege explicitly before attaching;
// this only changes the helper token and never changes the target process.
static void enableDebugPrivilege() {
  HANDLE token = nullptr;
  require(OpenProcessToken(GetCurrentProcess(), TOKEN_ADJUST_PRIVILEGES | TOKEN_QUERY, &token),
          "OpenProcessToken");
  try {
    TOKEN_PRIVILEGES privileges{};
    require(LookupPrivilegeValueW(nullptr, SE_DEBUG_NAME, &privileges.Privileges[0].Luid),
            "Lookup SeDebugPrivilege");
    privileges.PrivilegeCount = 1;
    privileges.Privileges[0].Attributes = SE_PRIVILEGE_ENABLED;
    SetLastError(ERROR_SUCCESS);
    require(AdjustTokenPrivileges(token, FALSE, &privileges, sizeof privileges, nullptr, nullptr) &&
                GetLastError() == ERROR_SUCCESS,
            "Enable SeDebugPrivilege");
  } catch (...) {
    CloseHandle(token);
    throw;
  }
  CloseHandle(token);
}
static void fileHash(const std::filesystem::path& path, const char* expected) {
  HCRYPTPROV provider = 0; HCRYPTHASH hash = 0;
  require(CryptAcquireContextW(&provider, nullptr, nullptr, PROV_RSA_AES, CRYPT_VERIFYCONTEXT), "SHA-256 provider");
  if (!CryptCreateHash(provider, CALG_SHA_256, 0, 0, &hash)) {
    CryptReleaseContext(provider, 0); throw std::runtime_error("SHA-256 hash initialization failed");
  }
  try {
    std::ifstream stream(path, std::ios::binary); require(stream.is_open(), "Open version-pinned DLL");
    char block[65536];
    while (stream.read(block, sizeof block) || stream.gcount()) {
      require(CryptHashData(hash, reinterpret_cast<BYTE*>(block), static_cast<DWORD>(stream.gcount()), 0), "SHA-256 update");
    }
    require(!stream.bad(), "Read version-pinned DLL");
    BYTE result[32]; DWORD size = sizeof result;
    require(CryptGetHashParam(hash, HP_HASHVAL, result, &size, 0) && size == 32, "SHA-256 digest");
    std::ostringstream text; text << std::hex << std::setfill('0');
    for (BYTE value : result) text << std::setw(2) << static_cast<unsigned>(value);
    require(text.str() == expected, "Unsupported original DLL SHA-256");
  } catch (...) { CryptDestroyHash(hash); CryptReleaseContext(provider, 0); throw; }
  CryptDestroyHash(hash); CryptReleaseContext(provider, 0);
}
struct Controller {
  DWORD pid = 0, gameThread = 0;
  HANDLE process = nullptr;
  bool attached = false, held = false, pendingCall = false, detached = false;
  DEBUG_EVENT event{};
  CONTEXT callRestore{};
  U scratch = 0;
  std::vector<BYTE> scratchOriginal;
  std::map<U, BYTE> patches;
  std::map<DWORD, HANDLE> threads;
  U observedEntry = 0;
  U observedAlternative = 0;
  bool observeOtherThreads = false;
  std::function<void(CONTEXT)> observeOnce;

  void read(U at, void* to, SIZE_T size) {
    SIZE_T got = 0;
    require(ReadProcessMemory(process, reinterpret_cast<void*>(at), to, size, &got) && got == size, "ReadProcessMemory");
  }
  template<class T = U> T get(U at) { T value{}; read(at, &value, sizeof value); return value; }
  void write(U at, const void* from, SIZE_T size, bool code = false) {
    DWORD old = 0, ignored = 0;
    if (code) require(VirtualProtectEx(process, reinterpret_cast<void*>(at), size, PAGE_EXECUTE_READWRITE, &old), "Unprotect breakpoint");
    SIZE_T written = 0;
    const bool ok = WriteProcessMemory(process, reinterpret_cast<void*>(at), from, size, &written) && written == size;
    if (code) {
      require(VirtualProtectEx(process, reinterpret_cast<void*>(at), size, old, &ignored), "Restore protection");
      require(FlushInstructionCache(process, reinterpret_cast<void*>(at), size), "Flush breakpoint");
    }
    require(ok, "WriteProcessMemory");
  }
  void arm(U at) {
    require(!patches.count(at), "Duplicate breakpoint");
    BYTE old = get<BYTE>(at), trap = 0xCC;
    require(old != trap, "Existing breakpoint: refusing to replace");
    patches[at] = old;
    write(at, &trap, 1, true);
  }
  void disarm(U at) {
    auto found = patches.find(at); require(found != patches.end(), "Missing breakpoint");
    write(at, &found->second, 1, true); patches.erase(found);
  }
  HANDLE thread(DWORD tid) {
    if (!threads.count(tid)) {
      HANDLE h = OpenThread(THREAD_GET_CONTEXT | THREAD_SET_CONTEXT | THREAD_QUERY_INFORMATION, FALSE, tid);
      require(h != nullptr, "OpenThread"); threads[tid] = h;
    }
    return threads[tid];
  }
  CONTEXT context() {
    CONTEXT c{}; c.ContextFlags = CONTEXT_CONTROL | CONTEXT_INTEGER;
    require(GetThreadContext(thread(event.dwThreadId), &c), "GetThreadContext"); return c;
  }
  void set(CONTEXT c) { require(SetThreadContext(thread(event.dwThreadId), &c), "SetThreadContext"); }
  void resume(DWORD status = DBG_CONTINUE) {
    require(held, "No held debug event");
    require(ContinueDebugEvent(event.dwProcessId, event.dwThreadId, status), "ContinueDebugEvent"); held = false;
  }
  void handles() {
    if (event.dwDebugEventCode == CREATE_PROCESS_DEBUG_EVENT) {
      if (event.u.CreateProcessInfo.hFile) CloseHandle(event.u.CreateProcessInfo.hFile);
      if (event.u.CreateProcessInfo.hProcess) CloseHandle(event.u.CreateProcessInfo.hProcess);
      if (event.u.CreateProcessInfo.hThread) CloseHandle(event.u.CreateProcessInfo.hThread);
    } else if (event.dwDebugEventCode == CREATE_THREAD_DEBUG_EVENT) {
      if (event.u.CreateThread.hThread) CloseHandle(event.u.CreateThread.hThread);
    } else if (event.dwDebugEventCode == LOAD_DLL_DEBUG_EVENT && event.u.LoadDll.hFile) {
      CloseHandle(event.u.LoadDll.hFile);
    }
  }
  // Returns with all target threads stopped at the requested breakpoint.
  // The owning instruction pointer is corrected immediately for safe detach.
  CONTEXT waitBreakpoint(U expected, DWORD timeout, bool initial = false) {
    const ULONGLONG deadline = GetTickCount64() + timeout;
    while (GetTickCount64() < deadline) {
      require(!held, "Unconsumed debug event");
      if (!WaitForDebugEvent(&event, 100)) {
        require(GetLastError() == ERROR_SEM_TIMEOUT, "WaitForDebugEvent"); continue;
      }
      held = true; handles();
      if (event.dwDebugEventCode == EXIT_PROCESS_DEBUG_EVENT) throw std::runtime_error("Target exited");
      if (event.dwDebugEventCode != EXCEPTION_DEBUG_EVENT) { resume(); continue; }
      const auto& x = event.u.Exception.ExceptionRecord;
      U at = reinterpret_cast<U>(x.ExceptionAddress);
      if (!initial && at != expected && x.ExceptionCode == EXCEPTION_BREAKPOINT &&
          (at == observedEntry || at == observedAlternative) && patches.count(at) && observeOnce) {
        CONTEXT c = context(); c.Eip = at; set(c); disarm(at);
        require(observeOtherThreads || !gameThread || event.dwThreadId == gameThread, "Observation on another thread");
        observeOnce(c); // One-shot hook; original instruction executes after resume.
        resume(); continue;
      }
      // expected==0 is a wildcard for a breakpoint armed by the caller. It
      // is used by passive multi-export probes; unrelated target breakpoints
      // must still be rejected rather than mistaken for a hit.
      const bool ours = patches.count(at) != 0;
      if (x.ExceptionCode == EXCEPTION_BREAKPOINT &&
          (initial || (ours && (expected == 0 || at == expected)))) {
        CONTEXT c = context();
        if (!initial) {
          c.Eip = at; set(c); disarm(at);
          require(!gameThread || event.dwThreadId == gameThread, "Breakpoint reached on another game thread");
        } else if (patches.count(at)) {
          c.Eip = at; set(c); disarm(at);
        }
        return c;
      }
      // Do not consume unrelated target faults or single-step exceptions.
      resume(DBG_EXCEPTION_NOT_HANDLED);
      throw std::runtime_error("Unexpected native exception " + std::to_string(x.ExceptionCode));
    }
    throw std::runtime_error("Timed out waiting for native breakpoint");
  }
  U callGetter(CONTEXT entry, U returnTo, U function, U unit, U skill, U level) {
    return callStdcall4(entry, returnTo, function, unit, skill, level, 0);
  }
  U callStdcall4(CONTEXT entry, U returnTo, U function, U a, U b, U cArg, U e) {
    require(held && !pendingCall, "Getter must start at held game-update entry");
    // Use unused native stack below the suspended entry, inside its normal
    // thread stack. The caller's live return address/locals remain untouched.
    scratch = entry.Esp - 0x80;
    scratchOriginal.resize(20); read(scratch, scratchOriginal.data(), 20);
    U args[] = {returnTo, a, b, cArg, e};
    callRestore = entry; pendingCall = true;
    write(scratch, args, sizeof args);
    arm(returnTo);
    CONTEXT c = entry; c.Eip = function; c.Esp = scratch; set(c); resume();
    CONTEXT result = waitBreakpoint(returnTo, 2000);
    require(result.Esp == scratch + sizeof args, "Getter stdcall stack imbalance");
    write(scratch, scratchOriginal.data(), scratchOriginal.size());
    set(entry); pendingCall = false;
    return result.Eax;
  }
  bool cleanup() {
    if (!attached) return true;
    try {
      // If the target is running and no synthetic call is in flight, restore
      // all temporary code bytes before detaching.  DebugBreakProcess is not
      // reliable for this legacy compatibility-layer process (it can return
      // ACCESS_DENIED even though the debugger is attached), while a direct
      // DebugActiveProcessStop is safe once no breakpoint remains armed.
      if (!held && !pendingCall) {
        while (!patches.empty()) disarm(patches.begin()->first);
        if (DebugActiveProcessStop(pid)) {
          attached = false; detached = true;
          return true;
        }
      }
      if (!held) {
        require(DebugBreakProcess(process), "Interrupt for cleanup");
        // Drain events only until the requested system breakpoint.
        waitBreakpoint(0, 3000, true);
      }
      if (pendingCall) {
        require(SetThreadContext(thread(gameThread), &callRestore), "Restore pending getter context");
        write(scratch, scratchOriginal.data(), scratchOriginal.size());
        pendingCall = false;
      }
      while (!patches.empty()) disarm(patches.begin()->first);
      resume();
      require(DebugActiveProcessStop(pid), "DebugActiveProcessStop");
      attached = false; detached = true;
      return true;
    } catch (const std::exception& error) {
      std::cerr << "Cleanup failed: " << error.what() << '\n';
      // Keep a stopped target stopped if original bytes/context are unresolved.
      if (patches.empty() && !pendingCall) {
        if (held && ContinueDebugEvent(event.dwProcessId, event.dwThreadId, DBG_CONTINUE)) held = false;
        if (!held && DebugActiveProcessStop(pid)) { attached = false; detached = true; }
      }
      return !attached; // fallback detach above may have completed successfully
    }
  }
  ~Controller() { for (auto pair : threads) CloseHandle(pair.second); if (process) CloseHandle(process); }
};

struct Module { U base = 0, size = 0; };
static Module module(DWORD pid, const wchar_t* name, const std::filesystem::path& root) {
  HANDLE snapshot = CreateToolhelp32Snapshot(TH32CS_SNAPMODULE | TH32CS_SNAPMODULE32, pid);
  require(snapshot != INVALID_HANDLE_VALUE, "Module snapshot");
  MODULEENTRY32W m{}; m.dwSize = sizeof m; Module result;
  for (BOOL more = Module32FirstW(snapshot, &m); more; more = Module32NextW(snapshot, &m)) {
    if (_wcsicmp(m.szModule, name) == 0) {
      CloseHandle(snapshot);
      require(std::filesystem::equivalent(m.szExePath, root / name), "Unexpected module path");
      return {reinterpret_cast<U>(m.modBaseAddr), m.modBaseSize};
    }
  }
  CloseHandle(snapshot); throw std::runtime_error("Required game module missing");
}
static void signature(Controller& d, U at, const std::vector<BYTE>& expected) {
  std::vector<BYTE> actual(expected.size()); d.read(at, actual.data(), actual.size());
  require(actual == expected, "Unsupported live instruction signature");
}
static U validateGame(Controller& d, U game, U expectedSeed, U& player) {
  require(game >= 0x10000 && d.get<BYTE>(game + 0x6D) == 0, "Invalid game/Normal difficulty");
  require(d.get(game + 0x1DDC) == 0, "Native debug trigger is enabled");
  U act = d.get(game + 0xBC), drlg = d.get(act + 8);
  require(d.get(act + 0xC) == expectedSeed && d.get(drlg + 0x3E8) == game, "Server DRLG/game/seed linkage failed");
  require(d.get(game + 0x8C) == 1, "Require exactly one local game client");
  std::set<U> seen; player = 0;
  for (U bin = 0; bin < 128; bin++) {
    U unit = d.get(game + 0x1120 + bin * 4);
    while (unit) {
      require(seen.insert(unit).second && seen.size() <= 16, "Invalid player hash chain");
      require(d.get(unit) == 0 && d.get(unit + 0x80) == game, "Server player/game back-reference failed");
      require(!player, "Require exactly one server player"); player = unit;
      unit = d.get(unit + 0xE4);
    }
  }
  require(player != 0, "No server player"); return drlg;
}

#ifndef D2_CONTROLLER_LIBRARY
int wmain(int argc, wchar_t** argv) {
  SetErrorMode(SEM_FAILCRITICALERRORS | SEM_NOGPFAULTERRORBOX | SEM_NOOPENFILEERRORBOX);
  if (argc != 7) return 2; // pid, home, new output dir, steps, burst(0/1), map seed
  Controller d; bool success = false; U completed = 0, firstFrame = 0, lastFrame = 0;
  double elapsed = 0;
  auto output = std::filesystem::absolute(argv[3]);
  try {
    d.pid = std::stoul(argv[1]); U steps = std::stoul(argv[4]), burst = std::stoul(argv[5]);
    U seed = std::stoul(argv[6]); require(steps >= 1 && steps <= 16 && burst <= 1, "Invalid bounded command");
    require(std::filesystem::create_directory(output), "Output directory must be new");
    enableDebugPrivilege();
    d.process = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_CREATE_THREAD | PROCESS_VM_READ | PROCESS_VM_WRITE | PROCESS_VM_OPERATION | PROCESS_SUSPEND_RESUME | SYNCHRONIZE, FALSE, d.pid);
    require(d.process != nullptr, "OpenProcess");
    wchar_t executable[32768]{}; DWORD chars = 32768;
    require(QueryFullProcessImageNameW(d.process, 0, executable, &chars), "Process image");
    const auto root = std::filesystem::absolute(argv[2]);
    require(std::filesystem::equivalent(executable, root / L"D2v110各版本免CD全功能启动.exe"), "Unexpected target executable");
    fileHash(root / L"D2Game.dll", "2bd92fb25e3e4b8269930712620ba9db00cdc1aa44569d87dc4aba76553272b6");
    fileHash(root / L"D2Common.dll", "b9d1a27c6a35992932c7c47438d02e5269f878e899f6e614d507902cecd1beab");
    Module gameModule = module(d.pid, L"D2Game.dll", root), common = module(d.pid, L"D2Common.dll", root);
    const U entry = gameModule.base + 0x86D0;
    signature(d, entry, {0x83,0xec,0x08,0x53,0x56,0x57,0x8b,0xf9,0xbb,0x01,0,0,0});
    for (U rva : {0x72810u, 0x72B00u}) signature(d, common.base + rva, {0x83,0xec,0x0c,0x53,0x8b,0x5c,0x24,0x18,0x55,0x57,0x85,0xdb});
    require(DebugActiveProcess(d.pid), "DebugActiveProcess"); d.attached = true;
    require(DebugSetProcessKillOnExit(FALSE), "Disable kill on debugger exit");
    d.waitBreakpoint(0, 3000, true);
    d.arm(entry); d.resume();
    CONTEXT original = d.waitBreakpoint(entry, 5000);
    d.gameThread = d.event.dwThreadId;
    const U game = original.Ecx, returnTo = d.get(original.Esp);
    require(returnTo >= gameModule.base && returnTo < gameModule.base + gameModule.size, "Caller outside D2Game");
    require(d.get<BYTE>(returnTo - 5) == 0xE8 && returnTo + d.get(returnTo - 4) == entry, "Unexpected native update caller");
    U player = 0; validateGame(d, game, seed, player);
    firstFrame = lastFrame = d.get(game + 0xA8);
    std::ofstream identity(output / "identity.json");
    identity << "{\"game\":" << game << ",\"server_player\":" << player << ",\"player_class\":" << d.get(player + 4)
             << ",\"player_id\":" << d.get(player + 0xC) << ",\"thread\":" << d.gameThread
             << ",\"frame\":" << firstFrame << ",\"seed\":" << seed << ",\"entry_rva\":\"0x86d0\"}"; identity.close();
    Sleep(200); require(d.get(game + 0xA8) == firstFrame, "Frame changed while held at debug event");
    std::ofstream values(output / "skill-values.csv");
    values << "skill,level,min_fixed,max_fixed,min_integer,max_integer,owner_class,mastery\n";
    for (U level = 1; level <= 20; level++) {
      U minimum = d.callGetter(original, returnTo, common.base + 0x72810, player, 36, level);
      U maximum = d.callGetter(original, returnTo, common.base + 0x72B00, player, 36, level);
      values << "36," << level << ',' << minimum << ',' << maximum << ',' << (minimum >> 8) << ',' << (maximum >> 8) << ',' << d.get(player + 4) << ",0\n";
      values.flush(); require(d.get(game + 0xA8) == firstFrame, "Getter advanced the game frame");
    }
    values.close();
    std::ofstream frames(output / "frames.csv"); frames << "step,before,after,wall_ms,game_seed_low,game_seed_high\n";
    ULONGLONG started = GetTickCount64();
    for (U step = 0; step < steps; step++) {
      CONTEXT c = original; U before = d.get(game + 0xA8);
      d.arm(returnTo); d.set(c); d.resume();
      CONTEXT after = d.waitBreakpoint(returnTo, 3000);
      U now = d.get(game + 0xA8); require(now == before + 1, "Native update did not advance exactly one frame");
      lastFrame = now; completed++;
      frames << completed << ',' << before << ',' << now << ',' << (GetTickCount64() - started)
             << ',' << d.get(game + 0xD0) << ',' << d.get(game + 0xD4) << '\n'; frames.flush();
      if (!burst && step + 1 < steps) {
        d.arm(entry); d.set(after); d.resume();
        original = d.waitBreakpoint(entry, 3000);
        require(original.Ecx == game && d.get(original.Esp) == returnTo, "Native update context changed");
      }
    }
    elapsed = static_cast<double>(GetTickCount64() - started);
    success = true;
  } catch (const std::exception& error) {
    std::cerr << error.what() << '\n';
  }
  bool cleaned = d.cleanup();
  auto saveSummary = [&]() {
    std::ofstream summary(output / "summary.json");
    summary << "{\"status\":\"" << (success && cleaned ? "passed" : "failed")
          << "\",\"frames_executed\":" << completed << ",\"first_frame\":" << firstFrame
          << ",\"last_frame\":" << lastFrame << ",\"update_wall_ms\":" << elapsed
          << ",\"detached\":" << (d.detached ? "true" : "false")
          << ",\"cleanup_confirmed\":" << (cleaned ? "true" : "false")
          << ",\"cleanup_attention_required\":" << (cleaned ? "false" : "true")
          << ",\"game_terminated_by_controller\":false,\"scope\":\"frame control and skill getter only; no casts, damage settlement or visual parity\"}";
  };
  saveSummary();
  // Never abandon an attached target with unresolved software breakpoints.
  // The parent reports this state and leaves this recovery helper alive.
  while (d.attached && !cleaned) { Sleep(1000); cleaned = d.cleanup(); }
  saveSummary();
  return success && cleaned ? 0 : 1;
}
#endif
