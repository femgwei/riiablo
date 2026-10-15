# VileDog Fn069 — 2026-10-16

Source: D2MOO `D2Game/src/AI/AiThink.cpp`,
`AITHINK_Fn069_VileDog` (0x6FCDCBF0), parameters 0/1/2:
attack chance, melee stall, approach chance.

The dedicated AI initializes once and idles five frames before target/combat
selection. It then chooses A1 or configured melee stall in combat; otherwise it
approaches on the approach roll, or idles ten frames. Initialization also runs
without a target. Active actions retain the shared NativeDecisionAI lifecycle.

All ten real rows resolve to the dedicated class: painworm1–5 and vilechild1–5.
Five new tests cover row selection, initial five-frame delay at all three
difficulties, busy action preservation, configured melee stall, independent
ten-frame ranged idle/approach, and initialization without a target. Branch
fixtures override probabilities only to isolate decisions; arena probabilities
and production data are unchanged.

The first baseline had an incorrect target-return coordinate in the new
no-target test (1,0 instead of fixture-relative 11,10). It is preserved in
`baseline-first.log/xml`. The corrected fixture was rerun before implementation:
all five tests still failed (`baseline.log/xml`). The implementation then passed
40 tests: VileDog 5, Act1NativeAi 26, Megademon 9. Failures/errors/skips are zero;
`fixed-tests.log` and the three final XML files retain the results.

Real MPQ arena: Normal, seed 1, 500 ticks/scene, 557 rows / 1174 scenes.
All twenty VileDog near/far scenes observed actions and damage. Ten near-scene
logs show IDLE / casting=false at tick 1, followed by natural ordinary attacks
and positive damage events. These log checks are baseline observations, not
dedicated full behavioral oracles; exact wait boundaries are covered by the
focused tests. The existing sixty dedicated assertions remain passing.

Counts: ACTION_OBSERVED 536, AI_FALLBACK 334, MOVEMENT_ONLY 7, NO_ACTION 186,
TERMINAL_OBSERVED 4, WARNING 107. No runtime/error-log, NOT_RUN or
SPECIAL_NOT_TRIGGERED results. Fallback now covers 44 AI names / 167 rows.
Only the twenty VileDog results changed versus the Megademon batch; no scenes
were added. Full independent rerun and comparison are checked by `verify.ps1`;
its output is retained in `comparison.log`. Results, repeat, and zipped scene
logs are retained alongside this file.

Reproduce from repository root with
`D2_HOME=G:\BaiduNetdiskDownload\Diablo II 1.10F`:

```powershell
./gradlew.bat :core:test --tests '*VileDogAiIntegrationTest' --tests '*Act1NativeAiIntegrationTest' --tests '*MegademonAiIntegrationTest' --console=plain --max-workers=1
./gradlew.bat :server:d2gs:monsterAiArena -ParenaOutput=server/d2gs/build/monster-ai-arena/20261016-viledog-full --console=plain --max-workers=1
./gradlew.bat :server:d2gs:monsterAiArena -ParenaOutput=server/d2gs/build/monster-ai-arena/20261016-viledog-repeat --console=plain --max-workers=1
./docs/ai-arena/20261016-viledog-seed1/verify.ps1
```

Arena output directories must not already exist (or use new paths and pass
them to verify.ps1). The archive stores repeat results as `repeat-results.jsonl`;
the script expects each live run directory to contain `results.jsonl`.

Status: PARTIAL. Native walk flags 7 are not projected by shared movement;
native RNG stream and scheduler/global-clock parity remain pending. No claim
of complete project tests, real GL/TCP, terrain navigation, or three-difficulty
multi-seed arena acceptance. Prior adjacent wolf landing failure remains
unresolved and is not counted as passing. Concurrent object/path work was
excluded from this commit.
