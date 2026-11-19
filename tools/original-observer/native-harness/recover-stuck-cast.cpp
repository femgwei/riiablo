// Recover one bounded d2game_cast helper that failed before fixture creation.
// Restores only the two known software-breakpoint bytes, detaches the target,
// then terminates the specific stuck helper. Refuses unexpected live bytes.
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <tlhelp32.h>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <string>

static void fail(const char* what) { std::cerr << what << " (" << GetLastError() << ")\n"; std::exit(1); }
static HMODULE module(DWORD pid, const wchar_t* wanted) {
  HANDLE s = CreateToolhelp32Snapshot(TH32CS_SNAPMODULE | TH32CS_SNAPMODULE32, pid);
  if (s == INVALID_HANDLE_VALUE) fail("module snapshot");
  MODULEENTRY32W m{}; m.dwSize = sizeof m; HMODULE result = nullptr;
  for (BOOL ok = Module32FirstW(s, &m); ok; ok = Module32NextW(s, &m))
    if (_wcsicmp(m.szModule, wanted) == 0) { result = m.hModule; break; }
  CloseHandle(s); if (!result) fail("D2Game module missing"); return result;
}
static BYTE readByte(HANDLE p, uintptr_t at) {
  BYTE b = 0; SIZE_T got = 0; if (!ReadProcessMemory(p, reinterpret_cast<void*>(at), &b, 1, &got) || got != 1) fail("read breakpoint byte"); return b;
}
static void restore(HANDLE p, uintptr_t at, BYTE expected) {
  BYTE b = readByte(p, at);
  if (b == expected) return;
  if (b != 0xCC) { std::cerr << "unexpected byte at " << std::hex << at << ": " << unsigned(b) << "\n"; std::exit(2); }
  SIZE_T wrote = 0; if (!WriteProcessMemory(p, reinterpret_cast<void*>(at), &expected, 1, &wrote) || wrote != 1) fail("restore breakpoint byte");
  FlushInstructionCache(p, reinterpret_cast<void*>(at), 1);
}
int wmain(int argc, wchar_t** argv) {
  if (argc != 3) { std::wcerr << L"usage: recover-stuck-cast.exe targetPid helperPid\n"; return 2; }
  DWORD target = std::stoul(argv[1]), helper = std::stoul(argv[2]);
  HANDLE p = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ | PROCESS_VM_WRITE | PROCESS_VM_OPERATION | PROCESS_SUSPEND_RESUME | SYNCHRONIZE, FALSE, target);
  if (!p) fail("OpenProcess target");
  uintptr_t base = reinterpret_cast<uintptr_t>(module(target, L"D2Game.dll"));
  restore(p, base + 0x86D0, 0x83);   // GAME_UpdateProgress entry
  restore(p, base + 0x91260, 0x83);  // damage observer entry (normally untouched)
  bool detached = !!DebugActiveProcessStop(target);
  DWORD detachError = detached ? ERROR_SUCCESS : GetLastError();
  HANDLE h = OpenProcess(PROCESS_TERMINATE | SYNCHRONIZE | PROCESS_QUERY_INFORMATION, FALSE, helper);
  bool helperGone = false;
  if (h) { helperGone = !!TerminateProcess(h, 0); if (!helperGone && !GetLastError()) helperGone = WaitForSingleObject(h, 2000) == WAIT_OBJECT_0; CloseHandle(h); }
  std::wcout << L"{\"target\":" << target << L",\"helper\":" << helper
             << L",\"entry_bytes_restored\":true,\"detached\":" << (detached ? L"true" : L"false")
             << L",\"detach_error\":" << detachError << L",\"helper_terminated\":" << (helperGone ? L"true" : L"false") << L"}\n";
  CloseHandle(p); return detached || helperGone ? 0 : 1;
}
