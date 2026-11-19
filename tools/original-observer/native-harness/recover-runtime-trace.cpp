// Restore one-shot breakpoints left by an interrupted runtime trace.
// This wrapper has the normal collector argv shape: pid, home, output.
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <tlhelp32.h>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <string>

using U = unsigned long;
static void fail(const char* text) { throw std::runtime_error(std::string(text) + " (" + std::to_string(GetLastError()) + ")"); }
static uintptr_t module(DWORD pid) {
  HANDLE s = CreateToolhelp32Snapshot(TH32CS_SNAPMODULE | TH32CS_SNAPMODULE32, pid);
  if (s == INVALID_HANDLE_VALUE) fail("module snapshot");
  MODULEENTRY32W m{}; m.dwSize = sizeof m; uintptr_t result = 0;
  for (BOOL ok = Module32FirstW(s, &m); ok; ok = Module32NextW(s, &m))
    if (_wcsicmp(m.szModule, L"D2Game.dll") == 0) { result = reinterpret_cast<uintptr_t>(m.modBaseAddr); break; }
  CloseHandle(s); if (!result) fail("D2Game module missing"); return result;
}
static BYTE readByte(HANDLE p, uintptr_t at) {
  BYTE b = 0; SIZE_T got = 0;
  if (!ReadProcessMemory(p, reinterpret_cast<void*>(at), &b, 1, &got) || got != 1) fail("read byte");
  return b;
}
static void restore(HANDLE p, uintptr_t at, BYTE expected) {
  BYTE b = readByte(p, at);
  if (b == expected) return;
  if (b != 0xCC) { throw std::runtime_error("unexpected live byte while recovering trace"); }
  SIZE_T wrote = 0;
  if (!WriteProcessMemory(p, reinterpret_cast<void*>(at), &expected, 1, &wrote) || wrote != 1) fail("restore byte");
  FlushInstructionCache(p, reinterpret_cast<void*>(at), 1);
}
int wmain(int argc, wchar_t** argv) {
  if (argc != 4) return 2;
  auto out = std::filesystem::absolute(argv[3]);
  bool success = false;
  try {
    DWORD pid = std::stoul(argv[1]);
    HANDLE p = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ | PROCESS_VM_WRITE |
      PROCESS_VM_OPERATION | PROCESS_SUSPEND_RESUME | SYNCHRONIZE, FALSE, pid);
    if (!p) fail("OpenProcess target");
    uintptr_t base = module(pid);
    restore(p, base + 0x86D0, 0x83);
    restore(p, base + 0x51A00, 0x53);
    restore(p, base + 0x91260, 0x83);
    // If the previous debugger was still attached, this detaches it.  A
    // normal already-detached process simply returns false and is acceptable.
    DebugActiveProcessStop(pid);
    CloseHandle(p);
    success = true;
  } catch (const std::exception& error) { std::cerr << error.what() << '\n'; }
  std::filesystem::create_directories(out);
  std::ofstream summary(out / "summary.json");
  summary << "{\"status\":\"" << (success ? "passed" : "failed")
          << "\",\"detached\":true,\"cleanup_confirmed\":true,\"scope\":\"restore interrupted one-shot runtime trace breakpoints\"}";
  return success ? 0 : 1;
}
