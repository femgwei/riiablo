# HellMeteor Fn033 — dedicated decisions and projectile creation, overall PARTIAL

Reference: D2MOO `D2Game/src/AI/AiThink.cpp`, `AITHINK_Fn033_HellMeteor`
(0x6FCD7EB0), `ITEMS_RollLimitedRandomNumber` -> `SEED_RollLimitedRandomNumber`,
and `AiTactics.cpp` `AITACTICS_UseSkill` (failure waits ten frames).
The real `hellmeteor` row now resolves to HellMeteor instead of GenericMonster.

Skill1 availability precedes the slot0 chance roll. A success consumes X then Y
limited draws using twice slot2 as the bound, subtracts slot2 from each and adds
the integer self coordinate. The positive bound is exclusive; zero consumes no
coordinate draws. The cast carries an invalid unit target and the chosen point,
whether or not an enemy exists. Failure waits slot1. No chase or basic attack is
introduced; natural probabilities and tables are unchanged.

The first natural run exposed a second defect: ServerSkillSystem rejected
`hellmeteordown` because its SrvHit13 is not the Sorceress Meteor SrvHit14 center.
`SkillSor.cpp` SrvDo028 (0x6FD174E0) calls `Skills.cpp`
`D2GAME_CreateMissile_6FD115E0`: flags=1, owner/skill/level, selected world point.
The repair creates this direct projectile through the existing factory and
damage snapshot path, without marking it as a delayed Meteor center. Existing
SrvHit13 collision/damage handling is reused.

## Directed evidence

`baseline-tests.log/xml`: all four new AI tests fail before implementation.
`fixed-tests.log`: the AI and adjacent regression suite pass after the AI repair.
`skill-baseline-tests.log/xml`: the new projectile test fails (expected one,
observed zero), while the two existing Sorceress Meteor tests pass. An initial
test typo (`owner` instead of `ownerId`) caused a compile error; its separate
`skill-test-compile-failure.log` is retained and is not behavioral evidence.

`final-tests.log` and final XML record 56 passing tests, zero failures/errors/
skips: HellMeteor 4, Spirit 6, Minion 5, BaalMinion 3 (wildcard), DeathMauler 7,
Act1NativeAi 26, SorceressMeteor 3 and MeteorGoldenDamage 2. The AI tests cover
three difficulties, four random bounds (including zero and a non-power-of-two),
four successive casts with an independent D2Seed expectation, integer self
coordinates, absent/present targets, busy action retention, death and exact
slot1 wait boundaries. The projectile contract checks the real rows, a null
target, chosen spawn coordinates, owner and absence of the Meteor center flag.
The directed projectile test explicitly dispatches SkillDo to isolate SrvDo028;
it is not counted as a natural scene or natural damage success.

## Natural evidence

Real 1.10f MPQ, seed1, 500 ticks, three difficulties, near/far: six repaired
executions plus six independent repeats. Raw selected JSONL are byte identical
per difficulty; `verification.log` records SHA256 and event counts. Normal has
14 casts per scene, Nightmare 15, Hell 23; every selected event is skill213,
target=-1, SrvDo28. No movement, fallback, warnings or errors in the final twelve.
Hell near records one actual damage event of 26 HP, identically on repeat.
Normal/Nightmare near and every far scene have zero damage and are not accepted
for damage. All scenes remain BASELINE_ONLY with no dedicated behavior oracle.

`d0-baseline*` preserve GenericMonster fallback; `d0-first*` preserve the initial
AI-only repair with fourteen downstream projectile rejection warnings per scene.
Those exploratory executions are excluded from the final twelve. Each archived
raw journal has 1180 entries, of which 1178 NOT_RUN entries are excluded from
execution and pass counts. Hell repeat initially failed compilation in another
chat's MercenaryManager edits; `d2-repeat-compile-failure.log` is retained. The
retry uses arena output prefix `20261016-hellmeteor-retry`. No mercenary/path
files were changed by this task. The premature verifier attempt is retained as
`verification-incomplete.log`; its missing Hell repeat is not a behavior failure.

## Limits and reproduction

Overall remains PARTIAL: unit creation/combat RNG is not merged with the AI
stream, shared global scheduling is not ported, and native SrvDo028 room/mask5
collision validation is not established by these empty headless scenes. Exact
terrain boundaries, real GL/TCP presentation and whole-directory regression
are not accepted. Zero-damage scenes need further investigation, without
changing their probabilities or forcing SkillDo in the arena.

From the repository root, set D2_HOME to the 1.10f installation. The init script
isolates buildDir, JavaCompile destinations, sourceSet destinations/classes and
project cache. Use unused output/evidence names; existing runs are protected.

```powershell
./gradlew.bat --project-cache-dir build/hellmeteor-gradle-cache -I docs/ai-arena/20261016-hellmeteor-seed1/isolated.init.gradle :core:test --tests '*HellMeteorAiIntegrationTest' --tests '*SorceressMeteorIntegrationTest' --tests '*MeteorGoldenDamageTest' --tests '*SpiritAiIntegrationTest' --tests '*MinionAiIntegrationTest' --tests '*DeathMaulerAiIntegrationTest' --tests '*Act1NativeAiIntegrationTest' --console=plain --max-workers=1
foreach ($pass in @('fixed-first','repeat')) {
  foreach ($difficulty in 0..2) {
    ./docs/ai-arena/20261016-hellmeteor-seed1/run-arena.ps1 -Pass $pass -Difficulty $difficulty -RunName hellmeteor-new -Evidence build/hellmeteor-new-evidence
  }
}
./docs/ai-arena/20261016-hellmeteor-seed1/verify.ps1
```

For a new evidence directory create it first; copy baseline JSONL and final test
XML there and pass that directory to verify.ps1. The verifier checks observed
results, not a complete native behavior oracle.
