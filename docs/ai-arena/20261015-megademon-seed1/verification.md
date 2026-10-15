# Megademon Fn089 — 2026-10-15/16

Implemented `AITHINK_Fn089_Megademon` (D2MOO AiThink.cpp, 0x6FCDF780):
strict range and timer comparisons, separate ranged/melee probabilities, state
clearing, swing/circle/idle priority, and cooldown consumption before UseSkill.
Busy actions advance the local timer without restarting the cast. Skill 319 uses
the configured S1 mode and the existing NativeMonInferno runtime.

`baseline.log` / `baseline.xml`: all five initial tests failed before the AI existed.
`fixed-first.log`: initial focused repair run passed. `final-tests.log` and XML:
27 tests passed (Megademon 9, FrozenHorror 4, MonInferno 14), zero failures,
errors or skips. `arena-tests.log` and XML: 14 server arena tests passed.

Real MPQ run: D2_HOME=`G:\BaiduNetdiskDownload\Diablo II 1.10F`.
Normal, seed 1, 500 ticks per scene, 557 rows / 1174 scenes. Natural probabilities
were preserved. All six Megademon rows (megademon1–5, venomlord) passed the
inferno-cycle oracle: valid owned skill missiles, pulse timing/deadline/tail,
INFERNO reset and subsequent cast, plus a skill-319 missile fire-only hit whose
damage matches the victim HP reduction. Ordinary attacks cannot satisfy this
damage gate. All 60 dedicated scene assertions passed.

Counts: ACTION_OBSERVED 516, AI_FALLBACK 354, MOVEMENT_ONLY 7,
NO_ACTION 186, TERMINAL_OBSERVED 4, WARNING 107. No runtime/error-log,
NOT_RUN or SPECIAL_NOT_TRIGGERED results. Fallback now covers 45 AI names /
177 rows. Six Megademon far scenes remain NO_ACTION and are not hidden.
Against the Sentry batch, only the 12 shared Megademon near/far records changed;
six new inferno-cycle scenes were added. Full independent repeat was byte-identical:

`46E73D500B7BC983D5B9ED5D345C80F7D4893D24CD3D346F7C95450885DE6FD4`

Results are in `results.jsonl` and `repeat-results.jsonl`; per-scene logs in
`logs.zip`. Run from repository root with D2_HOME above:

```powershell
./gradlew.bat :core:test --tests '*MegademonAiIntegrationTest' --tests '*MonInfernoIntegrationTest' --tests '*FrozenHorrorAiIntegrationTest' --console=plain --max-workers=1
./gradlew.bat :server:d2gs:test --tests '*MonsterAiArenaTest' --console=plain --max-workers=1
./gradlew.bat :server:d2gs:monsterAiArena -ParenaOutput=server/d2gs/build/monster-ai-arena/UNUSED-DIRECTORY --console=plain --max-workers=1
```

Status remains PARTIAL: shared approach omits native flags 7; circle approximates
sub_6FCD0E80 without native velocity types 5/6 and unit-RNG direction selection.
The timer uses elapsed local frames rather than the global native game clock;
RNG stream parity remains pending. Focused fixtures isolate branch inputs; the
direct ranged branch test supplies native combat/distance inputs explicitly.
These runs do not establish full-project, real GL/TCP, terrain, or three-difficulty
multi-seed acceptance. The prior adjacent wolf landing regression was not changed
or counted as passing in this batch. Other simultaneous workspace edits were
excluded from this commit.
