// Fixed Barbarian Bash lifecycle experiment in the user's CXBarb world.
// All calls run on the stopped original game update thread. No arbitrary RPC.
#define D2_CONTROLLER_LIBRARY
#include "d2game_controller.cpp"
#include <array>

struct CastController : Controller {
  U call(CONTEXT entry, U returnTo, U function, U ecx, U edx, std::initializer_list<U> stack) {
    require(held && !pendingCall && stack.size() <= 5, "Invalid fixed call boundary");
    scratch = entry.Esp - 0x100;
    std::vector<U> args{ returnTo }; args.insert(args.end(), stack.begin(), stack.end());
    scratchOriginal.resize(args.size() * 4); read(scratch, scratchOriginal.data(), scratchOriginal.size());
    callRestore = entry; pendingCall = true;
    write(scratch, args.data(), args.size() * 4); arm(returnTo);
    CONTEXT c = entry; c.Eip = function; c.Esp = scratch; c.Ecx = ecx; c.Edx = edx; set(c); resume();
    CONTEXT result;
    try {
      result = waitBreakpoint(returnTo, 3000);
    } catch (...) {
      // A target-mode ABI mismatch can leave the update thread inside the
      // callback instead of returning to our scratch frame. Interrupt once
      // and record the exact instruction for diagnosis before rethrowing.
      if (!held && process && DebugBreakProcess(process)) {
        try {
          CONTEXT stuck = waitBreakpoint(0, 3000, true);
          std::cerr << "Fixed call timeout eip=" << std::hex << stuck.Eip
                    << " esp=" << stuck.Esp << std::dec << '\n';
          resume();
        } catch (...) {
          // cleanup() below will perform the final debugger interruption.
        }
      }
      throw;
    }
    require(result.Esp == scratch + args.size() * 4, "Fixed call stack imbalance");
    write(scratch, scratchOriginal.data(), scratchOriginal.size()); set(entry); pendingCall = false;
    return result.Eax;
  }
};

static std::pair<U, U> position(CastController& d, U unit) {
  U path = d.get(unit + 0x2C); require(path && d.get(path + 0x30) == unit, "Invalid dynamic path");
  return {d.get<std::uint16_t>(path + 2), d.get<std::uint16_t>(path + 6)};
}

static std::pair<U, U> targetPosition(CastController& d, U room, U px, U py, const std::filesystem::path& output) {
  U grid = d.get(room + 0x34); require(grid != 0, "Missing room collision grid");
  int x = d.get<int>(grid), y = d.get<int>(grid + 4), w = d.get<int>(grid + 8), h = d.get<int>(grid + 12);
  require(w > 0 && h > 0 && w <= 512 && h <= 512, "Invalid collision bounds");
  U cells = d.get(grid + 0x20); require(cells != 0, "Missing collision cells");
  std::vector<std::uint16_t> flags(w * h); d.read(cells, flags.data(), flags.size() * 2);
  std::ofstream collision(output / "collision.csv"); collision << "x,y,flags\n";
  for (int cy = 0; cy < h; cy++) for (int cx = 0; cx < w; cx++) collision << x+cx << ',' << y+cy << ',' << flags[cy*w+cx] << '\n';
  // Keep the complete shot corridor inside this same populated room.
  // Bash's native melee range is shorter than a projectile corridor. Prefer
  // the nearest clear point so the server can resolve a target during A1.
  for (int length = 2; length <= 7; length++) for (auto direction : std::vector<std::pair<int, int>>{{1,0},{0,1},{0,-1},{-1,0},{1,1},{1,-1},{-1,1},{-1,-1}}) {
    bool clear = true;
    for (int distance = 1; distance <= length && clear; distance++) {
      int tx = static_cast<int>(px) + direction.first * distance, ty = static_cast<int>(py) + direction.second * distance;
      int radius = distance == length ? 1 : 0;
      for (int ox = -radius; ox <= radius && clear; ox++) for (int oy = -radius; oy <= radius; oy++) {
        int cx = tx + ox - x, cy = ty + oy - y;
        U mask = distance == length ? 0x3FAF : 0x0127; // placement footprint vs missile corridor
        if (cx < 0 || cy < 0 || cx >= w || cy >= h || (flags[cy * w + cx] & mask)) { clear = false; break; }
      }
    }
    if (clear) return {px + direction.first * length, py + direction.second * length};
  }
  throw std::runtime_error("No clear bounded 3..7 subtile shot corridor in current room");
}

