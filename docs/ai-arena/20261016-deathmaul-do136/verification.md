# DeathMaul SrvDo136 — 2026-10-16

Server effect acceptance passes in the scoped headless fixtures; overall native
alignment remains PARTIAL. CltDo76/trails, real GL/TCP, terrain, multi-seed and
three-difficulty natural arenas, native unit RNG and global scheduling are not
claimed complete.

## Native behavior implemented

Sources in D2MOO:

- `D2Game/src/SKILLS/SkillDruid.cpp`, `SKILLS_SrvDo136_DeathMaul` (0x6FD01F10).
- `D2Game/src/SKILLS/Skills.cpp`, `D2GAME_CreateMissile_6FD115E0`: zero supplied
  coordinates resolve to the target coordinates; reject (0,0) and distance >100.
- `D2Common/src/Units/Units.cpp`, `UNITS_GetDistanceToCoordinates`: integer
  `max(abs(dx),abs(dy)) + min(abs(dx),abs(dy))/2`.
- `D2Game/src/MISSILES/Missiles.cpp`: creation stores original Range-Activate.
- `D2Game/src/MISSILES/MissMode.cpp`, `MISSMODE_HandleMissileCollision`:
  decrement CurrentFrame first; expiry at <=0 precedes unit contact; collision
  stays disabled while CurrentFrame exceeds the activation countdown.

The real MPQ has skill308 / Do136 / missile502 `death mauler`, stationary Vel0,
Range60, Activate30, NextHit1/NextDelay10, CollideKill0, calc1=96. All six monster
rows use the previously ported 35-point MonSeq with action at point13.

The missile now spawns at the live target's integer coordinates (coordinate
fallback uses the cast target vector). It captures Skills.txt physical damage
at the actual MonStats skill level, not the owner's weapon or monster A2
damage. Lifetime preserves native truncation: distance0 ->32, distance1 ->32,
distance9 ->52, distance10 ->50; distance12 ->60. Do136 changes Total/Current
frames but does not rewrite the original activation countdown (60-30=30).
The stationary missile may hit again after the target's shared JUSTHIT state
expires; it is neither attached nor converted to a persistent field. Map/room
collision remains enabled. Expiry performs native null-hit/destruction without
an extra frame-zero unit hit. Pool reset clears the new marker. The root client
animation rate resolves calc1=96; this does not implement the trail visual chain.
Actioneer delegates Do136 only now that ServerSkillSystem implements it.

## Regression and integration evidence

`baseline.log/xml` are the pre-change animation-driven two-test regression:
the generic path already created a missile, but at caster (10,10), not target
(21,12), and kept 60 frames instead of the distance10 expectation50. Both tests
failed. No SkillDo injection is used: real M5A2HTH data and AnimStepper's native
MonSeq action invoke Actioneer and ServerSkillSystem.

Final `fixed.log` and ten root `TEST-*.xml` files record **92** passing tests,
zero failures/errors/skips: integration4, arithmetic/NextDelay3, real data1,
MonSeq4, DeathMauler AI7, client animation8, Inferno14, Act1 native26, missile
policy18, lifecycle7. `repeat.log` and `repeat-xml/` preserve an independent
cleanTest rerun of all92.

The integration fixture checks: one root at live target after point13; owner,
row, skill, level, snapshot18..28 at level3 and animation rate96; no damage
before CurrentFrame30; hits at elapsed20/30/40 and expiry at50; actual physical
HP loss for all six real monster rows; no extra frame-zero hit; move-away,
creation failure/no retry, invalid coordinates/distance, and physical immunity.
Huge owner weapon/fire stats cannot contaminate the skill packet. A separate
policy test proves different mauls share target NextDelay. Existing projectile
deduplication, native map/lifetime and Inferno tests remain passing.

Early failed builds are retained and not counted as acceptance: `fixed-first.log`
encountered a concurrent cursor-source edit; `fixed-second.log` and
`fixed-tests-six-arena.log` encountered class outputs disappearing through the
shared Gradle project cache. `isolated-first.log` passed the initial focused
tests. The final init gives every module unique output directories, and final
commands also use `--project-cache-dir build/deathmaul-do136-gradle-cache`.
`cache-isolated-tests-six.log` passed the92 core tests then exposed a missing
Velocity import in the new arena oracle, which was repaired.

`fixed-tests-full.log` then passed core tests but all1180 scenes failed world
initialization because the concurrently updated WarpInteractor requires a
TownPortalRegistry. `initial-arena-runtime-errors.jsonl` and
`initial-runtime-error.log` retain that failure. The arena now registers the
real registry, matching server wiring; it does not disable WarpInteractor.
Only `fixed.log` and `repeat.log` establish final arena acceptance.

## Natural arena acceptance

The six added `deathmaul-cycle` scenes start at distance10 with the unchanged
production AI, probabilities, seed1, Normal and500 simulation ticks. They do
not inject actions, teleport units, alter rolls or disable collision.
The subject-only oracle requires the actual A2 MonSeq point13 action, a real
owner/skill308/row502 missile, correct level/lifetime/target position, a positive
inactive interval with unchanged HP, stationary position, multiple physical-only
MISSILE DamageEvents from that same entity after activation and >=10 frames
apart, matching actual HP deduction, and lifetime expiry/removal. Ordinary
melee or an unrelated projectile cannot satisfy this gate.

All six pass. For deathmauler1, for example, root creation at tick36 has
lifetime50, and removal at tick86 confirms three hits totaling45 and actual
HP1000000 ->999955. The full scene logs are retained in `logs.zip`.

Both complete runs contain **557 rows / 1180 scenes**, with byte-identical JSONL
SHA256 `417D516E82CCEF1E00C198EF8C4589E7D33E64ABB1C1FB6795366AF14EDAFBC2`.
ACTION_OBSERVED558 / AI_FALLBACK316 / MOVEMENT_ONLY7 / NO_ACTION188 /
TERMINAL_OBSERVED4 / WARNING107; no runtime/error-log, NOT_RUN or untriggered
special results. All **66** dedicated assertions pass (60 existing +6 new).

`changes.json` compares all fields against the preceding MonSeq batch by
monster/scenario key. Aside from the six new scenes, only deathmauler3–6 far
change: WARNING ->ACTION_OBSERVED, warnings14 ->0, damageEvents0 ->35/35/36/36,
damage0 ->791/931/1099/523. Actions stay14. All other prior scene fields are
unchanged, including six melee observations and deathmauler1/2 far NO_ACTION.
Warnings disappear because Do136 now has an actual server effect, not because
diagnostics or the oracle were relaxed. Fallback remains42 AI names/158 rows.

Reproduce from repository root with real resources (choose unused output dirs):

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
./gradlew.bat --project-cache-dir build/deathmaul-do136-gradle-cache -I docs/ai-arena/20261016-deathmaul-do136/isolated.init.gradle :core:cleanTest :core:test --tests '*DeathMaul*Test' --tests '*DeathMaulerAiIntegrationTest' --tests '*AnimationStepperTest' --tests '*MonInfernoIntegrationTest' --tests '*Act1NativeAiIntegrationTest' --tests '*MissileNativePolicyTest' --tests '*SpecialMissileLifecycleTest' :server:d2gs:monsterAiArena -ParenaOutput=server/d2gs/build/monster-ai-arena/20261016-deathmaul-new --console=plain --max-workers=1
./docs/ai-arena/20261016-deathmaul-do136/verify.ps1
```

Concurrent path, inventory, identify and portal production edits are excluded
from this commit. Prior wolf landing collision limitations remain unresolved.
