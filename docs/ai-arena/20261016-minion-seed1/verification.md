# Minion Fn116 ordinary decision branch — PARTIAL

Reference: D2MOO `D2Game/src/AI/AiThink.cpp`,
`AITHINK_Fn116_Minion` (0x6FCE0A50). Port the ordinary, no-command branch:
melee attack chance slot 0, A2 chance slot 1, melee stall slot 1;
noncombat approach chance slot 2 and ranged stall slot 3.
Preserve the original bug explicitly documented upstream: A2 chance uses
slot 1 rather than the documented slot 4. Production table values, random
probabilities, collision and arena logic are unchanged.

Native command queue target selection, expiry, dead-target removal and forced
attack/approach are not implemented. Dedicated class selection therefore does
not establish full Fn116 parity. Shared unit RNG/global scheduling, actual
terrain, GL/TCP and the prior adjacent-wolf collision failure remain pending.

## Tests and retained failures

`baseline.log/xml`: five new Minion tests fail before implementation; the run
also selected three existing BaalMinion tests through the wildcard.
`fixed-tests.log` and the initial XML: 51 tests pass (Minion 5, BaalMinion 3,
DeathMauler 7, DoomKnight 5, VileDog 5, Act1NativeAi 26).
Tests cover all eleven real rows, all three difficulties' A1/A2 selection,
the slot-1 bug against deliberately contradictory slot 4, busy action retention,
exact melee/ranged stalls, approach and missing targets. Fixture probability
overrides are branch tests, not natural probability acceptance.

An additional hypothesis that distance 18 lies outside activation range failed:
`final-tests.log` and `activation-hypothesis-failure.xml` retain that failure.
The hypothesis test was removed; no production change was made to force it.
`accepted-tests.log` and final XML record the repeated original test suite.

## Natural scenes

Real 1.10f MPQ, eleven rows, near/far, difficulty 0/1/2, seed 1, 500 ticks.
66 executions plus an independent 66-execution repeat: 132 executions total.
All three difficulty journals are byte identical to their repeat (hashes in
`verification.log`). All selected scenes use Minion, without fallback, warnings
or errors. The archived original journal includes 1180 catalog entries per run;
1158 unselected NOT_RUN records are excluded from execution/pass counts.

Each difficulty has eleven near ACTION_OBSERVED scenes with actual ordinary
attack damage, and eleven far NO_ACTION scenes with no movement or damage.
The 22-scene Normal baseline used GenericMonster fallback; its far scenes also
had no action. Far behavior remains unresolved and is not accepted as correct.
`strict-action-failure.log` preserves the initial all-scenes action/damage check
failure. `verify.ps1` now explicitly separates near damage checks from far
observation/regression checks; it does not claim all scenes pass behavior.
No skill-specific natural oracle was added, and no full-directory regression
claim is made during concurrent path/AI work.

## Reproduce from repository root

Set `D2_HOME` to the real 1.10f install. Use unused RunName and evidence paths.

```powershell
./gradlew.bat --project-cache-dir build/minion-gradle-cache -I docs/ai-arena/20261016-minion-seed1/isolated.init.gradle :core:test --tests '*MinionAiIntegrationTest' --tests '*DeathMaulerAiIntegrationTest' --tests '*DoomKnightAiIntegrationTest' --tests '*VileDogAiIntegrationTest' --tests '*Act1NativeAiIntegrationTest' --console=plain --max-workers=1
foreach ($pass in @('first','repeat')) {
  foreach ($difficulty in 0..2) {
    ./docs/ai-arena/20261016-minion-seed1/run-arena.ps1 -Pass $pass -Difficulty $difficulty -RunName minion-new -Evidence build/minion-new-evidence
  }
}
./docs/ai-arena/20261016-minion-seed1/verify.ps1 -Evidence build/minion-new-evidence
```

Create the new evidence directory first. The verifier also expects the retained
`d0-baseline.jsonl` from this directory. Isolated Java outputs and a dedicated
Gradle project cache avoid concurrent output cleanup. Unrelated shared-tree
changes are excluded from the commit.
