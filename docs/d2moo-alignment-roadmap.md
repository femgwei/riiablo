# D2MOO behavior-alignment roadmap

Baseline: `F:/3rd_src/D2MOO` (Diablo II 1.10f reverse-engineered behavior).

This document replaces completion claims based only on the existence of a
class, directory, or method name. A module is **aligned** only when its Java
entry point, data-table selection, fixed-point units, RNG consumption, state
transitions, side effects, and failure cases have focused parity tests against
the corresponding D2MOO call chain.

## Status vocabulary

- **ALIGNED**: the inspected call chain has native behavior and parity tests.
- **PARTIAL**: a useful loop exists, but native branches or side effects are missing.
- **CUSTOM**: the active path contains invented formulas, approximations, or hard limits.
- **MISSING**: D2MOO behavior has no active Java implementation.
- **ARCHITECTURAL**: Java cannot share the DLL implementation, so observable
  behavior rather than internal structure must be aligned.

## Current audit matrix

| Priority | Module | Current status | Verified divergence / remaining work | D2MOO baseline |
|---|---|---|---|---|
| P0 | Excel tables, fixed-point stats, RNG/seed | PARTIAL | Several runtime systems still substitute constants when table data is available; seed ownership and RNG consumption are not centralized | `D2Common/DataTbls`, `D2Common/Units`, `D2Common/Items` |
| P0 | Monster construction and level stats | PARTIAL | Native difficulty/area level, `MonLvl` ratios, spawn HP/XP player-count bonuses and base combat/resistance stats are initialized; unit-seed RNG, all monster skills, unique HP/affix modifiers and classic scaling remain incomplete | `D2Game/MONSTER/Monster.cpp`, `D2Common/DataTbls/MonsterTbls.cpp` |
| P0 | Experience, level-up and party XP | PARTIAL | Native `Experience.txt`, level-difference/`ExpRatio` ordering and item XP are aligned; monster spawn stats, same-level party awards and mercenary persistence remain incomplete | `D2Game/UNIT/SUnitDmg.cpp:2908-3168`, `D2Game/PLAYER/PlayerStats.cpp` |
| P0 | Unit ownership, room/level membership | PARTIAL | ECS ownership and `MapWrapper` cover common cases; native owner chains, room activation and same-level party iteration are incomplete | `D2Game/UNIT`, `D2Common/Units`, `D2Common/Dungeon` |
| P1 | Damage, hit result and mitigation | CUSTOM | Active code still labels ToHit, pierce, leech and several damage paths simplified; native `D2DamageStrc` ordering/flags are not represented end to end | `D2Game/UNIT/SUnitDmg.cpp` |
| P1 | States, poison/cold/stun and regeneration | PARTIAL | State containers exist, but state groups, periodic damage, cleanse and several stat-list effects are approximations | `D2Game/UNIT/SUnitStates.cpp`, `D2Game/UNIT/SUnitDmg.cpp`, `D2Common/DataTbls/StatesTbls.cpp` |
| P1 | Skills, missiles and auras | PARTIAL | Server authority and multiple skill functions exist; synergy/cooldown/target branches and many `srvstfunc/srvdofunc` handlers remain generic | `D2Game/SKILLS`, `D2Game/MISSILES` |
| P1 | Equipment, inventory and item movement | PARTIAL | Authoritative move validation/revisions exist; some body/belt/location semantics and full native item-stat aggregation remain incomplete | `D2Game/INVENTORY`, `D2Common/Items` |
| P1 | Item generation, affixes and quality | PARTIAL | Basic normal/magic/rare/set/unique paths exist; durability initialization, ethereal/socket/property functions and several eligibility branches remain TODO/fallback | `D2Game/ITEMS/Items.cpp`, `D2Common/Items` |
| P1 | TreasureClass and ground drops | PARTIAL | Root TC, Picks, NoDrop/PlayersX and six-entity cap are aligned; fallback procedural loot, full unique/set property generation and complete native item construction remain | `D2Game/ITEMS/Items.cpp:2064-2595` |
| P2 | Monster AI and packs | CUSTOM | Many named AIs exist, but explicit approximations, generic fallback AI, incomplete minion commands and missing AI-state tests remain | `D2Game/AI/AiThink.cpp`, `AiTactics.cpp`, `AiStates.cpp` |
| P2 | Objects, chests, traps, shrines and wells | PARTIAL | First-use authority and major Act 1 object classes exist; trap damage/projectiles, shrine effect classes, party checks and some persistence are incomplete | `D2Game/OBJECTS` |
| P2 | Act 1 quests and transitions | PARTIAL | Six quest records and core event bridges exist; multiplayer eligibility, all native sequence/filter callbacks, dialog variants and persistence regression are not complete | `D2Game/QUESTS/ACT1` |
| P2 | NPC vendors, gamble, repair and hire | PARTIAL | Local/network transaction loops and idempotency exist; native stock generation, all repair-price branches, hire/resurrection and reconnect recovery remain | `D2Game/PLAYER/PlrTrade.cpp`, `D2Game/ITEMS`, quest/NPC callbacks |
| P2 | Party, hostility and player trade | PARTIAL | Managers/data structures exist; same-level/living iteration is not wired consistently and detailed trade validation is still TODO | `D2Game/UNIT/Party.cpp`, `D2Game/PLAYER/PlrTrade.cpp` |
| P2 | Mercenaries and pets | CUSTOM | Creation/reward paths exist; stats, experience, equipment, death/resurrection and AI ownership are incomplete or simplified | `D2Game/MONSTER/Monster.cpp`, `D2Game/UNIT/SUnitDmg.cpp`, pet/AI code |
| P2 | Horadric Cube | CUSTOM | Several recipes/actions are approximated and some lookup identity uses string hashes | `D2Game/ITEMS/CubeMain.cpp`, `D2Common/DataTbls` |
| P3 | Act 1 DRLG topology and warps | PARTIAL | Major outdoor topology/warp fixes are present; native dirt paths, complete RoomTile/TileSub and room lifecycle remain partial | `D2Common/Drlg` |
| P3 | Collision and pathfinding | PARTIAL | Map collision works for the playable loop; ray tests, dynamic collision, unit footprints and several path types are simplified | `D2Common/Path`, `D2Common/Collision` |
| P3 | Acts 2-5 maps/quests/population | MISSING/PARTIAL | Constants and partial generators exist but have not received the Act 1 parity audit | `D2Common/Drlg`, `D2Game/QUESTS/ACT2-5` |
| P3 | D2S/item serialization and validation | PARTIAL | Reading/writing and new-character creation work for common cases; version validation, remaining sections and exact save masks need native fixtures | `D2Game/PLAYER/PlrSave.cpp`, `PlrSave2.cpp`, `D2Common` item codecs |
| P3 | Multiplayer transport/snapshots | ARCHITECTURAL | FlatBuffers/Netty cannot be made internally identical to D2Net; authoritative ownership, ordering, idempotency and visible state must be parity-tested instead | `D2Net`, D2Game client packet handlers |
| P4 | UI, rendering, audio | ARCHITECTURAL | Riiablo uses LibGDX and D2MOO client DLL coverage is incomplete; align observable layout/timing/assets, not implementation structure | `D2Win`, `D2Gfx`, `D2CMP`, incomplete client modules |

