# Spirit Fn097 — decision/event coverage, overall PARTIAL

Reference: D2MOO `D2Game/src/AI/AiThink.cpp`,
`AITHINK_Fn097_Spirit` (0x6FCE28A0), and `AiTactics.cpp`
`AITACTICS_IdleInNeutralMode` / `AITACTICS_Idle`.

The real `mephistospirit` row now resolves to Spirit instead of GenericMonster.
Before the first attack, noncombat decisions wait ten frames. The first melee
decision commits a per-entity flag, then starts A1. Once committed, subsequent
decisions wait fifty frames, including decisions without a target. Losing the
target or an owner warp does not rearm the attack. No random rolls, approach,
repeated attack, table changes or damage overrides are introduced.

## Directed tests

`baseline.log/xml` preserve five failures before implementation.
`fixed-tests.log` records 56 passing tests. The final suite adds an explicit
fifty-frame scheduling boundary check, with and without a target, using an
instrumented Spirit subclass that counts real decisions and delegates behavior.
`final-tests.log` and final XML contain 57 passing tests: Spirit 6, Minion 5,
BaalMinion 3 (also matched by the Minion wildcard), DeathMauler 7, DoomKnight 5,
VileDog 5 and Act1NativeAi 26. Tests also cover real-row selection, all three
difficulties, one-shot persistence over ten later decisions, busy action
retention, ten-frame pre-attack wait, target loss/reacquisition, warp and death.

The first final-test attempt and final Hell repeat were blocked by concurrent
path source edits referencing `RayResult.point`; both compile-failure logs are
retained. The path task corrected those references to `endpoint` independently.
No path files were edited by this task. The final tests and Hell repeat then
ran again; the retry uses output name `20261016-spirit-retry-d2-repeat`.

## Natural scenes

Real 1.10f MPQ; Normal/Nightmare/Hell; seed 1; 500 ticks; near and far.
Six executions plus an independent six-execution repeat: twelve total.
Each difficulty's extracted raw JSON lines are byte identical to the repeat;
SHA256 values and verification outcome are in `verification.log`.

The six near executions each produce exactly one ordinary SkillDo event,
then remain idle through the final sample. The six far executions have no
actions or movement. All selected executions resolve to Spirit without
fallback, warnings or errors. The Normal repair baseline has two fallback
scenes, both without actions. Original journals contain 1180 entries each;
1178 unselected NOT_RUN entries per journal are archived and excluded from
execution/pass counts. No arena or scene oracle production code was changed.

Nightmare and Hell near executions each have one actual damage event (79 and
131 HP respectively, identical on repeat). Normal near has no damage events or
HP loss and remains unaccepted for damage. The initial verifier incorrectly
assumed all difficulties matched Normal's zero-damage observation and failed;
`verification-initial-failure.log` records that failed assumption. The corrected
verifier requires one damage event in the higher-difficulty near scenes and
keeps Normal's zero damage as an explicit observation, not damage acceptance.
Native unit RNG, global
event scheduling, real terrain, GL/TCP and full-directory regression remain
outside this acceptance. The previously unresolved adjacent-wolf collision
failure is unchanged. Overall alignment remains PARTIAL.

## Reproduction

From repository root, set `D2_HOME` to the real 1.10f install. The init script
isolates Java outputs and the project cache isolates Gradle task history.

```powershell
./gradlew.bat --project-cache-dir build/spirit-gradle-cache -I docs/ai-arena/20261016-spirit-seed1/isolated.init.gradle :core:test --tests '*SpiritAiIntegrationTest' --tests '*MinionAiIntegrationTest' --tests '*DeathMaulerAiIntegrationTest' --tests '*DoomKnightAiIntegrationTest' --tests '*VileDogAiIntegrationTest' --tests '*Act1NativeAiIntegrationTest' --console=plain --max-workers=1
foreach ($pass in @('first','repeat')) {
  foreach ($difficulty in 0..2) {
    ./docs/ai-arena/20261016-spirit-seed1/run-arena.ps1 -Pass $pass -Difficulty $difficulty -RunName spirit-new -Evidence build/spirit-new-evidence
  }
}
./docs/ai-arena/20261016-spirit-seed1/verify.ps1 -Evidence build/spirit-new-evidence
```

Use unused output/evidence names and create the evidence directory first.
Copy `d0-baseline.jsonl` from this evidence directory for the verifier's
before/after fallback check. Existing evidence is protected against overwrite.
