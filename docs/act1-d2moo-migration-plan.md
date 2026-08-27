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
  - [x] Native Akara message selection and scroll completion before state/reward changes.
  - [x] Full D2S write/read/load round-trip for all three difficulty records.
  - [ ] Native party eligibility/completion propagation for multiplayer.
  - [ ] End-to-end in-game Den validation.
- [ ] Phase 2: native map objects
  - [x] D2Game OperateFn lifecycle table for Act 1 containers and doors.
  - [x] One-shot urn/barrel/corpse/rack/bookshelf state and one-way secret doors.
  - [ ] Native container drops, trap callbacks and exploding-barrel damage events.
    - [x] TreasureClassEx table, native chest selector and weighted raw roll.
    - [x] Recursive TC expansion with native Picks/NoDrop and quality inheritance.
    - [x] First-open event idempotency and authoritative object-drop entities.
    - [x] Native multiplayer NoDrop scaling from total/effective player counts.
    - [x] Same-level ECS player count wired into native NoDrop context.
    - [ ] Party-manager membership/living-state wiring and full unique/set property generation.
    - [x] Exploding barrel OperateFn=7 is separated from ordinary container drops.
    - [x] Trap trigger context event exposes player/object/operate/trap type.
    - [x] InitFn 2/3/57 trapped urn/chest InteractType and lock-bit initialization.
    - [x] Persistent InteractType and trapped-container event dispatch.
    - [x] Locked-chest inventory key consumption and Assassin bypass.
    - [ ] Trap projectile/damage callback.
  - [ ] Shrine and well effects/cooldowns.
    - [x] Shrines.txt table and native effect-class/LevelMin/preset selection.
    - [x] Shrine health/mana effects (Code 1-5) and 1200-frame/minute reset.
    - [x] Well health/mana/stamina charges, visual modes and regeneration.
    - [x] Structured event boundary for combat/item/portal shrine effects.
    - [x] Synchronous well cleanse/pet-heal request and charge acknowledgement boundary.
    - [ ] Shrine buffs, portal/gem/monster effects and well status/pet cleansing.
    - [ ] Quest-object handlers and room unload/reload integration tests.
    - [x] Act 1 quest-object classifier for tome, cairns, cage, tree, Malus and Countess emitters.
    - [x] Synchronous quest acceptance event and persistent one-shot lifecycle.
    - [x] Room entity recreation restores mode and disables exhausted interaction.
    - [x] Tower Tome is routed to A1Q5 instead of the ordinary chest drop path.
    - [x] A1Q3 Malus level gate, `mdh` quest-item drop and pickup event bridge.
    - [x] A1Q3 Charsi Malus turn-in and asynchronous imbue reward boundary.
    - [x] A1Q4 native record/stone-order foundation and Inifuss/Cairn/Gibbet event boundary.
    - [x] A1Q4 Inifuss tree creates the `skb` quest-item through the authoritative drop adapter.
    - [x] A1Q4 Akara message 112 atomically converts `skb` to `dkb` without changing its inventory slot.
    - [x] A1Q4 uses one game-scoped, seed-stable permutation of native stones 17..21; wrong stones do not advance.
    - [x] The fifth stone consumes `dkb` only after a permanent quest warp to Tristram was created.
    - [x] Runtime Objects.txt-class synchronization lets multiplayer clients rebuild D2Game-created portal objects.
    - [x] Cain's gibbet creates Tristram Cain before setting the player's primary-goal/reward-pending flags.
    - [x] Cain1 is transferred after the native delay to Cain5 at object 385 in Rogue Encampment.
    - [x] Cain release is propagated to every eligible player currently in Tristram.
    - [ ] PartyManager registration/state updates for eligible Act I party members outside Tristram.
    - [ ] A1Q3 imbue item transformation and A1Q5 quest script.
- [ ] Phase 3: A1Q2 and mercenary entry
- [ ] Phase 4: Act 1 NPC services (local single-player loop complete)
  - [x] Vendor stock receives persistent item ids and valid in-store flags.
  - [x] Buy/sell transaction loop, inventory placement and live gold display.
  - [x] Single-item and repair-all actions with durability and gold updates.
  - [x] Gheed gambling stock generation and purchase handoff.
  - [x] Paid Kashya Rogue creation reuses the A1Q2 mercenary entity path.
  - [x] Native `Items.txt`/`Npc.txt` transaction-cost fields and 1024-scale price calculation.
  - [ ] Hire-selection panel, resurrection dialog and restored-save entity recreation.
  - [x] Authoritative multiplayer NPC-service request/result/stock protocol and D2GS validation boundary.
    - [x] Server-owned vendor stock snapshots and atomic BUY/SELL mutation.
      - [x] Network VendorPanel consumes server stock/prices and commits wallet/item changes on acknowledgement.
      - [ ] Request idempotency cache and reconnect/session recovery.
- [ ] Phase 5: A1Q6 and Act completion
- [ ] Phase 6: remaining Act 1 quests
- [ ] Phase 7: portals, drops and persistence
- [ ] Phase 8: population, environment and regression
