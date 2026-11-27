// Owned, console-free child only: demonstrate debugger event-handle lifetime.
#include <windows.h>
#include <iostream>
#include <filesystem>
#include <string>

static DWORD WINAPI worker(void*) { Sleep(400); return 0; }

int wmain(int argc, wchar_t** argv) {
  if (argc == 2 && std::wstring(argv[1]) == L"--child") {
    Sleep(200);
    HANDLE thread = CreateThread(nullptr, 0, worker, nullptr, 0, nullptr);
    if (!thread) return 2;
    WaitForSingleObject(thread, 2000);
    CloseHandle(thread);
    Sleep(200);
    return 0;
  }
  if (argc != 3) return 2;
  const bool early = std::wstring(argv[1]) == L"early-close";
  wchar_t self[32768]{};
  GetModuleFileNameW(nullptr, self, 32768);
  std::wstring command = L"\"" + std::wstring(self) + L"\" --child";
  STARTUPINFOW startup{}; startup.cb = sizeof startup;
  PROCESS_INFORMATION child{};
  if (!CreateProcessW(self, command.data(), nullptr, nullptr, FALSE,
      DEBUG_ONLY_THIS_PROCESS | CREATE_NO_WINDOW, nullptr, nullptr, &startup, &child)) return 3;
  HANDLE file = INVALID_HANDLE_VALUE;
  DWORD tracked = 0;
  bool reused = false, invalidAfterExit = false, exited = false;
  const auto deadline = GetTickCount64() + 5000;
  while (GetTickCount64() < deadline) {
    DEBUG_EVENT event{};
    if (!WaitForDebugEvent(&event, 500)) continue;
    if (event.dwDebugEventCode == CREATE_PROCESS_DEBUG_EVENT && event.u.CreateProcessInfo.hFile)
      CloseHandle(event.u.CreateProcessInfo.hFile);
    if (event.dwDebugEventCode == LOAD_DLL_DEBUG_EVENT && event.u.LoadDll.hFile)
      CloseHandle(event.u.LoadDll.hFile);
    if (event.dwDebugEventCode == CREATE_THREAD_DEBUG_EVENT && !tracked) {
      tracked = event.dwThreadId;
      HANDLE debugThread = event.u.CreateThread.hThread;
      if (early) CloseHandle(debugThread);
      file = CreateFileW(argv[2], GENERIC_WRITE, FILE_SHARE_READ, nullptr,
          CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
      reused = file == debugThread;
      DWORD written = 0;
      WriteFile(file, "before\n", 7, &written, nullptr);
      std::cout << "{\"stage\":\"create_thread\",\"early_close\":" << (early ? "true" : "false")
          << ",\"debug_thread_handle\":" << reinterpret_cast<ULONG_PTR>(debugThread)
          << ",\"file_handle\":" << reinterpret_cast<ULONG_PTR>(file)
          << ",\"reused\":" << (reused ? "true" : "false") << "}\n";
    }
    const bool trackedExit = event.dwDebugEventCode == EXIT_THREAD_DEBUG_EVENT && event.dwThreadId == tracked;
    const bool processExit = event.dwDebugEventCode == EXIT_PROCESS_DEBUG_EVENT;
    ContinueDebugEvent(event.dwProcessId, event.dwThreadId, DBG_CONTINUE);
    if (trackedExit) {
      DWORD written = 0; SetLastError(0);
      BOOL ok = WriteFile(file, "after\n", 6, &written, nullptr);
      DWORD error = GetLastError();
      invalidAfterExit = !ok && error == ERROR_INVALID_HANDLE;
      std::cout << "{\"stage\":\"after_exit_continue\",\"write_ok\":" << (ok ? "true" : "false")
          << ",\"error\":" << error << "}\n";
    }
    if (processExit) { exited = true; break; }
  }
  if (!exited) {
    // This executable owns the sole test child; never attach to another process.
    TerminateProcess(child.hProcess, 99);
    DEBUG_EVENT event{};
    while (WaitForDebugEvent(&event, 500)) {
      bool last = event.dwDebugEventCode == EXIT_PROCESS_DEBUG_EVENT;
      ContinueDebugEvent(event.dwProcessId, event.dwThreadId, DBG_CONTINUE);
      if (last) break;
    }
  }
  if (file != INVALID_HANDLE_VALUE && !invalidAfterExit) CloseHandle(file);
  CloseHandle(child.hThread); CloseHandle(child.hProcess);
  return exited && tracked && (early ? reused && invalidAfterExit : !invalidAfterExit) ? 0 : 1;
}
