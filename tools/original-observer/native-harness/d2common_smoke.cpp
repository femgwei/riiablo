// Read-only 1.10f D2Common loader. It never starts Game.exe and never writes
// to another process. Keep calls limited to no-argument smoke exports until a
// function-specific ABI/fixture has been reviewed.
#define WIN32_LEAN_AND_MEAN
#include <windows.h>

#include <cstdint>
#include <filesystem>
#include <iostream>
#include <sstream>

namespace fs = std::filesystem;

static std::string win_error(DWORD code = GetLastError()) {
  std::ostringstream out;
  out << "Win32 error " << code;
  return out.str();
}

static FARPROC ordinal(HMODULE module, WORD value) {
  return GetProcAddress(module, reinterpret_cast<LPCSTR>(static_cast<ULONG_PTR>(value)));
}

int wmain(int argc, wchar_t** argv) {
  const bool probe_archives = argc == 3 && wcscmp(argv[2], L"--probe-archives") == 0;
  if (argc < 2 || argc > 3 || (argc == 3 && !probe_archives)) {
    std::wcerr << L"usage: d2common_smoke.exe <original-1.10f-directory> [--probe-archives]\n";
    return 2;
  }
  const fs::path root = fs::absolute(argv[1]);
  const fs::path dll = root / L"D2Common.dll";
  if (!fs::is_regular_file(dll)) {
    std::cerr << "missing D2Common.dll\n";
    return 3;
  }
  if (!SetDllDirectoryW(root.c_str())) {
    std::cerr << win_error() << " while setting DLL directory\n";
    return 4;
  }
  HMODULE storm = LoadLibraryW((root / L"Storm.dll").c_str());
  if (!storm) {
    std::cerr << win_error() << " while loading Storm.dll\n";
    SetDllDirectoryW(nullptr);
    return 5;
  }
  using SetBasePath = BOOL(__stdcall*)(char*);
  using OpenArchive = BOOL(__stdcall*)(char*, int, int, void**);
  using CloseArchive = BOOL(__stdcall*)(void*);
  auto set_base_path = reinterpret_cast<SetBasePath>(ordinal(storm, 270));
  auto open_archive = reinterpret_cast<OpenArchive>(ordinal(storm, 266));
  auto close_archive = reinterpret_cast<CloseArchive>(ordinal(storm, 252));
  if (probe_archives) {
    std::cerr << "[smoke] Storm.dll loaded\n" << std::flush;
  }
  std::string root_a = root.string();
  void* archives[3] = {};
  const char* archive_names[] = {"d2data.mpq", "d2exp.mpq", "patch_d2.mpq"};
  const int archive_priorities[] = {1000, 3000, 5000};
  if (probe_archives) {
    std::cerr << "[smoke] calling SFileSetBasePath\n" << std::flush;
  }
  if (!set_base_path || !open_archive || !close_archive || !set_base_path(root_a.data())) {
    std::cerr << "Storm archive setup is unavailable\n";
    FreeLibrary(storm);
    SetDllDirectoryW(nullptr);
    return 6;
  }
  if (probe_archives) {
    std::cerr << "[smoke] SFileSetBasePath returned\n" << std::flush;
    for (int i = 0; i < 3; ++i) {
      std::string path = (root / archive_names[i]).string();
      std::cerr << "[smoke] opening " << path << "\n" << std::flush;
      if (!open_archive(path.data(), archive_priorities[i], 2, &archives[i])) {
        std::cerr << "unable to open " << path << "\n";
        for (void* archive : archives) if (archive) close_archive(archive);
        FreeLibrary(storm);
        SetDllDirectoryW(nullptr);
        return 7;
      }
      std::cerr << "[smoke] opened " << path << "\n" << std::flush;
    }
  }
  HMODULE module = LoadLibraryW(dll.c_str());
  if (probe_archives) {
    std::cerr << "[smoke] D2Common loaded\n" << std::flush;
  }
  if (!module) {
    std::cerr << win_error() << " while loading " << dll.string() << "\n";
    for (void* archive : archives) if (archive) close_archive(archive);
    FreeLibrary(storm);
    SetDllDirectoryW(nullptr);
    return 8;
  }

  auto safe0 = [](FARPROC proc) -> int { return reinterpret_cast<int(__stdcall*)()>(proc)(); };
  FARPROC f10016 = ordinal(module, 10016);
  FARPROC f10018 = ordinal(module, 10018);
  FARPROC f10576 = ordinal(module, 10576);
  FARPROC tables = GetProcAddress(module, "sgptDataTables");
  auto table_ptr = tables ? *reinterpret_cast<std::uintptr_t*>(tables) : 0;
  auto table_i32 = [table_ptr](std::size_t offset) -> std::int32_t {
    return table_ptr ? *reinterpret_cast<std::int32_t*>(table_ptr + offset) : 0;
  };

  std::cout << "{\n"
            << "  \"dll\": \"D2Common.dll\",\n"
            << "  \"module_base\": \"0x" << std::hex
            << reinterpret_cast<std::uintptr_t>(module) << "\",\n"
            << "  \"ordinal_10016_present\": " << (f10016 ? "true" : "false") << ",\n"
            << "  \"ordinal_10018_present\": " << (f10018 ? "true" : "false") << ",\n"
            << "  \"ordinal_10576_present\": " << (f10576 ? "true" : "false") << ",\n"
            << "  \"archives_requested\": " << (probe_archives ? "true" : "false") << ",\n"
            << "  \"sgptDataTables_present\": " << (tables ? "true" : "false") << ",\n"
            << "  \"sgptDataTables_value\": \"0x" << table_ptr << "\",\n"
            << "  \"states_record_count\": " << std::dec << table_i32(0xC4) << ",\n"
            << "  \"skills_record_count\": " << table_i32(0xBA0) << ",\n"
            << "  \"monstats_record_count\": " << table_i32(0xA80) << ",\n"
            << "  \"ordinal_10016_result\": ";
  if (!f10016) {
    std::cout << "null,\n";
  } else {
    std::cout << std::dec << safe0(f10016) << ",\n";
  }
  std::cout << "  \"ordinal_10018_result\": ";
  if (!f10018) {
    std::cout << "null\n";
  } else {
    std::cout << std::dec << safe0(f10018) << ",\n";
  }
  if (probe_archives) {
    std::cout << "  \"archives_opened\": 3,\n"
              << "  \"table_loader_export_present\": " << (f10576 ? "true" : "false") << "\n";
  } else {
    std::cout << "  \"table_loader_export_present\": " << (f10576 ? "true" : "false") << "\n";
  }
  std::cout << "}\n";

  FreeLibrary(module);
  for (void* archive : archives) if (archive) close_archive(archive);
  FreeLibrary(storm);
  SetDllDirectoryW(nullptr);
  return (f10016 && f10018 && tables && (!probe_archives || (f10576 && archives[0] && archives[1] && archives[2]))) ? 0 : 9;
}
