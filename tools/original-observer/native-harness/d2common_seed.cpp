// Isolated 1.10f seed-state oracle. No game, graphics, Fog initialization or MPQs.
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <cstdint>
#include <filesystem>
#include <iostream>
#include <string>

struct SeedState { std::uint32_t low, high; };
static_assert(sizeof(void*) == 4 && sizeof(SeedState) == 8, "Requires x86 ABI");

static FARPROC entry(HMODULE module, WORD number, std::uintptr_t rva) {
  FARPROC proc = GetProcAddress(module, MAKEINTRESOURCEA(number));
  if (reinterpret_cast<std::uintptr_t>(proc) != reinterpret_cast<std::uintptr_t>(module) + rva)
    return nullptr;
  return proc;
}

int wmain(int argc, wchar_t** argv) {
  SetErrorMode(SEM_FAILCRITICALERRORS | SEM_NOGPFAULTERRORBOX | SEM_NOOPENFILEERRORBOX);
  if (argc != 2) { std::cerr << "usage: d2common_seed.exe <1.10f-directory>\n"; return 2; }
  const auto root = std::filesystem::absolute(argv[1]);
  if (!SetDllDirectoryW(root.c_str())) return 3;
  HMODULE module = LoadLibraryW((root / L"D2Common.dll").c_str());
  if (!module) { std::cerr << "LoadLibrary failed: " << GetLastError() << '\n'; return 4; }
  // Function types come from D2MOO D2Seed.h. RVAs checked against this 1.10f DLL.
  auto init = reinterpret_cast<void(__fastcall*)(SeedState*)>(entry(module, 10912, 0x6EAB0));
  auto init_low = reinterpret_cast<void(__fastcall*)(SeedState*, std::uint32_t)>(entry(module, 10913, 0x6EAC0));
  auto low = reinterpret_cast<std::uint32_t(__fastcall*)(SeedState*)>(entry(module, 10914, 0x6EAD0));
  auto high = reinterpret_cast<std::uint32_t(__fastcall*)(SeedState*)>(entry(module, 10915, 0x6EB00));
  auto set = reinterpret_cast<void(__fastcall*)(SeedState*, std::uint32_t, std::uint32_t)>(entry(module, 10921, 0x6EAE0));
  auto get = reinterpret_cast<void(__fastcall*)(SeedState*, std::uint32_t*, std::uint32_t*)>(entry(module, 10922, 0x6EAF0));
  if (!init || !init_low || !low || !high || !set || !get) {
    std::cerr << "Unsupported seed export layout\n"; FreeLibrary(module); return 5;
  }
  std::string op;
  std::uint64_t input_low, input_high;
  int count = 0;
  while (std::cin >> op) {
    if (!(std::cin >> input_low >> input_high) || input_low > UINT32_MAX
        || input_high > UINT32_MAX || ++count > 2048) {
      std::cerr << "Invalid fixture\n"; FreeLibrary(module); return 6;
    }
    SeedState seed{static_cast<std::uint32_t>(input_low), static_cast<std::uint32_t>(input_high)};
    if (op == "init") init(&seed);
    else if (op == "low") init_low(&seed, static_cast<std::uint32_t>(input_low));
    else if (op == "set") {
      seed = {0xDEADBEEF, 0xCAFEBABE};
      set(&seed, static_cast<std::uint32_t>(input_low), static_cast<std::uint32_t>(input_high));
    } else { std::cerr << "Unknown fixture operation\n"; FreeLibrary(module); return 6; }
    std::uint32_t pair_low = 0, pair_high = 0;
    get(&seed, &pair_low, &pair_high);
    std::cout << op << ',' << input_low << ',' << input_high << ',' << seed.low << ',' << seed.high
              << ',' << low(&seed) << ',' << high(&seed) << ',' << pair_low << ',' << pair_high << '\n';
  }
  FreeLibrary(module);
  SetDllDirectoryW(nullptr);
  return count ? 0 : 7;
}