## Execution order

1. **Foundation**: native Excel records, fixed-point conventions, RNG/seed,
   difficulty/level/player-count context and unit ownership.
2. **Progression**: monster construction, experience/level-up, party XP and
   mercenary XP.
3. **Items**: generation, affixes, quality, durability, equipment aggregation,
   TreasureClass handoff and ground lifecycle.
4. **Combat**: hit-result flags, damage ordering, mitigation, state effects,
   skills, missiles and AI decisions.
5. **World interaction**: objects, shrines, quests, NPC services, party/trade
   and pets.
6. **World generation/persistence**: remaining DRLG/room lifecycle, Acts 2-5,
   D2S validation and full regression.

## Per-module completion gate

Every module must be handled as a separate commit and pass all of the following:

1. Identify the exact D2MOO entry function and every table/flag it consumes.
2. Record current Java divergences before editing.
3. Remove active invented formulas/hard limits; compatibility fallback must be
   explicit, logged, and unreachable with valid native data.
4. Add pure boundary tests plus at least one real-table or ECS integration test.
5. Run dependent module tests and server compilation.
6. Check the diff for unrelated module changes, commit, and push.
7. Update this matrix only for the call chain actually verified; never mark an
   entire directory aligned from one passing test.

## First implementation batch

The first batch is the native progression data path:

- load `Experience.txt` instead of using an invented threshold/ratio table;
- port `DATATBLS_GetExpRatio` semantics;
- make `SUNITDMG_ComputeExperienceGain` a pure, directly testable rule;
- apply `STAT_ITEM_ADDEXPERIENCE` in the authoritative path;
- preserve the already-fixed native level-up stat/skill point behavior;
- leave party and mercenary distribution marked partial until authoritative
  member entities and mercenary save state are connected.

Progress:

- [x] Native `Experience.txt` schema and runtime loading.
- [x] Native per-class thresholds, maximum-level metadata and `ExpRatio` lookup.
- [x] Pure `SUNITDMG_ComputeExperienceGain` ordering and boundary tests.
- [x] Authoritative `STAT_ITEM_ADDEXPERIENCE` application for the killer.
- [x] Monster `STAT_LEVEL`/`STAT_EXPERIENCE` are initialized at spawn and the
  authoritative death/loot paths consume those unit stats rather than raw
  `MonStats` columns.
- [ ] Same-level living party-member iteration and awards.
- [ ] Mercenary experience persistence and level-up.
