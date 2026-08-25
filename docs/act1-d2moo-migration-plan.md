# Act 1 D2MOO migration plan

## Goal

Make Act 1 playable from a new character through Andariel while preserving
native D2 save flags, map topology, object state, quest rewards and level
transitions. Native behavior is taken from `F:/3rd_src/D2MOO/source`; Java
DRLG support in `D2MOO_JAVA` is reused where it is already verified.

## Scope and ownership

- This plan owns quests, Act 1 NPC/objects, portals, room population and map
  persistence.
- Combat formulas, skill damage, missiles and `LootManager` remain owned by
  the combat task. Quest code may subscribe to `DeathEvent` but must not
  change how combat produces it.
- Shared files such as `GameScreen`, `ServerEntityFactory`, `Map` and server
  bootstrap code receive only minimal registration changes.
- Every completed phase requires focused tests, `git diff --check`, a commit
  and a push to the current remote branch.

## Phase 1: native quest foundation and A1Q1 Den of Evil

Sources:

- `D2Game/src/QUESTS/Quests.cpp`
- `D2Game/src/QUESTS/ACT1/A1Q1.cpp`
- `D2Common/src/D2QuestRecord.cpp`

Work:

- Implement the native 16-bit quest record flags per player and difficulty.
- Dispatch level change, monster killed, NPC activation and object interaction
  events to quest scripts.
- Register the quest system in local and headless authoritative worlds.
- Implement A1Q1 state progression: Akara start, leave town, enter the Den,
  remaining-monster progress, objective completion, reward pending, Akara
  reward and one skill point.
- Persist the result through the existing D2S quest record.

Acceptance:

- Separate players and difficulties never share transient quest state.
- Existing D2S quest bits round-trip unchanged.
- Clearing the Den sets `PRIMARYGOALDONE` and `REWARDPENDING`.
- Akara reward sets `REWARDGRANTED`, clears intermediate flags and grants one
  skill point exactly once.

## Phase 2: native map objects

Sources: `D2Game/src/OBJECTS/{Objects,ObjMode,ObjRgn,ObjEval}.cpp`.

Complete doors, ordinary/preset/special chests, shrines, wells, breakables,
traps and quest objects. Replace no-op `OperateFn` branches incrementally and
persist opened/activated modes when rooms unload and reload.

## Phase 3: A1Q2 and Act 1 mercenary entry

Implement Blood Raven quest eligibility, fixed boss completion, Kashya dialog,
the free rogue reward and the interface to hiring/resurrection. Mercenary
combat remains behind the combat-task boundary.

## Phase 4: Act 1 NPC services

Wire Akara, Kashya, Charsi, Gheed and Warriv dialogs to quest state, vendor
inventory, gamble, repair, hire and travel. Use existing Riiablo UI because
the D2MOO tree does not contain the complete D2Client implementation.

## Phase 5: A1Q6 and Act completion

Implement Andariel quest completion, post-kill state, Warriv travel, Act 1
completion save bits and the transition to Act 2. This establishes the minimum
complete Act 1 playthrough before optional quests are finished.

## Phase 6: remaining Act 1 quests

Implement A1Q3 Malus, A1Q5 Countess and A1Q4 Cain in increasing dependency
order. Cain includes the scroll, cairn-stone sequence, Tristram portal, cage,
Cain town state and multiplayer eligibility.

## Phase 7: portals, drops and persistence

Complete player town portals, quest portals, quest item lifecycle, native
TreasureClass handoff, inactive-room units, unique/boss respawn rules and
object/monster persistence. Changes to combat-owned loot files require prior
coordination and a minimal event interface.

## Phase 8: population, environment and full regression

Align Act 1 room population, champion/unique packs and super-unique placement;
then add day/night, outdoor lighting and environment audio. Run an Act 1
regression from character creation through Andariel, including save/reload,
waypoints, caves, quest rewards and collision boundaries.

## Progress

- [ ] Phase 1: native quest foundation and A1Q1 (in progress)
  - [x] Native 16-bit save-record flags and A1Q1 record transitions.
  - [x] Per-character/per-difficulty storage through `CharData`.
  - [x] Authoritative local and `server:d2gs` system registration.
  - [x] Den live-monster counting, including revived Fallen.
  - [x] Akara start/reward and one-time skill-point grant.
  - [x] Focused pure-state and headless ECS regression tests.
  - [ ] Native scroll-message callback before applying the Akara reward.
  - [ ] Native party eligibility/completion propagation for multiplayer.
  - [ ] End-to-end D2S save/reload and in-game Den validation.
- [ ] Phase 2: native map objects
- [ ] Phase 3: A1Q2 and mercenary entry
- [ ] Phase 4: Act 1 NPC services
- [ ] Phase 5: A1Q6 and Act completion
- [ ] Phase 6: remaining Act 1 quests
- [ ] Phase 7: portals, drops and persistence
- [ ] Phase 8: population, environment and regression
