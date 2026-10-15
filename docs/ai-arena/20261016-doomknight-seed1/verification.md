# DoomKnight Fn072 — 2026-10-16

Source: D2MOO `D2Game/src/AI/AiThink.cpp`,
`AITHINK_Fn072_DoomKnight` (0x6FCDD790). Parameters 0–3 are attack chance,
melee stall, approach chance, and regular/ranged stall. Combat selects A1 or
the melee stall; noncombat selects approach or the regular stall. Unlike
VileDog there is no initial five-frame wait. `sub_6FCD0410` (AiTactics.cpp,
0x6FCD0410) sets velocity type 13 before walking with flags 7.

All three real MonStats rows now resolve to a dedicated DoomKnight AI. The
implementation reuses NativeDecisionAI for busy action protection, targeting,
ordinary attacks and movement. It does not replace shared pathfinding.

Five focused tests failed before the implementation (`baseline.log/xml`).
After the repair, all 45 selected tests passed: DoomKnight 5, VileDog 5,
Act1NativeAi 26, Megademon 9; failures/errors/skips are zero. Coverage includes
dedicated selection, immediate A1 attacks in all three difficulties, preservation
of active actions, separate exact melee/ranged stall waits, approach retry,
absence of an initial delay, and no-target behavior. Probability overrides
isolate branches in fixtures only; production tables and arena probabilities
were not changed. `fixed-tests.log` and final XML retain the evidence.

The full real-MPQ arena uses Normal, seed 1 and 500 ticks/scene. `verify.ps1`
checks all 1174 scene records, the existing sixty dedicated assertions,
DoomKnight class resolution, natural ordinary attack plus positive damage in
each near scene, six direct changes plus six identified helper effects, and byte-identical
full independent repeat. Its output is retained in `comparison.log`. These
near/far observations are baseline checks, not a new dedicated full-behavior
oracle. The focused tests establish the isolated branch timing.

The first comparison rejected dkmag1/near as an unexpected change; this failure
is retained in `comparison-first.log`. The arena builds helpers from the real
MonStats minion fields: dkmag1 uses dkfig1, dkmag2 uses dkfig2, doomknight3 uses
doomknight1 (entity 2). Their six near/far scenes therefore also execute the new
DoomKnight AI. Each loses exactly one helper fallback warning; only warning,
damage-event count and damage-total fields change. The comparator now verifies
those exact six dependency relationships and field boundaries, requiring all
other fields and all unrelated scene records to remain identical. OblivionKnight
itself remains GenericMonster; no claim of its port or native correctness is made.

Real rows are dkfig1, dkfig2 and doomknight1; all six near/far scenes observed
actions and damage. Status counts: ACTION_OBSERVED 542, AI_FALLBACK 328,
MOVEMENT_ONLY 7, NO_ACTION 186, TERMINAL_OBSERVED 4, WARNING 107.
Fallback covers 43 AI names / 164 rows. Warnings remain visible; no runtime/error-log,
NOT_RUN or SPECIAL_NOT_TRIGGERED results occurred.

Results and independent repeat are stored as `results.jsonl` and
`repeat-results.jsonl`; all per-scene logs are in `logs.zip`.

Reproduce from repository root with
`D2_HOME=G:\BaiduNetdiskDownload\Diablo II 1.10F`:

```powershell
./gradlew.bat :core:test --tests '*DoomKnightAiIntegrationTest' --tests '*VileDogAiIntegrationTest' --tests '*Act1NativeAiIntegrationTest' --tests '*MegademonAiIntegrationTest' --console=plain --max-workers=1
./gradlew.bat :server:d2gs:monsterAiArena -ParenaOutput=server/d2gs/build/monster-ai-arena/20261016-doomknight-full --console=plain --max-workers=1
./gradlew.bat :server:d2gs:monsterAiArena -ParenaOutput=server/d2gs/build/monster-ai-arena/20261016-doomknight-repeat --console=plain --max-workers=1
./docs/ai-arena/20261016-doomknight-seed1/verify.ps1
```

Use unused arena directories; if paths change, supply Arena/Repeat arguments
to verify.ps1. The script expects each live directory to contain results.jsonl;
the archived repeat file has the distinct repeat-results.jsonl name.

Status remains PARTIAL: shared approach does not project native velocity type
13 or walk flags 7. Native unit RNG and global scheduler parity are still
pending. These runs do not establish full-project, GL/TCP, terrain, or
three-difficulty multi-seed arena acceptance. The prior adjacent wolf landing
failure was not modified or counted as passing. Concurrent object/path edits
are excluded from this commit.
