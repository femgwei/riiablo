# D2MOO latest applicability audit — 2026-10-17

## Upstream snapshot

- `origin/master`: `0c0bbd1e6bef9181a3da1c046523af622a925416`
- non-merge commits from the agreed baseline: 101
- `origin/test_utilities`: `8390bfe02c63ed2d8a5c2719e79eb3764f0ae217`
- `origin/visitor_framework`: `14110b37faa289f9f25011c9794cbfe10791f815`
- branch-only commits: 17, with no production game logic or path algorithm changes

No newer upstream commits were found during this audit.

## Production-applicable coverage

The following upstream behavior changes have a production landing in riiablo:

- signed MonStats missile columns and signed Missiles Accel data: `codec/excel` and native table loaders;
- missile acceleration: `ServerEntityFactory`, `Missile`, `MissileCollisionSystem`, and `NativeMissileAcceleration`;
- monster owner-chain combat relations: `engine/server/party/CombatOwnership` and its six relation gates;
- target-list slot 8: `NativeTargetNode`, `AI`, and `ServerEntityFactory` NPC registration;
- quill-rat seeded spread, upgraded-missile bounds, charged-bolt lifetime, collision-size bounds,
  warp exclusion, path-step checks, velocity clamp, CRC32 and warp-attempt accounting: existing
  server/path/skill implementations with focused regression coverage;
- animation attack-rate, velocity-modifier, GetHit block-mode and monster fallthrough rules:
  existing animation/sequence gates;
- quest, scroll, item-event and skill-event safety paths: existing QuestManager,
  ItemReader/ItemGenerator and ServerSkillSystem validators;
- unique-drop limit behavior: `UniqueDropHistory` owned by the World `ItemGenerator`, including
  `nolimit` and the native 4096 identity boundary.

The runtime batch was verified by `NativeMissileAccelerationTest` (2),
`UniqueDropHistoryTest` (2), and `UniqueDropIntegrationTest` (1): 5 tests,
0 failures, 0 errors, 0 skipped. Log:
`core/build/upstream-runtime-02.log`.

## Not one-to-one production ports

The remaining upstream commits are intentionally classified as not applicable:

- 1.11+/1.13+/1.14 version gates and 1.14 address annotations;
- Win32 debugger, DLL base-address, inline-hook and native function-pair utilities;
- CMake, clang-tidy, D2.Detours and native build wiring;
- packed D2Net packet layouts and fixed-offset 0x67/0x68 protocol writes;
- native DRLG room pointer lists, event queue memory layout and packed game structs;
- Uber content that is outside the 1.10f Java runtime target;
- VisitorFramework/TestUtilities C++ modules, whose Java-usable comparison/bin-reader behavior
  is already represented by `com.d2moo.testutil`.

“Not applicable” means no equivalent production behavior is required by the current target
architecture; it does not mean the native C++ source was ignored.

## Conclusion

All upstream changes with a confirmed Java production landing have been migrated and verified at
their current integration boundaries. The audit does not claim binary or full fixed-point runtime
parity for native-only structures. The only remaining parity risks are the documented differences
between the Java ECS/FlatBuffers runtime and the original DLL memory/protocol implementation.
