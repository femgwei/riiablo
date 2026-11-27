// Prepare one bounded, identified native attacker, then detach before D2Gfx capture.
// Native AI/hit RNG/armor consumption run normally; no shield/state edits or damage RPC.
#define D2_FALLEN_HELPERS_ONLY
#include "d2game_fallen_death.cpp"

int wmain(int argc, wchar_t** argv) {
  SetErrorMode(SEM_FAILCRITICALERRORS | SEM_NOGPFAULTERRORBOX | SEM_NOOPENFILEERRORBOX);
  if (argc != 4) return 2;
  CastController d; bool success = false;
  U monster = 0, player = 0, frame = 0;
  auto output = std::filesystem::absolute(argv[3]);
  try {
    require(std::filesystem::create_directory(output), "Output directory must be new");
    d.pid = std::stoul(argv[1]); auto root = std::filesystem::absolute(argv[2]);
    enableDebugPrivilege();
    d.process = OpenProcess(PROCESS_ALL_ACCESS, FALSE, d.pid); require(d.process != nullptr, "OpenProcess");
    wchar_t image[32768]{}; DWORD chars = 32768;
    require(QueryFullProcessImageNameW(d.process, 0, image, &chars), "Process identity");
    require(std::filesystem::equivalent(image, root / L"D2v110各版本免CD全功能启动.exe"), "Wrong loader");
    fileHash(root / L"D2Game.dll", "2bd92fb25e3e4b8269930712620ba9db00cdc1aa44569d87dc4aba76553272b6");
    fileHash(root / L"D2Common.dll", "b9d1a27c6a35992932c7c47438d02e5269f878e899f6e614d507902cecd1beab");
    Module gm = module(d.pid, L"D2Game.dll", root), cm = module(d.pid, L"D2Common.dll", root);
    U entry = gm.base + 0x86D0, spawn = gm.base + 0x3A090;
    U getStat = cm.base + 0x77C30, setStat = cm.base + 0x77AB0;
    signature(d, entry, {0x83,0xEC,8,0x53,0x56,0x57,0x8B,0xF9,0xBB,1,0,0,0});
    signature(d, spawn, {0x83,0xEC,0x28,0x56,0x8B,0x74,0x24,0x38,0x33,0xC0});
    signature(d, setStat, {0x8B,0x44,0x24,0x0C,0x53,0x8B,0x5C,0x24,0x0C});
    signature(d, getStat, {0x8B,0x44,0x24,4,0x55,0x85,0xC0,0x75,4,0x5D,0xC2,0x0C,0});
    require(DebugActiveProcess(d.pid), "Attach"); d.attached = true;
    require(DebugSetProcessKillOnExit(FALSE), "Disable kill on debugger exit");
    d.waitBreakpoint(0, 3000, true); d.arm(entry); d.resume();
    CONTEXT original = d.waitBreakpoint(entry, 5000); d.gameThread = d.event.dwThreadId;
    U game = original.Ecx, ret = d.get(original.Esp), act = d.get(game + 0xBC);
    validateGame(d, game, d.get(act + 0xC), player);
    char name[16]{}; d.read(d.get(player + 0x14), name, 16);
    require(std::memcmp(name, "CXNecro\0", 8) == 0 && d.get(player + 4) == 2, "Require CXNecro");
    U room = d.get(d.get(player + 0x2C) + 0x1C);
    require(d.get(d.get(d.get(room + 0x38)) + 4) == 2, "Require Blood Moor");
    auto pp = position(d, player);
    auto point = targetPosition(d, room, pp.first, pp.second, output);
    monster = d.call(original, ret, spawn, game, room, {point.first, point.second, 19, 1, 0});
    require(monster && d.get(monster + 4) == 19 && d.get(monster + 0x80) == game, "Attacker identity");
    std::ofstream stats(output / "attacker-stats.jsonl");
    auto set = [&](U stat, U value) {
      U before = d.call(original, ret, getStat, 0, 0, {monster, stat, 0});
      d.call(original, ret, setStat, 0, 0, {monster, stat, value, 0});
      U after = d.call(original, ret, getStat, 0, 0, {monster, stat, 0});
      stats << "{\"stat\":" << stat << ",\"before\":" << before << ",\"set_base\":" << value
            << ",\"after_aggregate\":" << after << "}\n" << std::flush;
      require(after >= value && after < value + 10000, "Attacker aggregate stat outside bounded range");
    };
    set(12, d.call(original, ret, getStat, 0, 0, {player, 12, 0})); // same level, ordinary hit roll
    set(19, 100000); // bounded high AR; native 95% ceiling and RNG remain active
    set(21, 80); set(22, 80); // 210-point shield crosses both third boundaries before depletion
    frame = d.get(game + 0xA8);
    auto mp = position(d, monster);
    std::ofstream identity(output / "identity.json");
    identity << "{\"player_id\":" << d.get(player + 0xC)
      << ",\"attacker_id\":" << d.get(monster + 0xC) << ",\"attacker_class\":19"
      << ",\"server_frame\":" << frame << ",\"player_position\":[" << pp.first << ',' << pp.second
      << "],\"attacker_position\":[" << mp.first << ',' << mp.second << "]"
      << ",\"min_physical\":80,\"max_physical\":80,\"attack_rating\":100000"
      << ",\"shield_values_changed\":false,\"synthetic_damage_call\":false"
      << ",\"native_ai_after_detach\":true,\"lifetime\":\"owned private game closes after capture\"}";
    success = true;
  } catch (const std::exception& error) { std::cerr << error.what() << '\n'; }
  bool cleaned = d.cleanup();
  std::ofstream summary(output / "summary.json");
  summary << "{\"status\":\"" << (success && cleaned ? "passed" : "failed")
    << "\",\"detached\":" << (d.detached ? "true" : "false")
    << ",\"cleanup_confirmed\":" << (cleaned ? "true" : "false")
    << ",\"attacker_preserved_for_capture\":" << (success ? "true" : "false")
    << ",\"shield_values_changed\":false,\"synthetic_damage_call\":false}";
  return success && cleaned ? 0 : 1;
}
