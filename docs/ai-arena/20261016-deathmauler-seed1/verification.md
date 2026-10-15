# DeathMauler Fn130 decision port — 2026-10-16

Source: D2MOO `D2Game/src/AI/AiThink.cpp`,
`AITHINK_Fn130_DeathMauler` (0x6FCEEAD0). Parameter slots 0–3 are melee
attack chance, approach chance, strict shooting range, and shooting chance.
Combat attempts A1 and otherwise waits fifteen frames. Noncombat first attempts
Skill1 when distance is strictly below range, then approach, then fifteen-frame
idle. A rejected skill attempt returns without substituting movement or attack.

All six deathmauler1–6 data rows are covered by dedicated selection tests.
Skill1 is Death Maul, ID 308, srvdofunc 136. The shared configured-mode resolution
is preserved; this decision port does not implement Death Maul's MonSeq or Do136.
Natural ranged calls may still warn and must not be counted as projectile,
collision or damage acceptance. No warnings or production probabilities were
suppressed or changed.

The initial shared-output baseline returned a failing test result but its XML
was unavailable while concurrent builds modified shared outputs; the subsequent shared-output repair run
failed compiling tests with missing RiiabloTest classes. Both logs are retained
as `shared-baseline.log` and `shared-fixed-failure.log`; neither establishes
behavioral acceptance. Early isolation attempts still had class output bindings
to shared directories; initialization failures are retained in
`isolation-first-failure.log` and `isolation-second-failure.log`. A first
class-absent run did execute seven failing tests (`baseline-first.log/xml`),
but was followed by these isolation problems.

The final `isolated.init.gradle` explicitly sets build directories, Java class
destinations and source-set class outputs under build/deathmauler-isolated.
Its `deathMaulerBaseline` task copies compiled main classes into a separate
snapshot excluding only DeathMauler.class, and runs tests against that snapshot
plus test classes/resources/dependencies. The normal main output is unchanged.
`baseline.log/xml` retain this final controlled class-absent run;
`fixed-tests.log` and final XML retain the implementation run. All 43 selected
tests passed: DeathMauler 7, DoomKnight 5, VileDog 5, Act1NativeAi 26, with zero
failures, errors or skips.

The new tests cover real rows, three-difficulty melee A1 precedence, exact
fifteen-frame melee/ranged waits, ranged skill-before-approach, strict range
equality, failed shooting roll, rejected cast, busy preservation, and no target.
The equality test supplies native combat/distance inputs directly. Fixture
probabilities isolate branches and are not natural-scene evidence.

`verify.ps1` checks the complete real-MPQ Normal/seed-1/500-tick arena, six
natural melee attack/damage observations, class selection for all twelve
near/far scenes, preserved unsupported Do136 diagnostics when invoked, sixty
existing dedicated assertions, changes confined to DeathMauler, and an identical
independent repeat. These are baseline and decision checks, not a Death Maul
skill-effect oracle. The full JSONL files and zipped scene logs are archived.

Full directory: 557 rows / 1174 scenes. ACTION_OBSERVED 548, AI_FALLBACK 316,
MOVEMENT_ONLY 7, NO_ACTION 188, TERMINAL_OBSERVED 4, WARNING 111.
Fallback covers 42 AI names / 158 rows. Six near scenes observe ordinary melee
damage without warnings. deathmauler1/2 far remain NO_ACTION; deathmauler3–6 far
remain WARNING with skill-308 calls but zero damage events. They have not passed
Death Maul skill acceptance. No runtime/error-log, NOT_RUN or
SPECIAL_NOT_TRIGGERED results occurred; all sixty pre-existing dedicated
assertions remain passing.

Run from repository root with D2_HOME pointing to the real 1.10f install:

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
./gradlew.bat -I docs/ai-arena/20261016-deathmauler-seed1/isolated.init.gradle :core:deathMaulerBaseline --tests '*DeathMaulerAiIntegrationTest' --console=plain --max-workers=1
./gradlew.bat -I docs/ai-arena/20261016-deathmauler-seed1/isolated.init.gradle :core:test --tests '*DeathMaulerAiIntegrationTest' --tests '*DoomKnightAiIntegrationTest' --tests '*VileDogAiIntegrationTest' --tests '*Act1NativeAiIntegrationTest' --console=plain --max-workers=1
./gradlew.bat -I docs/ai-arena/20261016-deathmauler-seed1/isolated.init.gradle :server:d2gs:monsterAiArena -ParenaOutput=server/d2gs/build/monster-ai-arena/20261016-deathmauler-full --console=plain --max-workers=1
./gradlew.bat -I docs/ai-arena/20261016-deathmauler-seed1/isolated.init.gradle :server:d2gs:monsterAiArena -ParenaOutput=server/d2gs/build/monster-ai-arena/20261016-deathmauler-repeat --console=plain --max-workers=1
./docs/ai-arena/20261016-deathmauler-seed1/verify.ps1
```

Use unused arena output directories, or pass alternate paths to verify.ps1.
Live run directories contain results.jsonl; the archived repeat is named
repeat-results.jsonl. Status remains PARTIAL: Do136, corresponding MonSeq,
native RNG and global scheduling remain pending. No full-project, GL/TCP,
terrain or multi-seed/three-difficulty arena acceptance is claimed. The prior
adjacent wolf landing failure remains unresolved and is not counted as passing.
Concurrent path/object/vendor edits are excluded from this commit.