int wmain(int argc, wchar_t** argv) {
  SetErrorMode(SEM_FAILCRITICALERRORS | SEM_NOGPFAULTERRORBOX | SEM_NOOPENFILEERRORBOX);
  if (argc != 4) return 2; // pid, exact home, NEW output directory
  CastController d; bool success = false, castStarted = false, outputCreated = false;
  U firstFrame = 0, lastFrame = 0, target = 0, initialHP = 0, lastHP = 0, initialMana = 0, lowestMana = 0;
  U missilePeak = 0, damageHits = 0, damageFire = 0, damageTotal = 0, targetId = 0, expectedPlayer = 0;
  U observedDamageTarget = 0, observedDamageAttacker = 0;
  bool targetMoved = false, targetHit = false;
  U observedMissile = 0;
  auto output = std::filesystem::absolute(argv[3]);
  try {
    outputCreated = std::filesystem::create_directory(output);
    require(outputCreated, "Output directory must be new");
    d.pid = std::stoul(argv[1]); auto root = std::filesystem::absolute(argv[2]);
    d.process = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_CREATE_THREAD | PROCESS_VM_READ | PROCESS_VM_WRITE | PROCESS_VM_OPERATION | PROCESS_SUSPEND_RESUME | SYNCHRONIZE, FALSE, d.pid);
    require(d.process != nullptr, "OpenProcess");
    wchar_t image[32768]{}; DWORD chars = 32768;
    require(QueryFullProcessImageNameW(d.process, 0, image, &chars), "Process identity");
    require(std::filesystem::equivalent(image, root / L"D2v110各版本免CD全功能启动.exe"), "Wrong loader");
    fileHash(root / L"D2Game.dll", "2bd92fb25e3e4b8269930712620ba9db00cdc1aa44569d87dc4aba76553272b6");
    fileHash(root / L"D2Common.dll", "b9d1a27c6a35992932c7c47438d02e5269f878e899f6e614d507902cecd1beab");
    Module gameModule = module(d.pid, L"D2Game.dll", root), common = module(d.pid, L"D2Common.dll", root);
    U entry = gameModule.base + 0x86D0, spawn = gameModule.base + 0x3A090;
    // Keep the proven coordinate entry for the bounded, non-crashing probe.
    // The target-ID wrapper and direct StartAttackID callback remain separate
    // ABI work and are not used by this collector yet.
    U cast = gameModule.base + 0x517D0;
    U deleteEvent = gameModule.base + 0x4840, damageEntry = gameModule.base + 0x91260;
    U getStat = common.base + 0x77C30, setStat = common.base + 0x77AB0;
    signature(d, entry, {0x83,0xEC,0x08,0x53,0x56,0x57,0x8B,0xF9,0xBB,1,0,0,0});
    signature(d, spawn, {0x83,0xEC,0x28,0x56,0x8B,0x74,0x24,0x38,0x33,0xC0});
    signature(d, cast, {0x53,0x55,0x56,0x57,0x8B,0xF9,0x8B,0xF2,0x85,0xFF});
    signature(d, deleteEvent, {0x55,0x8B,0xE9,0x8B,0xCA,0xE8,0xF6,0x85,0x08,0});
    signature(d, damageEntry, {0x83,0xEC,0x24,0x53,0x55,0x56,0x8B,0x74,0x24,0x34,0x57,0x8B,0xD9});
    signature(d, getStat, {0x8B,0x44,0x24,4,0x55,0x85,0xC0,0x75,4,0x5D,0xC2,0x0C,0});
    signature(d, setStat, {0x8B,0x44,0x24,0x0C,0x53,0x8B,0x5C,0x24,0x0C,0x56,0x8B,0x74,0x24,0x18});
    enableDebugPrivilege();
    require(DebugActiveProcess(d.pid), "Attach"); d.attached = true;
    require(DebugSetProcessKillOnExit(FALSE), "Disable kill-on-exit");
    d.waitBreakpoint(0, 3000, true); d.arm(entry); d.resume();
    CONTEXT original = d.waitBreakpoint(entry, 5000); d.gameThread = d.event.dwThreadId;
    U game = original.Ecx, returnTo = d.get(original.Esp), player = 0;
    require(returnTo >= gameModule.base && returnTo < gameModule.base + gameModule.size, "Update caller outside game");
    require(d.get<BYTE>(returnTo - 5) == 0xE8 && returnTo + d.get(returnTo - 4) == entry, "Update caller mismatch");
    validateGame(d, game, 127897359, player); expectedPlayer = player;
    char name[16]{}; d.read(d.get(player + 0x14), name, 16);
    require(std::memcmp(name, "CXBarb", 7) == 0 && d.get(player + 4) == 4, "Require exact CXBarb Barbarian");
    U skills = d.get(player + 0xA8), skill = d.get(skills + 0xC);
    require(skill && d.get<std::uint16_t>(d.get(skill)) == 126 && d.get(skill + 0x28) == 1, "Require selected Bash level one");
    U inventory = d.get(player + 0x60);
    // Bash is a weapon skill.  The fixture may contain a deterministic
    // equipped weapon; validate only the inventory pointer here and record the
    // first item for the evidence stream instead of requiring a naked player.
    require(inventory, "Missing player inventory");
    U equipped = d.get(inventory + 0x0C);
    U equippedData = equipped ? d.get(equipped + 0x14) : 0;
    require(!equipped || (d.get(equipped) == 4 && equippedData), "Invalid equipped item pointer");
    U path = d.get(player + 0x2C), room = d.get(path + 0x1C), roomEx = d.get(room + 0x38);
    require(d.get(d.get(roomEx) + 4) == 2, "Require Blood Moor");
    auto pos = position(d, player); auto point = targetPosition(d, room, pos.first, pos.second, output);
    auto stat = [&](U unit, U id) { return d.call(original, returnTo, getStat, 0, 0, {unit,id,0}); };
    auto assign = [&](U unit, U id, U value) { d.call(original, returnTo, setStat, 0, 0, {unit,id,value,0}); require(stat(unit,id) == value, "Stat fixture did not apply"); };
    firstFrame = d.get(game + 0xA8);
    target = d.call(original, returnTo, spawn, game, room, {point.first,point.second,0,1,0}); // skeleton1, neutral, normal
    require(target && d.get(target) == 1 && d.get(target + 4) == 0 && d.get(target + 0x80) == game, "Spawned target identity mismatch");
    require(position(d, target) == point, "Target spawn moved from verified position");
    targetId = d.get(target + 0xC);
    d.call(original, returnTo, deleteEvent, game, target, {2,0}); // remove this target's pending AI think only
    d.call(original, returnTo, deleteEvent, game, target, {3,0}); // no target regeneration events
    assign(target, 7, 1000 << 8); assign(target, 6, 1000 << 8);
    for (U id : {39u,35u,74u}) assign(target,id,0); // fire resist, magic DR, HP regen
    initialHP = lastHP = stat(target,6); initialMana = lowestMana = stat(player,8);
    require(initialMana >= 2 << 8, "Insufficient fixture mana");
    std::ofstream identity(output / "identity.json");
    identity << "{\"game\":" << game << ",\"player\":" << player << ",\"name\":\"CXBarb\",\"class\":4,\"skill\":126,\"level\":1,\"map_seed\":127897359,\"target\":" << target
             << ",\"target_id\":" << targetId << ",\"target_class\":0,\"target_x\":" << point.first << ",\"target_y\":" << point.second
             << ",\"player_x\":" << pos.first << ",\"player_y\":" << pos.second << ",\"equipped_item\":" << equipped << ",\"thread\":" << d.gameThread << "}"; identity.close();
    std::ofstream equipment(output / "equipment.json");
    equipment << "{\"inventory\":" << inventory << ",\"equipped_item\":" << equipped;
    if (equipped) {
      equipment << ",\"unit_type\":" << d.get(equipped) << ",\"class_id\":" << d.get(equipped + 4)
                << ",\"unit_id\":" << d.get(equipped + 0x0C) << ",\"mode\":" << d.get(equipped + 0x10)
                << ",\"item_data\":" << equippedData << ",\"quality\":" << d.get(equippedData)
                << ",\"item_flags\":" << d.get(equippedData + 0x18) << ",\"file_index\":" << d.get(equippedData + 0x28)
                << ",\"item_level\":" << d.get(equippedData + 0x2C) << ",\"format\":" << d.get<std::uint16_t>(equippedData + 0x30)
                << ",\"body_loc\":" << static_cast<unsigned>(d.get<BYTE>(equippedData + 0x44));
    }
    equipment << "}"; equipment.close();
    std::ofstream hits(output / "damage.csv");
    hits << "frame,attacker,victim,hit_flags,result_flags,physical_fixed,fire_fixed,total_fixed,missile_seed_after_low,missile_seed_after_high\n";
    d.observedEntry = damageEntry;
    d.observeOnce = [&](CONTEXT c) {
      observedDamageAttacker = c.Edx; observedDamageTarget = d.get(c.Esp + 4); U damage = d.get(c.Esp + 8);
      require(c.Ecx == game, "Damage event from another game");
      damageFire = d.get(damage + 0x10); damageTotal = d.get(damage + 0x4C); damageHits++;
      hits << d.get(game + 0xA8) << ',' << c.Edx << ',' << observedDamageTarget << ',' << d.get(damage) << ',' << d.get<std::uint16_t>(damage + 4)
           << ',' << d.get(damage + 8) << ',' << damageFire << ',' << damageTotal;
      hits << ",0,0\n"; hits.flush();
    };
    d.arm(damageEntry);
    // Bash is a weapon attack and uses Attack1 (A1), which is mode 7 in the
    // 1.10f player-mode table. Mode 4 is GetHit: it can change the animation
    // state but has no attack callback and therefore cannot settle damage.
    // Allow re-entry for this isolated probe; the live character may still
    // carry a previous used-skill pointer after a menu transition.
    d.call(original, returnTo, cast, game, player, {skill,7,point.first,point.second,1});
    castStarted = d.get(player + 0x10) == 7; require(castStarted, "Bash Attack1 mode was not entered");
    std::ofstream frames(output / "frames.csv");
    frames << "step,frame,player_mode,mana_fixed,target_hp_fixed,target_x,target_y\n";
    for (U step = 0; step <= 80; step++) {
      U now = d.get(game + 0xA8), mana = stat(player,8), hp = stat(target,6), count = 0, mid = 0, mx = 0, my = 0;
      U seedLow = 0, seedHigh = 0, fireMin = 0, fireMax = 0;
      lowestMana = (std::min)(lowestMana,mana); lastHP = hp; lastFrame = now;
      std::set<U> seen;
      for (U bin = 0; bin < 128; bin++) for (U missile = d.get(game + 0x1120 + 4 * 128 * 4 + bin * 4); missile; missile = d.get(missile + 0xE4)) {
        require(seen.insert(missile).second && seen.size() < 1024, "Invalid missile chain");
        U data = d.get(missile + 0x14);
        if (d.get(missile) == 3 && data && d.get(data + 0x18) == 0 && d.get(data + 0x1C) == d.get(player + 0xC) && d.get<std::uint16_t>(data + 0xA) == 126) {
          count++; auto mpos = position(d,missile); mid = d.get(missile + 0xC); mx = mpos.first; my = mpos.second;
          observedMissile = missile; seedLow = d.get(missile + 0x20); seedHigh = d.get(missile + 0x24);
          fireMin = stat(missile,48); fireMax = stat(missile,49);
        }
      }
      missilePeak = (std::max)(missilePeak,count); auto targetPos = position(d,target);
      frames << step << ',' << now << ',' << d.get(player + 0x10) << ',' << mana << ',' << hp << ',' << targetPos.first << ',' << targetPos.second << '\n'; frames.flush();
      if (hp < initialHP) targetHit = true;
      if (targetPos != point) {
        require(targetHit, "Target moved before Bash damage");
        targetMoved = true;
      }
      require(now == firstFrame + step, "Non-contiguous authoritative frames");
      if (step == 80) break;
      // Get-hit/end-animation may schedule another think event. Keep only
      // this disposable target stationary throughout the bounded experiment.
      d.call(original, returnTo, deleteEvent, game, target, {2,0});
      d.call(original, returnTo, deleteEvent, game, target, {3,0});
      d.arm(returnTo); d.set(original); d.resume(); CONTEXT returned = d.waitBreakpoint(returnTo,3000);
      require(d.get(game + 0xA8) == now + 1, "Update skipped a frame");
      d.arm(entry); d.set(returned); d.resume(); original = d.waitBreakpoint(entry,3000);
      require(original.Ecx == game && d.get(original.Esp) == returnTo, "Update context changed");
    }
    require(castStarted && lowestMana < initialMana, "Missing cast/mana evidence");
    require(damageHits == 1 && observedDamageTarget == target && observedDamageAttacker == expectedPlayer, "Missing player-to-fixture damage event");
    require(damageTotal > 0 && initialHP - lastHP == damageTotal, "Damage/HP settlement mismatch");
    success = true;
  } catch (const std::exception& error) { std::cerr << error.what() << '\n'; }
  bool cleaned = d.cleanup();
  auto save = [&]() {
    if (!outputCreated) return; // never overwrite a previous run on rejected input
    std::ofstream summary(output / "summary.json");
    summary << "{\"status\":\"" << (success && cleaned ? "passed" : "failed") << "\",\"cast_started\":" << (castStarted ? "true" : "false")
      << ",\"first_frame\":" << firstFrame << ",\"last_frame\":" << lastFrame << ",\"target_hp_before_fixed\":" << initialHP << ",\"target_hp_after_fixed\":" << lastHP
      << ",\"mana_before_fixed\":" << initialMana << ",\"lowest_mana_fixed\":" << lowestMana << ",\"owned_barb-bash_peak\":" << missilePeak << ",\"damage_events\":" << damageHits
      << ",\"damage_fire_fixed\":" << damageFire << ",\"damage_total_fixed\":" << damageTotal << ",\"detached\":" << (d.detached ? "true" : "false")
      << ",\"cleanup_confirmed\":" << (cleaned ? "true" : "false") << ",\"cleanup_attention_required\":" << (cleaned ? "false" : "true")
      << ",\"target_binding\":\"coordinate_only\",\"target_moved_after_hit\":" << (targetMoved ? "true" : "false")
      << ",\"scope\":\"one original Bash level-one coordinate-targeted A1 mode; damage event and HP settlement are asserted, entity target binding is not; no port or visual parity claim\"}";
  };
  save(); while (d.attached && !cleaned) { Sleep(1000); cleaned = d.cleanup(); } save();
  return success && cleaned ? 0 : 1;
}

