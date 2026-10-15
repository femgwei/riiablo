# DeathMauler MonSeq — 2026-10-16 (PARTIAL)

This batch implements only the animation prerequisite for DeathMaul. It does
not implement SrvDo136, missile 502 activation/NextDelay collision, damage, or
CltDo76/trails. The original Do136 unsupported warning remains enabled.

The real 1.10f MPQ MonSeq `seq_deathmaulerdig` contains **35** points, all A2:
`0,0,1,1,2,2,3,3,4,4,5,6,7,7,7,7,8,8,8,8,9,9,9,9,9,10,10,10,10,11,11,11,11,12,13`.
Only point 13 carries event 1. All six DeathMauler MonStats rows name this
sequence for Skill1 (308). DeathMauler now starts A2 and marks the native
sequence after a successful cast. Ordinary A2 COF keyframes are bypassed.
The sequence uses the D2Common initialization default of 256, with existing
state animation-rate scaling and FREEZE handling; it finishes once at point
35. Starting a new action clears the cursor and flag. Local client playback
follows the authoritative source frames rather than playing A2 independently.

`baseline.log`, `baseline-ai.xml` and `baseline-data.xml` preserve the actual
pre-change run: real-table assertions passed, and the ranged AI test failed
because mode was XX (14) instead of A2 (5). This is a behavioral regression
baseline, not a deliberately missing-class run.

`final-tests.log` and the six `TEST-*.xml` files record 60 passing tests,
with no failures/errors/skips: data 1, sequence 4, DeathMauler AI 7, client
animation 8, Inferno integration 14, Act1 native AI 26. The sequence tests
cover every mapped point, unrelated COF speed/markers, single action/finish,
start gating, FREEZE/resume, fractional/accelerated state rates, and reset.
The client test loads the real M5A2HTH COF. `fixed.log` was an earlier passing
59-test run before adding the state-rate test. `repeat-tests.log` records an
independent cleanTest rerun of all 60 tests.

Both complete Normal/seed-1/500-tick arenas contain 557 monster rows / 1174
scenes. Byte-identical results SHA256:
`9EBE74C75E91431B10EAD2981170E3A14D93489E0A5AFE1C9C95243F335F6F5C`.
Statuses remain ACTION_OBSERVED 548, AI_FALLBACK 316, MOVEMENT_ONLY 7,
NO_ACTION 188, TERMINAL_OBSERVED 4, WARNING 111. No runtime errors.
All 60 pre-existing dedicated arena assertions pass.

`changes.json` compares every result field against the prior Fn130 batch.
Only deathmauler3–6 far differ: actions become 14 (previously 32/34/35/35),
warnings become 14 (previously 65/69/70/70). Each now has one unsupported
Do136 diagnostic per full 35-point action; logs show SkillDo at ticks
14,49,84,119... (one tick includes action startup). Removing the invalid XX
mode fallback is an actual mode fix; Do136 warnings have not been hidden.
Their status remains WARNING with zero damage events and zero damage.
Deathmauler1/2 far stay NO_ACTION; all six near scenes still observe ordinary
melee damage with no warnings. This is **not** DeathMaul skill-effect acceptance.
`logs.zip` preserves the full first-run scene logs; `verify.ps1` checks all
scene differences, repeat hash, existing assertions and retained limitations.

Run from repository root with the real resource installation:

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
./gradlew.bat -I docs/ai-arena/20261016-deathmauler-seed1/isolated.init.gradle :core:cleanTest :core:test --tests '*DeathMaulDataTest' --tests '*DeathMaulSequenceTest' --tests '*DeathMaulerAiIntegrationTest' --tests '*AnimationStepperTest' --tests '*MonInfernoIntegrationTest' --tests '*Act1NativeAiIntegrationTest' --console=plain --max-workers=1
./gradlew.bat -I docs/ai-arena/20261016-deathmauler-seed1/isolated.init.gradle :server:d2gs:monsterAiArena -ParenaOutput=server/d2gs/build/monster-ai-arena/20261016-deathmauler-monseq-new --console=plain --max-workers=1
./docs/ai-arena/20261016-deathmauler-monseq/verify.ps1
```

The prior committed isolation init is reused, without modifying prior
evidence. Its class-absent baseline task is not used here. No production AI
probabilities, collision, arena oracle or warnings were relaxed. Native RNG,
global scheduling, full skill damage and real GL/TCP acceptance remain pending.
Concurrent path/UI changes are excluded from this batch.
