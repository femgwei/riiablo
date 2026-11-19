// Execute reviewed, position-independent 1.10f RNG routines in the actual DLL.
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <cstdint>
#include <cstring>
#include <filesystem>
#include <iostream>
#include <string>
struct SeedState { std::uint32_t low, high; };
static_assert(sizeof(void*) == 4 && sizeof(SeedState) == 8, "Requires x86 ABI");
static constexpr std::uint8_t raw_bytes[] = {0x8b,0x01,0xba,0xc5,0x90,0xc6,0x6a,0xf7,0xe2,0x56,0x8b,0x71,0x04,0x57,0x33,0xff,0x03,0xc6,0x13,0xd7,0x5f,0x89,0x51,0x04,0x89,0x01,0x5e,0xc3};
static constexpr std::uint8_t limited_bytes[] = {0x53,0x56,0x8b,0xf2,0x57,0x85,0xf6,0x7f,0x06,0x5f,0x5e,0x33,0xc0,0x5b,0xc3,0x8b,0x01,0xba,0xc5,0x90,0xc6,0x6a,0x8d,0x7e,0xff,0x33,0xdb,0xf7,0xe2,0x85,0xfe,0x74,0x16,0x8b,0x79,0x04,0x03,0xc7,0x5f,0x13,0xd3,0x89,0x01,0x89,0x51,0x04,0x33,0xd2,0xf7,0xf6,0x5e,0x5b,0x8b,0xc2,0xc3,0x8b,0xf0,0x8b,0x41,0x04,0x03,0xf0,0x8b,0xc7,0x13,0xd3,0x89,0x31,0x23,0xc6,0x5f,0x5e,0x89,0x51,0x04,0x5b,0xc3};
int wmain(int argc, wchar_t** argv) {
  SetErrorMode(SEM_FAILCRITICALERRORS | SEM_NOGPFAULTERRORBOX | SEM_NOOPENFILEERRORBOX);
  if (argc != 2) return 2;
  auto root = std::filesystem::absolute(argv[1]);
  if (!SetDllDirectoryW(root.c_str())) return 3;
  HMODULE module = LoadLibraryW((root / L"D2Common.dll").c_str());
  if (!module) { std::cerr << "LoadLibrary failed " << GetLastError(); return 4; }
  auto base = reinterpret_cast<const std::uint8_t*>(module);
  // Exact bodies include every return branch. These routines have no external
  // calls, global state or graphics dependencies; ECX is the caller's seed.
  if (std::memcmp(base + 0x38E30, raw_bytes, sizeof(raw_bytes))
      || std::memcmp(base + 0x3D3E0, limited_bytes, sizeof(limited_bytes))) {
    std::cerr << "Unsupported RNG instruction bytes\n"; FreeLibrary(module); return 5;
  }
  auto raw = reinterpret_cast<std::uint64_t(__fastcall*)(SeedState*)>(
      const_cast<std::uint8_t*>(base + 0x38E30));
  auto limited = reinterpret_cast<std::uint32_t(__fastcall*)(SeedState*, int)>(
      const_cast<std::uint8_t*>(base + 0x3D3E0));
  std::string op; std::uint64_t low, high; int bound, steps, index = 0;
  while (std::cin >> op) {
    if (!(std::cin >> low >> high >> bound >> steps) || low > UINT32_MAX || high > UINT32_MAX
        || steps < 1 || steps > 256 || index >= 4096 || (op != "raw" && op != "limited")) {
      std::cerr << "Invalid fixture\n"; FreeLibrary(module); return 6;
    }
    SeedState seed{static_cast<std::uint32_t>(low), static_cast<std::uint32_t>(high)};
    for (int step = 0; step < steps; step++) {
      std::uint64_t value = op == "raw" ? raw(&seed) : limited(&seed, bound);
      std::cout << index << ',' << step << ',' << value << ',' << seed.low << ',' << seed.high << '\n';
    }
    index++;
  }
  FreeLibrary(module); SetDllDirectoryW(nullptr);
  return index ? 0 : 7;
}
