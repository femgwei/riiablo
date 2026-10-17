# Scarab / Baboon arena verification ¡ª 2026-10-18

At source commit 14868abcf417eeea4da8b258fbf3ecf087b11c62, ran the real server ECS arena with installed Diablo II 1.10F data. No production code changed in this batch. Source hashes are recorded alongside this report.

Executed scarab1¨Cscarab7 and baboon1¨Cbaboon8, each near/far, on Normal/Nightmare/Hell, seed 1, 500 authoritative 25Hz ticks per scene (20 simulated seconds). All three runs completed: 90 selected scenes. The archive contains per-scene and runner logs. results.jsonl contains only these 90 executed scenes; each original full-catalog run additionally recorded 1165 NOT_RUN entries, which are not counted as passes.

- Baboon: 48 ACTION_OBSERVED scenes, all with attack and damage events, no warnings/errors/fallback. All 24 far scenes recorded position changes.
- Scarab: 42 WARNING scenes, all with action and damage events. All 21 far scenes recorded position changes. Total 4226 warnings, zero errors/fallback. Warning kinds were missing SCS1HTH COF/keyframes and AMAZON_JAB_ANIM attack_keyframe_missing.
- Near-scene zero movement is compatible with attacking a nearby stationary player; it is not treated as a failure or evidence of pursuit.

These runs did not reproduce complete immobility in the synthetic server arena. They do not prove the user's actual-map/client issue is fixed, nor establish native AI/animation equivalence. Scarab is explicitly not accepted as passing: issuing Jab and observing damage does not validate its animation/keyframes. The remaining Jab dispatch issue belongs to the shared execution chain, which this chat has not modified. No messages were sent to another chat.

Reproduction from the repository root (PowerShell): set D2_HOME to the installed 1.10F folder; run ./gradlew.bat -I docs/ai-arena/20261018-scarab-baboon/isolated.init.gradle :server:d2gs:monsterAiArena with --max-workers=1 --console=plain and these properties:

- '-ParenaMonsters=scarab1,scarab2,scarab3,scarab4,scarab5,scarab6,scarab7,baboon1,baboon2,baboon3,baboon4,baboon5,baboon6,baboon7,baboon8'
- -ParenaTicks=500 -ParenaSeed=1
- -ParenaDifficulty=0, then 1, then 2 in separate sequential runs
- -ParenaOutput=<a new non-existing output directory for each run>

The arena uses a synthetic map, stationary high-HP player and no network or renderer. Movement count is the number of ticks whose position differs by more than the arena tolerance, not a count of accepted path requests. ACTION_OBSERVED, BUILD SUCCESSFUL and COMPLETED are diagnostic observations, not correctness passes. These runs cover one seed and do not isolate which historical change caused movement; no original-version comparison or client run was performed.
